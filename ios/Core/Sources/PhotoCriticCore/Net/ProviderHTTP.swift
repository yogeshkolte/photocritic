import Foundation

/// A request a provider client asks `ProviderHTTP` to send. The key travels only in `headers`.
public struct ProviderRequest: Sendable {
    public let url: URL
    public let headers: [String: String]
    public let jsonBody: Data?
    /// The response header that holds the provider's request ID, if it has one (§3a).
    public let requestIDHeader: String?

    public init(url: URL, headers: [String: String], jsonBody: Data?, requestIDHeader: String?) {
        self.url = url
        self.headers = headers
        self.jsonBody = jsonBody
        self.requestIDHeader = requestIDHeader
    }
}

/// What `ProviderHTTP.exchange` yields: the response head once, then the body line by line.
public enum HTTPChunk: Equatable, Sendable {
    /// `errorBody` is set, and no lines follow, when `status` is not 2xx. A 3xx is never followed.
    case head(status: Int, requestID: String?, errorBody: String?)
    case line(String)
}

/// Thrown before any data is sent when a request's host is not allowed.
public struct HostNotAllowedError: Error, Equatable {
    public let host: String
}

/// The one HTTP client for provider calls (design §4). It refuses hosts outside
/// the allowlist, never follows redirects, never retries, applies the request
/// timeout, and keeps no cache, cookies or credentials (ephemeral session).
/// Cancelling the consuming task cancels the request (Stop).
public final class ProviderHTTP: Sendable {
    private let policy: HostPolicy
    private let session: URLSession
    private static let errorBodyLimit = 64 * 1024

    /// `protocolClasses` is for tests (a `URLProtocol` stub in place of a server).
    public init(
        policy: HostPolicy = .production,
        requestTimeoutMs: Int,
        protocolClasses: [AnyClass]? = nil
    ) {
        self.policy = policy
        let configuration = URLSessionConfiguration.ephemeral
        configuration.urlCache = nil
        configuration.httpCookieStorage = nil
        configuration.httpShouldSetCookies = false
        configuration.urlCredentialStorage = nil
        configuration.requestCachePolicy = .reloadIgnoringLocalCacheData
        // URLSession has no separate connect timeout: the idle and total timeouts
        // both use REQUEST_TIMEOUT_MS, so a slow first token is not cut off.
        configuration.timeoutIntervalForRequest = Double(requestTimeoutMs) / 1000
        configuration.timeoutIntervalForResource = Double(requestTimeoutMs) / 1000
        if let protocolClasses { configuration.protocolClasses = protocolClasses }
        session = URLSession(configuration: configuration, delegate: RefuseRedirects(policy: policy), delegateQueue: nil)
    }

    public func exchange(_ request: ProviderRequest) -> AsyncThrowingStream<HTTPChunk, Error> {
        AsyncThrowingStream { continuation in
            let task = Task {
                do {
                    try await self.run(request, continuation)
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    private func run(_ request: ProviderRequest, _ out: AsyncThrowingStream<HTTPChunk, Error>.Continuation) async throws {
        guard policy.allows(request.url) else { throw HostNotAllowedError(host: request.url.host ?? "") }
        var urlRequest = URLRequest(url: request.url)
        urlRequest.httpMethod = request.jsonBody == nil ? "GET" : "POST"
        urlRequest.httpBody = request.jsonBody
        if request.jsonBody != nil { urlRequest.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type") }
        for (name, value) in request.headers { urlRequest.setValue(value, forHTTPHeaderField: name) }

        let (bytes, response) = try await session.bytes(for: urlRequest)
        let http = response as? HTTPURLResponse
        let status = http?.statusCode ?? 0
        let requestID = request.requestIDHeader.flatMap { http?.value(forHTTPHeaderField: $0) }
        guard (200..<300).contains(status) else {
            var body = Data()
            for try await byte in bytes {
                body.append(byte)
                if body.count >= Self.errorBodyLimit { break }
            }
            out.yield(.head(status: status, requestID: requestID, errorBody: String(decoding: body, as: UTF8.self)))
            return
        }
        out.yield(.head(status: status, requestID: requestID, errorBody: nil))
        // Split lines by hand: blank lines end server-sent events and must be kept.
        var line = Data()
        for try await byte in bytes {
            if byte == UInt8(ascii: "\n") {
                if line.last == UInt8(ascii: "\r") { line.removeLast() }
                out.yield(.line(String(decoding: line, as: UTF8.self)))
                line.removeAll(keepingCapacity: true)
            } else {
                line.append(byte)
            }
        }
        if !line.isEmpty { out.yield(.line(String(decoding: line, as: UTF8.self))) }
    }

    private final class RefuseRedirects: NSObject, URLSessionTaskDelegate, Sendable {
        let policy: HostPolicy

        init(policy: HostPolicy) { self.policy = policy }

        func urlSession(
            _ session: URLSession,
            task: URLSessionTask,
            willPerformHTTPRedirection response: HTTPURLResponse,
            newRequest request: URLRequest
        ) async -> URLRequest? {
            nil
        }
    }
}
