import AppKit
import Foundation
import SwiftUI

@MainActor
final class ReceiverViewModel: ObservableObject {

    struct ReceivedFile: Identifiable, Equatable {
        let id = UUID()
        let name: String
        let receivedAt: Date
    }

    struct PendingApproval: Identifiable, Equatable {
        let id = UUID()
        let deviceName: String
    }

    @Published private(set) var isRunning = false
    @Published private(set) var port: UInt16?
    @Published private(set) var destinationFolder: URL?
    @Published private(set) var receivedFiles: [ReceivedFile] = []
    @Published private(set) var statusMessage = "Choose a folder, then start receiving."
    @Published private(set) var pairingCode: PairingCode?
    @Published private(set) var pendingApproval: PendingApproval?
    @Published private(set) var pairedDevices: [PairedDevice] = []

    let receiverName = Host.current().localizedName ?? "MacBook"

    private let folderStore = DestinationFolderStore()
    private let advertiser = BonjourAdvertiser()
    private let receiverId = ReceiverIdentityStore().receiverId()
    private var pairing: PairingCoordinator!
    private var server: ReceiverServer?
    private var hasSecurityScopedAccess = false
    private var approvalContinuation: CheckedContinuation<Bool, Never>?
    private var pairingExpiryTask: Task<Void, Never>?

    init() {
        destinationFolder = folderStore.restore()
        pairing = PairingCoordinator(
            receiverId: receiverId,
            receiverName: receiverName,
            store: KeychainPairedDeviceStore()
        ) { [weak self] approval in
            guard let self else { return false }
            return await self.requestApproval(for: approval)
        }
        Task { await refreshPairedDevices() }
    }

    func chooseFolder() {
        let panel = NSOpenPanel()
        panel.canChooseDirectories = true
        panel.canChooseFiles = false
        panel.canCreateDirectories = true
        panel.prompt = "Choose"
        panel.message = "Choose where received photos are saved"
        guard panel.runModal() == .OK, let url = panel.url else { return }

        destinationFolder = url
        do {
            try folderStore.save(url)
        } catch {
            statusMessage = "Could not save folder access: \(error.localizedDescription)"
        }
    }

    func toggle() {
        if isRunning {
            stop()
        } else {
            start()
        }
    }

    // MARK: - Pairing

    func beginPairing() {
        guard isRunning else {
            statusMessage = "Start receiving before pairing a device."
            return
        }
        Task {
            let code = await pairing.beginPairing()
            pairingCode = code
            statusMessage = "Enter \(code.digits) on your phone."
            schedulePairingExpiry(at: code.expiresAt)
        }
    }

    func cancelPairing() {
        pairingExpiryTask?.cancel()
        pairingExpiryTask = nil
        pairingCode = nil
        Task { await pairing.cancelPairing() }
    }

    func respondToApproval(approved: Bool) {
        resolvePendingApproval(approved)
    }

    func revoke(_ device: PairedDevice) {
        Task {
            await pairing.revoke(deviceToken: device.deviceToken)
            await refreshPairedDevices()
            statusMessage = "Removed \(device.deviceName)."
        }
    }

    /// Suspends the pairing request until the user answers, and treats a cancelled
    /// request (the receiver's approval timeout elapsing) as a denial.
    private func requestApproval(for approval: PairingApproval) async -> Bool {
        await withTaskCancellationHandler {
            await withCheckedContinuation { continuation in
                guard approvalContinuation == nil else {
                    continuation.resume(returning: false)
                    return
                }
                approvalContinuation = continuation
                pendingApproval = PendingApproval(deviceName: approval.deviceName)
            }
        } onCancel: {
            Task { @MainActor in self.resolvePendingApproval(false) }
        }
    }

    private func resolvePendingApproval(_ approved: Bool) {
        guard let continuation = approvalContinuation else { return }
        approvalContinuation = nil
        pendingApproval = nil
        continuation.resume(returning: approved)
    }

    private func schedulePairingExpiry(at date: Date) {
        pairingExpiryTask?.cancel()
        pairingExpiryTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(max(0, date.timeIntervalSinceNow) * 1_000_000_000))
            guard !Task.isCancelled else { return }
            await MainActor.run { self?.expirePairingCode() }
        }
    }

    private func expirePairingCode() {
        guard pairingCode != nil else { return }
        pairingCode = nil
        statusMessage = "Pairing code expired."
    }

    private func refreshPairedDevices() async {
        pairedDevices = await pairing.pairedDevices()
    }

    // MARK: - Server lifecycle

    private func start() {
        guard let folder = destinationFolder else {
            statusMessage = "Choose a destination folder first."
            return
        }
        hasSecurityScopedAccess = folder.startAccessingSecurityScopedResource()

        let server = ReceiverServer(
            configuration: ReceiverServerConfiguration(
                receiverId: receiverId,
                receiverName: receiverName,
                destinationDirectory: { folder },
                onEvent: { [weak self] event in
                    Task { @MainActor in self?.handle(event) }
                }
            ),
            pairing: pairing
        )
        self.server = server

        Task {
            do {
                let port = try await server.start()
                self.port = port
                self.isRunning = true
                self.advertiser.start(name: self.receiverName, port: port, receiverId: self.receiverId)
                self.statusMessage = "Receiving as \"\(self.receiverName)\" on port \(port)."
            } catch {
                self.releaseFolderAccessIfNeeded()
                self.statusMessage = "Failed to start: \(error.localizedDescription)"
            }
        }
    }

    private func stop() {
        advertiser.stop()
        cancelPairing()
        let server = self.server
        self.server = nil
        Task { await server?.stop() }

        releaseFolderAccessIfNeeded()
        isRunning = false
        port = nil
        statusMessage = "Stopped."
    }

    private func releaseFolderAccessIfNeeded() {
        if hasSecurityScopedAccess {
            destinationFolder?.stopAccessingSecurityScopedResource()
            hasSecurityScopedAccess = false
        }
    }

    private func handle(_ event: ReceiverEvent) {
        switch event {
        case .transferStarted(_, let fileCount):
            statusMessage = "Incoming transfer: \(fileCount) file(s)."
        case .fileReceived(let fileName, _):
            receivedFiles.insert(ReceivedFile(name: fileName, receivedAt: .now), at: 0)
            statusMessage = "Received \(fileName)."
        case .transferCompleted(_, let receivedCount):
            statusMessage = "Transfer complete: \(receivedCount) file(s) received."
        case .devicePaired(let deviceName):
            pairingCode = nil
            statusMessage = "Paired with \(deviceName)."
            Task { await refreshPairedDevices() }
        case .requestRejected(_, let reason):
            statusMessage = "Rejected an unauthorized request (\(reason.rawValue))."
        }
    }
}
