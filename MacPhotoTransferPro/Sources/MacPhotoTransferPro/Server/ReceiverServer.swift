import FlyingFox
import FlyingSocks
import Foundation

enum ReceiverEvent: Sendable {
    case transferStarted(transferId: String, fileCount: Int)
    case fileReceived(fileName: String, destination: URL)
    case transferCompleted(transferId: String, receivedFiles: Int)
    case devicePaired(deviceName: String)
    case requestRejected(path: String, reason: AuthorizationFailure)
}

struct ReceiverServerConfiguration: Sendable {
    let receiverId: String
    let receiverName: String
    let destinationDirectory: @Sendable () -> URL?
    let onEvent: @Sendable (ReceiverEvent) -> Void
}

enum ReceiverServerError: Error {
    case notListening
}

actor ReceiverServer {

    private let configuration: ReceiverServerConfiguration
    private let pairing: PairingCoordinator
    private let store = TransferStore()
    private var server: HTTPServer?
    private var serverTask: Task<Void, Never>?

    init(configuration: ReceiverServerConfiguration, pairing: PairingCoordinator) {
        self.configuration = configuration
        self.pairing = pairing
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
        let pairing = self.pairing

        await server.appendRoute("GET /v1/info") { _ in
            try .json(InfoResponse(
                protocolVersion: TransferProtocol.version,
                receiverId: configuration.receiverId,
                receiverName: configuration.receiverName
            ))
        }

        await server.appendRoute("POST /v1/pair") { request in
            guard let body = try await Self.bufferedBody(of: request),
                  let pairRequest = try? JSONDecoder().decode(PairRequest.self, from: body) else {
                return HTTPResponse(statusCode: .badRequest)
            }

            switch await pairing.pair(pairRequest, source: Self.source(of: request)) {
            case .success(let response):
                configuration.onEvent(.devicePaired(deviceName: pairRequest.deviceName))
                return try .json(response)
            case .failure(let failure):
                return HTTPResponse(statusCode: failure.statusCode)
            }
        }

        // Exists so the sender can authenticate this Mac before it sends anything,
        // including the manifest, which would otherwise leak filenames to an impostor.
        await server.appendRoute("POST /v1/verify") { request in
            await Self.authorizing(request, body: Data(), pairing: pairing, configuration: configuration) { _ in
                HTTPResponse(statusCode: .noContent)
            }
        }

        await server.appendRoute("POST /v1/transfers") { request in
            guard let body = try await Self.bufferedBody(of: request) else {
                return HTTPResponse(statusCode: .payloadTooLarge)
            }
            return await Self.authorizing(request, body: body, pairing: pairing, configuration: configuration) { device in
                guard let manifest = try? JSONDecoder().decode(TransferManifest.self, from: body) else {
                    return HTTPResponse(statusCode: .badRequest)
                }
                guard manifest.files.allSatisfy({ PhotoFileType.isAcceptable(name: $0.name) }) else {
                    return HTTPResponse(statusCode: .unsupportedMediaType)
                }
                let transferId = await store.createTransfer(
                    manifest: manifest,
                    ownerToken: device.deviceToken
                )
                configuration.onEvent(.transferStarted(transferId: transferId, fileCount: manifest.files.count))
                return try .json(TransferCreatedResponse(transferId: transferId), statusCode: .created)
            }
        }

        await server.appendRoute("PUT /v1/transfers/:transferId/files/:fileId") { request in
            // The body is streamed to disk rather than buffered, so it is not covered by
            // the signature; method and path still are. See docs/protocol.md.
            await Self.authorizing(request, body: nil, pairing: pairing, configuration: configuration) { device in
                try await Self.handleFileUpload(
                    request,
                    device: device,
                    store: store,
                    configuration: configuration
                )
            }
        }

        await server.appendRoute("POST /v1/transfers/:transferId/complete") { request in
            guard let body = try await Self.bufferedBody(of: request) else {
                return HTTPResponse(statusCode: .payloadTooLarge)
            }
            return await Self.authorizing(request, body: body, pairing: pairing, configuration: configuration) { device in
                guard let transferId = request.routeParameters["transferId"],
                      let receivedFiles = await store.complete(
                          transferId: transferId,
                          ownerToken: device.deviceToken
                      ) else {
                    return HTTPResponse(statusCode: .notFound)
                }
                configuration.onEvent(.transferCompleted(transferId: transferId, receivedFiles: receivedFiles))
                return try .json(CompleteResponse(receivedFiles: receivedFiles))
            }
        }

        await server.appendRoute("GET /v1/transfers/:transferId") { request in
            await Self.authorizing(request, body: nil, pairing: pairing, configuration: configuration) { device in
                guard let transferId = request.routeParameters["transferId"],
                      let status = await store.status(
                          transferId: transferId,
                          ownerToken: device.deviceToken
                      ) else {
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
    }

    /// Rejects an oversized body on its declared length, before a byte is read.
    ///
    /// Returns nil when the body is too large or its length is not declared, which is
    /// what stops an unauthenticated caller from exhausting memory: the signature covers
    /// the body hash, so authorization cannot happen any earlier than this.
    private static func bufferedBody(of request: HTTPRequest) async throws -> Data? {
        guard let declared = request.bodySequence.count,
              declared <= TransferProtocol.maximumBufferedBodyBytes else {
            return nil
        }
        let body = try await request.bodyData
        return body.count <= TransferProtocol.maximumBufferedBodyBytes ? body : nil
    }

    /// Runs `handler` only for a paired device, and signs the response so the caller can
    /// tell this Mac apart from an impostor advertising the same `receiverId`.
    ///
    /// A `nil` body means the request streams its payload, so the empty-body hash is signed.
    private static func authorizing(
        _ request: HTTPRequest,
        body: Data?,
        pairing: PairingCoordinator,
        configuration: ReceiverServerConfiguration,
        _ handler: (PairedDevice) async throws -> HTTPResponse
    ) async -> HTTPResponse {
        let authorization = await pairing.authorize(
            method: request.method.rawValue,
            path: request.path,
            bodySha256Hex: RequestSignature.sha256Hex(of: body ?? Data()),
            headers: signatureHeaders(from: request)
        )

        guard case .authorized(let device) = authorization else {
            if case .rejected(let reason) = authorization {
                configuration.onEvent(.requestRejected(path: request.path, reason: reason))
            }
            return HTTPResponse(statusCode: .unauthorized)
        }

        var response: HTTPResponse
        do {
            response = try await handler(device)
        } catch {
            response = HTTPResponse(statusCode: .internalServerError)
        }
        response.headers[HTTPHeader(TransferProtocol.Header.receiverSignature)] = receiverProof(
            for: request,
            device: device
        )
        return response
    }

    private static func receiverProof(for request: HTTPRequest, device: PairedDevice) -> String {
        RequestSignature.sign(
            canonicalString: RequestSignature.receiverProofString(
                method: request.method.rawValue,
                path: request.path,
                nonce: request.headers[HTTPHeader(TransferProtocol.Header.nonce)] ?? ""
            ),
            secret: device.secret
        )
    }

    /// The peer address without its port, since a port changes per connection and would
    /// hand every retry a fresh attempt budget.
    private static func source(of request: HTTPRequest) -> String {
        switch request.remoteAddress {
        case .ip4(let address, _), .ip6(let address, _):
            return address
        case .unix(let path):
            return path
        case nil:
            return PairingCoordinator.unknownSource
        }
    }

    private static func signatureHeaders(from request: HTTPRequest) -> [String: String] {
        let names = [
            TransferProtocol.Header.deviceToken,
            TransferProtocol.Header.timestamp,
            TransferProtocol.Header.nonce,
            TransferProtocol.Header.signature
        ]
        return names.reduce(into: [:]) { headers, name in
            if let value = request.headers[HTTPHeader(name)] {
                headers[name] = value
            }
        }
    }

    private static func handleFileUpload(
        _ request: HTTPRequest,
        device: PairedDevice,
        store: TransferStore,
        configuration: ReceiverServerConfiguration
    ) async throws -> HTTPResponse {
        guard let transferId = request.routeParameters["transferId"],
              let fileId = request.routeParameters["fileId"] else {
            return HTTPResponse(statusCode: .badRequest)
        }

        let lookup = await store.lookupFile(
            transferId: transferId,
            fileId: fileId,
            ownerToken: device.deviceToken
        )
        switch lookup {
        case .unknown:
            return HTTPResponse(statusCode: .notFound)
        case .alreadyReceived:
            return HTTPResponse(statusCode: .conflict)
        case .pending(let file):
            guard PhotoFileType.isAcceptable(name: file.name) else {
                return HTTPResponse(statusCode: .unsupportedMediaType)
            }
            guard let destinationDirectory = configuration.destinationDirectory() else {
                return HTTPResponse(statusCode: .serviceUnavailable)
            }

            let limit = uploadLimit(declaredSize: file.size)
            guard (request.bodySequence.count ?? 0) <= limit else {
                return HTTPResponse(statusCode: .payloadTooLarge)
            }

            let temporaryURL: URL
            do {
                temporaryURL = try await streamBodyToTemporaryFile(request, limit: limit)
            } catch is UploadTooLarge {
                return HTTPResponse(statusCode: .payloadTooLarge)
            }

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

    /// A manifest entry without a size still gets a ceiling, so a paired device cannot
    /// fill the disk by declaring nothing.
    private static func uploadLimit(declaredSize: Int64?) -> Int {
        guard let declaredSize, declaredSize > 0 else { return absoluteUploadLimit }
        return min(Int(declaredSize), absoluteUploadLimit)
    }

    private struct UploadTooLarge: Error {}

    private static func streamBodyToTemporaryFile(
        _ request: HTTPRequest,
        limit: Int
    ) async throws -> URL {
        let temporaryURL = FileManager.default.temporaryDirectory
            .appendingPathComponent("photo-transfer-\(UUID().uuidString)")
        FileManager.default.createFile(atPath: temporaryURL.path, contents: nil)
        let handle = try FileHandle(forWritingTo: temporaryURL)
        var written = 0
        do {
            for try await chunk in request.bodySequence {
                written += chunk.count
                guard written <= limit else { throw UploadTooLarge() }
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

    /// 2 GiB, above any plausible single photo including raw formats and live photos.
    private static let absoluteUploadLimit = 2 << 30
}

private extension PairingFailure {

    var statusCode: HTTPStatusCode {
        switch self {
        case .unsupportedProtocol: .badRequest
        case .invalidCode: .unauthorized
        case .throttled: .tooManyRequests
        case .declined: .forbidden
        // Deliberately not 408: HTTP clients treat that as retryable and resend the
        // request, which would re-prompt with a pairing code that is already spent.
        case .timedOut: .gatewayTimeout
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
