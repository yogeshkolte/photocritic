import Foundation

/// Gemini client (design §3a, file 45). Sends one streaming request through
/// `ProviderHTTP`; the key goes only in `x-goog-api-key`. Gemini sends no
/// request-ID header: the request ID is the body's `responseId`.
public struct GeminiClient: Provider {
    public let id = ProviderID.gemini
    private let http: ProviderHTTP
    private let baseURL: String

    public init(http: ProviderHTTP, baseURL: String = Allowlist.baseURLs["gemini"]!) {
        self.http = http
        self.baseURL = baseURL
    }

    public func send(_ request: TurnRequest, apiKey: String) -> AsyncStream<TurnEvent> {
        AsyncStream { continuation in
            let task = Task {
                var state = StreamState(apiKey: apiKey, out: continuation)
                await state.run(http: http, request: request, baseURL: baseURL)
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    private struct StreamState {
        let apiKey: String
        let out: AsyncStream<TurnEvent>.Continuation
        var headSeen = false
        var finished = false
        var requestID: String?
        var servedModel: String?
        var parser = SSEParser()

        mutating func run(http: ProviderHTTP, request: TurnRequest, baseURL: String) async {
            let body: Data
            do {
                body = try GeminiWire.requestBody(request)
            } catch {
                return fail(.beforeResponse, nil, "encode-failed", "The request could not be built.")
            }
            guard let url = URL(string: "\(baseURL)/v1beta/models/\(request.model.modelID):streamGenerateContent?alt=sse") else {
                return fail(.beforeResponse, nil, "bad-url", "The request URL could not be built.")
            }
            let providerRequest = ProviderRequest(
                url: url, headers: ["x-goog-api-key": apiKey], jsonBody: body, requestIDHeader: nil
            )
            do {
                for try await chunk in http.exchange(providerRequest) {
                    switch chunk {
                    case let .head(status, _, errorBody):
                        onHead(status: status, errorBody: errorBody)
                    case let .line(line):
                        if let event = parser.feed(line) { onEvent(event) }
                    }
                }
                if let event = parser.finish() { onEvent(event) }
                if !finished { fail(.midStream, nil, nil, "The stream ended without a finish reason.") }
            } catch is CancellationError {
                return
            } catch let error as HostNotAllowedError {
                failNetwork("host-not-allowed", "host not allowed: \(error.host)")
            } catch {
                if Task.isCancelled { return }
                let code = (error as? URLError).map { "URLError.\($0.code.rawValue)" } ?? String(describing: type(of: error))
                failNetwork(code, error.localizedDescription)
            }
        }

        mutating func onHead(status: Int, errorBody: String?) {
            headSeen = true
            guard let errorBody else { return }
            let error = try? JSONDecoder().decode(GeminiWire.ErrorEnvelope.self, from: Data(errorBody.utf8)).error
            fail(.beforeResponse, status, error?.status, error?.message ?? "HTTP \(status)")
        }

        mutating func onEvent(_ event: SSEEvent) {
            if finished || event.data.trimmingCharacters(in: .whitespaces).isEmpty || event.data == "[DONE]" { return }
            guard let chunk = try? JSONDecoder().decode(GeminiWire.StreamChunk.self, from: Data(event.data.utf8)) else {
                return fail(.midStream, nil, "unreadable-event", "An answer event could not be read.")
            }
            if let id = chunk.responseId { requestID = id }
            if let model = chunk.modelVersion { servedModel = model }
            if let error = chunk.error {
                return fail(.midStream, error.code, error.status, error.message ?? "Error event.")
            }
            if let reason = chunk.promptFeedback?.blockReason {
                return fail(.finish, nil, reason, "The request was blocked: \(reason).")
            }
            guard let candidate = chunk.candidates?.first else { return }
            for part in candidate.content?.parts ?? [] where part.thought != true {
                if let text = part.text, !text.isEmpty { out.yield(.text(text)) }
            }
            switch candidate.finishReason {
            case nil, "FINISH_REASON_UNSPECIFIED":
                break
            case "STOP":
                done(.stop)
            case "MAX_TOKENS":
                done(.length)
            case let reason?:
                // SAFETY, RECITATION, BLOCKLIST, PROHIBITED_CONTENT, SPII, IMAGE_SAFETY, OTHER and new reasons.
                fail(.finish, nil, reason, "The answer was stopped: \(reason).")
            }
        }

        mutating func done(_ reason: FinishReason) {
            finished = true
            out.yield(.done(reason, servedModel: servedModel, requestID: requestID))
        }

        mutating func failNetwork(_ code: String, _ message: String) {
            if finished { return }
            fail(headSeen ? .midStream : .beforeResponse, nil, code, message)
        }

        mutating func fail(_ phase: PcPhase, _ httpStatus: Int?, _ providerCode: String?, _ message: String) {
            finished = true
            out.yield(.error(ToPcError.make(
                phase: phase, httpStatus: httpStatus, providerCode: providerCode,
                requestID: requestID, message: message, apiKey: apiKey
            )))
        }
    }
}
