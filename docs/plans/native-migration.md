# Plan: move PhotoCritic to native Kotlin and Swift

* **Author:** yogeshkolte
* **Date:** 2026-11-17
* **Status:** Approved 2026-11-17. The design was revised to match on 2026-11-17; where they differ, the design wins.
* **Replaces:** the Expo / React Native stack in
  [the design](../designs/photocritic.md) (Alternative A1 was rejected there;
  this plan reverses that).

## Decisions this plan is built on (author, 2026-11-17)

1. **Two separate apps.** Android in Kotlin with Jetpack Compose; iOS in
   Swift with SwiftUI. No shared code module.
2. **Thin provider wrappers.** One small hand-written client per provider
   per platform (OpenAI, Anthropic, Gemini × Android, iOS = six clients).
   This replaces the Vercel AI SDK (C1), which exists only for JavaScript.
   Provider SDKs were considered and rejected (see "Rejected provider
   layers").

What does not change: every goal, non-goal and CUJ in the design; the
security rules (keys stay on the device, host allowlist, no redirects, no
retries, keys only in headers, text-only reports, opt-in telemetry); the
Firebase projects `photocritic-dev` and `photocritic-prod`; the app ID
`com.yk.photocritic`; iOS 17 and Android API 24; the tuning values in §8a.

## Risks of this switch (read first)

1. **Everything is written twice.** Provider clients, streaming, error
   conversion, redaction, config validation, the session state machine,
   reports and every screen exist once in Kotlin and once in Swift. Two
   copies drift. Phases 1–4 take roughly 1.7 to 2 times the work of the
   React Native plan; this is an estimate, not a measurement. Mitigation:
   the shared test vectors in `shared/`, which both apps must pass.
2. **Provider API changes are ours to follow.** No SDK maintainer updates
   the clients. A provider can change event names, error bodies or image
   options. Mitigations: each client uses only one endpoint and a few event
   types; unknown events are ignored, not treated as errors; recorded
   responses in `shared/` are refreshed each release; the request ID in
   "Show details" makes a broken client quick to diagnose.
3. **Six clients, about 1,000–1,500 lines in total** (150–300 each), plus
   one stream parser per platform.
4. **Phase 0 work is thrown away.** The Expo scaffold, ESLint layering rules,
   Jest, CI, the AI SDK checks and the EAS setup are deleted. What carries
   over is listed under "Keep, move, delete".
5. **Skills.** The design rejected native apps because the work was in
   TypeScript and React Native. That reason no longer applies only if you
   are now comfortable in both Kotlin and Swift.

What gets easier:
- **Security rules hold by construction.** We own the HTTP client, so the
  allowlist, refused redirects, timeouts, no retries and header-only keys
  are our code, not SDK settings to verify after every upgrade.
- **No dependency on Gemini SDK support.** Google's client SDKs for Android
  and Swift were deprecated in favor of Firebase AI Logic, which bills the
  app owner's project and cannot use the user's key. With a wrapper, Gemini
  is one HTTP call like the others.
- **No provider SDKs or community libraries** to vet, pin or check for
  16 KB page alignment.
- **Q3 (sheet library) disappears.** Material 3 `ModalBottomSheet` and
  SwiftUI `presentationDetents` are built in.
- **The `Origin` header rule goes away.** Native HTTP clients do not add one.
- **Phase 5 (on-device models)** uses Apple Foundation Models and Android
  ML Kit GenAI directly, with no bridge.

## Provider wrappers

### Interface (same shape on both platforms)

```
interface ProviderClient
  stream(request: AskRequest, apiKey: String) -> stream of ProviderEvent
  // Kotlin: Flow<ProviderEvent>; cancelling the collector cancels the call.
  // Swift: AsyncThrowingStream<ProviderEvent, Error>; Task cancellation
  //        cancels the URLSession task.

AskRequest
  modelId: String
  history: [Turn]            // capped at HISTORY_TURNS_CAP, text only
  question: String           // at most MAX_QUESTION_CHARS
  image: JPEG bytes          // sent on the first turn only (design §3)
  imageDetail: low | high    // OpenAI detail / Gemini media resolution
  maxOutputTokens: Int

ProviderEvent
  TextDelta(text)
  Finished(reason: stop | length, requestId?)
  Failed(PcError)            // phase, httpStatus, providerCode, requestId, detail
```

`PcError` keeps the design §7 fields. Every failure shows the one generic
message (G12); finish reasons `content-filter` and provider `error` are
failures.

### Per-provider wire format

| | OpenAI | Anthropic | Gemini |
| --- | --- | --- | --- |
| Endpoint | `POST https://api.openai.com/v1/responses` | `POST https://api.anthropic.com/v1/messages` | `POST https://generativelanguage.googleapis.com/v1beta/models/{model}:streamGenerateContent?alt=sse` |
| Key header | `Authorization: Bearer <key>` | `x-api-key: <key>`, plus `anthropic-version: 2023-06-01` | `x-goog-api-key: <key>` |
| Image | `input_image` part, `data:image/jpeg;base64,…`, `detail` | `image` block, `source.type: base64`, `media_type: image/jpeg` | `inline_data` part, `mime_type: image/jpeg`; `mediaResolution` in `generationConfig` |
| Required extras | `stream: true`, **`store: false`** (otherwise OpenAI keeps the response for 30 days; `research/openai-your-data.txt`) | `stream: true`, `max_tokens` (required by the API) | none |
| Text events | `response.output_text.delta` | `content_block_delta` with `text_delta` | each `data:` object, `candidates[0].content.parts[].text` |
| End events | `response.completed`; `response.incomplete` (length or content filter); `response.failed`, `error` | `message_delta` (`stop_reason`), `message_stop`; `error` event mid-stream | `finishReason` on the last object (`STOP`, `MAX_TOKENS`, `SAFETY`, …); `promptFeedback.blockReason` |
| Request ID | `x-request-id` header | `request-id` header | No header; body `responseId` (checked 2026-11-17). Failed calls have none |

Phase 0 checks every event name and field above against live responses
before the clients are written; the table is from documentation and the
2026-11-17 Gemini run.

Anthropic requires `max_tokens`. The design already has a per-model
`maxOutputTokens` in Remote Config; it is sent to all three providers, so
answers have the same length limit. (An earlier version of this plan added a
`MAX_OUTPUT_TOKENS` tuning constant; it is not needed.)

### Transport (one per platform, in `platform/net`)

| Rule | Android (OkHttp) | iOS (URLSession) |
| --- | --- | --- |
| Host allowlist | Application interceptor rejects any host not in the in-code list | Check in the client before the task starts, and again in the redirect delegate |
| No redirects | `followRedirects(false)`, `followSslRedirects(false)`; a 3xx is a failure | Delegate `willPerformHTTPRedirection` returns `nil`; a 3xx is a failure |
| No retries | `retryOnConnectionFailure(false)`; no retry code anywhere | No retry code anywhere |
| Timeouts | `connectTimeout(CONNECT_TIMEOUT_MS)`, `callTimeout(REQUEST_TIMEOUT_MS)` | `timeoutIntervalForRequest`, `timeoutIntervalForResource` |
| No local traces | No cache, no cookie jar | Ephemeral configuration: no cache, no cookies, no credential storage |
| Keys | Header only; never logged; the redactor removes them from `detail` | Same |
| Stop | Cancelling the coroutine cancels the `Call` | Cancelling the task cancels the `URLSessionDataTask` |

Each platform has one server-sent-events line parser (`data:` and `event:`
lines, blank-line event end, `[DONE]`), tested with the recorded streams in
`shared/provider-streams/`. JSON uses kotlinx.serialization on Android and
`Codable` on iOS; unknown fields and unknown event types are ignored.

### Acceptance tests for each client (from the earlier SDK criteria)

| # | Test |
| --- | --- |
| W1 | Key sent only in its header; never in the URL, logs or `detail` |
| W2 | Text streams in more than one delta |
| W3 | Image sent with the right detail or resolution setting (request body check) |
| W4 | Disallowed host and any redirect are refused before data is sent |
| W5 | Exactly one request per question, including on errors |
| W6 | Stop ends the stream and the network request |
| W7 | Errors give status, provider code and request ID where the provider sends one |
| W8 | Each recorded stream in `shared/` gives the expected events (success, length limit, content filter, mid-stream error) |
| W9 | OpenAI requests contain `store: false` |

W8 runs in CI on both platforms against recordings. W1–W7 and W9 run in CI
against a local mock server (OkHttp `MockWebServer`; a `URLProtocol` stub on
iOS), and once per release against the live APIs with a spend-capped key.

### Rejected provider layers

- **Vercel AI SDK:** JavaScript only.
- **Provider SDKs** (official Java SDKs for OpenAI and Anthropic; community
  Swift libraries; Google Gen AI SDK). Rejected by author, 2026-11-17: the
  security rules would depend on each SDK accepting our HTTP client and
  allowing retries off; Gemini has no SDK that takes the user's key on
  mobile (Firebase AI Logic bills the app owner); iOS would rely on
  community libraries for code that handles the key; and two SDKs per
  provider behave differently between the apps.

## Target stack

| Area | Android | iOS |
| --- | --- | --- |
| Language, UI | Kotlin 2.x, Jetpack Compose, Material 3 | Swift 6, SwiftUI |
| Minimum OS | API 24 (Compose supports 21) | iOS 17 |
| State | `ViewModel` + `StateFlow`, coroutines | `@Observable` (Observation), Swift concurrency |
| Navigation | Navigation Compose | `NavigationStack` |
| HTTP, JSON | OkHttp, kotlinx.serialization | URLSession, `Codable` |
| API key storage | Android Keystore AES key encrypting a file in app storage (EncryptedSharedPreferences is deprecated) | Keychain, `kSecAttrAccessibleWhenUnlockedThisDeviceOnly` |
| Photo choice | Photo Picker (`PickVisualMedia`; Google Play services backport below API 30); camera via `TakePicture` | `PhotosPicker`; camera via `UIImagePickerController` |
| Image resize | `ImageDecoder` (API 28+), `BitmapFactory` below; JPEG 0.8, long edge 2048 | ImageIO thumbnail downsampling; JPEG 0.8, long edge 2048 |
| Answer list | `LazyColumn` | `List` / `LazyVStack` |
| Sheet | Material 3 `ModalBottomSheet` | `.sheet` with `presentationDetents` |
| Firebase | Firebase Android BoM: Remote Config, Analytics, Crashlytics, Firestore | Firebase Apple SDK through Swift Package Manager, same four |
| Build | Gradle (Kotlin DSL), version catalog | Xcode project generated by XcodeGen from `project.yml` (a reviewable text file instead of `.pbxproj`) |
| Lint, format | ktlint, detekt; layering: only `platform/net` creates HTTP clients, only `features/providers` calls them, only `platform/telemetry` imports Analytics or Crashlytics | SwiftLint with the same layering as custom rules |
| Tests | JUnit 5, Turbine for flows, OkHttp `MockWebServer`, Compose UI tests, Robolectric where needed | Swift Testing, `URLProtocol` stubs, XCUITest |
| Distribution | Play internal track, signed with a new upload key (Play App Signing) | TestFlight (needs the Apple Developer account) |

Firebase collection stays off until opt-in. The `firebase.json`
`react-native` keys become Android manifest `<meta-data>` and iOS
`Info.plist` keys, for example `firebase_analytics_collection_enabled` /
`FIREBASE_ANALYTICS_COLLECTION_ENABLED` and
`firebase_crashlytics_collection_enabled` /
`FirebaseCrashlyticsCollectionEnabled`. Phase 0 checks every key name
against the current Firebase SDKs, as was done for React Native Firebase.

## Repository layout

```
android/                 Gradle project (app module; packages by feature as in design §2)
  app/src/dev/google-services.json     from firebase/dev
  app/src/prod/google-services.json    from firebase/prod
ios/
  project.yml            XcodeGen spec
  PhotoCritic/           SwiftUI app (same feature folders)
  Config/Dev/GoogleService-Info.plist, Config/Prod/GoogleService-Info.plist
shared/                  Test data both apps must pass (no code)
  provider-streams/      Recorded streams per provider: success, length limit, content filter, mid-stream error
  provider-errors/       Recorded error bodies and headers (invalid key, rate limit, bad model, overloaded)
  provider-requests/     Expected request bodies per provider (W3, W9)
  remote-config/         JSON Schema for the Remote Config template + valid and invalid samples
  redaction/             Input strings and their expected redacted output
  tuning.json            §8a values; a test in each app checks its constants match
firebase/                firestore.rules, indexes, rules tests (small Node package, emulator only)
docs/
```

Recordings contain no keys: a script records with a real key and replaces
key-bearing headers and request IDs before saving.

Android build flavors `dev` and `prod` pick the Firebase project; iOS uses
two build configurations (Debug-Dev, Release-Prod) that copy the matching
plist. This replaces `APP_ENV`.

## Keep, move, delete

| Item | Action |
| --- | --- |
| `docs/` (design, VERSIONS, spikes), review reports, `research/` | Keep; design and VERSIONS updated (below) |
| `firebase/dev`, `firebase/prod` service files | Move into `android/app/src/{dev,prod}/` and `ios/Config/{Dev,Prod}/` |
| `firestore.rules`, `firestore.indexes.json`, `.firebaserc` | Move under `firebase/` |
| `firebase.json` | Keep the Firestore and emulator parts; drop the `react-native` section (moves to manifest and plist) |
| `assets/` icons and splash | Keep as sources; regenerate as Android adaptive icons and an iOS asset catalog |
| `src/platform/config/tuning.ts` values | Become `shared/tuning.json` plus Kotlin and Swift constants |
| `package.json`, `package-lock.json`, `node_modules/`, `app.config.ts`, `eas.json`, `tsconfig.json`, `eslint.config.js`, `jest.config.js`, `test/`, `src/`, `spikes/`, `expo-env.d.ts` | Delete |
| `.github/workflows/ci.yml` | Rewrite (below) |
| `.gitignore` | Replace Expo rules with Gradle, Android Studio and Xcode rules |
| EAS project `@yogeshkolte/photocritic` and the Expo-stored Android keystore | No longer used. Leave it or delete it on expo.dev (D1); the cloud spike build can be ignored |

The old stack stays in git history (commit `a1f4c49`).

## CI

GitHub Actions (macOS minutes are free on this public repo):

- **android** (ubuntu): JDK 17, Gradle cache, `./gradlew ktlintCheck detekt
  testDevDebugUnitTest assembleDevDebug`.
- **ios** (macos): `xcodegen`, `swiftlint`, `xcodebuild test` on an iOS 17
  simulator.
- **rules** (later, Phase 4): Firestore emulator rules tests.

CI makes no live provider calls, except the daily canary from Phase 3,
which uses spend-capped keys stored as GitHub Actions secrets. Builds for
testers are made locally (Android Studio, Xcode) or with fastlane later; no
EAS.

## Local toolchain

| Tool | Status on this Mac |
| --- | --- |
| Xcode | Installed: 27.2 |
| Android Studio (bundles a JDK and installs the Android SDK) | Not installed |
| XcodeGen, SwiftLint | Not installed (`brew install xcodegen swiftlint`) |
| Apple Developer account | Not yet; needed for device builds and TestFlight (simulator works without it) |

## Phases (replaces "Implementation order" in the design)

Each phase ends with both apps passing CI. Within a phase, Android goes
first, then iOS, so one platform's lessons reach the other.

### Phase 0, setup and spikes (redo)

1. Tear down the Expo stack (Keep, move, delete table); one commit.
2. Scaffold `android/` (Compose app, flavors, Firebase, collection-off
   meta-data, lint, one unit test) and `ios/` (XcodeGen project, Firebase
   through Swift Package Manager, collection-off plist keys, SwiftLint, one
   test).
3. CI for both.
4. `shared/` with `tuning.json` and its matching tests in both apps.
5. **Recordings:** record streams and errors for all three providers with
   spend-capped keys, strip keys and IDs, save to `shared/`. Confirm every
   event name and field in the wire-format table.
6. **Wrapper spike:** transport plus Gemini client on both platforms, run
   against W1–W9 on a phone; this replaces the AI SDK setup checks (Q1).
7. Firebase behavior checks (as listed today), now on the native SDKs.
8. Q5 image-size accuracy and history cap; set the PROVISIONAL tuning
   values.
9. Android API 24–27: HEIC and AVIF show the generic error.

Removed from Phase 0: Q3 sheet library, Expo version pinning, EAS and the
dev client.

### Phase 1, foundations (both apps)

Key storage, install marker, Remote Config with snapshot and validation
against the `shared/remote-config` schema, kill switch at cold start, the
restart screen, telemetry wrapper with opt-in, image pipeline.

### Phase 2, Gemini end to end (both apps)

Provider interface, the Gemini client from the Phase 0 spike made
production-ready, error conversion to the one generic message, consent,
setup, home screen, Stop, text cards.

### Phase 3, Anthropic then OpenAI (both apps)

Same interface and the same W1–W9 tests.

### Phase 4, polish and tester release

Reports (Firestore, persistence off), Settings, wide layout (Android window
size classes; iPad Split View), accessibility (TalkBack, VoiceOver, Dynamic
Type), privacy documents, store forms, Play internal track and TestFlight.

### Phase 5, on-device evaluation

Apple Foundation Models and ML Kit GenAI behind the same provider
interface. Not detailed here.

## Design document changes (after you approve this plan)

- **Status and Overview:** native apps, two codebases.
- **Alternatives:** A1 becomes the chosen option; record React Native / Expo
  as rejected (author, 2026-11-17). Provider layer: thin wrappers chosen;
  Vercel AI SDK and provider SDKs rejected with the reasons above.
- **Q1:** becomes "wrapper wire formats confirmed against live responses".
  **Q3:** closed (built-in sheets). **AR1:** rewritten: provider API changes
  are followed by hand.
- **Technical Plan §2** (code layout per platform), **§3** (provider
  interface and wire formats), **§4** (OkHttp and URLSession transport;
  Origin rule removed; OpenAI `store: false`), **§6** (native image APIs),
  **§7** (`PcError` from wrapper events),
  **§9** (Keystore and Keychain), **§11** (native Firebase), **§12**
  (Compose and SwiftUI), **§13** (libraries).
- **Privacy (G14):** OpenAI requests use `store: false`; the privacy policy
  states the 30-day abuse-monitoring retention that still applies.
- **Detailed Implementation Plan:** file list rewritten for `android/` and
  `ios/`; Verification Plan rewritten for both.
- **docs/VERSIONS.md:** rewritten for Gradle and Swift Package Manager pins.

## Open decisions for you

| # | Decision | When |
| --- | --- | --- |
| D1 | Delete the Expo project on expo.dev, or leave it | Any time |
| D2 | Run the design review again on the rewritten design, or go straight to Phase 0 | After the design is updated |

## Next steps after approval

1. Update the design document (list above).
2. Install Android Studio, XcodeGen and SwiftLint (your approval needed for
   installs).
3. Phase 0 step 1: tear down the Expo stack in one commit.
