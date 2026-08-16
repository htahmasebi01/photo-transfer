import XCTest
@testable import MacPhotoTransferPro

final class PhotoFileTypeTests: XCTestCase {

    func testCommonPhotoExtensionsAreAccepted() {
        for name in ["IMG_1234.jpg", "photo.JPEG", "scan.png", "burst.heic", "raw.DNG", "clip.webp"] {
            XCTAssertTrue(PhotoFileType.isAcceptable(name: name), name)
        }
    }

    /// The destination may be the home folder, and the app is not sandboxed, so a shell
    /// startup file or an executable would be code execution on the next login.
    func testExecutableAndShellNamesAreRejected() {
        for name in ["payload.command", "hook.sh", "inject.dylib", "tool", "app.zsh"] {
            XCTAssertFalse(PhotoFileType.isAcceptable(name: name), name)
        }
    }

    func testDotfilesAreRejected() {
        for name in [".zshenv", ".bash_profile", ".jpg"] {
            XCTAssertFalse(PhotoFileType.isAcceptable(name: name), name)
        }
    }

    func testAnExtensionAfterAnImageExtensionIsRejected() {
        XCTAssertFalse(PhotoFileType.isAcceptable(name: "photo.jpg.command"))
    }

    func testAnAbsurdlyLongNameIsRejected() {
        XCTAssertFalse(PhotoFileType.isAcceptable(name: String(repeating: "a", count: 300) + ".jpg"))
    }

    func testTraversalIsRejectedOnTheNameThatWouldBeWritten() {
        XCTAssertFalse(PhotoFileType.isAcceptable(name: "../../.zshenv"))
    }
}
