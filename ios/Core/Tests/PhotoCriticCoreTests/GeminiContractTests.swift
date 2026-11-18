import Foundation
import Testing
@testable import PhotoCriticCore

/// Contract tests against the recordings in `shared/` (design files 13 and 47):
/// every recorded Gemini response gives its expected events, and every request
/// input gives exactly the recorded body.
struct GeminiContractTests {
    static let responses = ["provider-streams/gemini", "provider-errors/gemini"].flatMap { folder in
        RepoRoot.sharedJSONFiles(folder).map { "\(folder)/\($0)" }
    }
    static let requestNames = RepoRoot.sharedJSONFiles("provider-requests/gemini")
        .filter { $0.hasSuffix(".input.json") }
        .map { String($0.dropLast(".input.json".count)) }

    let host = StubProtocol.newHost()
    let key = "AQ.contract-test-key-0123456789abcdef"

    private func client() -> GeminiClient {
        let http = ProviderHTTP(
            policy: HostPolicy(hosts: [host], requireHTTPS: false),
            requestTimeoutMs: 5000,
            protocolClasses: [StubProtocol.self]
        )
        return GeminiClient(http: http, baseURL: "http://\(host)")
    }

    private func json(_ path: String) throws -> [String: Any] {
        try #require(try JSONSerialization.jsonObject(with: RepoRoot.shared(path)) as? [String: Any])
    }

    private func request(_ name: String) throws -> TurnRequest {
        let input = try json("provider-requests/gemini/\(name).input.json")
        let history = try #require(input["history"] as? [[String: String]])
        let image = try #require(input["image"] as? String)
        let kind = try #require(input["promptKind"] as? String)
        return TurnRequest(
            model: ModelSettings(
                modelID: try #require(input["modelId"] as? String),
                maxOutputTokens: try #require(input["maxOutputTokens"] as? Int),
                contextTokens: 1_000_000,
                thinkingLevel: try #require(input["thinkingLevel"] as? String)
            ),
            history: history.enumerated().map { index, turn in
                NeutralTurn(question: turn["question"] ?? "", answerText: turn["answerText"] ?? "", imageVersionID: "img\(index)")
            },
            question: try #require(input["question"] as? String),
            image: SendCopy(
                jpeg: try RepoRoot.shared(String(image.dropFirst("shared/".count))),
                width: 0,
                height: 0,
                imageDetail: try #require(input["imageDetail"] as? String)
            ),
            promptKind: try #require(PromptKind(rawValue: kind))
        )
    }

    private func events(_ request: TurnRequest) async -> [TurnEvent] {
        var out: [TurnEvent] = []
        for await event in client().send(request, apiKey: key) { out.append(event) }
        return out
    }

    private func asJSON(_ event: TurnEvent) -> [String: Any] {
        switch event {
        case let .text(delta):
            return ["type": "text", "delta": delta]
        case let .done(reason, servedModel, requestID):
            return [
                "type": "done", "finishReason": reason == .stop ? "stop" : "length",
                "servedModel": servedModel ?? NSNull(), "requestId": requestID ?? NSNull(),
            ]
        case let .error(error):
            return ["type": "error", "error": [
                "phase": error.phase.rawValue, "httpStatus": error.httpStatus ?? NSNull(),
                "providerCode": error.providerCode ?? NSNull(), "requestId": error.requestID ?? NSNull(),
            ] as [String: Any]]
        }
    }

    @Test func recordingsArePresent() {
        #expect(Self.responses.count >= 5)
        #expect(!Self.requestNames.isEmpty)
    }

    @Test(arguments: responses)
    func recordedResponseGivesTheExpectedEvents(path: String) async throws {
        let fixture = try json(path)
        let lines = fixture["sseLines"] as? [String]
        let body = lines.map { $0.map { $0 + "\n" }.joined() } ?? (fixture["body"] as? String ?? "")
        StubProtocol.enqueue(
            .init(
                status: try #require(fixture["status"] as? Int),
                headers: try #require(fixture["headers"] as? [String: String]),
                body: Data(body.utf8)
            ),
            host: host
        )
        let got = await events(try request("normal"))
        let expected = try #require(fixture["expected"] as? [Any])
        #expect(NSArray(array: got.map(asJSON)).isEqual(to: expected), "\(got)")
        for case let .error(error) in got {
            #expect(!error.detail.contains(key))
        }
        #expect(StubProtocol.requests(host: host).count == 1)
    }

    @Test(arguments: requestNames)
    func requestInputGivesTheRecordedBody(name: String) async throws {
        StubProtocol.enqueue(.init(status: 200), host: host)
        _ = await events(try request(name))
        let sent = try #require(StubProtocol.requests(host: host).first?.body)
        let got = try JSONSerialization.jsonObject(with: sent) as? NSDictionary
        let expected = try JSONSerialization.jsonObject(with: RepoRoot.shared("provider-requests/gemini/\(name).json")) as? NSDictionary
        #expect(got != nil && got == expected)
    }
}
