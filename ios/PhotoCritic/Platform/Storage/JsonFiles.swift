import Foundation

/// What a read found; a file that exists but cannot be read is not "missing" (§9).
enum FileRead<T: Sendable>: Sendable {
    case missing
    case unreadable
    case ok(T)
}

/// The small JSON files in §9, in Application Support. A write replaces the
/// file atomically; the actor makes one writer per store at a time (file 31).
actor JsonFiles {
    private let dir: URL

    init(dir: URL) {
        self.dir = dir
    }

    private func url(_ name: String) -> URL { dir.appendingPathComponent(name) }

    func readState<T: Decodable & Sendable>(_ name: String, as type: T.Type) -> FileRead<T> {
        let url = url(name)
        guard FileManager.default.fileExists(atPath: url.path) else { return .missing }
        guard let data = try? Data(contentsOf: url), let value = try? JSONDecoder().decode(T.self, from: data) else { return .unreadable }
        return .ok(value)
    }

    /// The file's value, or `fallback` when it is missing or unreadable.
    func read<T: Decodable & Sendable>(_ name: String, default fallback: T) -> T {
        if case let .ok(value) = readState(name, as: T.self) { return value }
        return fallback
    }

    /// Throws when the write fails; the previous file is kept.
    func write<T: Encodable & Sendable>(_ name: String, _ value: T) throws {
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        try encoder.encode(value).write(to: url(name), options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
    }

    /// Reads, changes and writes in one step, so two updates cannot interleave.
    func update<T: Codable & Sendable>(_ name: String, default fallback: T, _ change: @Sendable (T) -> T) throws -> T {
        let next = change(read(name, default: fallback))
        try write(name, next)
        return next
    }
}

enum UtcTime {
    static func iso(_ date: Date = Date()) -> String {
        date.formatted(.iso8601.year().month().day().dateSeparator(.dash).time(includingFractionalSeconds: false).timeSeparator(.colon))
    }
}
