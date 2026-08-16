import Foundation
import XCTest
@testable import MacPhotoTransferPro

final class PairingCoordinatorTests: XCTestCase {

    private var store: InMemoryPairedDeviceStore!

    override func setUp() {
        store = InMemoryPairedDeviceStore()
    }

    func testCorrectCodeAndApprovalIssuesCredentials() async {
        let coordinator = makeCoordinator(approves: true)
        let code = await coordinator.beginPairing()

        let result = await coordinator.pair(request(code: code.digits))

        let response = try? result.get()
        XCTAssertEqual(response?.receiverId, "receiver-1")
        XCTAssertEqual(response?.receiverName, "Test Mac")
        XCTAssertEqual(Data(base64Encoded: response?.secretBase64 ?? "")?.count, 32)
        XCTAssertEqual(try? store.all().count, 1)
    }

    func testWrongCodeIsRejectedWithoutAskingTheUser() async {
        let askedForApproval = Expectation()
        let coordinator = PairingCoordinator(
            receiverId: "receiver-1",
            receiverName: "Test Mac",
            store: store
        ) { _ in
            askedForApproval.fulfil()
            return true
        }
        _ = await coordinator.beginPairing()

        let result = await coordinator.pair(request(code: "000000"))

        XCTAssertEqual(result.failure, .invalidCode)
        XCTAssertFalse(askedForApproval.isFulfilled)
        XCTAssertEqual(try? store.all().count, 0)
    }

    func testDeclinedApprovalIssuesNothing() async {
        let coordinator = makeCoordinator(approves: false)
        let code = await coordinator.beginPairing()

        let result = await coordinator.pair(request(code: code.digits))

        XCTAssertEqual(result.failure, .declined)
        XCTAssertEqual(try? store.all().count, 0)
    }

    func testApprovalTimeoutIsReported() async {
        let coordinator = PairingCoordinator(
            receiverId: "receiver-1",
            receiverName: "Test Mac",
            store: store,
            approvalTimeout: 0.1
        ) { _ in
            try? await Task.sleep(nanoseconds: 5_000_000_000)
            return true
        }
        let code = await coordinator.beginPairing()

        let result = await coordinator.pair(request(code: code.digits))

        XCTAssertEqual(result.failure, .timedOut)
    }

    func testCodeCannotBeUsedTwice() async {
        let coordinator = makeCoordinator(approves: true)
        let code = await coordinator.beginPairing()

        _ = await coordinator.pair(request(code: code.digits))
        let second = await coordinator.pair(request(code: code.digits))

        XCTAssertEqual(second.failure, .invalidCode)
    }

    func testExpiredCodeIsRejected() async {
        let clock = MutableClock(now: Date(timeIntervalSince1970: 1_000_000))
        let coordinator = PairingCoordinator(
            receiverId: "receiver-1",
            receiverName: "Test Mac",
            store: store,
            now: { clock.now }
        ) { _ in true }
        let code = await coordinator.beginPairing()

        clock.advance(by: 181)
        let result = await coordinator.pair(request(code: code.digits))

        XCTAssertEqual(result.failure, .invalidCode)
    }

    func testOneSourceRunsOutOfGuesses() async {
        let clock = MutableClock(now: .now)
        let coordinator = makeCoordinator(approves: true, now: { clock.now })
        let code = await coordinator.beginPairing()

        for _ in 0..<5 {
            _ = await coordinator.pair(request(code: "000000"), source: "10.0.0.9")
            clock.advance(by: 2)
        }
        let result = await coordinator.pair(request(code: code.digits), source: "10.0.0.9")

        XCTAssertEqual(result.failure, .throttled)
    }

    /// The griefing case: someone else's wrong guesses must not void the code the user
    /// is reading off the screen.
    func testWrongGuessesFromOneSourceLeaveAnotherAbleToPair() async {
        let clock = MutableClock(now: .now)
        let coordinator = makeCoordinator(approves: true, now: { clock.now })
        let code = await coordinator.beginPairing()

        for _ in 0..<5 {
            _ = await coordinator.pair(request(code: "000000"), source: "10.0.0.9")
            clock.advance(by: 2)
        }
        let result = await coordinator.pair(request(code: code.digits), source: "10.0.0.4")

        XCTAssertNotNil(try? result.get())
    }

    func testGuessesInQuickSuccessionAreThrottled() async {
        let clock = MutableClock(now: .now)
        let coordinator = makeCoordinator(approves: true, now: { clock.now })
        let code = await coordinator.beginPairing()

        _ = await coordinator.pair(request(code: "000000"), source: "10.0.0.9")
        let immediate = await coordinator.pair(request(code: code.digits), source: "10.0.0.9")
        clock.advance(by: 2)
        let afterWaiting = await coordinator.pair(request(code: code.digits), source: "10.0.0.9")

        XCTAssertEqual(immediate.failure, .throttled)
        XCTAssertNotNil(try? afterWaiting.get())
    }

    /// A host with many addresses walks past the per-source cap, so the window is what
    /// bounds how fast the six-digit space can be searched.
    func testGuessesSpreadAcrossSourcesAreCappedPerWindow() async {
        let clock = MutableClock(now: .now)
        let coordinator = makeCoordinator(approves: true, now: { clock.now })
        let code = await coordinator.beginPairing()

        await exhaustTheWindow(of: coordinator, clock: clock)
        let result = await coordinator.pair(request(code: code.digits), source: "10.0.1.1")

        XCTAssertEqual(result.failure, .throttled)
    }

