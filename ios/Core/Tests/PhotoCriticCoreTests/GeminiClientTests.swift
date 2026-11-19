import Foundation
import Testing
@testable import PhotoCriticCore

/// Gemini client against a stub that speaks the §3a stream format. Hand-written
/// streams cover what cannot be recorded on demand (safety stop, blocked prompt,
/// mid-stream error); `GeminiContractTests` replays the recordings.
struct GeminiClientTests {
    let host = StubProtocol.newHost()
    let key = "AQ.test-key-0123456789abcdefghij"

    private func client(hosts: Set<String>? = nil) -> GeminiClient {
        let http = ProviderHTTP(
            policy: HostPolicy(hosts: hosts ?? [host], requireHTTPS: false),
            requestTimeoutMs: 5000,
            protocolClasses: [StubProtocol.self]
        )
        return GeminiClient(http: http, baseURL: "http://\(host)")
    }

    private func request(history: [NeutralTurn] = []) -> TurnRequest {
        TurnRequest(
            model: ModelSettings(modelID: "gemini-4-hydrogen", maxOutputTokens: 512, contextTokens: 1_000_000),
            history: history,
            question: "What is this?",
            image: SendCopy(jpeg: Data([1, 2, 3]), width: 1, height: 1, imageDetail: "MEDIA_RESOLUTION_HIGH"),
            promptKind: .free
        )
    }

    private func stream(_ events: String...) {
        let body = events.map { "data: \($0)\r\n\r\n" }.joined()
        StubProtocol.enqueue(.init(status: 200, headers: ["Content-Type": "text/event-stream"], body: Data(body.utf8)), host: host)
    }

    private func chunk(_ text: String, finish: String? = nil) -> String {
        #"{"candidates":[{"content":{"parts":[{"text":"\#(text)"}],"role":"model"}"# +
            (finish.map { #","finishReason":"\#($0)""# } ?? "") +
            #"}],"modelVersion":"gemini-4-hydrogen-001","responseId":"resp_1"}"#
    }

    private func events(_ client: GeminiClient, _ request: TurnRequest) async -> [TurnEvent] {
        var out: [TurnEvent] = []
        for await event in client.send(request, apiKey: key) { out.append(event) }
        return out
    }

    private func lastError(_ events: [TurnEvent]) -> PcError? {
        if case let .error(error)? = events.last { return error }
        return nil
    }

    @Test func streamsTextThenDoneWithTheBodyResponseIDAsRequestID() async {
        stream(chunk("A white "), chunk("square."), chunk("", finish: "STOP"))
        #expect(await events(client(), request()) == [
            .text("A white "), .text("square."),
            .done(.stop, servedModel: "gemini-4-hydrogen-001", requestID: "resp_1"),
        ])
    }

    @Test func keyOnlyInTheHeaderImageAndResolutionInTheBodyOneRequest() async throws {
        stream(chunk("ok", finish: "STOP"))
        _ = await events(client(), request(history: [NeutralTurn(question: "First?", answerText: "First answer.", imageVersionID: "img1")]))
        let recorded = StubProtocol.requests(host: host)
        #expect(recorded.count == 1)
        let first = try #require(recorded.first)
        #expect(first.request.value(forHTTPHeaderField: "x-goog-api-key") == key)
        #expect(first.request.url?.absoluteString.contains(key) == false)
        #expect(first.request.url?.path == "/v1beta/models/gemini-4-hydrogen:streamGenerateContent")
        #expect(first.request.url?.query == "alt=sse")
        let body = try #require(try JSONSerialization.jsonObject(with: first.body) as? [String: Any])
        let contents = try #require(body["contents"] as? [[String: Any]])
        #expect(contents.compactMap { $0["role"] as? String } == ["user", "model", "user"])
        let firstParts = try #require(contents[0]["parts"] as? [[String: Any]])
        #expect((firstParts[0]["inlineData"] as? [String: Any])?["data"] as? String == "AQID")
        #expect(firstParts[1]["text"] as? String == "First?")
        let config = try #require(body["generationConfig"] as? [String: Any])
        #expect(config["mediaResolution"] as? String == "MEDIA_RESOLUTION_HIGH")
        #expect(config["maxOutputTokens"] as? Int == 512)
        #expect(config["thinkingConfig"] == nil)
    }

    @Test func invalidKeyGivesBeforeResponseErrorWithoutTheKeyInDetail() async throws {
        let body = #"{"error":{"code":400,"message":"API key not valid: \#(key)","status":"INVALID_ARGUMENT"}}"#
        StubProtocol.enqueue(.init(status: 400, body: Data(body.utf8)), host: host)
        let out = await events(client(), request())
        let error = try #require(lastError(out))
        #expect(out.count == 1)
        #expect(error.phase == .beforeResponse)
        #expect(error.httpStatus == 400)
        #expect(error.providerCode == "INVALID_ARGUMENT")
        #expect(error.requestID == nil)
        #expect(!error.detail.contains(key))
        #expect(StubProtocol.requests(host: host).count == 1)
    }

