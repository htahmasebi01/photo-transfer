import Foundation

enum FileNaming {

    static func sanitized(_ suppliedName: String) -> String {
        // NSString.lastPathComponent is purely lexical; URL(fileURLWithPath:)
        // would resolve "." and ".." against the current working directory.
        let lastComponent = (suppliedName as NSString).lastPathComponent
        let trimmed = lastComponent.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, trimmed != "/", trimmed != ".", trimmed != ".." else {
            return "photo"
        }
        return trimmed
    }

    static func collisionSafeURL(
        forSuppliedName suppliedName: String,
        in directory: URL,
        fileExists: (URL) -> Bool = { FileManager.default.fileExists(atPath: $0.path) }
    ) -> URL {
        let name = sanitized(suppliedName)
        let base = (name as NSString).deletingPathExtension
        let ext = (name as NSString).pathExtension

        var candidate = directory.appendingPathComponent(name)
        var counter = 1
        while fileExists(candidate) {
            let numbered = ext.isEmpty ? "\(base) (\(counter))" : "\(base) (\(counter)).\(ext)"
            candidate = directory.appendingPathComponent(numbered)
            counter += 1
        }
        return candidate
    }
}
