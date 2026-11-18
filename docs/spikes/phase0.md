# Phase 0 results

Status of each Phase 0 item for the native apps (design: Detailed
Implementation Plan, Implementation order and file 50). **Result** is filled
in with evidence (device, build, date).

## Setup

| Item | Result |
| --- | --- |
| Expo stack removed (file 1); `shared/tuning.json` created from `src/platform/config/tuning.ts` at `a1f4c49` | Done 2026-11-17 |
| Firebase service files moved to `android/app/src/{dev,prod}/` and `ios/Config/{Dev,Prod}/`; rules, indexes and CLI config moved to `firebase/` | Done 2026-11-17 |
| Toolchain: JDK 17 (Zulu), Android command-line tools with platform-tools, platforms 36 and 37.0, build-tools 36.1.0; Xcode 27.2; XcodeGen 2.47.0; SwiftLint 0.65.1 | Installed 2026-11-17 |
| Android scaffold (files 16–22): builds, lint and one test pass | Done 2026-11-17: `assembleDevDebug`, ktlint, detekt, `checkLayering`, `:core:test` (2), `koverVerify`, `testDevDebugUnitTest` (1, Robolectric SDK 35), `checkPermissions` |
| iOS scaffold (files 41–44): builds, lint and one test pass | Done 2026-11-17: `swift test`, `swiftlint --strict`, app build with Firebase 13.1.1 and the dev plist, app unit test and UI launch test on the iOS 27.2 simulator |
| CI for both apps (file 3) | Done 2026-11-17: android, ios and shared jobs pass on GitHub (`1e01f86`) |
| `shared/` README, fixture schema, prompts, Remote Config schema and template; `tools/` validate and record scripts | Done 2026-11-17: `config:validate` passes the template and rejects all 6 invalid samples; `fixture:record` and `fixture:check` (step 4) |
| Expo project on expo.dev deleted (author, D1) | Pending: needs the author's password on expo.dev |

## Wire formats (§3a, Q1)

Recorded 2026-11-17 with `npm run fixture:record` into `shared/provider-*`
(file 13), using `shared/images/test.jpg` (a 768×512 trail sign drawn for
this project). Models: `gpt-5.4-mini`, `claude-haiku-5-5`,
`gemini-4-hydrogen`. Cases: normal answer, second turn with history, length
limit (16 output tokens), invalid key, unknown model.

| Check | Gemini | Anthropic | OpenAI |
| --- | --- | --- | --- |
| Endpoint, key header and image fields as in §3a | Yes. The client sends camelCase `inlineData`/`mimeType`; the API accepts it | Yes | Yes (`input_image` with a `data:` URL and `detail`) |
| Text, end and failure event names as in §3a | Yes: `candidates[0].content.parts[].text`, `finishReason` `STOP` / `MAX_TOKENS` | Yes: `content_block_delta` / `text_delta`, `message_delta.stop_reason` (`end_turn`, `max_tokens`), `message_stop`. Also `ping`, and `thinking` blocks with `thinking_delta` and `signature_delta` (Haiku 5.5 thinks by default); both are skipped | Yes: `response.output_text.delta`, `response.completed`, `response.incomplete` with `incomplete_details.reason` `max_output_tokens` |
| Request ID location | Body `responseId`; no header; failed calls carry none | `request-id` header (also `request_id` in error bodies) | `x-request-id` header, also on errors |
| Error shapes | 401 `UNAUTHENTICATED` for an `AQ.` key that does not exist (reason `ACCESS_TOKEN_TYPE_UNSUPPORTED`; the earlier Node run saw 400 `INVALID_ARGUMENT` for another key shape); 404 `NOT_FOUND` for an unknown model | 401 `authentication_error`; 404 `not_found_error` | 401 `invalid_api_key` (message masks the key); 404 `model_not_found` |
| Refusal and content-filter shapes | Not recorded: cannot be triggered on demand. Hand-written tests cover `SAFETY` and `blockReason` | Not recorded (`stop_reason` `refusal`) | Not recorded (`response.refusal.delta`, content filter) |
| OpenAI `store: false` accepted | n/a | n/a | Yes: 200, and the response echoes `"store": false` |
| Length limit with a thinking model | `MAX_TOKENS` with no text: the 16 tokens went to thinking | `max_tokens` with no text: the 16 tokens went to thinking | Text, then `response.incomplete` |

