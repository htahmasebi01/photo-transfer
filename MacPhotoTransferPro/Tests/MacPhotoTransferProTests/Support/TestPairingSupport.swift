import Foundation
@testable import MacPhotoTransferPro

/// Keeps pairing secrets in memory so tests never touch the login Keychain.
final class InMemoryPairedDeviceStore: PairedDeviceStoring, @unchecked Sendable {

    private let lock = NSLock()
    private var devices: [String: PairedDevice] = [:]

    init(seeded: [PairedDevice] = []) {
        devices = Dictionary(uniqueKeysWithValues: seeded.map { ($0.deviceToken, $0) })
    }

    func save(_ device: PairedDevice) throws {
        lock.withLock { devices[device.deviceToken] = device }
    }

    func all() throws -> [PairedDevice] {
        lock.withLock { Array(devices.values) }
    }

    func delete(deviceToken: String) throws {
        lock.withLock { devices.removeValue(forKey: deviceToken) }
    }
}

extension PairedDevice {

    static func stub(
        deviceName: String = "Test Pixel",
        secret: Data = Data(repeating: 7, count: 32)
    ) -> PairedDevice {
        PairedDevice(
            deviceToken: "token-\(UUID().uuidString.prefix(8))",
            deviceId: "device-1",
            deviceName: deviceName,
            secret: secret,
            pairedAt: .now
        )
    }
}

extension HTTPURLResponse {

    /// The receiver's proof that it holds the pairing secret.
    var receiverProof: String? {
        value(forHTTPHeaderField: TransferProtocol.Header.receiverSignature)
    }
}

extension URLRequest {

    /// Adds the signature headers a paired sender would attach.
    ///
    /// `bodyForSigning` is empty for streamed uploads, matching the receiver, which cannot
    /// hash a body it is writing straight to disk.
    mutating func addSignature(
        device: PairedDevice,
        bodyForSigning: Data = Data(),
        timestamp: Date = .now,
        nonce: String = UUID().uuidString
    ) {
        let epochSeconds = Int64(timestamp.timeIntervalSince1970)
        let canonicalString = RequestSignature.canonicalString(
            method: httpMethod ?? "GET",
            path: url?.path ?? "/",
            timestamp: epochSeconds,
            nonce: nonce,
            bodySha256Hex: RequestSignature.sha256Hex(of: bodyForSigning)
        )
        let signature = RequestSignature.sign(canonicalString: canonicalString, secret: device.secret)

        setValue(device.deviceToken, forHTTPHeaderField: TransferProtocol.Header.deviceToken)
        setValue(String(epochSeconds), forHTTPHeaderField: TransferProtocol.Header.timestamp)
        setValue(nonce, forHTTPHeaderField: TransferProtocol.Header.nonce)
        setValue(signature, forHTTPHeaderField: TransferProtocol.Header.signature)
    }
}
