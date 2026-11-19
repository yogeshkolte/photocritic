import Foundation

/// A `MAJOR.MINOR.PATCH` version, compared by number so `1.10.0` is above
/// `1.9.0` (design file 26). Cases: `shared/vectors/app-version.json`.
public struct AppVersion: Comparable, Sendable, CustomStringConvertible {
    public let major: Int
    public let minor: Int
    public let patch: Int

    public init(major: Int, minor: Int, patch: Int) {
        self.major = major
        self.minor = minor
        self.patch = patch
    }

    public var description: String { "\(major).\(minor).\(patch)" }

    public static func < (lhs: AppVersion, rhs: AppVersion) -> Bool {
        (lhs.major, lhs.minor, lhs.patch) < (rhs.major, rhs.minor, rhs.patch)
    }

    /// Nil unless `text` is exactly three dot-separated numbers of at most 9 digits.
    public static func parse(_ text: String) -> AppVersion? {
        let parts = text.split(separator: ".", omittingEmptySubsequences: false)
        guard parts.count == 3 else { return nil }
        let numbers = parts.compactMap { part -> Int? in
            guard (1...9).contains(part.count), part.allSatisfy({ $0.isASCII && $0.isNumber }) else { return nil }
            return Int(part)
        }
        guard numbers.count == 3 else { return nil }
        return AppVersion(major: numbers[0], minor: numbers[1], patch: numbers[2])
    }
}
