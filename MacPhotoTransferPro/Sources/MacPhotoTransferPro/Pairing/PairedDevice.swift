import Foundation

/// A device that completed pairing, along with the secret it signs requests with.
struct PairedDevice: Codable, Equatable, Sendable, Identifiable {

    /// Opaque identifier the device sends back in `X-PT-Device`; also the Keychain account.
    let deviceToken: String
    let deviceId: String
    let deviceName: String
    let secret: Data
    let pairedAt: Date

    var id: String { deviceToken }

    static func issue(deviceId: String, deviceName: String, now: Date = .now) -> PairedDevice {
        PairedDevice(
            deviceToken: UUID().uuidString.lowercased(),
            deviceId: deviceId,
            deviceName: deviceName,
            secret: Data.randomSecret(byteCount: 32),
            pairedAt: now
        )
    }
}

extension Data {

    static func randomSecret(byteCount: Int) -> Data {
        var generator = SystemRandomNumberGenerator()
        return Data((0..<byteCount).map { _ in UInt8.random(in: .min ... .max, using: &generator) })
    }
}
