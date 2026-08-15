import Foundation
import XCTest
@testable import MacPhotoTransferPro

final class ReceiverServerTests: XCTestCase {

    private var server: ReceiverServer!
    private var destinationDirectory: URL!
    private var baseURL: URL!

    override func setUp() async throws {
        destinationDirectory = FileManager.default.temporaryDirectory
            .appendingPathComponent("receiver-tests-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: destinationDirectory, withIntermediateDirectories: true)

        let directory = destinationDirectory!
        server = ReceiverServer(configuration: ReceiverServerConfiguration(
            receiverName: "Test Mac",
            destinationDirectory: { directory },
            onEvent: { _ in }
        ))
        let port = try await server.start()
        baseURL = URL(string: "http://127.0.0.1:\(port)")!
    }

    override func tearDown() async throws {
        await server.stop()
        try? FileManager.default.removeItem(at: destinationDirectory)
    }

    func testInfoEndpoint() async throws {
        let (data, response) = try await URLSession.shared.data(from: baseURL.appendingPathComponent("v1/info"))

        XCTAssertEqual((response as? HTTPURLResponse)?.statusCode, 200)
        let info = try JSONDecoder().decode(InfoResponse.self, from: data)
        XCTAssertEqual(info, InfoResponse(protocolVersion: 1, receiverName: "Test Mac"))
    }

    func testFullTransferFlow() async throws {
        let photoBytes = Data((0..<10_000).map { UInt8(truncatingIfNeeded: $0) })

        let transferId = try await createTransfer(files: [
            ManifestFile(id: "file-1", name: "IMG_1.jpg", mediaType: "image/jpeg", size: Int64(photoBytes.count))
        ])

        let uploadStatus = try await uploadFile(transferId: transferId, fileId: "file-1", body: photoBytes)
        XCTAssertEqual(uploadStatus, 200)

        let received = try Data(contentsOf: destinationDirectory.appendingPathComponent("IMG_1.jpg"))
        XCTAssertEqual(received, photoBytes)

        let completeStatus = try await completeTransfer(transferId: transferId)
        XCTAssertEqual(completeStatus.receivedFiles, 1)
    }

    func testDuplicateUploadIsRejected() async throws {
        let transferId = try await createTransfer(files: [
            ManifestFile(id: "file-1", name: "a.jpg", mediaType: "image/jpeg", size: 3)
        ])

        let first = try await uploadFile(transferId: transferId, fileId: "file-1", body: Data([1, 2, 3]))
        let second = try await uploadFile(transferId: transferId, fileId: "file-1", body: Data([1, 2, 3]))

        XCTAssertEqual(first, 200)
        XCTAssertEqual(second, 409)
    }

    func testUnknownFileReturnsNotFound() async throws {
        let transferId = try await createTransfer(files: [])

        let status = try await uploadFile(transferId: transferId, fileId: "nope", body: Data([1]))

        XCTAssertEqual(status, 404)
    }

    func testCollidingFilenamesGetNumberedSuffix() async throws {
        let transferId = try await createTransfer(files: [
            ManifestFile(id: "file-1", name: "IMG.jpg", mediaType: "image/jpeg", size: 1),
            ManifestFile(id: "file-2", name: "IMG.jpg", mediaType: "image/jpeg", size: 1)
        ])

        _ = try await uploadFile(transferId: transferId, fileId: "file-1", body: Data([1]))
        _ = try await uploadFile(transferId: transferId, fileId: "file-2", body: Data([2]))

        let names = try FileManager.default.contentsOfDirectory(atPath: destinationDirectory.path).sorted()
        XCTAssertEqual(names, ["IMG (1).jpg", "IMG.jpg"])
    }

    // MARK: - Helpers

    private func createTransfer(files: [ManifestFile]) async throws -> String {
        var request = URLRequest(url: baseURL.appendingPathComponent("v1/transfers"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(TransferManifest(protocolVersion: 1, files: files))

        let (data, response) = try await URLSession.shared.data(for: request)
        XCTAssertEqual((response as? HTTPURLResponse)?.statusCode, 201)
        return try JSONDecoder().decode(TransferCreatedResponse.self, from: data).transferId
    }

    private func uploadFile(transferId: String, fileId: String, body: Data) async throws -> Int {
        var request = URLRequest(url: baseURL.appendingPathComponent("v1/transfers/\(transferId)/files/\(fileId)"))
        request.httpMethod = "PUT"
        request.setValue("image/jpeg", forHTTPHeaderField: "Content-Type")

        let (_, response) = try await URLSession.shared.upload(for: request, from: body)
        return (response as? HTTPURLResponse)?.statusCode ?? -1
    }

    private func completeTransfer(transferId: String) async throws -> CompleteResponse {
        var request = URLRequest(url: baseURL.appendingPathComponent("v1/transfers/\(transferId)/complete"))
        request.httpMethod = "POST"

        let (data, response) = try await URLSession.shared.data(for: request)
        XCTAssertEqual((response as? HTTPURLResponse)?.statusCode, 200)
        return try JSONDecoder().decode(CompleteResponse.self, from: data)
    }
}
