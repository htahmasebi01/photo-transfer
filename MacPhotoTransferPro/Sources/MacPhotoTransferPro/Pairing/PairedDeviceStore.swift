import Foundation
import Security

protocol PairedDeviceStoring: Sendable {
    func save(_ device: PairedDevice) throws
    func all() throws -> [PairedDevice]
    func delete(deviceToken: String) throws
}

enum KeychainError: Error {
    case unexpectedStatus(OSStatus)
}

/// Stores pairing secrets as Keychain generic passwords, one item per paired device.
///
/// Secrets stay out of `UserDefaults` (which is a plain plist in the container) so a
/// stolen preferences file does not hand over upload authority.
struct KeychainPairedDeviceStore: PairedDeviceStoring {

    private let service: String

    init(service: String = "com.agiletech.mac.phototransfer.pairing") {
        self.service = service
    }

    func save(_ device: PairedDevice) throws {
        try delete(deviceToken: device.deviceToken)

        let attributes: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: device.deviceToken,
            kSecValueData as String: try JSONEncoder().encode(device),
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlocked
        ]
        let status = SecItemAdd(attributes as CFDictionary, nil)
        guard status == errSecSuccess else { throw KeychainError.unexpectedStatus(status) }
    }

    func all() throws -> [PairedDevice] {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecMatchLimit as String: kSecMatchLimitAll,
            kSecReturnData as String: true
        ]

        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        if status == errSecItemNotFound { return [] }
        guard status == errSecSuccess else { throw KeychainError.unexpectedStatus(status) }

        let decoder = JSONDecoder()
        return (result as? [Data] ?? [])
            .compactMap { try? decoder.decode(PairedDevice.self, from: $0) }
            .sorted { $0.pairedAt > $1.pairedAt }
    }

    func delete(deviceToken: String) throws {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: deviceToken
        ]
        let status = SecItemDelete(query as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw KeychainError.unexpectedStatus(status)
        }
    }
}
