import Foundation
import XCTest
@testable import PhotoReceiver

final class FileNamingTests: XCTestCase {

    func testSanitizedStripsDirectoryComponents() {
        XCTAssertEqual(FileNaming.sanitized("../../etc/passwd"), "passwd")
        XCTAssertEqual(FileNaming.sanitized("/tmp/evil.jpg"), "evil.jpg")
        XCTAssertEqual(FileNaming.sanitized("photos/IMG_1.jpg"), "IMG_1.jpg")
    }

    func testSanitizedKeepsPlainNames() {
        XCTAssertEqual(FileNaming.sanitized("IMG_20260802_173201.jpg"), "IMG_20260802_173201.jpg")
    }

    func testSanitizedFallsBackForUnusableNames() {
        XCTAssertEqual(FileNaming.sanitized(""), "photo")
        XCTAssertEqual(FileNaming.sanitized(".."), "photo")
        XCTAssertEqual(FileNaming.sanitized("/"), "photo")
        XCTAssertEqual(FileNaming.sanitized("   "), "photo")
    }

    func testCollisionSafeURLWithoutCollision() {
        let directory = URL(fileURLWithPath: "/dest")
        let url = FileNaming.collisionSafeURL(
            forSuppliedName: "IMG_1234.jpg",
            in: directory,
            fileExists: { _ in false }
        )
        XCTAssertEqual(url.lastPathComponent, "IMG_1234.jpg")
    }

    func testCollisionSafeURLAppendsCounter() {
        let directory = URL(fileURLWithPath: "/dest")
        let taken: Set<String> = ["IMG_1234.jpg", "IMG_1234 (1).jpg"]
        let url = FileNaming.collisionSafeURL(
            forSuppliedName: "IMG_1234.jpg",
            in: directory,
            fileExists: { taken.contains($0.lastPathComponent) }
        )
        XCTAssertEqual(url.lastPathComponent, "IMG_1234 (2).jpg")
    }

    func testCollisionSafeURLWithoutExtension() {
        let directory = URL(fileURLWithPath: "/dest")
        let url = FileNaming.collisionSafeURL(
            forSuppliedName: "photo",
            in: directory,
            fileExists: { $0.lastPathComponent == "photo" }
        )
        XCTAssertEqual(url.lastPathComponent, "photo (1)")
    }
}
