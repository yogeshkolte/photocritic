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
| iOS scaffold (files 41–44): builds, lint and one test pass | Partly, 2026-11-17: `swift test` (2) and `swiftlint --strict` pass; the app builds for the simulator with Firebase 13.1.1 and the dev plist. App and UI tests not run: no simulator runtime installed |
| CI for both apps (file 3) | Written 2026-11-17; not yet run on GitHub |
| `shared/` README, fixture schema, prompts, Remote Config schema and template; `tools/` validate and record scripts | Done except `record-fixture.ts` (step 4): `config:validate` passes the template and rejects all 6 invalid samples |
| Expo project on expo.dev deleted (author, D1) | Pending: needs the author's password on expo.dev |

## Wire formats (§3a, Q1)

Recordings go to `shared/provider-*` (file 13). Record the date and model.

| Check | Gemini | Anthropic | OpenAI |
| --- | --- | --- | --- |
| Endpoint, key header and image fields as in §3a | | | |
| Text, end and failure event names as in §3a | | | |
| Request ID location | Body `responseId`; no header; failed calls carry none (2026-11-17, Node run below) | | |
| Refusal and content-filter shapes | | | |
| OpenAI `store: false` accepted | n/a | n/a | |

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
| Recorded streams give the expected events | | |

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
| Anthropic automatic caching fields | |
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
