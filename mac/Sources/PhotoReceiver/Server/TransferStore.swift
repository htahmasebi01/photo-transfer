import Foundation

struct TransferStatus: Equatable, Sendable {
    enum State: String, Sendable {
        case receiving
        case completed
    }

    let transferId: String
    let state: State
    let receivedFileCount: Int
    let totalFileCount: Int
}

actor TransferStore {

    enum FileLookup: Equatable {
        case unknown
        case alreadyReceived
        case pending(ManifestFile)
    }

    private struct Transfer {
        let manifest: TransferManifest
        var receivedFileIds: Set<String> = []
        var isCompleted = false
    }

    private var transfers: [String: Transfer] = [:]

    func createTransfer(manifest: TransferManifest) -> String {
        let transferId = UUID().uuidString.lowercased()
        transfers[transferId] = Transfer(manifest: manifest)
        return transferId
    }

    func lookupFile(transferId: String, fileId: String) -> FileLookup {
        guard let transfer = transfers[transferId],
              let file = transfer.manifest.files.first(where: { $0.id == fileId }) else {
            return .unknown
        }
        if transfer.receivedFileIds.contains(fileId) {
            return .alreadyReceived
        }
        return .pending(file)
    }

    func markReceived(transferId: String, fileId: String) {
        transfers[transferId]?.receivedFileIds.insert(fileId)
    }

    func complete(transferId: String) -> Int? {
        guard var transfer = transfers[transferId] else { return nil }
        transfer.isCompleted = true
        transfers[transferId] = transfer
        return transfer.receivedFileIds.count
    }

    func status(transferId: String) -> TransferStatus? {
        guard let transfer = transfers[transferId] else { return nil }
        return TransferStatus(
            transferId: transferId,
            state: transfer.isCompleted ? .completed : .receiving,
            receivedFileCount: transfer.receivedFileIds.count,
            totalFileCount: transfer.manifest.files.count
        )
    }
}
