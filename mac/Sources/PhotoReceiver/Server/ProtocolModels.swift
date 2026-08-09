import Foundation

enum TransferProtocol {
    static let version = 1
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
    let receiverName: String
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