    /// The point of throttling rather than voiding: a flood delays the real sender for a
    /// window, and the code they are reading off the screen still works afterwards.
    func testAFloodOfGuessesDoesNotVoidTheCode() async {
        let clock = MutableClock(now: .now)
        let coordinator = makeCoordinator(approves: true, now: { clock.now })
        let code = await coordinator.beginPairing()

        await exhaustTheWindow(of: coordinator, clock: clock)
        clock.advance(by: 61)
        let result = await coordinator.pair(request(code: code.digits), source: "10.0.1.1")

        XCTAssertNotNil(try? result.get())
    }

    private func exhaustTheWindow(of coordinator: PairingCoordinator, clock: MutableClock) async {
        for attempt in 0..<30 {
            _ = await coordinator.pair(request(code: "000000"), source: "10.0.0.\(attempt)")
            clock.advance(by: 1)
        }
    }

    func testMismatchedProtocolVersionIsRejected() async {
        let coordinator = makeCoordinator(approves: true)
        let code = await coordinator.beginPairing()

        let result = await coordinator.pair(PairRequest(
            protocolVersion: TransferProtocol.version + 1,
            deviceId: "device-1",
            deviceName: "Test Pixel",
            pairingCode: code.digits
        ))

        XCTAssertEqual(result.failure, .unsupportedProtocol)
    }

    // MARK: - Authorization

    func testRevokedDeviceCanNoLongerSign() async {
        let device = PairedDevice.stub()
        let coordinator = PairingCoordinator(
            receiverId: "receiver-1",
            receiverName: "Test Mac",
            store: InMemoryPairedDeviceStore(seeded: [device])
        ) { _ in true }

        let before = await authorize(coordinator, device: device, nonce: "n1")
        await coordinator.revoke(deviceToken: device.deviceToken)
        let after = await authorize(coordinator, device: device, nonce: "n2")

        XCTAssertEqual(before, .authorized(device))
        XCTAssertEqual(after, .rejected(.unknownDevice))
    }

    func testMissingHeadersAreRejected() async {
        let coordinator = makeCoordinator(approves: true)

        let authorization = await coordinator.authorize(
            method: "POST",
            path: "/v1/transfers",
            bodySha256Hex: RequestSignature.sha256Hex(of: Data()),
            headers: [:]
        )

        XCTAssertEqual(authorization, .rejected(.missingCredentials))
    }

    func testSignatureIsBoundToPathAndMethod() async {
        let device = PairedDevice.stub()
        let coordinator = PairingCoordinator(
            receiverId: "receiver-1",
            receiverName: "Test Mac",
            store: InMemoryPairedDeviceStore(seeded: [device])
        ) { _ in true }

        let headers = signatureHeaders(device: device, method: "POST", path: "/v1/transfers", nonce: "n1")
        let replayedElsewhere = await coordinator.authorize(
            method: "POST",
            path: "/v1/transfers/abc/complete",
            bodySha256Hex: RequestSignature.sha256Hex(of: Data()),
            headers: headers
        )

        XCTAssertEqual(replayedElsewhere, .rejected(.badSignature))
    }

    // MARK: - Helpers

    private func makeCoordinator(
        approves: Bool,
        now: @escaping @Sendable () -> Date = { .now }
    ) -> PairingCoordinator {
        PairingCoordinator(
            receiverId: "receiver-1",
            receiverName: "Test Mac",
            store: store,
            now: now
        ) { _ in approves }
    }

    private func request(code: String) -> PairRequest {
        PairRequest(
            protocolVersion: TransferProtocol.version,
            deviceId: "device-1",
            deviceName: "Test Pixel",
            pairingCode: code
        )
    }

    private func signatureHeaders(
        device: PairedDevice,
        method: String,
        path: String,
        nonce: String,
        timestamp: Date = .now
    ) -> [String: String] {
        let epochSeconds = Int64(timestamp.timeIntervalSince1970)
        let canonicalString = RequestSignature.canonicalString(
            method: method,
            path: path,
            timestamp: epochSeconds,
            nonce: nonce,
            bodySha256Hex: RequestSignature.sha256Hex(of: Data())
        )
        return [
            TransferProtocol.Header.deviceToken: device.deviceToken,
            TransferProtocol.Header.timestamp: String(epochSeconds),
            TransferProtocol.Header.nonce: nonce,
            TransferProtocol.Header.signature: RequestSignature.sign(
                canonicalString: canonicalString,
                secret: device.secret
            )
        ]
    }

    private func authorize(
        _ coordinator: PairingCoordinator,
        device: PairedDevice,
        nonce: String
    ) async -> RequestAuthorization {
        await coordinator.authorize(
            method: "POST",
            path: "/v1/transfers",
            bodySha256Hex: RequestSignature.sha256Hex(of: Data()),
            headers: signatureHeaders(device: device, method: "POST", path: "/v1/transfers", nonce: nonce)
        )
    }
}

private extension Result {

    var failure: Failure? {
        if case .failure(let error) = self { return error }
        return nil
    }
}

private final class Expectation: @unchecked Sendable {

    private let lock = NSLock()
    private var fulfilled = false

    var isFulfilled: Bool { lock.withLock { fulfilled } }

    func fulfil() {
        lock.withLock { fulfilled = true }
    }
}

private final class MutableClock: @unchecked Sendable {

    private let lock = NSLock()
    private var current: Date

    init(now: Date) {
        current = now
    }

    var now: Date { lock.withLock { current } }

    func advance(by interval: TimeInterval) {
        lock.withLock { current = current.addingTimeInterval(interval) }
    }
}
