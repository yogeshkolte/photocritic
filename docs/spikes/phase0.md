# Phase 0 results

Status of each Phase 0 item for the native apps (design: Detailed
Implementation Plan, Implementation order and file 50). **Result** is filled
in with evidence (device, build, date).

Author decision 2026-11-17: Phase 1 finishes the Android app first; iOS
follows in Phase 1.a. The iOS column and iOS-only checks below are done in
Phase 1.a, not Phase 0.

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
| Expo project on expo.dev deleted (author, D1) | Done 2026-11-17 by the author |

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
| Key only in its header; never in the URL, logs or `detail` | Done (Samsung SM-E176B, API 37, 2026-11-18): the mock server sees the key only in `x-goog-api-key`; no event holds it; 0 logcat lines hold the real key after the run | |
| Text streams in more than one delta | Done: 9 deltas, 900 characters, `STOP`, request ID from `responseId`. First text after 7.4 s (thinking), total 7.9 s. Needed `maxOutputTokens` 8192, see below | |
| Image carries the model's detail or resolution setting | Done: body has `inlineData` and `mediaResolution: MEDIA_RESOLUTION_HIGH` | |
| Disallowed host and any redirect refused before data is sent | Done: disallowed host gives `host-not-allowed` with 0 requests; a 302 gives an error with status 302 and the redirect target gets 0 requests | |
| Exactly one request per question, including on errors | Done: one request each for 500, 503, 429, 400 and a stream cut mid-answer | |
| Stop ends the stream and the request | Done (live): the collector ends 7 ms after Stop, with no event after it | |
| Errors carry status, provider code and request ID where sent | Done (live): invalid key gives 401 `UNAUTHENTICATED`, unknown model 404 `NOT_FOUND`; Gemini sends no request ID on errors | |
| Recorded streams give the expected events | Done 2026-11-17 on the JVM (`GeminiContractTest`) and 2026-11-18 on the phone (5 recordings) | Done 2026-11-17 under `swift test` (`GeminiContractTests`); phone run pending |

Run with `GeminiPhoneTest` (`app/src/androidTest/.../providers/`). The run
script writes the key into the app's private files through stdin, because
`adbd` logs the command line of `am instrument` arguments.

**Thinking uses the answer budget (found 2026-11-18).** Gemini counts
thinking tokens against `maxOutputTokens`. With the app's prompts and the
template's 2048, a "describe this image" question used 1,421 to 1,923
thinking tokens; one run in three on the Mac was cut off (`MAX_TOKENS`),
and on the phone three runs ended `LENGTH` with no text at all. The
extract prompt used 223. `thinkingConfig.thinkingLevel: "low"` brought a
describe question to 610 thinking tokens with a full answer; at a limit of
8192, `low` used about 820 and the default about 1,600. Gemini 4 Argon
reported no thinking tokens at any level and accepted the setting.

Decided (author, 2026-11-18): both. Each model has a `thinkingLevel` in
Remote Config (Hydrogen `low`, Argon empty for the default), and
`maxOutputTokens` is 8192 in the template. The phone run above used 8192
with the default level; `GeminiPhoneTest` now uses the template values.
Rerun on the phone with them (8192, `low`), twice: all 9 pass; 8 deltas
each, `STOP`, first text after 4.4 and 4.8 s (7.4 s before), Stop 7 and
16 ms, no logcat line holds the key.

## Firebase and platform checks

