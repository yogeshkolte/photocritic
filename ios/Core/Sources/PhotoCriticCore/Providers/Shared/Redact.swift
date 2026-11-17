import Foundation

/// Removes keys, URL query strings and file paths from text shown in "Show
/// details" or sent to telemetry (design file 14). Cases: `shared/redaction/cases.json`.
public enum Redact {
    public static let maxDetailChars = 200

    private static let keyPatterns: [NSRegularExpression] = [
        #"sk-ant-[A-Za-z0-9_-]{16,}"#,
        #"sk-[A-Za-z0-9_-]{16,}"#,
        #"AIza[0-9A-Za-z_-]{35}"#,
        #"AQ\.[0-9A-Za-z_-]{20,}"#,
        #"Bearer\s+\S+"#,
    ].map(regex)
    private static let urlQuery = regex(#"(https?://[^\s?#]+)\?[^\s#]*"#)
    private static let uri = regex(#"\b(?:file|content)://\S+"#)
    private static let absolutePath = regex(#"(?<![\w.:/])/(?:[\w.-]+/)+[\w.-]+"#)

    public static func text(_ input: String, userKey: String? = nil) -> String {
        var out = input
        if let userKey, !userKey.isEmpty { out = out.replacingOccurrences(of: userKey, with: "[key]") }
        for pattern in keyPatterns { out = replace(pattern, in: out, with: "[key]") }
        out = replace(urlQuery, in: out, with: "$1?[query]")
        out = replace(uri, in: out, with: "[path]")
        out = replace(absolutePath, in: out, with: "[path]")
        return out.count <= maxDetailChars ? out : String(out.prefix(maxDetailChars))
    }

    private static func regex(_ pattern: String) -> NSRegularExpression {
        // The patterns are constants; a bad one is a programming error.
        // swiftlint:disable:next force_try
        try! NSRegularExpression(pattern: pattern)
    }

    private static func replace(_ regex: NSRegularExpression, in text: String, with template: String) -> String {
        regex.stringByReplacingMatches(in: text, range: NSRange(text.startIndex..., in: text), withTemplate: template)
    }
}
