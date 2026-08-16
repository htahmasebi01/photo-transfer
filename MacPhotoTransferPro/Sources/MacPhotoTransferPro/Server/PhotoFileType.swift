import Foundation

/// Restricts what a transfer is allowed to create on disk.
///
/// `FileNaming` already blocks path traversal, so a sender cannot escape the chosen
/// folder. What it cannot judge is whether the name is a photo at all. The app is not
/// sandboxed, so if the user picks their home folder as the destination, an unrestricted
/// write is code execution on the next shell: `.zshenv`, `.command`, a `.dylib`.
///
/// An extension allowlist is the cheap half of the fix, and it is the half that matters,
/// because macOS decides how to treat a file from its extension. Content sniffing would
/// only add protection against a mislabelled payload, which stays inert either way.
enum PhotoFileType {

    static func isAcceptable(name: String) -> Bool {
        // The same last-path-component that FileNaming will write, so this judges the name
        // that actually lands on disk rather than the one that was sent.
        let fileName = (name as NSString).lastPathComponent
        guard !fileName.hasPrefix("."), fileName.count <= maximumNameLength else { return false }

        // Only the final extension decides how macOS treats the file, which is why
        // "photo.jpg.command" fails here.
        return allowedExtensions.contains((fileName as NSString).pathExtension.lowercased())
    }

    private static let maximumNameLength = 255

    private static let allowedExtensions: Set<String> = [
        "jpg", "jpeg", "jpe", "png", "gif", "bmp", "webp",
        "heic", "heif", "avif",
        "tif", "tiff", "dng", "raw", "arw", "cr2", "cr3", "nef", "orf", "rw2", "raf", "srw"
    ]
}
