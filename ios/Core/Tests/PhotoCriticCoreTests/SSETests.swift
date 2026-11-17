import Testing
@testable import PhotoCriticCore

struct SSETests {
    private func parse(_ lines: String...) -> [SSEEvent] {
        var parser = SSEParser()
        var events = lines.compactMap { parser.feed($0) }
        if let last = parser.finish() { events.append(last) }
        return events
    }

    @Test func dataOnlyEventsEndAtBlankLines() {
        #expect(parse(#"data: {"a":1}"#, "", #"data: {"b":2}"#, "") ==
            [SSEEvent(event: nil, data: #"{"a":1}"#), SSEEvent(event: nil, data: #"{"b":2}"#)])
    }

    @Test func namedEventsAndMultiLineData() {
        #expect(parse("event: content_block_delta", "data: line1", "data: line2", "") ==
            [SSEEvent(event: "content_block_delta", data: "line1\nline2")])
    }

    @Test func commentsAndUnknownFieldsAreIgnored() {
        #expect(parse(": keep-alive", "id: 7", "retry: 10", "data: x", "") == [SSEEvent(event: nil, data: "x")])
    }

    @Test func eventWithoutClosingBlankLineIsReturnedByFinish() {
        #expect(parse("data: [DONE]") == [SSEEvent(event: nil, data: "[DONE]")])
    }

    @Test func valueWithoutSpaceAfterColon() {
        #expect(parse("data:x", "") == [SSEEvent(event: nil, data: "x")])
    }

    @Test func blankLinesAloneProduceNothing() {
        #expect(parse("", "").isEmpty)
    }
}