Not recordable on demand, so still covered only by hand-written tests or
left for later: rate limit (429), overloaded (Anthropic 529, OpenAI 503),
no billing, Gemini 403, mid-stream error events, refusals.

Usage seen for the 768×512 image (prompt plus image, input tokens): Gemini
1,250 (image 1,080 at `MEDIA_RESOLUTION_HIGH`); Anthropic 722 (Anthropic's
formula gives 524 for the image); OpenAI 641 at `detail: high`. Anthropic
returns `cache_creation_input_tokens` and `cache_read_input_tokens` (0 on
these single calls).

Contract tests replay the recordings on both platforms: Android
`GeminiContractTest`, iOS `GeminiContractTests`. Each of the 5 Gemini
recordings gives its expected events, and both request inputs give exactly
the recorded body. CI runs `fixture:check`: every fixture matches the schema
and contains no key-shaped string. The OpenAI and Anthropic recordings wait
for those clients (Phase 3).

## Gemini client and `core/net` (step 5)

Built on both platforms on 2026-11-17 to the §3a and §4 tables: Android
`core/net` (`Allowlist.kt`, `ProviderHttp.kt`, `Sse.kt`) and
`providers/gemini/`; iOS `Core/Net/` and `Providers/Gemini/`, plus types,
prompts, redaction and error helpers. The same 26 checks pass on both: SSE
parsing (6), host policy and transport (5: refused host sends nothing, no
redirect followed, no retry, request ID header, lines kept), redaction
against `shared/redaction/cases.json`, prompts against `shared/prompts/`,
tuning (2), and the Gemini client (11: text and done with `responseId`, key
only in the header, image and resolution in the body, invalid key, SAFETY,
MAX_TOKENS, blocked prompt, stream without finish, error event, thought
parts hidden, Stop, refused host). They run against a local mock server
(Android) and a `URLProtocol` stub (iOS) with hand-written streams; the
recorded fixtures (step 4) replace those, and the phone runs below remain.

## Client acceptance tests on a phone (Gemini client, both platforms)

| Test | Android | iOS |
| --- | --- | --- |
| Key only in its header; never in the URL, logs or `detail` | | |
| Text streams in more than one delta | | |
| Image carries the model's detail or resolution setting | | |
| Disallowed host and any redirect refused before data is sent | | |
| Exactly one request per question, including on errors | | |
| Stop ends the stream and the request | | |
| Errors carry status, provider code and request ID where sent | | |
| Recorded streams give the expected events | Done 2026-11-17 on the JVM (`GeminiContractTest`); phone run pending | Done 2026-11-17 under `swift test` (`GeminiContractTests`); phone run pending |

## Firebase and platform checks

| Check | Result |
| --- | --- |
| Collection-off key names (Android manifest, iOS `Info.plist`) | |
| `getAppInstanceId` with collection off and on | |
| Crashlytics unsent reports before opt-in (Q10) | |
| Firestore memory cache leaves no report text on disk | |
| Server timestamp plus offset for `expireAt` (file 36) | |
| Analytics reserved event names (file 33) | |
| Merged-manifest permission list (file 17) | Done 2026-11-17: `INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `BIND_GET_INSTALL_REFERRER_SERVICE`, `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` |
| Atomic file writes on both platforms | |
| HEIC on API 24–27 and AVIF on API 24–30 give `image-error` (Q2) | |
| Harness output compared with the Android pipeline (file 40) | |

## Measurements

| Item | Result |
| --- | --- |
| Image-size accuracy (Q5) | |
| `HISTORY_TURNS_CAP` | |
| Token estimates per provider (`shared/token-estimates.json`) | |
| Anthropic automatic caching fields | `usage.cache_creation_input_tokens` and `cache_read_input_tokens` present (2026-11-17); behaviour across turns not measured yet |
| `CONNECT_TIMEOUT_MS`, `REQUEST_TIMEOUT_MS` from measured first-token and total times | |
| `REPORT_WRITE_TIMEOUT_MS` | |

## Historical note: Expo-era AI SDK check (2026-11-17)

Before the move to native apps, the Vercel AI SDK checks ran under Node
against Gemini `gemini-4-hydrogen`; all 8 passed after two fixes. Findings
that carry over to the native clients:

- Gemini sends no request-ID header; the body `responseId` is the only ID,
  and failed calls carry none.
- An invalid Gemini key returns 400 `INVALID_ARGUMENT` with "API key not
  valid".
- First text arrived after 2.5–5.7 seconds for a tiny image.
