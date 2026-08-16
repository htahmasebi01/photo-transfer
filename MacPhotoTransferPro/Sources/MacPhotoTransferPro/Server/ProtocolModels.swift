import Foundation

enum TransferProtocol {
    static let version = 1

        enum Header {
            static let deviceToken = "X-PT-Device"
            static let timestamp = "X-PT-Timestamp"
            static let nonce = "X-PT-Nonce"
            static let signature = "X-PT-Signature"
            /// The receiver's proof that it holds the pairing secret.
            static let receiverSignature = "X-PT-Receiver-Signature"
        }

        /// A ceiling on request bodies the receiver buffers in memory. Uploads stream to
        /// disk instead and are bounded by their manifest entry.
        static let maximumBufferedBodyBytes = 1 << 20
}

struct TransferManifest: Codable, Equatable, Sendable {
    let protocolVersion: Int
    let files: [ManifestFile]
}

struct ManifestFile: Codable, Equatable, Sendable {
    let id: String
    let name: String
    let mediaType: String
    let size: Int64?
}

struct InfoResponse: Codable, Equatable, Sendable {
    let protocolVersion: Int
    let receiverId: String
    let receiverName: String
}

struct PairRequest: Codable, Equatable, Sendable {
    let protocolVersion: Int
    let deviceId: String
    let deviceName: String
    let pairingCode: String
}

struct PairResponse: Codable, Equatable, Sendable {
    let receiverId: String
    let receiverName: String
    let deviceToken: String
    let secretBase64: String
}

struct TransferCreatedResponse: Codable, Equatable, Sendable {
    let transferId: String
}

struct CompleteResponse: Codable, Equatable, Sendable {
    let receivedFiles: Int
}

struct TransferStatusResponse: Codable, Equatable, Sendable {
    let transferId: String
    let state: String
    let receivedFiles: Int
    let totalFiles: Int
}