| Check | Result |
| --- | --- |
| Collection-off key names (Android manifest, iOS `Info.plist`) | Android: `firebase_analytics_collection_enabled` and `firebase_crashlytics_collection_enabled` read back as false; Crashlytics reports collection off (emulators API 24, 30 and 36 (Google APIs), 2026-11-17, `app/src/androidTest/.../phase0/`). iOS in Phase 1.a |
| `getAppInstanceId` with collection off and on | Android: returns the same ID with collection off, on, and off again, on all three API levels. So `Telemetry.instanceIdOrNull()` has an ID whether or not sharing is on; whether a report carries it while sharing is off is a design choice (file 30), not an SDK limit |
| Crashlytics unsent reports before opt-in (Q10) | Android: a non-fatal recorded with collection off is held on the device (`checkForUnsentReports` true in the next process, files under `files/.crashlytics.v3/`) and removed by `deleteUnsentReports`. The event write is asynchronous: a process that ends within about a second can lose it |
| Firestore memory cache leaves no report text on disk | Android: with `MemoryCacheSettings` and the network off, the pending write's text is not found anywhere under the app's data directory; with `PersistentCacheSettings` (control) it is found. Same on API 24, 30, 36 |
| Server timestamp plus offset for `expireAt` (file 36) | Not possible: Firestore Android 27.2.0 `FieldValue` has `serverTimestamp()` but no offset (`increment` is numbers only). File 36's fallback applies: no `expireAt`; `reports:purge` deletes by `createdAt` plus `REPORT_RETENTION_DAYS` |
| Analytics reserved event names (file 33) | All seven events start with `pc_`: none is a reserved name or uses a reserved prefix (`firebase_`, `google_`, `ga_`); all are under 40 characters. A test against `shared/telemetry/events.json` follows in Phase 1.1 |
| Merged-manifest permission list (file 17) | Done 2026-11-17: `INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `BIND_GET_INSTALL_REFERRER_SERVICE`, `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` |
| Atomic file writes on both platforms | Android: `AtomicFile` keeps the previous file after a failed write and after a write interrupted before `finishWrite`, on API 24, 30 and 36. iOS in Phase 1.a |
| HEIC on API 24–27 and AVIF on API 24–30 give `image-error` (Q2) | Android, with `shared/images/test.heic` and `test.avif`: API 24 decodes neither (BitmapFactory); API 30 decodes HEIC but not AVIF (both decoders); API 36 decodes both. API 26–27 not run; they use the same BitmapFactory path as 24 |
| Harness output compared with the Android pipeline (file 40) | Done 2026-11-18. Five photos (the four `harness/datasets/synthetic` images made by `harness synth`, and `shared/images/test.jpg`) at 1024 and 2048 px: `harness prepare` against `MeasureTest#pipelineCopies` on the phone, compared with `harness compare`. Same width and height for all ten. Mean absolute pixel difference 0.54–0.84 (of 255) at 1024 and 0.20–0.28 at 2048, PSNR 34–46 dB; `test.jpg`, which is not resized, decodes identically. The harness's JPEGs are 27–54% larger than the phone's for the same pixels, so harness accuracy results carry over to the app but its byte counts do not. A live `harness run` on the set gave the same floor as the phone: 15 px text 95% error at 1536 px and 3.6% at 2048 |
| Remote Config published to dev | Done 2026-11-18 with `npm run config:publish -- --project dev` (Remote Config version 1, `configVersion` 1, six parameters read back). A fresh install on the phone now passes config loading and opens setup |

## Measurements

