import SwiftUI

struct ContentView: View {

    @StateObject private var viewModel = ReceiverViewModel()

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            header
            folderRow
            controlRow
            Divider()
            receivedList
        }
        .padding(20)
        .frame(minWidth: 440, minHeight: 380)
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
