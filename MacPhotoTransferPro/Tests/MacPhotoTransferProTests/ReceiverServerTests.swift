import Foundation
import XCTest
@testable import MacPhotoTransferPro

final class ReceiverServerTests: XCTestCase {

    private var server: ReceiverServer!
    private var destinationDirectory: URL!
    private var baseURL: URL!
    private var pairedDevice: PairedDevice!
    private var otherPairedDevice: PairedDevice!

    override func setUp() async throws {
        destinationDirectory = FileManager.default.temporaryDirectory
            .appendingPathComponent("receiver-tests-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: destinationDirectory, withIntermediateDirectories: true)

        pairedDevice = .stub()
        otherPairedDevice = .stub(deviceName: "Other Pixel", secret: Data(repeating: 9, count: 32))
        let pairing = PairingCoordinator(
            receiverId: "receiver-1",
            receiverName: "Test Mac",
            store: InMemoryPairedDeviceStore(seeded: [pairedDevice, otherPairedDevice])
        ) { _ in true }

        let directory = destinationDirectory!
        server = ReceiverServer(
            configuration: ReceiverServerConfiguration(
                receiverId: "receiver-1",
                receiverName: "Test Mac",
                destinationDirectory: { directory },
                onEvent: { _ in }
            ),
            pairing: pairing
        )
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
        XCTAssertEqual(
            info,
            InfoResponse(protocolVersion: 1, receiverId: "receiver-1", receiverName: "Test Mac")
        )
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

    // MARK: - Authorization

    func testUnsignedTransferRequestIsRejected() async throws {
        var request = URLRequest(url: baseURL.appendingPathComponent("v1/transfers"))
        request.httpMethod = "POST"
        request.httpBody = try JSONEncoder().encode(TransferManifest(protocolVersion: 1, files: []))

        let (_, response) = try await URLSession.shared.data(for: request)

        XCTAssertEqual((response as? HTTPURLResponse)?.statusCode, 401)
    }

    func testSignatureFromUnknownDeviceIsRejected() async throws {
        let body = try JSONEncoder().encode(TransferManifest(protocolVersion: 1, files: []))
        var request = URLRequest(url: baseURL.appendingPathComponent("v1/transfers"))
        request.httpMethod = "POST"
        request.httpBody = body
        request.addSignature(device: .stub(), bodyForSigning: body)

        let (_, response) = try await URLSession.shared.data(for: request)

        XCTAssertEqual((response as? HTTPURLResponse)?.statusCode, 401)
    }

    func testTamperedBodyIsRejected() async throws {
        let signedBody = try JSONEncoder().encode(TransferManifest(protocolVersion: 1, files: []))
        var request = URLRequest(url: baseURL.appendingPathComponent("v1/transfers"))
        request.httpMethod = "POST"
        request.addSignature(device: pairedDevice, bodyForSigning: signedBody)
        request.httpBody = try JSONEncoder().encode(TransferManifest(
            protocolVersion: 1,
            files: [ManifestFile(id: "sneaky", name: "x.jpg", mediaType: "image/jpeg", size: 1)]
        ))

        let (_, response) = try await URLSession.shared.data(for: request)

        XCTAssertEqual((response as? HTTPURLResponse)?.statusCode, 401)
    }

    func testReplayedRequestIsRejected() async throws {
        let body = try JSONEncoder().encode(TransferManifest(protocolVersion: 1, files: []))
        var request = URLRequest(url: baseURL.appendingPathComponent("v1/transfers"))
        request.httpMethod = "POST"
        request.httpBody = body
        request.addSignature(device: pairedDevice, bodyForSigning: body, nonce: "fixed-nonce")

        let (_, first) = try await URLSession.shared.data(for: request)
        let (_, replay) = try await URLSession.shared.data(for: request)

        XCTAssertEqual((first as? HTTPURLResponse)?.statusCode, 201)
        XCTAssertEqual((replay as? HTTPURLResponse)?.statusCode, 401)
    }

    func testStaleTimestampIsRejected() async throws {
        let body = try JSONEncoder().encode(TransferManifest(protocolVersion: 1, files: []))
        var request = URLRequest(url: baseURL.appendingPathComponent("v1/transfers"))
        request.httpMethod = "POST"
        request.httpBody = body
        request.addSignature(
            device: pairedDevice,
            bodyForSigning: body,
            timestamp: Date().addingTimeInterval(-PairingCoordinator.freshnessWindow - 60)
        )

        let (_, response) = try await URLSession.shared.data(for: request)

        XCTAssertEqual((response as? HTTPURLResponse)?.statusCode, 401)
    }

    func testUnsignedUploadIsRejected() async throws {
        let transferId = try await createTransfer(files: [
            ManifestFile(id: "file-1", name: "a.jpg", mediaType: "image/jpeg", size: 1)
        ])

        var request = URLRequest(url: baseURL.appendingPathComponent("v1/transfers/\(transferId)/files/file-1"))
        request.httpMethod = "PUT"
        let (_, response) = try await URLSession.shared.upload(for: request, from: Data([1]))

        XCTAssertEqual((response as? HTTPURLResponse)?.statusCode, 401)
        XCTAssertEqual(try FileManager.default.contentsOfDirectory(atPath: destinationDirectory.path), [])
    }

    // MARK: - Proving the receiver's identity

    func testVerifyReturnsAProofBoundToTheRequestNonce() async throws {
        var request = URLRequest(url: baseURL.appendingPathComponent("v1/verify"))
        request.httpMethod = "POST"
        request.addSignature(device: pairedDevice, nonce: "nonce-1")

        let (_, response) = try await URLSession.shared.data(for: request)

        let expected = RequestSignature.sign(
            canonicalString: RequestSignature.receiverProofString(
                method: "POST",
                path: "/v1/verify",
                nonce: "nonce-1"
            ),
            secret: pairedDevice.secret
        )
        XCTAssertEqual((response as? HTTPURLResponse)?.statusCode, 204)
        XCTAssertEqual((response as? HTTPURLResponse)?.receiverProof, expected)
    }

    func testProofDiffersPerDeviceSoOneCannotVouchForAnother() async throws {
        var mine = URLRequest(url: baseURL.appendingPathComponent("v1/verify"))
        mine.httpMethod = "POST"
        mine.addSignature(device: pairedDevice, nonce: "shared-nonce")

        var theirs = URLRequest(url: baseURL.appendingPathComponent("v1/verify"))
        theirs.httpMethod = "POST"
        theirs.addSignature(device: otherPairedDevice, nonce: "shared-nonce")

        let (_, myResponse) = try await URLSession.shared.data(for: mine)
        let (_, theirResponse) = try await URLSession.shared.data(for: theirs)

        XCTAssertNotEqual(
            (myResponse as? HTTPURLResponse)?.receiverProof,
            (theirResponse as? HTTPURLResponse)?.receiverProof
        )
    }

    func testARejectedRequestCarriesNoProof() async throws {
        var request = URLRequest(url: baseURL.appendingPathComponent("v1/verify"))
        request.httpMethod = "POST"

        let (_, response) = try await URLSession.shared.data(for: request)

        XCTAssertEqual((response as? HTTPURLResponse)?.statusCode, 401)
        XCTAssertNil((response as? HTTPURLResponse)?.receiverProof)
    }

    // MARK: - Transfer ownership

    func testAnotherPairedDeviceCannotUploadIntoThisTransfer() async throws {
        let transferId = try await createTransfer(files: [
            ManifestFile(id: "file-1", name: "a.jpg", mediaType: "image/jpeg", size: 1)
        ])

        var request = URLRequest(url: baseURL.appendingPathComponent("v1/transfers/\(transferId)/files/file-1"))
        request.httpMethod = "PUT"
        request.addSignature(device: otherPairedDevice)
        let (_, response) = try await URLSession.shared.upload(for: request, from: Data([1]))

        XCTAssertEqual((response as? HTTPURLResponse)?.statusCode, 404)
        XCTAssertEqual(try FileManager.default.contentsOfDirectory(atPath: destinationDirectory.path), [])
    }

    func testAnotherPairedDeviceCannotReadThisTransfersStatus() async throws {
        let transferId = try await createTransfer(files: [])

        var request = URLRequest(url: baseURL.appendingPathComponent("v1/transfers/\(transferId)"))
        request.httpMethod = "GET"
        request.addSignature(device: otherPairedDevice)
        let (_, response) = try await URLSession.shared.data(for: request)

        XCTAssertEqual((response as? HTTPURLResponse)?.statusCode, 404)
    }

    func testAnotherPairedDeviceCannotCompleteThisTransfer() async throws {
        let transferId = try await createTransfer(files: [])

        var request = URLRequest(url: baseURL.appendingPathComponent("v1/transfers/\(transferId)/complete"))
        request.httpMethod = "POST"
        request.addSignature(device: otherPairedDevice)
        let (_, response) = try await URLSession.shared.data(for: request)

        XCTAssertEqual((response as? HTTPURLResponse)?.statusCode, 404)
    }

    // MARK: - Size and type limits

    /// Also proves the size check runs before authorization: an unsigned request gets 413
    /// rather than 401, which is only possible if the body was never buffered.
    func testAnOversizedBodyIsRefusedWithoutBeingRead() async throws {
        var request = URLRequest(url: baseURL.appendingPathComponent("v1/transfers"))
        request.httpMethod = "POST"
        request.httpBody = Data(repeating: 0x20, count: TransferProtocol.maximumBufferedBodyBytes + 1)

        let (_, response) = try await URLSession.shared.data(for: request)

        XCTAssertEqual((response as? HTTPURLResponse)?.statusCode, 413)
    }

    func testAnUploadLargerThanDeclaredIsRefused() async throws {
        let transferId = try await createTransfer(files: [
            ManifestFile(id: "file-1", name: "small.jpg", mediaType: "image/jpeg", size: 3)
        ])

        let status = try await uploadFile(
            transferId: transferId,
            fileId: "file-1",
            body: Data(repeating: 1, count: 10_000)
        )

        XCTAssertEqual(status, 413)
        XCTAssertEqual(try FileManager.default.contentsOfDirectory(atPath: destinationDirectory.path), [])
    }

    func testAManifestNamingANonImageIsRejected() async throws {
        let body = try JSONEncoder().encode(TransferManifest(
            protocolVersion: 1,
            files: [ManifestFile(id: "file-1", name: "payload.command", mediaType: "image/jpeg", size: 1)]
        ))
        var request = URLRequest(url: baseURL.appendingPathComponent("v1/transfers"))
        request.httpMethod = "POST"
        request.httpBody = body
        request.addSignature(device: pairedDevice, bodyForSigning: body)

        let (_, response) = try await URLSession.shared.data(for: request)

        XCTAssertEqual((response as? HTTPURLResponse)?.statusCode, 415)
    }

    // MARK: - Helpers

    private func createTransfer(files: [ManifestFile]) async throws -> String {
        let body = try JSONEncoder().encode(TransferManifest(protocolVersion: 1, files: files))
        var request = URLRequest(url: baseURL.appendingPathComponent("v1/transfers"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = body
        request.addSignature(device: pairedDevice, bodyForSigning: body)

        let (data, response) = try await URLSession.shared.data(for: request)
        XCTAssertEqual((response as? HTTPURLResponse)?.statusCode, 201)
        return try JSONDecoder().decode(TransferCreatedResponse.self, from: data).transferId
    }

    private func uploadFile(transferId: String, fileId: String, body: Data) async throws -> Int {
        var request = URLRequest(url: baseURL.appendingPathComponent("v1/transfers/\(transferId)/files/\(fileId)"))
        request.httpMethod = "PUT"
        request.setValue("image/jpeg", forHTTPHeaderField: "Content-Type")
        request.addSignature(device: pairedDevice)

        let (_, response) = try await URLSession.shared.upload(for: request, from: body)
        return (response as? HTTPURLResponse)?.statusCode ?? -1
    }

    private func completeTransfer(transferId: String) async throws -> CompleteResponse {
        var request = URLRequest(url: baseURL.appendingPathComponent("v1/transfers/\(transferId)/complete"))
        request.httpMethod = "POST"
        request.addSignature(device: pairedDevice)

        let (data, response) = try await URLSession.shared.data(for: request)
        XCTAssertEqual((response as? HTTPURLResponse)?.statusCode, 200)
        return try JSONDecoder().decode(CompleteResponse.self, from: data)
    }
}
