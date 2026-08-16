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
        let ownerToken: String
        var receivedFileIds: Set<String> = []
        var isCompleted = false
    }

    private var transfers: [String: Transfer] = [:]

    func createTransfer(manifest: TransferManifest, ownerToken: String) -> String {
        let transferId = UUID().uuidString.lowercased()
        transfers[transferId] = Transfer(manifest: manifest, ownerToken: ownerToken)
        return transferId
    }

    func lookupFile(transferId: String, fileId: String, ownerToken: String) -> FileLookup {
        guard let transfer = owned(transferId, by: ownerToken),
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

    func complete(transferId: String, ownerToken: String) -> Int? {
        guard var transfer = owned(transferId, by: ownerToken) else { return nil }
        transfer.isCompleted = true
        transfers[transferId] = transfer
        return transfer.receivedFileIds.count
    }

    /// Another paired device is told the transfer does not exist rather than that it is
    /// forbidden, so a device learns nothing about transfers that are not its own.
    private func owned(_ transferId: String, by ownerToken: String) -> Transfer? {
        guard let transfer = transfers[transferId],
              ConstantTime.equals(transfer.ownerToken, ownerToken) else {
            return nil
        }
        return transfer
    }

    func status(transferId: String, ownerToken: String) -> TransferStatus? {
        guard let transfer = owned(transferId, by: ownerToken) else { return nil }
        return TransferStatus(
            transferId: transferId,
            state: transfer.isCompleted ? .completed : .receiving,
            receivedFileCount: transfer.receivedFileIds.count,
            totalFileCount: transfer.manifest.files.count
        )
    }
}
