import Foundation

/// One server-sent event: the `event:` name, if any, and the joined `data:` lines.
public struct SSEEvent: Equatable, Sendable {
    public let event: String?
    public let data: String

    public init(event: String?, data: String) {
        self.event = event
        self.data = data
    }
}

/// Server-sent-events line parser (design §4). Feed it each line; it returns an
/// event at each blank line. Comment lines (`:`) and unknown fields are ignored.
public struct SSEParser: Sendable {
    private var event: String?
    private var data = ""
    private var hasData = false

    public init() {}

    public mutating func feed(_ line: String) -> SSEEvent? {
        if line.isEmpty { return dispatch() }
        if line.hasPrefix(":") { return nil }
        let field: Substring
        var value: Substring
        if let colon = line.firstIndex(of: ":") {
            field = line[..<colon]
            value = line[line.index(after: colon)...]
            if value.hasPrefix(" ") { value = value.dropFirst() }
        } else {
            field = line[...]
            value = ""
        }
        switch field {
        case "event":
            event = String(value)
        case "data":
            if hasData { data += "\n" }
            data += value
            hasData = true
        default:
            break
        }
        return nil
    }

    /// Call at the end of the stream: returns an event that had no closing blank line.
    public mutating func finish() -> SSEEvent? { dispatch() }

    private mutating func dispatch() -> SSEEvent? {
        defer {
            event = nil
            data = ""
            hasData = false
        }
        if !hasData && event == nil { return nil }
        return SSEEvent(event: event, data: data)
    }
}
