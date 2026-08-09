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

    @Published private(set) var isRunning = false
    @Published private(set) var port: UInt16?
    @Published private(set) var destinationFolder: URL?
    @Published private(set) var receivedFiles: [ReceivedFile] = []
    @Published private(set) var statusMessage = "Choose a folder, then start receiving."

    let receiverName = Host.current().localizedName ?? "MacBook"

    private let folderStore = DestinationFolderStore()
    private let advertiser = BonjourAdvertiser()
    private var server: ReceiverServer?
    private var hasSecurityScopedAccess = false

    init() {
        destinationFolder = folderStore.restore()
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

    private func start() {
        guard let folder = destinationFolder else {
            statusMessage = "Choose a destination folder first."
            return
        }
        hasSecurityScopedAccess = folder.startAccessingSecurityScopedResource()

        let server = ReceiverServer(configuration: ReceiverServerConfiguration(
            receiverName: receiverName,
            destinationDirectory: { folder },
            onEvent: { [weak self] event in
                Task { @MainActor in self?.handle(event) }
            }
        ))
        self.server = server

        Task {
            do {
                let port = try await server.start()
                self.port = port
                self.isRunning = true
                self.advertiser.start(name: self.receiverName, port: port)
                self.statusMessage = "Receiving as \"\(self.receiverName)\" on port \(port)."
            } catch {
                self.releaseFolderAccessIfNeeded()
                self.statusMessage = "Failed to start: \(error.localizedDescription)"
            }
        }
    }

    private func stop() {
        advertiser.stop()
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
        }
    }
}
