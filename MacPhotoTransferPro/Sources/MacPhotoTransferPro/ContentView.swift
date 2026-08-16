import SwiftUI

struct ContentView: View {

    @StateObject private var viewModel = ReceiverViewModel()

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            header
            folderRow
            controlRow
            pairingSection
            Divider()
            receivedList
        }
        .padding(20)
        .frame(minWidth: 440, minHeight: 460)
        .alert(
            "Allow \(viewModel.pendingApproval?.deviceName ?? "this device") to send photos?",
            isPresented: .constant(viewModel.pendingApproval != nil),
            presenting: viewModel.pendingApproval
        ) { _ in
            Button("Allow") { viewModel.respondToApproval(approved: true) }
            Button("Don't Allow", role: .cancel) { viewModel.respondToApproval(approved: false) }
        } message: { _ in
            Text("It will be able to send photos to this Mac until you remove it.")
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Photo Receiver")
                .font(.title2.bold())
            Text(viewModel.statusMessage)
                .font(.callout)
                .foregroundStyle(.secondary)
        }
    }

    private var folderRow: some View {
        HStack {
            Image(systemName: "folder")
            Text(viewModel.destinationFolder?.path ?? "No folder selected")
                .lineLimit(1)
                .truncationMode(.middle)
            Spacer()
            Button("Choose Folder") {
                viewModel.chooseFolder()
            }
            .disabled(viewModel.isRunning)
        }
    }

    private var controlRow: some View {
        HStack {
            Button(viewModel.isRunning ? "Stop Receiving" : "Start Receiving") {
                viewModel.toggle()
            }
            .keyboardShortcut(.defaultAction)

            if viewModel.isRunning, let port = viewModel.port {
                Label("\(viewModel.receiverName) : \(String(port))", systemImage: "dot.radiowaves.left.and.right")
                    .foregroundStyle(.green)
            }
            Spacer()
        }
    }

    private var pairingSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Paired devices")
                    .font(.headline)
                Spacer()
                if viewModel.pairingCode == nil {
                    Button("Pair a Device") { viewModel.beginPairing() }
                        .disabled(!viewModel.isRunning)
                } else {
                    Button("Cancel Pairing") { viewModel.cancelPairing() }
                }
            }

            if let code = viewModel.pairingCode {
                pairingCodeCard(code)
            }

            if viewModel.pairedDevices.isEmpty {
                Text("No devices paired yet. Only paired devices can send photos.")
                    .font(.callout)
                    .foregroundStyle(.secondary)
            } else {
                ForEach(viewModel.pairedDevices) { device in
                    HStack {
                        Image(systemName: "iphone")
                        Text(device.deviceName)
                        Spacer()
                        Button("Remove") { viewModel.revoke(device) }
                            .buttonStyle(.borderless)
                    }
                }
            }
        }
    }

    private func pairingCodeCard(_ code: PairingCode) -> some View {
        HStack(spacing: 12) {
            Text(code.digits)
                .font(.system(.largeTitle, design: .monospaced).bold())
                .tracking(6)
            VStack(alignment: .leading) {
                Text("Enter this code on your phone")
                Text("Expires \(code.expiresAt, style: .relative)")
                    .foregroundStyle(.secondary)
            }
            .font(.callout)
        }
        .padding(12)
        .background(.quaternary, in: RoundedRectangle(cornerRadius: 8))
    }

    private var receivedList: some View {
        Group {
            if viewModel.receivedFiles.isEmpty {
                ContentUnavailableView(
                    "No photos received yet",
                    systemImage: "photo.on.rectangle.angled",
                    description: Text("Photos sent from your Android phone will appear here.")
                )
            } else {
                List(viewModel.receivedFiles) { file in
                    HStack {
                        Image(systemName: "photo")
                        Text(file.name)
                        Spacer()
                        Text(file.receivedAt, style: .time)
                            .foregroundStyle(.secondary)
                    }
                }
                .listStyle(.inset)
            }
        }
    }
}
