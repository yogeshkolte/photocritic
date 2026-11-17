import Foundation
import Testing
@testable import PhotoCriticCore

/// Design §4 rules, against a `URLProtocol` stub (Verification Plan: client acceptance tests).
struct ProviderHTTPTests {
    let host = StubProtocol.newHost()

    private func http(hosts: Set<String>? = nil) -> ProviderHTTP {
        ProviderHTTP(
            policy: HostPolicy(hosts: hosts ?? [host], requireHTTPS: false),
            requestTimeoutMs: 5000,
            protocolClasses: [StubProtocol.self]
        )
    }

    private func request(requestIDHeader: String? = "x-request-id") -> ProviderRequest {
        ProviderRequest(
            url: URL(string: "http://\(host)/v1/test")!,
            headers: ["x-test-key": "secret"],
            jsonBody: Data("{}".utf8),
            requestIDHeader: requestIDHeader
        )
    }

    private func collect(_ stream: AsyncThrowingStream<HTTPChunk, Error>) async throws -> [HTTPChunk] {
        var chunks: [HTTPChunk] = []
        for try await chunk in stream { chunks.append(chunk) }
        return chunks
    }

    @Test func hostOutsideThePolicyIsRefusedBeforeAnythingIsSent() async {
        await #expect(throws: HostNotAllowedError.self) {
            _ = try await collect(http(hosts: ["api.example.invalid"]).exchange(request()))
        }
        #expect(StubProtocol.requests(host: host).isEmpty)
    }

    @Test func productionPolicyRefusesPlainHTTPAndUnknownHosts() {
        let policy = HostPolicy.production
        #expect(policy.allows(URL(string: "https://api.openai.com/v1")!))
        #expect(!policy.allows(URL(string: "http://api.openai.com/v1")!))
        #expect(!policy.allows(URL(string: "https://api.openai.com.evil.example/v1")!))
    }

    @Test func redirectStatusIsReturnedNotFollowed() async throws {
        StubProtocol.enqueue(.init(status: 302, headers: ["Location": "http://\(host)/elsewhere"]), host: host)
        let chunks = try await collect(http().exchange(request()))
        #expect(chunks == [.head(status: 302, requestID: nil, errorBody: "")])
        #expect(StubProtocol.requests(host: host).count == 1)
    }

    @Test func errorIsNotRetriedAndItsBodyAndRequestIDAreReturned() async throws {
        StubProtocol.enqueue(
            .init(status: 503, headers: ["x-request-id": "req_1"], body: Data(#"{"error":"overloaded"}"#.utf8)),
            host: host
        )
        let chunks = try await collect(http().exchange(request()))
        #expect(chunks == [.head(status: 503, requestID: "req_1", errorBody: #"{"error":"overloaded"}"#)])
        #expect(StubProtocol.requests(host: host).count == 1)
    }

    @Test func bodyIsReturnedLineByLineKeepingBlankLines() async throws {
        StubProtocol.enqueue(.init(status: 200, body: Data("data: a\r\n\r\ndata: b\n".utf8)), host: host)
        let chunks = try await collect(http().exchange(request(requestIDHeader: nil)))
        #expect(chunks == [.head(status: 200, requestID: nil, errorBody: nil), .line("data: a"), .line(""), .line("data: b")])
        let recorded = try #require(StubProtocol.requests(host: host).first)
        #expect(recorded.request.value(forHTTPHeaderField: "x-test-key") == "secret")
        #expect(recorded.request.value(forHTTPHeaderField: "Cookie") == nil)
    }
}