    @Test func safetyStopIsAFinishFailureThatKeepsEarlierText() async throws {
        stream(chunk("Partial"), chunk("", finish: "SAFETY"))
        let out = await events(client(), request())
        #expect(out.first == .text("Partial"))
        let error = try #require(lastError(out))
        #expect(error.phase == .finish)
        #expect(error.providerCode == "SAFETY")
        #expect(error.requestID == "resp_1")
    }

    @Test func maxTokensEndsTheAnswerNormally() async {
        stream(chunk("Long", finish: "MAX_TOKENS"))
        #expect(await events(client(), request()).last == .done(.length, servedModel: "gemini-4-hydrogen-001", requestID: "resp_1"))
    }

    @Test func blockedPromptIsAFinishFailure() async throws {
        stream(#"{"promptFeedback":{"blockReason":"PROHIBITED_CONTENT"},"responseId":"resp_2"}"#)
        let error = try #require(lastError(await events(client(), request())))
        #expect(error.phase == .finish)
        #expect(error.providerCode == "PROHIBITED_CONTENT")
    }

    @Test func streamEndingWithoutFinishReasonIsMidStreamFailure() async throws {
        stream(chunk("Cut"))
        #expect(try #require(lastError(await events(client(), request()))).phase == .midStream)
    }

    @Test func errorEventAfter200IsMidStreamFailure() async throws {
        stream(chunk("Start"), #"{"error":{"code":503,"message":"overloaded","status":"UNAVAILABLE"}}"#)
        let error = try #require(lastError(await events(client(), request())))
        #expect(error.phase == .midStream)
        #expect(error.providerCode == "UNAVAILABLE")
    }

    @Test func thoughtPartsAreNotShown() async {
        stream(#"{"candidates":[{"content":{"parts":[{"text":"thinking","thought":true},{"text":"Answer"}]},"finishReason":"STOP"}]}"#)
        #expect(await events(client(), request()).first == .text("Answer"))
    }

    @Test func stoppingAfterTheFirstTextEndsTheStream() async {
        stream(chunk("One"), chunk("Two"), chunk("", finish: "STOP"))
        var first: TurnEvent?
        for await event in client().send(request(), apiKey: key) {
            first = event
            break
        }
        #expect(first == .text("One"))
        #expect(StubProtocol.requests(host: host).count == 1)
    }

    @Test func disallowedHostIsBeforeResponseFailureWithNoRequestSent() async throws {
        let out = await events(client(hosts: ["api.example.invalid"]), request())
        let error = try #require(lastError(out))
        #expect(out.count == 1)
        #expect(error.phase == .beforeResponse)
        #expect(error.providerCode == "host-not-allowed")
        #expect(StubProtocol.requests(host: host).isEmpty)
    }
}

/// The key check (§3): a tiny text-only call; only the status matters.
struct GeminiKeyCheckTests {
    let host = StubProtocol.newHost()
    let key = "AQ.test-key-0123456789abcdefghij"
    let model = ModelSettings(modelID: "gemini-4-hydrogen", maxOutputTokens: 8192, contextTokens: 1_000_000, thinkingLevel: "low")

    private func client() -> GeminiClient {
        let policy = HostPolicy(hosts: [host], requireHTTPS: false)
        let http = ProviderHTTP(policy: policy, requestTimeoutMs: 5000, protocolClasses: [StubProtocol.self])
        return GeminiClient(http: http, baseURL: "http://\(host)")
    }

    @Test func anOKStatusPasses() async throws {
        StubProtocol.enqueue(.init(status: 200, body: Data(#"{"candidates":[]}"#.utf8)), host: host)
        #expect(await client().checkKey(key, model: model) == .ok)
        let sent = try #require(StubProtocol.requests(host: host).first)
        #expect(sent.request.url?.path == "/v1beta/models/gemini-4-hydrogen:generateContent")
        #expect(sent.request.value(forHTTPHeaderField: "x-goog-api-key") == key)
        let body = String(decoding: sent.body, as: UTF8.self)
        #expect(body.contains("Reply with OK."))
        #expect(body.contains(#""thinkingLevel":"low""#))
        #expect(!body.contains("systemInstruction"))
    }

    @Test func aRejectedKeyFailsWithoutTheKey() async throws {
        let error = #"{"error":{"code":400,"message":"API key not valid: \#(key)","status":"INVALID_ARGUMENT"}}"#
        StubProtocol.enqueue(.init(status: 400, body: Data(error.utf8)), host: host)
        guard case let .failed(e) = await client().checkKey(key, model: model) else {
            Issue.record("accepted")
            return
        }
        #expect(e.httpStatus == 400)
        #expect(e.providerCode == "INVALID_ARGUMENT")
        #expect(!e.detail.contains(key))
    }

    @Test func aHostOutsideTheAllowlistFails() async {
        let policy = HostPolicy(hosts: [], requireHTTPS: false)
        let http = ProviderHTTP(policy: policy, requestTimeoutMs: 5000, protocolClasses: [StubProtocol.self])
        guard case let .failed(e) = await GeminiClient(http: http, baseURL: "http://\(host)").checkKey(key, model: model) else {
            Issue.record("accepted")
            return
        }
        #expect(e.providerCode == "host-not-allowed")
    }
}
