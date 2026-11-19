import Foundation

/// A piece of an answer: plain text, or a "Text in photo" card (G3, D36).
public enum Segment: Equatable, Sendable {
    case text(String)
    case card(String)
}

/// Splits an answer into plain text and cards (§3, "Extracted text"). The
/// base prompt asks the model to wrap text copied from the photo in a fence
/// tagged `text`: a line that is only ```` ```text ```` or `~~~text`, closed by
/// a line that is only the same fence. Other fenced blocks stay plain text. A
/// fence that is not yet closed (an answer still streaming) is plain text
/// until it closes. Tested against `shared/vectors/extracted-text.json`.
public enum ExtractedText {
    public static func parse(_ answer: String) -> [Segment] {
        let lines = answer.components(separatedBy: "\n")
        var segments: [Segment] = []
        var plain: [String] = []
        var i = 0
        while i < lines.count {
            let fence = openingFence(dropCR(lines[i]))
            let close = fence.flatMap { f in (i + 1 ..< lines.count).first { dropCR(lines[$0]).trimmingCharacters(in: .whitespaces) == f } }
            guard let close else {
                plain.append(lines[i])
                i += 1
                continue
            }
            flush(&plain, &segments)
            segments.append(.card(lines[(i + 1) ..< close].map(dropCR).joined(separator: "\n")))
            i = close + 1
        }
        flush(&plain, &segments)
        return segments
    }

    /// "```" or "~~~" when the line is that fence tagged `text`, with optional trailing whitespace.
    private static func openingFence(_ line: String) -> String? {
        for fence in ["```", "~~~"] where line.hasPrefix(fence + "text") {
            let rest = line.dropFirst(fence.count + 4)
            if rest.allSatisfy(\.isWhitespace) { return fence }
        }
        return nil
    }

    private static func dropCR(_ line: String) -> String {
        var line = line
        while line.hasSuffix("\r") { line.removeLast() }
        return line
    }

    /// Copy and Share give the card text exactly; plain text is trimmed of blank edges.
    private static func flush(_ plain: inout [String], _ segments: inout [Segment]) {
        let text = plain.joined(separator: "\n").trimmingCharacters(in: CharacterSet(charactersIn: "\n\r "))
        if !text.isEmpty { segments.append(.text(text)) }
        plain.removeAll()
    }
}
