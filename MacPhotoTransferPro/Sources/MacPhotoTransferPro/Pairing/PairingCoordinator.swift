import Foundation

struct PairingCode: Equatable, Sendable {

    let digits: String
    let expiresAt: Date

    static func generate(now: Date = .now, lifetime: TimeInterval = 180) -> PairingCode {
        var generator = SystemRandomNumberGenerator()
        let digits = (0..<6)
            .map { _ in String(Int.random(in: 0...9, using: &generator)) }
            .joined()
        return PairingCode(digits: digits, expiresAt: now.addingTimeInterval(lifetime))
    }

    func isValid(at date: Date) -> Bool {
        date < expiresAt
    }
}

struct PairingApproval: Equatable, Sendable {
    let deviceId: String
    let deviceName: String
}

enum PairingFailure: Error, Equatable, Sendable {
    case unsupportedProtocol
    case invalidCode
    case throttled
    case declined
    case timedOut
}

enum AuthorizationFailure: String, Equatable, Sendable {
    case missingCredentials
    case unknownDevice
    case staleTimestamp
    case replayedNonce
    case badSignature
}

enum RequestAuthorization: Equatable, Sendable {
    case authorized(PairedDevice)
    case rejected(AuthorizationFailure)
}

/// Owns pairing state and authorizes every signed request.
///
/// Pairing deliberately requires two independent things: the code, which proves the
/// sender can see this Mac's screen, and an explicit approval, which proves a human
/// is present at the moment of pairing.
actor PairingCoordinator {

    typealias ApprovalHandler = @Sendable (PairingApproval) async -> Bool

    /// Attempts with no identifiable peer share one budget rather than escaping the limit.
    static let unknownSource = "unknown"

    static let freshnessWindow: TimeInterval = 300
    static let approvalTimeout: TimeInterval = 60

    // Wrong guesses never void the code, because anyone on the network can make them and
    // the user is looking at that code. Instead each source spends its own small budget,
    // and a window caps how fast guesses can arrive in total. A host with many IPv6
    // addresses defeats the per-source cap, so the window is what actually bounds the
    // search: 30 per minute over a 3 minute code is 90 guesses against a six-digit space.
    // A saturated window delays the real sender by a minute rather than locking them out.
    private static let maximumAttemptsPerSource = 5
    private static let maximumAttemptsPerWindow = 30
    private static let attemptWindow: TimeInterval = 60
    private static let minimumRetryInterval: TimeInterval = 1

    private let receiverId: String
    private let receiverName: String
    private let store: any PairedDeviceStoring
    private let approvalHandler: ApprovalHandler
    private let approvalTimeout: TimeInterval
    private let nonceCache = NonceCache(window: PairingCoordinator.freshnessWindow)
    private let now: @Sendable () -> Date

    private struct AttemptRecord {
        var failures = 0
        var lastAttemptAt: Date?
    }

    private var activeCode: PairingCode?
    private var attemptsBySource: [String: AttemptRecord] = [:]
    private var recentFailures: [Date] = []
    private var cachedDevices: [String: PairedDevice]?

    init(
        receiverId: String,
        receiverName: String,
        store: any PairedDeviceStoring,
        approvalTimeout: TimeInterval = PairingCoordinator.approvalTimeout,
        now: @escaping @Sendable () -> Date = { .now },
        approvalHandler: @escaping ApprovalHandler
    ) {
        self.receiverId = receiverId
        self.receiverName = receiverName
        self.store = store
        self.approvalTimeout = approvalTimeout
        self.now = now
        self.approvalHandler = approvalHandler
    }

    // MARK: - Pairing window

    func beginPairing() -> PairingCode {
        let code = PairingCode.generate(now: now())
        activeCode = code
        resetAttempts()
        return code
    }

    func cancelPairing() {
        activeCode = nil
        resetAttempts()
    }

    func pair(
        _ request: PairRequest,
        source: String = unknownSource
    ) async -> Result<PairResponse, PairingFailure> {
        guard request.protocolVersion == TransferProtocol.version else {
            return .failure(.unsupportedProtocol)
        }
        switch consumeCodeIfValid(request.pairingCode, from: source) {
        case .accepted:
            break
        case .rejected:
            return .failure(.invalidCode)
        case .throttled:
            // Told apart from a wrong code so the user is not led to believe the code on
            // screen has stopped working when someone else is guessing at it.
            return .failure(.throttled)
        }

        let approval = PairingApproval(deviceId: request.deviceId, deviceName: request.deviceName)
        switch await awaitApproval(for: approval) {
        case .approved:
            return .success(issueCredentials(for: request))
        case .declined:
            return .failure(.declined)
        case .timedOut:
            return .failure(.timedOut)
        }
    }

    private enum CodeCheck {
        case accepted
        case rejected
        case throttled
    }

    /// A code is single use, and wrong guesses are budgeted rather than fatal to the code.
    private func consumeCodeIfValid(_ supplied: String, from source: String) -> CodeCheck {
        let currentTime = now()
        guard let code = activeCode, code.isValid(at: currentTime) else { return .rejected }
        guard canAttempt(from: source, at: currentTime) else { return .throttled }

        var record = attemptsBySource[source] ?? AttemptRecord()
        record.lastAttemptAt = currentTime

        guard ConstantTime.equals(code.digits, supplied) else {
            record.failures += 1
            attemptsBySource[source] = record
            recentFailures.append(currentTime)
            return .rejected
        }
        attemptsBySource[source] = record
        activeCode = nil
        return .accepted
    }

    private func canAttempt(from source: String, at date: Date) -> Bool {
        recentFailures.removeAll { date.timeIntervalSince($0) >= Self.attemptWindow }
        guard recentFailures.count < Self.maximumAttemptsPerWindow else { return false }

        guard let record = attemptsBySource[source] else { return true }
        guard record.failures < Self.maximumAttemptsPerSource else { return false }
        guard let last = record.lastAttemptAt else { return true }
        return date.timeIntervalSince(last) >= Self.minimumRetryInterval
    }

    private func resetAttempts() {
        attemptsBySource = [:]
        recentFailures = []
    }

    private enum ApprovalOutcome {
        case approved
        case declined
        case timedOut
    }

    private func awaitApproval(for approval: PairingApproval) async -> ApprovalOutcome {
        let handler = approvalHandler
        let timeout = approvalTimeout

        return await withTaskGroup(of: ApprovalOutcome?.self) { group in
            group.addTask { await handler(approval) ? .approved : .declined }
            group.addTask {
                try? await Task.sleep(nanoseconds: UInt64(timeout * 1_000_000_000))
                return .timedOut
            }
            let outcome = await group.next() ?? .timedOut
            group.cancelAll()
            return outcome ?? .timedOut
        }
    }

    private func issueCredentials(for request: PairRequest) -> PairResponse {
        let device = PairedDevice.issue(
            deviceId: request.deviceId,
            deviceName: request.deviceName,
            now: now()
        )
        try? store.save(device)
        cachedDevices?[device.deviceToken] = device

        return PairResponse(
            receiverId: receiverId,
            receiverName: receiverName,
            deviceToken: device.deviceToken,
            secretBase64: device.secret.base64EncodedString()
        )
    }

    // MARK: - Paired devices

    func pairedDevices() -> [PairedDevice] {
        Array(devicesByToken().values).sorted { $0.pairedAt > $1.pairedAt }
    }

    func revoke(deviceToken: String) {
        try? store.delete(deviceToken: deviceToken)
        cachedDevices?.removeValue(forKey: deviceToken)
    }

    private func devicesByToken() -> [String: PairedDevice] {
        if let cachedDevices { return cachedDevices }
        let loaded = Dictionary(
            uniqueKeysWithValues: ((try? store.all()) ?? []).map { ($0.deviceToken, $0) }
        )
        cachedDevices = loaded
        return loaded
    }

    // MARK: - Request authorization

    func authorize(
        method: String,
        path: String,
        bodySha256Hex: String,
        headers: [String: String]
    ) async -> RequestAuthorization {
        guard let deviceToken = headers[TransferProtocol.Header.deviceToken],
              let timestampText = headers[TransferProtocol.Header.timestamp],
              let timestamp = Int64(timestampText),
              let nonce = headers[TransferProtocol.Header.nonce],
              let signature = headers[TransferProtocol.Header.signature] else {
            return .rejected(.missingCredentials)
        }
        guard let device = devicesByToken()[deviceToken] else {
            return .rejected(.unknownDevice)
        }

        let currentTime = now()
        let skew = abs(currentTime.timeIntervalSince1970 - Double(timestamp))
        guard skew <= Self.freshnessWindow else {
            return .rejected(.staleTimestamp)
        }

        let canonicalString = RequestSignature.canonicalString(
            method: method,
            path: path,
            timestamp: timestamp,
            nonce: nonce,
            bodySha256Hex: bodySha256Hex
        )
        guard RequestSignature.isValid(
            signatureBase64: signature,
            canonicalString: canonicalString,
            secret: device.secret
        ) else {
            return .rejected(.badSignature)
        }

        // Claimed only after the signature checks out, so unauthenticated callers
        // cannot exhaust the cache or invalidate a legitimate sender's nonce.
        guard await nonceCache.claim(nonce, at: currentTime) else {
            return .rejected(.replayedNonce)
        }
        return .authorized(device)
    }
}

enum ConstantTime {

    static func equals(_ lhs: String, _ rhs: String) -> Bool {
        let left = Array(lhs.utf8)
        let right = Array(rhs.utf8)
        guard left.count == right.count else { return false }

        var difference: UInt8 = 0
        for index in left.indices {
            difference |= left[index] ^ right[index]
        }
        return difference == 0
    }
}
