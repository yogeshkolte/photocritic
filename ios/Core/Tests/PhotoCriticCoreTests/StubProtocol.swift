import Foundation

/// A `URLProtocol` stub in place of a server (design file 47). Each test uses
/// its own host, so tests running in parallel do not share replies.
final class StubProtocol: URLProtocol, @unchecked Sendable {
    struct Reply {
        var status: Int
        var headers: [String: String] = [:]
        var body: Data = Data()
    }

    struct Recorded {
        let request: URLRequest
        let body: Data
    }

    private static let lock = NSLock()
    nonisolated(unsafe) private static var replies: [String: [Reply]] = [:]
    nonisolated(unsafe) private static var recorded: [String: [Recorded]] = [:]

    /// A fresh host name for one test.
    static func newHost() -> String { "t\(UUID().uuidString.prefix(8).lowercased()).stub.test" }

    static func enqueue(_ reply: Reply, host: String) {
        lock.withLock { replies[host, default: []].append(reply) }
    }

    static func requests(host: String) -> [Recorded] {
        lock.withLock { recorded[host] ?? [] }
    }

    override static func canInit(with request: URLRequest) -> Bool { true }
    override static func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        let host = request.url?.host ?? ""
        let body = Self.readBody(request)
        let reply: Reply? = Self.lock.withLock {
            Self.recorded[host, default: []].append(Recorded(request: request, body: body))
            return Self.replies[host]?.isEmpty == false ? Self.replies[host]!.removeFirst() : nil
        }
        guard let reply, let url = request.url,
              let response = HTTPURLResponse(
                  url: url, statusCode: reply.status, httpVersion: "HTTP/1.1", headerFields: reply.headers
              )
        else {
            client?.urlProtocol(self, didFailWithError: URLError(.cannotConnectToHost))
            return
        }
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: reply.body)
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}

    private static func readBody(_ request: URLRequest) -> Data {
        if let body = request.httpBody { return body }
        guard let stream = request.httpBodyStream else { return Data() }
        stream.open()
        defer { stream.close() }
        var data = Data()
        var buffer = [UInt8](repeating: 0, count: 4096)
        while stream.hasBytesAvailable {
            let count = stream.read(&buffer, maxLength: buffer.count)
            if count <= 0 { break }
            data.append(buffer, count: count)
        }
        return data
    }
}
