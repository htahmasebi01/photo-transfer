import FlyingFox
import FlyingSocks
import Foundation

enum ReceiverEvent: Sendable {
    case transferStarted(transferId: String, fileCount: Int)
    case fileReceived(fileName: String, destination: URL)
    case transferCompleted(transferId: String, receivedFiles: Int)
}

struct ReceiverServerConfiguration: Sendable {
    let receiverName: String
    let destinationDirectory: @Sendable () -> URL?
    let onEvent: @Sendable (ReceiverEvent) -> Void
}

enum ReceiverServerError: Error {
    case notListening
}

actor ReceiverServer {

    private let configuration: ReceiverServerConfiguration
    private let store = TransferStore()
    private var server: HTTPServer?
    private var serverTask: Task<Void, Never>?

    init(configuration: ReceiverServerConfiguration) {
        self.configuration = configuration
    }

    /// Starts the server on an ephemeral port and returns the bound port.
    func start() async throws -> UInt16 {
        let server = HTTPServer(address: .inet(port: 0))
        await appendRoutes(to: server)
        self.server = server
        serverTask = Task { try? await server.run() }
        try await server.waitUntilListening()

        switch await server.listeningAddress {
        case .ip4(_, let port), .ip6(_, let port):
            return port
        default:
            await stop()
            throw ReceiverServerError.notListening
        }
    }

    func stop() async {
        await server?.stop()
        serverTask?.cancel()
        server = nil
        serverTask = nil
    }

    private func appendRoutes(to server: HTTPServer) async {
        let store = self.store
        let configuration = self.configuration

        await server.appendRoute("GET /v1/info") { _ in
            try .json(InfoResponse(
                protocolVersion: TransferProtocol.version,
                receiverName: configuration.receiverName
            ))
        }

        await server.appendRoute("POST /v1/transfers") { request in
            let manifest = try JSONDecoder().decode(TransferManifest.self, from: await request.bodyData)
            let transferId = await store.createTransfer(manifest: manifest)
            configuration.onEvent(.transferStarted(transferId: transferId, fileCount: manifest.files.count))
            return try .json(TransferCreatedResponse(transferId: transferId), statusCode: .created)
        }

        await server.appendRoute("PUT /v1/transfers/:transferId/files/:fileId") { request in
            try await Self.handleFileUpload(request, store: store, configuration: configuration)
        }

        await server.appendRoute("POST /v1/transfers/:transferId/complete") { request in
            guard let transferId = request.routeParameters["transferId"],
                  let receivedFiles = await store.complete(transferId: transferId) else {
                return HTTPResponse(statusCode: .notFound)
            }
            configuration.onEvent(.transferCompleted(transferId: transferId, receivedFiles: receivedFiles))
            return try .json(CompleteResponse(receivedFiles: receivedFiles))
        }

        await server.appendRoute("GET /v1/transfers/:transferId") { request in
            guard let transferId = request.routeParameters["transferId"],
                  let status = await store.status(transferId: transferId) else {
                return HTTPResponse(statusCode: .notFound)
            }
            return try .json(TransferStatusResponse(
                transferId: status.transferId,
                state: status.state.rawValue,
                receivedFiles: status.receivedFileCount,
                totalFiles: status.totalFileCount
            ))
        }
    }

    private static func handleFileUpload(
        _ request: HTTPRequest,
        store: TransferStore,
        configuration: ReceiverServerConfiguration
    ) async throws -> HTTPResponse {
        guard let transferId = request.routeParameters["transferId"],
              let fileId = request.routeParameters["fileId"] else {
            return HTTPResponse(statusCode: .badRequest)
        }

        switch await store.lookupFile(transferId: transferId, fileId: fileId) {
        case .unknown:
            return HTTPResponse(statusCode: .notFound)
        case .alreadyReceived:
            return HTTPResponse(statusCode: .conflict)
        case .pending(let file):
            guard let destinationDirectory = configuration.destinationDirectory() else {
                return HTTPResponse(statusCode: .serviceUnavailable)
            }
            let temporaryURL = try await streamBodyToTemporaryFile(request)
            let destination = FileNaming.collisionSafeURL(
                forSuppliedName: file.name,
                in: destinationDirectory
            )
            do {
                try FileManager.default.moveItem(at: temporaryURL, to: destination)
            } catch {
                try? FileManager.default.removeItem(at: temporaryURL)
                throw error
            }
            await store.markReceived(transferId: transferId, fileId: fileId)
            configuration.onEvent(.fileReceived(fileName: destination.lastPathComponent, destination: destination))
            return HTTPResponse(statusCode: .ok)
        }
    }

    private static func streamBodyToTemporaryFile(_ request: HTTPRequest) async throws -> URL {
        let temporaryURL = FileManager.default.temporaryDirectory
            .appendingPathComponent("photo-transfer-\(UUID().uuidString)")
        FileManager.default.createFile(atPath: temporaryURL.path, contents: nil)
        let handle = try FileHandle(forWritingTo: temporaryURL)
        do {
            for try await chunk in request.bodySequence {
                try handle.write(contentsOf: chunk)
            }
            try handle.close()
            return temporaryURL
        } catch {
            try? handle.close()
            try? FileManager.default.removeItem(at: temporaryURL)
            throw error
        }
    }
}

extension HTTPResponse {

    static func json(_ value: some Encodable, statusCode: HTTPStatusCode = .ok) throws -> HTTPResponse {
        HTTPResponse(
            statusCode: statusCode,
            headers: [.contentType: "application/json"],
            body: try JSONEncoder().encode(value)
        )
    }
}
