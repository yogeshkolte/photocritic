import Foundation

/// The repository root, found from this file's path, so tests read `shared/`
/// (design file 47).
enum RepoRoot {
    static let url: URL = {
        var url = URL(fileURLWithPath: #filePath)
        while url.path != "/" {
            url.deleteLastPathComponent()
            if FileManager.default.fileExists(atPath: url.appendingPathComponent("shared").path) {
                return url
            }
        }
        fatalError("shared/ not found above \(#filePath)")
    }()

    static func shared(_ path: String) throws -> Data {
        try Data(contentsOf: url.appendingPathComponent("shared").appendingPathComponent(path))
    }

    /// The `.json` file names in a `shared/` folder, sorted; empty if the folder does not exist.
    static func sharedJSONFiles(_ folder: String) -> [String] {
        let dir = url.appendingPathComponent("shared").appendingPathComponent(folder)
        let names = (try? FileManager.default.contentsOfDirectory(atPath: dir.path)) ?? []
        return names.filter { $0.hasSuffix(".json") }.sorted()
    }
}