| Item | Result |
| --- | --- |
| Image-size accuracy (Q5) | Samsung SM-E176B, API 37, Wi-Fi, 2026-11-18, `phase0/MeasureTest`, Gemini 4 Hydrogen with the template settings. Four synthetic 4032×3024 photos, each with six lines of random codes at one text height (15, 22, 32, 48 px), prepared by `ImagePipeline` and read with the extract prompt; character error rate against the known text. Error by long edge, `HIGH`: 15 px text 96% / 89% / 6.5% / **0.6%** at 768 / 1024 / 1536 / 2048; 22 px 88% / 63% / 0% / 0%; 32 px 96% / 0.6% / 0% / 0%; 48 px 0.6% / 0% / 0% / 0%. At 2048, `MEDIUM` gave 10% and `LOW` 54% on 15 px text, 0% on the rest. Send copies were 21–132 KB. **Decision: keep `DEFAULT_LONG_EDGE_PX` 2048 and `MEDIA_RESOLUTION_HIGH`.** Gemini's image tokens do not depend on pixel size (see token estimates), so 2048 costs no more tokens than 768; the floor is text about 0.4% of the photo's height. Synthetic images, not real photos |
| `HISTORY_TURNS_CAP` | Samsung SM-E176B, API 37, Wi-Fi, 2026-11-18, `phase0/MeasureTest`, Gemini 4 Hydrogen with the template settings. 0, 5, 10 and 20 earlier turns (about 620 characters each): first text after 3.3, 6.0, 2.7 and 3.5 s; length does not change the time. 10 turns are about 1,800 Gemini tokens next to the 1,120 of the image, far below the 1M context. **Keep 10**: it bounds the cost of each question, not the context |
| Token estimates per provider (`shared/token-estimates.json`) | Done 2026-11-18 with each provider's token counter, written to `shared/token-estimates.json`. Characters per token (lowest measured, rounded down): Gemini 3.4, Anthropic 2.9, OpenAI 4.0. Images: Gemini a fixed 280 / 560 / 1,120 tokens for `LOW` / `MEDIUM` / `HIGH` at any size; Anthropic width × height ÷ 750 (within 5%); OpenAI `gpt-5.4-mini` about 1.2 tokens per 32×32 patch, `high` capped near 2,942, not the tile rule the design expected (provisional until the Phase 2 OpenAI client) |
| Anthropic automatic caching fields | `usage.cache_creation_input_tokens` and `cache_read_input_tokens` present (2026-11-17). Across turns (2026-11-18, `claude-haiku-5-5`, photo plus the free prompt, then a second turn sent twice): both fields stay 0 every time, so nothing is cached without `cache_control`; whether to add it is a Phase 2 decision with the Anthropic client. The same run showed Haiku counts thinking against `max_tokens` too (all 300 on the second turn), so `thinkingLevel` needs an Anthropic mapping in Phase 2 |
| `CONNECT_TIMEOUT_MS`, `REQUEST_TIMEOUT_MS` from measured first-token and total times | Samsung SM-E176B, API 37, Wi-Fi, 2026-11-18, `phase0/MeasureTest`, Gemini 4 Hydrogen with the template settings. New connection to the response head (DNS, TCP, TLS, first byte), 6 runs: median 184 ms, max 338 ms. Extract, 6 runs at 2048 px: first text median 3.4 s, max 4.1 s; total max 4.5 s. Describe, 6 runs: first text median 4.8 s, max 7.0 s; total max 8.0 s; the slowest seen anywhere was 11.9 s (default thinking). **Keep 15,000 and 120,000**: connect has 40× headroom for mobile networks, and the request limit leaves room for an 8,192-token answer. Wi-Fi only; a mobile-data run is still worth doing |
| `REPORT_WRITE_TIMEOUT_MS` | Samsung SM-E176B, API 37, Wi-Fi, 2026-11-18, `phase0/MeasureTest#reportWriteRoundTrip`: six writes of a 20,000-character document (`MAX_REPORT_CONVERSATION_CHARS`) with the memory cache, until the server confirmed: 1,670 ms for the first (new connection), then 305–483 ms. The six documents were deleted afterwards. **Keep 10,000** (6× the slowest). Before the database existed, the write's stream closed with `PERMISSION_DENIED` and the SDK retried for as long as the app ran without failing the write, so the app's own timeout is required, not optional |

## Historical note: Expo-era AI SDK check (2026-11-17)

Before the move to native apps, the Vercel AI SDK checks ran under Node
against Gemini `gemini-4-hydrogen`; all 8 passed after two fixes. Findings
that carry over to the native clients:

- Gemini sends no request-ID header; the body `responseId` is the only ID,
  and failed calls carry none.
- An invalid Gemini key returns 400 `INVALID_ARGUMENT` with "API key not
  valid".
- First text arrived after 2.5–5.7 seconds for a tiny image.
