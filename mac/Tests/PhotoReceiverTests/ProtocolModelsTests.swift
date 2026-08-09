import Foundation
import XCTest
@testable import PhotoReceiver

final class ProtocolModelsTests: XCTestCase {

    func testManifestDecodesWithSize() throws {
        let json = """
        {
          "protocolVersion": 1,
          "files": [
            {
              "id": "file-1",
              "name": "IMG_20260802_173201.jpg",
              "mediaType": "image/jpeg",
              "size": 4837912
            }
          ]
        }
        """

        let manifest = try JSONDecoder().decode(TransferManifest.self, from: Data(json.utf8))

        XCTAssertEqual(manifest.protocolVersion, 1)
        XCTAssertEqual(manifest.files, [
            ManifestFile(id: "file-1", name: "IMG_20260802_173201.jpg", mediaType: "image/jpeg", size: 4_837_912)
        ])
    }

    func testManifestDecodesWithNullSize() throws {
        let json = """
        {
          "protocolVersion": 1,
          "files": [
            { "id": "file-1", "name": "a.jpg", "mediaType": "image/jpeg", "size": null }
          ]
        }
        """

        let manifest = try JSONDecoder().decode(TransferManifest.self, from: Data(json.utf8))

        XCTAssertNil(manifest.files[0].size)
    }
}
