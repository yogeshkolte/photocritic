# PhotoCritic: Ask Questions About a Photo

- **Author:** yogeshkolte (yogeshkolte@gmail.com)
- **Status:** Proposed (revised for native apps, 2026-11-17)
- **Date:** 2026-11-15; revised 2026-11-17
- **Source:** [concepts_brief.md](../../concepts_brief.md) (decisions 1–37), [clickable mockup](https://claude.ai/artifact/QMFTwpETHLVAHHHyySA9e7) (private; copy in [`mockup/`](../../mockup/))

---

## Overview

PhotoCritic is a free iOS and Android app. The user takes or picks one photo
and asks questions about it: what objects are in it, what the scene means, or
what text it contains, with that text ready to copy. The app sends the photo
and the question to a cloud AI model the user chooses (OpenAI, Anthropic or
Google Gemini), using the user's own API key. A later phase adds on-device
models and compares them with the cloud models.

The app is a technology demonstrator (decision 7). It does not try to compete
with the ChatGPT, Gemini or Claude apps, Google Lens or Apple Visual
Intelligence. Its purpose is to measure, on current phones, how well vision
models read and explain photos, how long the first answer takes, and how the
app handles every kind of failure. The first builds go only to TestFlight (iOS)
and the Google Play internal testing track (Android) (decisions 15–16). Apple
expects TestFlight apps to be intended for public release, so the tester build
must work as a complete app.

The app is built as two native apps: Kotlin with Jetpack Compose on
Android and Swift with SwiftUI on iOS, with no shared code module (author
decision, 2026-11-17, replacing the earlier Expo and React Native plan). The
two apps share test data, the Remote Config schema, the tuning values and the
prompts, not code. The app has no backend of its own. Firebase supplies
remote configuration, opt-in usage and crash data, and a store for text-only
answer reports.

## The Problem

**What a user needs.** A person looking at a photo wants quick answers:
"What is this?", "What does this sign say?", "Copy the text on this receipt".
General chat apps can do this, but they mix photo questions into long chat
histories, hide which model answered, and do not let the user compare
providers with their own keys.

**What the author needs.** The author wants hard numbers on how cloud vision
models perform on a phone before investing in on-device models:

- how accurate text extraction is at a given image size;
- how long the user waits for the first words of an answer;
- whether every provider error reaches the user as a plain sentence with a
  clear next step;
- later, how an on-device model compares with the cloud on the same photos.

**What makes this hard.**

- **Three providers, three different APIs.** OpenAI, Anthropic and Gemini
  differ in request format, image limits, error codes and rate-limit
  behavior. The app must hide these differences behind one interface, and
  that interface must also accept on-device models later without screen
  changes.
- **Photos are large and private.** Phone photos are often 12 MP or more,
  HEIC, and carry GPS location. The app must shrink each photo to what the
  model needs, remove location and camera data, and still show the user the
  full-quality original.
- **Keys belong to the user.** API keys must never leave the device except
  in requests to the provider. They must not go to Firebase.
- **Store rules.** Apple requires consent naming the AI provider before
  personal data is sent to it (guideline 5.1.2(i)). Google Play requires an
  in-app way to report AI output. Gemini's terms set an 18+ minimum age.
- **The photo must stay visible.** On a phone, the conversation shares the
  screen with the photo, so the design has to keep the photo visible while
  answers stay readable.

**Proposed solution in one paragraph.** A single-photo conversation screen:
the photo fills the screen and a pull-up conversation sheet sits over it.
Answers stream in and can be stopped. Text found in the photo appears in its
own card with Copy and Share. Each request goes through PhotoCritic's own
provider interface, implemented as a thin hand-written client per provider
in each app, directly from the phone to the provider. Remote Config controls models, limits, error text and
consent text without a store release. Keys stay in the device's secure store.

## Goals

### Photo and conversation

- **G1. One photo per conversation.** The user takes a photo with the camera
  or picks one through the system photo picker (no broad photo-library
  permission). Choosing a new photo, or changing provider or model, asks for
  confirmation if anything has been asked, then clears the conversation
  (decision 30).
- **G2. Starter questions and free questions.** Buttons for Identify,
  Explain and Copy text, plus a multi-line text box for any question.
- **G3. Extracted text as a card.** When the model returns text found in the
  photo, it appears in a separate card that keeps line breaks, with Copy and
  Share buttons (decision 29). If the model does not mark the text, the full
  answer is shown as a normal reply with a Copy button.
- **G4. Streaming and Stop.** Answers appear as they arrive. A Stop button
  replaces Send while an answer is arriving. A stopped answer keeps its text,
  is labelled "Stopped", can be copied, and is not sent back to the model
  with later questions (decision 36, D34).
- **G5. Plain-text answers.** Normal replies are plain text in v1; copying
  always gives plain text (D41).

### Image handling

- **G6. Full-quality display, small send copy.** The screen shows the
  original photo. Only the copy sent to the model is reduced (decision 8).
- **G7. Send copy rules.** Every input format (HEIC, AVIF and animated GIF
  included) is converted on the device to JPEG, where the OS can decode it
  (on Android, HEIC needs API 28 and AVIF API 31; see Q2). EXIF orientation is applied
  to the pixels, then all metadata is removed. The long edge is reduced to
  about 2048 px, with a per-model detail or resolution setting from Remote
  Config. The send copy is re-sent with every question (decision 11), with a
  cap on how much earlier conversation is sent.
- **G8. Ready for image editing.** The photo is stored as three parts: the
  untouched original, a list of edits stored as data (empty in v1), and the
  rendered send copy. Editing, when added, starts a new conversation
  (decision 34).

### Providers, keys and models

- **G9. Three providers, user's own key.** OpenAI, Anthropic and Gemini.
  The user always chooses both provider and model, at first launch and in
  Settings, from a curated list of vision models in Remote Config. "Fast" and
  "Best" are labels next to model names only (decision 36, final).
- **G10. Keys stay on the device.** Keys are kept in the iOS Keychain and,
  on Android, in a file encrypted with an Android Keystore key; readable
  only while the device is unlocked (iOS) and never synced or backed up.
  They are deleted on the first launch after a reinstall. Each key is checked
  when entered (decision 21). The setup screen explains, before key entry,
  that usage is billed to the user's provider account and that the key must
  be re-entered after reinstalling.
- **G11. One provider interface for cloud now and on-device later.** The
  screens talk only to PhotoCritic's own interface. Each cloud provider is a
  thin hand-written client in each app (six clients), with no provider SDK
  and no third-party AI library (author decision, 2026-11-17; this replaces
  the Vercel AI SDK, which exists only for JavaScript). The interface reports
  what each model can do (for example, whether it accepts images) so an
  on-device provider can be added later without screen changes (decision 35).

### Errors

- **G12. One generic error message, no automatic retries.** Every failure
  (provider, network, image format, key check) shows the same message:
  "Something went wrong. Please try again." There are no automatic retries;
  the user sends again if they want to. "Show details" shows redacted
  details: where the failure happened, the HTTP status, the provider's error
  code and request ID, never the key (decision 9; author decision after
  review, 2026-11-15).

### Consent, privacy and reporting

- **G13. Consent before the first send to each provider.** A sheet names the
  provider and says what is sent and how the provider keeps it. It appears at
  the first Send for each provider and again when the consent text changes.
  The record stores provider, text version and time (decisions 17, 36 D33).
- **G14. Privacy policy and store forms.** A privacy policy in the app and in
  the store fields that require it, covering each provider's retention and
  training terms and every piece of data Firebase collects. OpenAI requests
  are sent with `store: false`, so OpenAI keeps no application state for
  them (§3a). 18+ age rating.
- **G15. Report an answer.** Each answer has a Report action. The report is
  text only (the user's note, the question, the answer and the model name; no
  photo) and is written to Cloud Firestore with create-only rules; there is
  no App Check (author decision after review, 2026-11-15). Reports need a
  connection: nothing is stored on the device to send later. The user sees a
  report ID and whether it was sent (decisions 22, 25).
- **G16. Usage and crash data only with permission.** Firebase Analytics and
  Crashlytics are off by default for everyone and turn on only through
  "Share usage and crash data". Events never contain photos, questions,
  answers or keys. When on, the Analytics app instance ID identifies the
  install (decisions 20, 24, 26).

### Remote configuration

- **G17. Behavior changes without a store release.** Firebase Remote Config,
  fetched at every cold start, supplies the kill switch, the model list and
  per-model settings, consent text and the privacy-policy version. The app
  checks every fetched value. There are no built-in fallback values: if the
  configuration cannot be fetched or any value is invalid, the app shows a
  screen asking the user to check the connection and restart the app (author
  decision during drafting, Q9). Tuning values (send-image size and quality,
  history cap, question length, timeouts, report limits) are constants in
  code, not in Remote Config (author decision after review, 2026-11-15).
  Provider host names and the key header format are also fixed in code
  (decisions 13, 19, 23).
- **G18. Kill switch, applied at app start.** The kill switch is read once,
  at cold start. Changes published while the app is running take effect the
  next time the app starts. Because no conversation is stored, no
  conversation is open at that point. If the saved provider or model is
  disabled, the app shows the kill-switch message and opens setup so the
  user picks another model; it does not pick one for the user (decision 36,
  D35 and D39; author decision after review, 2026-11-15).

### Screens and look

- **G19. Phone layout.** The photo fills the screen. The top bar holds only
  Settings and New photo; no model name is shown on the main screen
  (decision 37). The conversation sheet has two heights: collapsed (sized to
  its content) and expanded. Any text over the photo sits on at least a 65%
  dark tint, so it stays readable over bright photos (decision 36, D27–D28).
- **G20. Tablets and landscape.** In windows wider than about 700 points, the
  layout switches to a fixed two-pane split: photo on the left, conversation
  on the right. The switch follows window width, not device type, so iPad
  Split View works (decisions 33, 36 D29). English only.
- **G21. Darkroom v1 look.** Dark theme only, system fonts, one amber accent
  for the main action, Lucide icons, and a custom app icon (decision 36, D32).
- **G22. Accessibility.** Screen-reader labels on every control, and text
  that grows with the system text size setting.

### Measurement

- **G23. Benchmark harness.** A test harness, separate from the app, measures
  text-extraction accuracy per image size, time to first answer and error
  coverage, and records the exact model and settings of each run. This is the
  main measure of the demonstrator. Opt-in Analytics events from testers are
  supporting data only.

### Distribution

- **G24. Tester release.** TestFlight (internal testers on every build,
  external testers after Beta App Review) and the Google Play internal
  testing track.

## Non-Goals

- **User accounts, sign-in, and any stored conversation history.** Nothing
  about past conversations is stored on the device or anywhere else in v1.
  The conversation log is withdrawn; the author will add it later
  (decision 36, D30).
- **Cost display.** No price or cost estimate is shown.
- **A backend of PhotoCritic's own.** Firebase is used only for remote
  configuration, opt-in Analytics and Crashlytics, and Firestore reports.
  No Cloud Storage and no Cloud Functions.
- **Photos in reports** (decision 25), and notifications when a report
  arrives (the console is checked by hand).
- **Keeping keys, settings or consent records across reinstall**
  (decisions 17, 21).
- **On-device model inference in v1.** It is a later phase, starting with
  on-device models that accept images (D37), after the first external tester
  build ships or a harness baseline is recorded (D40).
- **Image editing in v1** (only the data structure is prepared, G8).
- **Offline use.** The app shows a no-connection state instead.
- **DeepSeek, or any provider whose API does not accept images.**
- **A free tier or a pointer to one** (decision 10).
- **Canned demo answers for store reviewers.**
- **Public App Store listing and Play production release** in v1. A later
  public release is intended.
- **Markdown rendering, light theme, and languages other than English.**
  Strings are kept in a form that can be translated later.
- **Provider caching APIs and file-upload APIs.**
- **Choosing models by tier** (decision 36, final).
- **Low-data mode.** One send size per model in v1 (decided during drafting).
- **Over-the-air updates.** Every fix ships as a new tester build (decided
  during drafting).
- **Shared code between the two apps** (Kotlin Multiplatform or similar).
  Only data is shared (author decision, 2026-11-17).
- **Older phones and OS versions** below the minimums set in D9.

## Open Questions

These are carried from the brief without decisions. The author deferred
several of them to development (decision 27). They stay open until decided.

- **Q1. Resolved: provider layer (D3).** Thin hand-written clients, one per
  provider in each app (author decision, 2026-11-17). Phase 0 confirms the
  wire formats in §3a against live responses (endpoints, event names, end
  and error events, request-ID location, image fields) and records them in
  `shared/` before the clients are written. The Gemini request-ID location
  (body `responseId`, no header) was confirmed on 2026-11-17.
- **Q2. Minimum OS and platform setup (D9).** iOS 17. Android minSdk 24
  (Android 7.0), decided by the author after review (2026-11-15); Jetpack
  Compose would allow 21, but 24 is kept. Android cannot decode HEIC below
  API 28 or AVIF below API 31, so on those phones such photos show the
  generic error message (G12). Library versions are pinned at Phase 0
  (D26).
- **Q3. Resolved: sheet library (D26).** No library: Material 3
  `BottomSheetScaffold` on Android and SwiftUI `presentationDetents` on iOS
  (§12), since the move to native apps (2026-11-17).
- **Q4. Resolved: session lifecycle (D8).** Decided at the implementation
  review (2026-11-17): a streaming answer continues in the background until
  the OS stops the connection, then ends as Failed; rotation keeps the
  conversation; after process death the app starts at `no-photo`; the
  Android camera result survives activity recreation (§6). Rules in §5.
- **Q5. Send-size details (D7, D11).** The
  measured accuracy floor for image size, the history cap value, and whether
  Anthropic automatic caching is used.
- **Q6. Resolved: report retention (D21).** Stay on the free Spark plan; no
  automatic deletion. Old reports can be removed with a manual script.
- **Q7. Resolved: report write protection (D22).** The open-write risk is
  accepted. Protection is create-only rules only; no App Check, no sign-in
  and no per-install document IDs (App Check dropped by the author after
  review, 2026-11-15).
- **Q8. Resolved: backup (D23).** Nothing is backed up and nothing is
  restored. All app files are excluded from iOS and Android backups; keys are
  already device-only.
- **Q9. Resolved: config failure (D24).** No fallback values. If the fetch
  fails or any value is invalid, the app shows "PhotoCritic couldn't load its
  settings. Check your connection and restart the app." The screen has no
  Try again button (author decision after review, 2026-11-15). There are no
  live updates; every Remote Config change, including the kill switch, takes
  effect at the next cold start.
- **Q10. Resolved: crashes before opt-in (D25).** No special handling. (Crash
  reports recorded while sharing is off may be sent once the user turns
  sharing on; check in the Phase 0 spike.)
- **Q11. Round-4 findings L1–L30 (deferred).** Listed in the brief, section L.
- **Q12. Full-screen layout (partly resolved; author will add the rest).**
  Decided: the photo is shown at its original aspect ratio, fitted whole,
  with black around it (no cropping). Still open: drawing under the status
  and navigation bars, safe-area spacing for controls, refitting on rotation,
  and the tablet top-button spacing.
- **Q13. Resolved: screen states.** The states are those in §5 plus
  `offline` (a banner) and `disabled` (a full screen); every error shows the
  one generic message (G12). Decided at the implementation review
  (2026-11-17).

## Accepted Risks

The author has reviewed and accepted these risks (AR2 and AR3 on
2026-11-15; AR1 and AR4 with the native-app and thin-client decisions on
2026-11-17).
**Reviewers: do not raise these as findings.** Comment only if the design
contradicts the accepted behavior described here.

- **AR1. Provider API changes are followed by hand (Q1).** No SDK
  maintainer updates the six clients. A provider can change event names,
  error bodies or image options, and the clients must be updated in both
  apps. Mitigations: one endpoint and a few event types per provider;
  unknown events and fields are ignored; recordings in `shared/` are
  refreshed before each tester release; the daily canary (Detailed
  Implementation Plan, file 4) calls every listed model; the request ID in "Show details" helps diagnosis.
- **AR2. No configuration fallback (Q9).** There are no built-in
  configuration values. If the fetch fails or any value is invalid, the app
  shows a restart screen with no Try again button. A single bad publish
  stops every tester's app until a fixed set is published and the app is
  restarted. The only protection is running `config:publish`, which
  validates before publishing. This is the single statement of the rule;
  other sections point here.
- **AR3. Report conversation stored as a list (contradiction 6).** Firestore
  rules can check the list's length but not the text inside each item, so a
  modified client could write oversize or wrongly typed text, up to the
  1 MiB document limit.
- **AR4. Two codebases (2026-11-17).** Every feature is written once in
  Kotlin and once in Swift, roughly 1.7 to 2 times the work of one
  cross-platform app (an estimate), and the two copies can drift. The
  mitigation is the shared test data in `shared/`, which both apps must
  pass, and building the whole Android app first (Phase 1), then iOS
  against the finished Android app and test data (Phase 1.a; author
  decision 2026-11-17). Until Phase 1.a, iOS covers only what Phase 0 built.

## Critical User Journeys (CUJs)

### CUJ 1: First launch to first answer

- **Given**: A fresh install on a supported phone, with network, and an API
  key the user created at one of the three providers.
- **When**: The user opens the app, picks a provider and a model, pastes the
  key, taps "Check key and continue", takes a photo, and taps Identify.
- **Then**: The key check succeeds, or shows the generic error message with
  "Show details" and keeps the screen open. On the first Send, the consent sheet for that provider
  appears. After "Agree and send", the answer streams into the sheet. A
  consent record (provider, text version, time) is written to
  `consent.json`. Nothing is sent to Firebase unless the user turned on
  "Share usage and crash data".

### CUJ 2: Copy text from a photo

- **Given**: A photo of a sign or receipt is loaded and consent is recorded.
- **When**: The user taps Copy text.
- **Then**: The sheet expands. The answer shows a "Text in photo" card with
  the text and its line breaks, and Copy and Share buttons. Copy puts plain
  text on the clipboard. If the model did not mark the text, the whole
  answer is shown with a Copy answer button.

### CUJ 3: Stop a long answer

- **Given**: An answer is streaming.
- **When**: The user taps Stop.
- **Then**: The request is cancelled. The partial text stays on screen,
  labelled "Stopped", and can be copied. It is not sent with later
  questions. Send and the starter buttons come back.

### CUJ 4: A provider error

- **Given**: The key has no billing set up, or the provider is overloaded.
- **When**: The user sends a question.
- **Then**: Nothing is retried automatically. The message "Something went
  wrong. Please try again." appears with Dismiss and "Show details". Details
  show where the failure happened (before the answer, during it, or at its
  end), the HTTP status, the provider's error code and request ID, never the
  key. If part of an answer had arrived, it stays on screen labelled
  "Failed", can be copied, and is not sent with later questions. After
  Dismiss, the user can send again.

### CUJ 5: New photo or new model

- **Given**: A conversation has at least one question.
- **When**: The user taps New photo, or changes provider or model in
  Settings.
- **Then**: A confirmation appears: "This conversation will be cleared.
  PhotoCritic keeps nothing from it." On confirm, the conversation, the send
  copy and any temporary files are deleted.

### CUJ 6: Report an answer

- **Given**: An answer is on screen.
- **When**: The user taps Report, picks a category, writes a note and taps
  Send report.
- **Then**: A text-only document is created in Firestore. The user sees a
  report ID and one of: sent, failed, or daily limit reached. Send report is
  disabled while the device is offline; nothing is saved to send later. No
  photo is sent.

### CUJ 7: The author disables a model remotely

- **Given**: The user's saved model is M, and the author adds M to
  `kill_switch.disabledModels` in Remote Config while the app is running.
- **When**: The app is closed and started again.
- **Then**: Nothing changes while the app is still running. At the next
  start, the app shows the kill-switch message and opens setup. M no longer
  appears in the model list, and the user picks another model. Disabling a
  provider works the same way; disabling the whole app shows the disabled
  screen.

### CUJ 8: Tablet or Split View

- **Given**: The app runs on an iPad or an Android tablet.
- **When**: The window is wider than about 700 points, or the user resizes
  it in Split View.
- **Then**: The layout switches between the bottom sheet and the two-pane
  split, with a margin so it does not switch back and forth near the
  threshold. The conversation and any streaming answer continue.

### CUJ 9: Settings cannot be loaded

- **Given**: The device has no connection at cold start, or the published
  Remote Config has an invalid value.
- **When**: The user opens the app.
- **Then**: After at most 10 seconds on the launch screen, the app shows
  "PhotoCritic couldn't load its settings. Check your connection and restart
  the app." There is no button. Closing the app and opening it again runs
  the fetch again (AR2).


## Technical Plan (Architecture)

Author decisions, 2026-11-17: two separate native apps (Android in Kotlin
with Jetpack Compose, iOS in Swift with SwiftUI), with no shared code
module; providers are called through thin hand-written clients, one per
provider per platform. The two apps share only data: the test vectors,
the Remote Config schema, the tuning values and the system prompt in
`shared/` (§2). Both apps follow every rule in this section; where the
platforms differ, both mechanisms are named.

### 1. Shape of the system

The app talks to three kinds of services directly from the phone. There is no
PhotoCritic server.

```text
                       ┌──────────────────────── Phone ────────────────────────┐
                       │                                                        │
  Camera / Photos ───▶ │  Screens (Jetpack Compose / SwiftUI)                   │
                       │     │  setup · home · settings · consent · report      │
                       │     ▼                                                  │
                       │  Features                                              │
                       │   session ── image ── consent ── report                │
                       │     │                                                  │
                       │     ▼                                                  │
                       │  providers/  PhotoCritic Provider interface            │
                       │     │  openai · anthropic · gemini (thin clients)      │
                       │     │  (on-device providers added in a later phase)    │
                       │     ▼                                                  │
                       │  platform/net  one HTTP client (OkHttp / URLSession)   │
                       │  platform/config · storage · telemetry                 │
                       └─────┬──────────────────┬──────────────────┬────────────┘
                             │ photo + question │ config / reports │ opt-in only
                             ▼                  ▼                  ▼
              api.openai.com           Firebase Remote Config   Firebase Analytics
              api.anthropic.com        Cloud Firestore          Crashlytics
              generativelanguage       (reports)
                .googleapis.com
```

- **Photos, questions and answers** go only to the provider the user chose,
  with the user's key.
- **Firebase** receives: config fetches at every start; report text when the
  user sends a report; usage events and crash reports only when sharing is
  on. Keys never go to Firebase.

### 2. Code layout

Each app has two parts: a **core** with no user interface and no Firebase
(providers, network, session logic, config validation, consent and report
rules), and the **app** (screens, platform services, Firebase). The core
builds and tests without a device or simulator.

```text
android/
  core/                pure Kotlin (JVM) module: no Android or Firebase imports
    net/               allowlist, the one OkHttp client, server-sent-events parser
    providers/         Provider interface, types, gemini/ anthropic/ openai/
                       clients, fake/, shared/ (system prompt, history trim,
                       extracted-text parser, PcError conversion, redaction)
    config/            Remote Config models, validator, snapshot, kill switch,
                       tuning constants, app-version comparison
    session/           one-photo conversation state machine, neutral history
    consent/, report/  consent gate rules, report document builder, daily limit
  app/                 Android application module
    platform/          storage (keys, files), config (Remote Config fetch),
                       telemetry, net (connectivity)
    features/          image (pick, pipeline, cleanup), report (Firestore write),
                       session (ViewModel)
    ui/                screens, sheets, design tokens and components
  harness/             JVM command-line tool using core/ (§G23)
ios/
  Core/                local Swift package PhotoCriticCore: Foundation only,
                       same folders and responsibilities as android/core
  PhotoCritic/         app target: Platform/, Features/, UI/ as on Android
shared/                data both apps read or test against; no code
  provider-streams/    recorded streams per provider
  provider-errors/     recorded error responses
  provider-requests/   expected request bodies
  remote-config/       JSON Schema, template, valid and invalid samples
  redaction/           redaction cases
  prompts/             system prompt and prompt-kind texts
  tuning.json          §8a values
firebase/              Firestore rules, indexes, rules tests
tools/                 author scripts (Node): config validate and publish,
                       fixture recording, report purge
```

Rules:

- Screens call features; features never import screens. `platform/` has no
  user interface.
- The core has no user interface, Android, UIKit, SwiftUI or Firebase
  imports. The Gradle module and the Swift package boundary enforce this:
  the core cannot see those libraries.
- Only `providers/` knows vendor names, vendor request formats and vendor
  JSON types. Nothing vendor-specific (request bodies, event types, error
  bodies) leaves a client. This keeps vendor details in one folder per app
  and lets on-device providers be added in Phase 2 behind the same
  interface.
- Only `core/net` creates an HTTP client, and only `providers/` calls it.
  Only `platform/telemetry` imports Firebase Analytics or Crashlytics.
  detekt `ForbiddenImport` rules (Android) and SwiftLint custom rules (iOS)
  enforce both.
- The system prompt and prompt-kind texts live once, in `shared/prompts/`,
  and are bundled into both apps at build time, so both apps send identical
  prompts.

### 3. Provider interface (author decision: each provider builds its own messages)

`session/` keeps a provider-neutral history. Each provider client turns that
history into its own request, places the image where its model needs it, and
applies its own length limit measured in tokens. The code constant
`HISTORY_TURNS_CAP` is an upper bound, not the rule.

Interface (language-neutral pseudocode; Kotlin and Swift follow the same
shape):

```text
interface Provider {
  id: 'openai' | 'anthropic' | 'gemini'          // local ids added later
  capabilities(modelId): Capabilities
  getAvailability(): Available | NeedsKey | NeedsDownload | Unsupported
  prepare(onProgress?)                           // no-op for cloud
  dispose()                                      // no-op for cloud
  checkKey(key, modelId): KeyCheckResult         // suspend / async, cancellable
  send(request: TurnRequest, key): stream of TurnEvent
    // Kotlin: Flow<TurnEvent>; cancelling the collector cancels the call.
    // Swift: AsyncThrowingStream<TurnEvent, Error>; cancelling the task
    //        cancels the URLSession task.
}

Capabilities {
  acceptsImages: boolean
  imagePlacement: 'any-turn' | 'final-turn-only'
  maxImageLongEdgePx, maxImageBytes
  contextTokens, maxOutputTokens
  streaming: 'tokens' | 'whole'
  usageReporting: boolean
  dataLeavesDevice: boolean        // consent gate keys on this
}

TurnRequest {
  modelId
  history: NeutralTurn[]           // finished turns only; stopped answers excluded
  question: string
  image: SendCopyRef               // JPEG bytes + size + per-model detail setting
  promptKind: 'identify' | 'explain' | 'extract' | 'free'
}

NeutralTurn { question, answerText, imageVersionId }

TurnEvent =
  | { type: 'text', delta }
  | { type: 'done', finishReason, servedModel, usage?, requestId? }
  | { type: 'error', error: PcError }       // also used for mid-stream errors
```

What each cloud client does inside `send`:

1.  Build the system prompt from the fixed base prompt in `shared/prompts/`
    (identical on every turn, so the start of the request stays the same)
    plus the prompt-kind text. The base prompt tells the model to wrap
    verbatim text from the photo in a fenced block tagged `text` (D36).
2.  Put the image in the first user message, then earlier turns, then the new
    question. Drop the oldest turns until the estimated token count fits the
    model's `contextTokens` and `HISTORY_TURNS_CAP`.
3.  Build the provider's JSON request (§3a) and send it through the
    `core/net` client (§4). There is no retry code anywhere (G12).
4.  Parse the server-sent events into `TurnEvent`s. Compare the served model
    with the requested one. Convert every error, before the response, inside
    the stream, or as a failing finish reason, into a `PcError` (§7).
    Unknown event types and unknown JSON fields are ignored, so an additive
    provider change does not break the client.

`checkKey`: OpenAI and Anthropic list models (`GET /v1/models`, no tokens
used). Gemini makes a minimal inference call, because a working model list
does not prove inference is enabled; the setup screen says this uses a few
tokens.

**Shared test vectors keep the six clients in step.** Every client, on both
platforms, runs the same contract tests against the recordings in
`shared/provider-streams/` and `shared/provider-errors/`, and compares its
request body with `shared/provider-requests/`. A recording carries its
date, and `tools/record-fixture.ts` refreshes them before each tester
release.

**Extracted text.** A parser in `providers/shared` splits answer text into
normal text and `text` cards. It runs on the finished answer and on each
streamed update, and shows an unfinished fence as normal text until it
closes.

#### 3a. Wire formats

Confirmed against live responses in Phase 0 (Q1) before the clients are
written; the Gemini row was checked on 2026-11-17.

| | OpenAI | Anthropic | Gemini |
| --- | --- | --- | --- |
| Endpoint | `POST https://api.openai.com/v1/responses` | `POST https://api.anthropic.com/v1/messages` | `POST https://generativelanguage.googleapis.com/v1beta/models/{model}:streamGenerateContent?alt=sse` |
| Key header | `Authorization: Bearer <key>` | `x-api-key: <key>`, plus `anthropic-version: 2023-06-01` | `x-goog-api-key: <key>` |
| Image | `input_image` part, `data:image/jpeg;base64,…`, `detail` from the model entry | `image` block, `source.type: base64`, `media_type: image/jpeg` | `inline_data` part, `mime_type: image/jpeg`; `mediaResolution` in `generationConfig` |
| Required fields | `stream: true`; **`store: false`**, because the Responses API otherwise keeps response data for 30 days ([OpenAI data controls](../../research/openai-your-data.txt)); `max_output_tokens` | `stream: true`; `max_tokens` (required by the API) | `generationConfig.maxOutputTokens`; no sampling parameters |
| Text events | `response.output_text.delta` | `content_block_delta` with `text_delta` | each `data:` object: `candidates[0].content.parts[].text` |
| End and failure events | `response.completed`; `response.incomplete` (length or content filter); `response.refusal.delta` (refusal text); `response.failed`; `error` | `message_delta` (`stop_reason`), `message_stop`; `error` event after a 200 | `finishReason` on the last object (`STOP`, `MAX_TOKENS`, `SAFETY`, …); `promptFeedback.blockReason` |
| Request ID | `x-request-id` header | `request-id` header | No header; body `responseId`. Failed calls carry none |

All three providers receive the model's `maxOutputTokens` from Remote
Config (§8), so answers have the same length limit everywhere.

### 4. Network client (`core/net`)

Every provider request goes through one HTTP client per app, built once in
`core/net`. Provider clients cannot create their own.

| Rule | Android (OkHttp) | iOS (URLSession) |
| --- | --- | --- |
| Host allowlist (`api.openai.com`, `api.anthropic.com`, `generativelanguage.googleapis.com`; decision 23) | Application interceptor refuses any other host before a connection is made | Checked before the task is created, and again in the redirect delegate |
| No redirects | `followRedirects(false)`, `followSslRedirects(false)`; a 3xx response is a failure | Delegate `willPerformHTTPRedirection` returns `nil`; a 3xx response is a failure |
| No retries | `retryOnConnectionFailure(false)`; no retry code anywhere | No retry code anywhere |
| Timeouts | `connectTimeout(CONNECT_TIMEOUT_MS)`, `callTimeout(REQUEST_TIMEOUT_MS)` | `timeoutIntervalForRequest`, `timeoutIntervalForResource` from the same constants |
| No local traces | No cache, no cookie jar | `URLSessionConfiguration.ephemeral`: no cache, cookies or credential storage |
| Keys | Only in the provider's header; never in the URL (no `?key=`); never logged | Same |
| Stop | Cancelling the coroutine cancels the OkHttp `Call` | Cancelling the task cancels the `URLSessionDataTask` |
| Request ID | Read from the response header named in §3a | Same |

The server-sent-events parser (one per app) reads `event:` and `data:`
lines, ends an event at a blank line, and treats `data: [DONE]` as the end
of the stream. Native HTTP clients do not send an `Origin` header, so the
React Native plan's Origin-removal rule is no longer needed.

### 5. Session state machine (`core/session`)

One conversation, one photo. The state drives what the sheet, the input and
the starter buttons show.

```text
 no-photo ──pick/take──▶ preparing ──ok──▶ ready ◀──────────────┐
    ▲                       │fail                │send           │
    │                       ▼                    ▼               │
    │                  image-error          awaiting-consent     │
    │                                        │agree   │not now   │
    │                                        ▼        └──────────┤
    │                                     sending ──first text──▶ streaming
    │                                        │                   │  │  │
    │                                        │error        done ─┘  │  └─stop─▶ stopped ─▶ ready
    │                                        ▼                      │error
    │                                      error ◀──────────────────┘
    │                                        │dismiss ──▶ ready
    └──── new photo / provider or model change (confirm if any turn) ─────
```

- **offline** is shown as a banner over `ready` when the device has no
  connection; Send is disabled.
- **disabled** (config unavailable, whole-app kill switch, app version too
  old) replaces the whole screen. It is decided once at cold start; nothing
  changes the session while the app runs.
- A stopped answer, and the partial text of an answer that failed, stay in
  the visible list (labelled "Stopped" or "Failed") but are never added to
  `history`.
- The state machine is plain code in the core (a reducer plus a controller
  that runs the provider stream), tested without a device. Android exposes
  it through a `ViewModel` with `StateFlow`; iOS through an `@Observable`
  model. Messages have stable IDs, so a streaming answer redraws only the
  message being written.
- `image-error` shows the generic message with Dismiss and "Show
  details"; Dismiss returns to `no-photo`.
- A failed consent write shows the generic message; the question is not
  sent and stays in the composer.
- Lifecycle (Q4): a streaming answer keeps running while the app is in the
  background and the process lives; if the OS stops the connection (iOS
  suspends network work shortly after backgrounding), the answer ends as
  Failed with its partial text. Rotation and window resizing keep the
  conversation and the stream. After process death the conversation is
  gone and the app starts at `no-photo`; nothing is restored.

### 6. Image pipeline (`features/image`)

```text
picker / camera ──▶ original (file or content URI, never changed, displayed full quality)
                       │
                       ▼
                    edits (list of operations; empty in v1)
                       │
                       ▼
                    send copy: decode with downsampling to the model's long-edge
                    target (DEFAULT_LONG_EDGE_PX, 2048) ─▶ apply EXIF orientation ─▶
                    encode JPEG (quality from JPEG_QUALITY) ─▶ no metadata kept
```

| Step | Android | iOS |
| --- | --- | --- |
| Pick | Photo Picker (`PickVisualMedia`, image only; the Google Play services backport below API 30); no storage or media permission | `PhotosPicker` (runs out of process; no photo-library permission) |
| Camera | `TakePicture` into a `FileProvider` file in the app cache; the target URI is kept in `SavedStateHandle`, so the result survives the activity being destroyed during capture; no `CAMERA` permission declared | `UIImagePickerController` with `.camera`, wrapped for SwiftUI; camera usage string |
| Decode with downsampling | `ImageDecoder` with a target size (API 28+); `BitmapFactory` with `inSampleSize` below API 28, which cannot decode HEIC (Q2) | ImageIO `CGImageSourceCreateThumbnailAtIndex` with `kCGImageSourceThumbnailMaxPixelSize` |
| Orientation | `ImageDecoder` applies it; on the `BitmapFactory` path, read with AndroidX `ExifInterface` and rotate | `kCGImageSourceCreateThumbnailWithTransform` |
| Encode, no metadata | `Bitmap.compress(JPEG, quality)` writes no EXIF | `CGImageDestination` JPEG with `kCGImageDestinationLossyCompressionQuality`; no source properties copied |
| Display | Decoded to the view size times the zoom limit, kept in memory only; pinch to zoom | Same, with a `UIScrollView`-based zoom view |

- A 48–200 MP photo is never fully decoded into memory: every decode is
  downsampled.
- Photos are never written to an image disk cache.
- The send copy and temporary files (including camera captures) are deleted
  when the conversation ends and at launch.
- Each send copy has an `imageVersionId`, so later image editing can start a
  new conversation instead of changing the image under old answers.

### 7. Errors

Author decision after review (2026-11-15): there is no error table, no
error classification and no retry policy. Every failure shows one generic
message:

> Something went wrong. Please try again.

with Dismiss and "Show details". Nothing is retried automatically: there is
no retry code, and OkHttp's connection retry is off (§4).

A failure is caught at one of three points, and the point is recorded as
`phase` in the details:

- `before-response`: the request failed, was refused by the network client,
  or returned a non-2xx status;
- `mid-stream`: an error event arrived after a 200 (OpenAI `error` or
  `response.failed`, Anthropic `error`, or a stream that ended without an
  end event);
- `finish`: the answer ended with a failing finish reason (OpenAI
  `response.incomplete` for a content filter or a response with refusal
  content, Anthropic `stop_reason` `refusal`, Gemini `SAFETY` or another blocking reason, or
  `promptFeedback.blockReason`). A length limit ends the answer normally.

Each failure becomes a `PcError` with `phase`, `httpStatus`, `providerCode`
(the provider's error code or type string, if any), `requestId` and a
redacted `detail` (the provider's message with keys and URLs removed, at most
200 characters). "Show details" shows these fields. The same message and
details are used for key-check failures on the setup screen and for photos
that cannot be read. The offline state is not an error: it is a banner, and
Send is disabled.

If text had already arrived, it stays on screen labelled "Failed" and is
not added to `history`, the same as a stopped answer. A refusal's text is
kept the same way, with `providerCode` `refusal`.

### 8. Remote configuration (`core/config`, `platform/config`)

Parameters (shapes as in brief section I.3, with round-4 changes). The JSON
Schema in `shared/remote-config/schema.json` is the reference; each app's
validator is tested against the valid and invalid samples next to it.

| Parameter             | Holds                                                                                                                                                                                                                       |
| --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `config_meta`         | `configVersion`, `publishedAt`                                                                                                                                                                                              |
| `kill_switch`         | `appDisabled`, message, `minSupportedAppVersion`, `updateUrl`, `disabledProviders[]`, `disabledModels[]`, `bannerMessage`, `reportsDisabled`                                                                                |
| `providers`           | per provider: `enabled`, `displayName`, `baseUrl` (must match the allowlist), `keyHelpUrl`, `consentTextVersion`                                                                                                            |
| `models`              | per model: `provider`, `id`, `displayName`, `label` (`Fast`/`Best`, display only), `maxLongEdgePx`, `maxBytes`, `imageDetail`, `maxOutputTokens`, `contextTokens`, `status`, `replacementId`, `minAppVersion`              |
| `consent_texts`       | per provider: `version`, `effectiveDate`, `summary[]`, `policyUrl`                                                                                                                                                          |
| `privacy_policy`      | `version` (of the policy bundled in the app), optional `url` (empty until a hosted copy exists)                                                                                                                             |

`consent_texts.<p>.version` is the consent version the send gate compares;
the validator requires `providers.<p>.consentTextVersion` to equal it. How
`status`, `replacementId`, `minAppVersion`, `bannerMessage` and `updateUrl`
are shown is in the Detailed Implementation Plan, file 26.

Changes from the brief: `models.tier` becomes `label` (display only) and the
`defaults` parameter is removed, because the user always picks the model
(decision 36). `contextTokens` is added for the provider interface.

Changes after review (author decisions, 2026-11-15): `image_policy`,
`conversation_policy` and the numeric part of `report_policy` move to code
constants (§8a); `report_policy.enabled` becomes `kill_switch.reportsDisabled`;
`error_map` is removed (§7); `models.fallbackId` is removed, because the
kill switch applies only at start and the user always picks the model.

#### 8a. Tuning constants (`core/config/Tuning.kt`, `Core/Config/Tuning.swift`)

These are constants in code, changed only by a new build. Both apps hold the
same values; `shared/tuning.json` lists them, and a unit test in each app
fails if its constants differ from that file. Values marked "Phase 0" are
set from the Phase 0 measurements and recorded in this table before Phase 1
ends.

| Constant                         | v1 value | Used by                                |
| -------------------------------- | -------- | -------------------------------------- |
| `CONFIG_FETCH_TIMEOUT_MS`        | 10000    | startup config fetch (§8)              |
| `DEFAULT_LONG_EDGE_PX`           | 2048     | send copy, when a model sets no limit  |
| `JPEG_QUALITY`                   | 0.8      | send copy encoding                     |
| `HISTORY_TURNS_CAP`              | Phase 0  | history trimming upper bound (§3)      |
| `MAX_QUESTION_CHARS`             | Phase 0  | composer input limit                   |
| `CONNECT_TIMEOUT_MS`             | Phase 0  | `core/net`                             |
| `REQUEST_TIMEOUT_MS`             | Phase 0  | `core/net`                             |
| `MAX_REPORT_CONVERSATION_CHARS`  | Phase 0  | report text cap (§10), Firestore rules |
| `REPORT_DAILY_LIMIT_PER_INSTALL` | Phase 0  | report daily limit                     |
| `REPORT_RETENTION_DAYS`          | Phase 0  | `reports:purge` cut-off (Q6)           |
| `REPORT_WRITE_TIMEOUT_MS`        | Phase 0  | report service (§10)                   |

Because the report limits are code constants, `firestore.rules` uses the
same literal numbers where rules can check them (`HISTORY_TURNS_CAP` for the
conversation list length);
`MAX_REPORT_CONVERSATION_CHARS` is enforced by the apps only. A test reads
`shared/tuning.json` and the rules file and fails if they differ.

Load order (brief K.4), the same on both platforms:

1.  At cold start, show the launch screen and fetch and activate Remote
    Config with a minimum fetch interval of 0 and a 10-second timeout. There
    are no in-app default values.
2.  If the fetch fails or times out, show the config-unavailable screen
    (AR2, CUJ 9). If Firebase reports the fetch as throttled, use the values
    activated by this install's last successful fetch, if there are any;
    otherwise show the same screen. This is the only case in which earlier
    values are used, and they go through the same check in step 3.
3.  Check every value against its expected shape. If any value is invalid,
    show the same screen; there is no per-value fallback (Q9). A `baseUrl`
    outside the allowlist counts as invalid.
4.  Build the in-memory session snapshot. Screens read only the snapshot.
5.  Evaluate the kill switch once. There is no real-time update listener: a
    change published while the app runs takes effect at the next cold start
    (G18).

### 9. Local storage (`platform/storage`)

| Item                   | Android                                                                                         | iOS                                                                                   | Contents                                                                                                                |
| ---------------------- | ----------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| API key per provider   | File in `noBackupFilesDir`, encrypted with AES-GCM using a non-exportable Android Keystore key | Keychain item, `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`, not synchronizable    | API key only                                                                                                            |
| `install.json`         | `filesDir`                                                                                      | Application Support                                                                   | `installedAt`, `appVersionAtInstall`                                                                                    |
| `settings.json`        | `filesDir`                                                                                      | Application Support                                                                   | `schemaVersion`, selected provider, selected model per provider, analytics opt-in, `onboardingCompletedAt`, `updatedAt` |
| `consent.json`         | `filesDir`                                                                                      | Application Support                                                                   | per provider: current record and up to 10 earlier ones (`consentTextVersion`, `decision`, `recordedAt` UTC)             |
| `reports.json`         | `filesDir`                                                                                      | Application Support                                                                   | sent report IDs and the daily counter                                                                                   |

- **Backup (Q8):** Android sets `allowBackup="false"` and data-extraction
  rules that exclude everything; iOS marks the Application Support folder
  `isExcludedFromBackup`. Keychain items use a "this device only" class.
- **Reinstall:** at launch, if `install.json` is missing, delete every key
  (on iOS, all Keychain items for the app's service name, which survive
  uninstall; on Android, the key files and the Keystore key), then write the
  marker. A read error is not treated as "missing". No backup restore is
  supported.
- Files are written to a temporary file, then renamed (Android
  `AtomicFile`; iOS `Data.write(options: .atomic)`).
- Sending is blocked unless `consent.json` has a granted record for the
  provider at or above the current `consentTextVersion`.

### 10. Reports (`core/report`, `features/report`)

A report is one Firestore document `reports/{reportId}` with the fields in
brief K.6: IDs and times, app and device info, `configVersion`, provider,
requested and served model, provider request ID, image settings (size and
detail, not the image), the conversation as a list of `{role, text}` up to
`MAX_REPORT_CONVERSATION_CHARS` in total,
the reported turn, category, the user's note, an optional redacted error, and
`instanceId` only when sharing is on. Security rules allow `create` only and
check every field. There is no App Check. Firestore uses its memory-only
cache (`MemoryCacheSettings` on both platforms), so report text is never
cached on the device. Send report is disabled while offline; a write the
server has not confirmed within `REPORT_WRITE_TIMEOUT_MS` is shown as failed
(author decisions after review, 2026-11-15). The document is built in the
core (tested on both platforms against the same expected fields); only the
write is platform code.

### 11. Telemetry (`platform/telemetry`)

Analytics and Crashlytics collection is off in the build configuration
(Android manifest `<meta-data>`, iOS `Info.plist` keys; exact names checked
in Phase 0) and turned on only by the "Share usage and crash data" switch.
Advertising-ID collection is off on both platforms. Events and parameters
are those in brief K.5, with `tier` removed from `session_start`. The
wrapper removes any text, key, image path or provider error body before
logging. When sharing turns on, the app reads the Analytics app instance ID
and sets it as the user ID for Analytics and Crashlytics; when it turns off,
the user ID is cleared. Android release builds upload the R8 mapping file
and iOS builds upload debug symbols for Crashlytics.

### 12. Screens and look

- **Setup** (first launch and Settings → Provider or Model): provider
  buttons, model list with Fast/Best labels, key field with checking and
  error states, the sharing switch (first launch only).
- **Home**: photo full screen; top bar with Settings and New photo only. A
  persistent bottom sheet with two heights: collapsed, sized to content up
  to a limit, and expanded. Android uses Material 3 `BottomSheetScaffold`
  with two anchors; iOS uses `.sheet` with two `presentationDetents`,
  `presentationBackgroundInteraction(.enabled)` and interactive dismissal
  off. Tint of at least 72% under text when collapsed and 94% when expanded;
  controls on the photo use at least 65%. Solid when Reduce Transparency is
  on, and on Android devices without blur. The conversation list is a
  `LazyColumn` (Android) or a `LazyVStack` in a `ScrollView` (iOS). The
  input moves above the keyboard (`imePadding` on Android; the sheet's
  keyboard avoidance on iOS).
- **Wide windows** (over about 700 points or dp, with a margin to prevent
  flipping): photo pane on the left, a fixed-width conversation pane on the
  right. Layout follows the measured window width, not device type.
- **Settings**: provider, model, key (replace), sharing switch, privacy
  policy, support ID (only when sharing is on), app version.
- **Design tokens** (`ui/design/Tokens.kt`, `UI/Design/Tokens.swift`, same
  values): colors `bg #0F1012`, `surface #17181B`, `field #1C1D21`, `text
  #F4F4F2`, `muted` (text at 68%), `accent #F5B13D`, `accentOnLight
  #8A5A00`, `onAccent #1A1206`, `danger #FF8A7A`; radii 12 / 14–16 / 24 /
  999; spacing 4–32; system fonts that scale with the system text size (sp
  units on Android, Dynamic Type text styles on iOS); touch targets at least
  44 points (48 dp on Android, the Material minimum).
- **Icons**: Lucide SVGs converted to Android vector drawables and added to
  the iOS asset catalog as SVG (ISC licence).

### 13. Libraries and versions

Exact versions are pinned at Phase 0 and recorded in
[docs/VERSIONS.md](../VERSIONS.md). Fixed choices:

- **Android:** Kotlin 2.x, Android Gradle Plugin with a version catalog,
  Jetpack Compose (BOM) with Material 3, Navigation Compose, Lifecycle
  `ViewModel`, kotlinx.coroutines, kotlinx.serialization, OkHttp, AndroidX
  Activity (Photo Picker) and ExifInterface, the Firebase Android BoM (Remote
  Config, Analytics, Crashlytics, Firestore). Tests: JUnit, Turbine, OkHttp
  `MockWebServer`, Robolectric, Compose UI tests. Tools: ktlint, detekt.
  minSdk 24; targetSdk the latest Play requires. Release builds use R8, and
  every native library is checked for 16 KB page alignment.
- **iOS:** Swift 6 language mode, SwiftUI, iOS 17 deployment target, the
  Firebase Apple SDK through Swift Package Manager (the same four products;
  Analytics without the advertising-ID product). No other runtime
  libraries. Tests: Swift Testing, XCUITest. Tools: XcodeGen, SwiftLint.
- **Neither app** uses a provider SDK or a third-party AI library.

## Alternatives Considered

Each entry gives the main benefit, the main cost, and why it was not chosen.
"Rejected by author" means the author decided it during brainstorming; the
decision numbers refer to [concepts_brief.md](../../concepts_brief.md).

### Platform and framework

Chosen (author decision, 2026-11-17): two native apps, Kotlin with Jetpack
Compose and Swift with SwiftUI, with no shared code module. This reverses
the brainstorm choice of Expo and React Native (answer 7).

#### Alternative A1: Expo and React Native (the earlier choice)

- **Pros**: One TypeScript codebase for both platforms; the Vercel AI SDK
  for the provider layer; a Phase 0 scaffold already existed (commit
  `a1f4c49`).
- **Cons**: A JavaScript bridge and native-module dependencies for camera,
  secure storage, Firebase and the sheet; the sheet library still to be
  chosen (Q3); on-device models in a later phase through React Native wrappers
  (llama.rn, ExecuTorch) instead of the platform frameworks.
- **Reason for Rejection**: Rejected by author (2026-11-17), in favor of
  native apps.

#### Alternative A3: Kotlin Multiplatform shared core with native UI

- **Pros**: Provider clients, session, config and report logic written
  once in Kotlin and used by both apps; native UI on each platform.
- **Cons**: iOS builds need the Kotlin Multiplatform toolchain, and Swift
  calls Kotlin through a generated bridge.
- **Reason for Rejection**: Rejected by author (2026-11-17): two separate
  apps; only data is shared (`shared/`).

#### Alternative A4: Flutter

- **Pros**: One codebase and one rendering engine on both platforms.
- **Cons**: A third language and UI toolkit; platform AI frameworks
  reached through plugins.
- **Reason for Rejection**: Rejected by author during brainstorming
  (answer 7); not reconsidered.

#### Alternative A2: On-device model first in v1

- **Pros**: No API key, works offline, photos never leave the phone; it is
  the comparison the demonstrator is ultimately about.
- **Cons**: On-device image input is narrow today (Apple reads an image only
  on the final prompt and from iOS 27; Core AI needs iOS 27 and a 2.9 GB
  download; llama.rn has a 4,096-token default context), so few devices
  could run it.
- **Reason for Rejection**: Rejected by author (deferred): start with cloud
  models, add on-device models later to evaluate them (answer 2). The
  provider interface includes availability and download methods so the
  later phase fits.

### Providers and models

#### Alternative B1: DeepSeek as a fourth provider

- **Pros**: Low prices.
- **Cons**: Its hosted API does not accept images (as of mid-2026; not
  re-verified).
- **Reason for Rejection**: Rejected by author: a photo app needs a vision
  API.

#### Alternative B2: Gemini only in v1

- **Pros**: One adapter, one error map, less testing.
- **Cons**: No provider comparison, which is part of what the demonstrator
  measures.
- **Reason for Rejection**: Rejected by author (decision 8): three
  providers. Gemini is still built first.

#### Alternative B3: A pointer to Gemini's free tier

- **Pros**: Testers could start without paying.
- **Cons**: Google's terms say not to send personal information to unpaid
  services, and only paid services may be offered to users in the EEA,
  Switzerland and the UK.
- **Reason for Rejection**: Rejected by author (decision 10).

#### Alternative B4: Choose a "Fast" or "Best" tier instead of a model

- **Pros**: Easier for people who do not know model names; the author can
  swap the model behind a tier remotely.
- **Cons**: Hides which model answered, which the demonstrator needs to
  compare models.
- **Reason for Rejection**: Rejected by author (decision 36, final): the user
  always chooses provider and model. Fast and Best remain as labels only.

#### Alternative B5: A model list fetched from each provider, or typed by the user

- **Pros**: Always current; no Remote Config publishing.
- **Cons**: Provider model lists include audio, embedding and image-generation
  models, and do not say which models suit this app. Models are also
  re-routed silently.
- **Reason for Rejection**: A curated list in Remote Config was chosen
  (D5), carrying status, replacement and re-route data per model.

### Provider layer

Chosen (author decision, 2026-11-17): thin hand-written clients, one per
provider in each app (§3, §3a, §4).

#### Alternative C1: The Vercel AI SDK

- **Pros**: One maintained library for all three providers; it was the
  provider layer of the React Native plan, and its Phase 0 checks passed
  for Gemini under Node (2026-11-17).
- **Cons**: JavaScript only; there is no Kotlin or Swift version.
- **Reason for Rejection**: Not possible in native apps (2026-11-17).

#### Alternative C4: Provider SDKs (official where they exist, community libraries otherwise)

- **Pros**: Less code to write; the SDK maintainer follows API changes.
- **Cons**: The security rules in §4 would hold only if each SDK accepts the
  app's HTTP client and allows retries off, checked again after every
  upgrade (the official OpenAI and Anthropic Java SDKs retry by default).
  Gemini has no mobile SDK that takes the user's key: Google's client SDKs
  for Android and Swift were deprecated, and Firebase AI Logic bills the
  app owner's project. iOS has no official OpenAI or Anthropic SDK, so code
  that handles the key would come from community libraries. The two apps
  would use different SDKs per provider, with different errors and
  cancellation behavior.
- **Reason for Rejection**: Rejected by author (2026-11-17), in favor of
  thin clients.

#### Alternative C2: Shared session code builds the messages (message-building option A)

- **Pros**: One place to read and test; least code in v1.
- **Cons**: Counts turns, not tokens, and fixes where the image goes, which
  fails for on-device models.
- **Reason for Rejection**: Rejected by author (Technical Plan §3): each
  provider builds its own messages (option B).

#### Alternative C3: One shared message builder configured per provider (message-building option C)

- **Pros**: One copy of the logic; differences written as data.
- **Cons**: A provider whose rules do not fit the settings forces special
  cases into the shared builder.
- **Reason for Rejection**: Rejected by author (Technical Plan §3), in favor
  of option B with a shared test suite.

### Images

#### Alternative D1: Send the smallest image the model accepts

- **Pros**: Fewest tokens and least data.
- **Cons**: Damages text extraction on small print, which is a core use.
- **Reason for Rejection**: Rejected by author: a fixed target of about
  2048 px on the long edge, set per model, after measuring accuracy in the
  harness.

#### Alternative D2: Send the image once, then refer to it (digest or provider caching APIs)

- **Pros**: Fewer tokens on follow-up questions.
- **Cons**: Provider caching rules differ and are partly undocumented; file
  APIs add upload and deletion steps.
- **Reason for Rejection**: Rejected by author (decision 11): re-send every
  turn with a history cap and a stable request start, so automatic caching
  can help where the provider offers it.

#### Alternative D3: Downscale the displayed photo

- **Pros**: Less memory.
- **Cons**: The user loses detail they want to zoom into.
- **Reason for Rejection**: Rejected by author (decision 8): show full
  quality; the display decoder already downsamples to the view size (§6).

### Data, configuration and reporting

#### Alternative E1: Static JSON on Firebase Hosting instead of Remote Config

- **Pros**: No Firebase SDK on the device.
- **Cons**: Conditions by app version, rollback and staged rollouts written by
  hand.
- **Reason for Rejection**: Rejected by author (round 3). Once Analytics was
  wanted (decision 20), its main advantage was gone.

#### Alternative E2: Signed configuration

- **Pros**: Protects against forged configuration.
- **Cons**: Remote Config already arrives over Google-authenticated HTTPS,
  and signing does not stop a mistaken or malicious publish.
- **Reason for Rejection**: Dropped (round 3). The real risk, a wrong
  provider host, is covered by the in-code host allowlist (decision 23).

#### Alternative E3: Keep keys, settings and consent across reinstall

- **Pros**: Testers do not re-enter keys.
- **Cons**: iOS keeps Keychain items after uninstall, so a key would outlive
  the app the user deleted.
- **Reason for Rejection**: Rejected by author (decisions 17, 21).

#### Alternative E4: Settings and consent in the key store, a key-value store or SQLite

- **Pros**: The Keychain and Keystore encrypt; DataStore or `UserDefaults`
  are built in; SQLite allows queries.
- **Cons**: None adds value for a few small files, and the key stores are
  meant for secrets, not consent history.
- **Reason for Rejection**: Rejected by author (decision 17): plain files in
  app storage, keys only in the Keychain or the Keystore-encrypted file.

#### Alternative E5: Report by email from the user's mail app

- **Pros**: No database.
- **Cons**: Leaves the app, which conflicts with Play's in-app reporting
  rule; collects photos and email addresses in the developer's inbox.
- **Reason for Rejection**: Replaced by author (decision 22) with in-app
  Firestore reports.

#### Alternative E6: Include the photo in reports (in the document, or in Cloud Storage with a notifying Cloud Function)

- **Pros**: The reported answer can be checked against the photo.
- **Cons**: Personal photos in Firebase; Cloud Storage and Functions need the
  paid Blaze plan.
- **Reason for Rejection**: Rejected by author (decision 25): text only.

#### Alternative E7: Analytics opt-in only for EEA, UK and Switzerland

- **Pros**: More data from testers elsewhere.
- **Cons**: Region detection and two privacy behaviors.
- **Reason for Rejection**: Rejected by author (decision 24): opt-in for
  everyone.

#### Alternative E8: A local conversation log with token counts and cost

- **Pros**: Testers see usage per conversation.
- **Cons**: Reverses "no stored history"; cost depends on prices that change
  within weeks.
- **Reason for Rejection**: Withdrawn by author (decision 36): no log in v1;
  to be added later.

### Screens and look

#### Alternative F1: 80% transparent conversation sheet

- **Pros**: The photo stays visible behind the conversation.
- **Cons**: Over a bright photo, text contrast falls to about 1.4:1, far
  below the 4.5:1 needed to read it.
- **Reason for Rejection**: Replaced by author (decision 36, D27): blur on
  controls, at least 65% tint under any text.

#### Alternative F2: Three sheet heights (25%, 50%, 90%)

- **Pros**: A middle reading height.
- **Cons**: A fixed 25% cannot hold the input and starter buttons at large
  text sizes.
- **Reason for Rejection**: Rejected by author (D28): two heights, the
  collapsed one sized to content.

#### Alternative F3: Phone portrait only in v1

- **Pros**: One layout; less testing.
- **Cons**: Tablets and landscape were requested.
- **Reason for Rejection**: Rejected by author (D29): fixed two-pane layout
  on wide windows.

#### Alternative F4: Full custom identity (bundled fonts, light and dark themes, custom icons)

- **Pros**: A more distinctive look.
- **Cons**: Font licensing and bundling, two themes to test, and a custom
  icon set to draw.
- **Reason for Rejection**: Rejected by author (D32): system fonts, dark
  only, Lucide icons, custom app icon.

#### Alternative F5: Consent at the end of setup

- **Pros**: One onboarding flow.
- **Cons**: Consent before the user has a reason to send; needs repeating
  for each provider anyway.
- **Reason for Rejection**: Rejected by author (D33): at the first Send for
  each provider.

#### Alternative F6: Model name on the main screen or on each answer

- **Pros**: The user sees which model is billed and which one answered.
- **Cons**: Takes space from the photo.
- **Reason for Rejection**: Rejected by author (decision 37): shown only in
  Settings; reports still record it.

#### Alternative F7: Canned demo mode for store review

- **Pros**: Reviewers can test without a key.
- **Cons**: Demo code shipped to every user, and Apple's demo-mode rule is
  meant for apps with logins.
- **Reason for Rejection**: Rejected by author: reviewers get a
  spend-capped key instead.

### Distribution

#### Alternative G1: Public App Store and Play production release first

- **Pros**: Store discovery; no 90-day TestFlight build expiry.
- **Cons**: Higher risk under Apple 4.2 (minimum functionality) and a larger
  audience while the app is a demonstrator.
- **Reason for Rejection**: Deferred by author (decisions 15–16): TestFlight
  and Play internal testing first.

#### Alternative G2: Firebase App Distribution or a Play closed test for Android

- **Pros**: App Distribution can skip Play review (not verified).
- **Cons**: A separate distribution path from the one a later public release
  would use.
- **Reason for Rejection**: Not chosen (decision 16): Play internal testing
  track.

### Decided during drafting

These alternatives were decided during drafting:

- Report retention: the Blaze plan with automatic deletion was not chosen;
  reports stay on the free plan with manual removal (Q6).
- Firebase Anonymous Auth or per-install document IDs for report writes: not
  chosen; the open-write risk is accepted (Q7).
- Built-in fallback configuration values: not chosen; a failed or invalid
  fetch shows a restart screen (Q9). This removes protection against a bad
  publish: one invalid value stops every tester's app until a fixed set is
  published.

Decided by the author after review (2026-11-15):

- An error table, error classes and automatic retries: not chosen; one
  generic message with details (G12, §7).
- Tuning values in Remote Config: not chosen; they are code constants (§8a).
- A Try again button on the config-unavailable screen: not chosen; the user
  restarts the app (AR2, CUJ 9).
- Live kill-switch updates and a fallback model: not chosen; the kill switch
  applies at cold start only (G18).

Decided by the author on 2026-11-17 (superseding the 2026-11-15 decision
that the Vercel AI SDK was final):

- Expo and React Native: replaced by two native apps (A1).
- A Kotlin Multiplatform shared core: not chosen (A3).
- The Vercel AI SDK and provider SDKs: replaced by thin clients (C1, C4).

## Non-Functional Requirements

- **Security and privacy.** API keys only in the Keychain (iOS) or the
  Keystore-encrypted file (Android), and only in request headers to the
  three allowlisted hosts. No key, photo, question,
  answer, image path or provider error body in any log, event, crash key or
  report field other than the report's own conversation text. Photos never
  written to the image disk cache. Analytics and Crashlytics off until the
  user opts in.
- **Performance (targets to measure on the oldest supported phone, set in
  Phase 0 from the first measurements):** time from Send to first streamed
  word; photo preparation time for a 48 MP HEIC; cold start to first
  interactive screen (including the config fetch, up to 10 seconds); sheet drag and
  streaming at the display frame rate. Optimize only what these numbers
  show.
- **Cost to the user.** One send copy per turn of about 2048 px; history
  capped by tokens; no automatic retries of any kind.
- **Rollout and rollback.** Remote Config kill switch (whole app, provider or
  model, applied at the next cold start) and `minSupportedAppVersion`; no
  bundled config values, so every
  publish must pass `config:validate` first, because a bad publish stops every
  install until it is fixed (Q9); TestFlight and Play internal track only.
  No over-the-air updates in v1.

---

### Additional Resources

- [concepts_brief.md](../../concepts_brief.md): all decisions, findings and
  evidence citations.
- [research/README.md](../../research/README.md): fetched provider, Apple, Play,
  Expo and Firebase documentation used as evidence.
- [research/callstack/](../../research/callstack/): Callstack React Native
  documentation, kept from the earlier React Native plan.
- [docs/plans/native-migration.md](../plans/native-migration.md): the
  2026-11-17 decision record for the move to native apps and thin clients.
- [mockup/](../../mockup/): clickable mockup source (phone, tablet, design
  system).

### Future Improvements

- **On-device models** (Phase 2): image-capable on-device providers first
  (Apple Foundation Models, Android ML Kit GenAI), with a download and
  storage feature (`features/models/`), then the local-versus-cloud
  benchmark.
- **Conversation log** with usage details, which the author will design
  later.
- **Image editing** using the edit list already in the image data model.
- **Light theme, Markdown answers, more languages.**
- **Full-screen layout details** (Open Question Q12).

---

## Detailed Implementation Plan

### Assumptions fixed by this plan

These are mechanical choices this plan makes so implementation can start. Change
them here before Phase 0 if they are wrong.

- The repository root holds `android/`, `ios/`, `shared/`, `firebase/`,
  `tools/`, `harness/` (data only), `docs/`. `TokenEconomizer/` and
  `research/raw/` are ignored by git; `research/`, `mockup/` and the review
  reports stay as they are.
- App identifiers: iOS bundle ID, Android application ID and Android
  namespace `com.yk.photocritic`; display name "PhotoCritic". These become
  permanent once a build is uploaded.
- Two Firebase projects: `photocritic-dev` and `photocritic-prod`. Android
  build flavors `dev` and `prod`; iOS build configurations `Debug-Dev`,
  `Release-Dev` and `Release-Prod`. This replaces `APP_ENV`.
- CI runs on GitHub Actions. Tester builds are made locally with Android
  Studio (`bundleProdRelease`) and Xcode (Archive), and uploaded by hand to
  the Play internal track and TestFlight. No EAS, no fastlane in v1.
  Signing material stays outside git (files 17 and 41).
- The Remote Config JSON Schema is the reference; each app has a
  hand-written validator (no schema library at run time), and both are
  tested against the same valid and invalid samples.
- Dependencies are passed by hand: each app has one `AppContainer` that
  holds the interfaces `ConfigSource`, `KeyStore`, `ReportSink`,
  `ProviderRegistry`, `Telemetry` and `Connectivity`. Tests and the fake
  mode build it with fakes (files 37 and 46). No injection library.
- Connectivity: Android `ConnectivityManager` network callbacks; iOS
  `NWPathMonitor`.
- Tests: JUnit 5 in `core/` and `harness/`; JUnit 4 in `app/`, where
  Robolectric and the Compose UI tests run; Swift Testing in `Core` and the
  app; XCUITest for screen flows. Each source file has one test file in the
  mirror test path, named `<Source>Test.kt` or `<Source>Tests.swift`.
- Within a phase, Android work is done first, then iOS, so iOS starts from
  Android code that already passes the shared contract tests (AR4).

### Implementation order

Author decision (2026-11-17): Phase 1 finishes the Android app with every
feature, through a Play internal-track tester release; Phase 1.a then builds
the iOS app to the same feature set. Each phase and step ends with CI
passing. During Phase 1 the iOS job keeps running, so the iOS code from
Phase 0 must stay green when `shared/` changes, but iOS gains no new
features until Phase 1.a.

1.  **Phase 0, setup and spikes.**
    1. Remove the Expo stack (file 1) in one commit, which also creates
       `shared/tuning.json` from `src/platform/config/tuning.ts` (file 10)
       and rewrites `docs/spikes/phase0.md` (file 50); the old stack stays in
       history (commit `a1f4c49`).
    2. Scaffold `android/` and `ios/` with Firebase, collection off, lint and
       one test each; CI for both.
    3. `shared/` with the README and fixture schema, the prompts, the
       Remote Config schema and template, and `tools/` (`config:validate`,
       `fixture:record`, `sync-shared.sh`), so the `shared` CI job passes.
    4. Record provider streams and errors for all three providers with
       spend-capped keys; confirm the wire-format table (§3a, Q1).
    5. Build `core/net` and the Gemini client on both platforms; run the
       client acceptance tests (Verification Plan) on an Android phone.
    6. On Android: Firebase behavior checks, the image-size accuracy test
       (Q5), and HEIC on API 24–27 and AVIF on API 24–30 (Q2), as listed
       under file 50. The iOS rows of file 50 move to Phase 1.a.
2.  **Phase 1, the complete Android app.** In four steps, each ending with
    a signed Android build:
    1. **1.1 Foundations.** Storage, install marker, Remote Config with
       snapshot and validation, telemetry wrapper, image pipeline.
    2. **1.2 Gemini end to end.** Provider interface, the Gemini client,
       error conversion, consent, setup, home screen, Stop, text cards.
    3. **1.3 Anthropic then OpenAI.** Contract tests against the
       recordings; the canary; the harness.
    4. **1.4 Polish and Android tester release.** Reports, Settings, wide
       layout, accessibility, Firestore rules and their tests, author
       tools, privacy documents, Play Data safety form, Play internal
       track.
3.  **Phase 1.a, the iOS app.** The iOS rows of file 50 (iPhone acceptance
    tests, Firebase checks, atomic writes); then the same features in the
    order of Phase 1 steps 1.1–1.4, passing the same contract tests and
    vectors in `shared/`; App Store privacy form and TestFlight. Needs the
    Apple Developer account.
4.  **Phase 2, on-device evaluation.** Apple Foundation Models and Android
    ML Kit GenAI behind the same provider interface. Starts after the first
    external tester build ships or a harness baseline is recorded (D40).
    Not detailed here.

Files by phase (a file split across phases lists the part):

| Phase | Files |
| --- | --- |
| 0 | 1–3, 5, 7, 9–14, 15 (`validate-config`, `record-fixture`, `sync-shared.sh`), 16–25, 27 (Gemini; kept and finished in 1.2), 41–45 (Net, provider types and shared code, Gemini), 49, 50 |
| 1.1 | 26, 31–35, 38 (startup and `DisabledScreen`), tests for these in 39 |
| 1.2 | 28, 29, 30 (consent), 37, 38 (setup, home, consent sheet), tests in 39 |
| 1.3 | 27 (Anthropic, OpenAI), 4, 40 |
| 1.4 | 6, 8, 15 (`publish-remote-config`, `purge-reports`), 30 (report), 36, 38 (Settings, report sheet, wide layout), 48 and 51–54 (Android parts) |
| 1.a | 45 (everything not built in Phase 0), 46, 47, the iOS parts of 48 and 52–54; `assets/` deleted (file 1) once 22 and 46 hold the final icons |

Decisions made during drafting (Open Questions Q6–Q10, Q12):

- **Q6 report retention:** free Spark plan; old reports are removed with a
  manual script that deletes by `createdAt` plus `REPORT_RETENTION_DAYS`
  (reports carry no `expireAt`: Phase 0 found the SDK cannot write a server
  timestamp plus an offset).
- **Q7 report writes:** create-only rules, no App Check, no sign-in.
- **Q8 backup:** all app files excluded from iOS and Android backup; no
  restore handling.
- **Q9 config failure:** no in-app values; a failed or invalid fetch shows
  the config-unavailable screen.
- **Q10 crashes before opt-in:** no special handling.
- **Q12 photo display:** original aspect ratio, fitted whole, black around
  it. Controls use safe-area insets until the author writes the rest of the
  full-screen decision.

Author decisions after review (2026-11-15): one generic error message and no
retries (G12); tuning values in code (§8a); restart instead of Try again
(AR2); kill switch at cold start only (G18). Author decisions, 2026-11-17:
two native apps with no shared code module; thin provider clients (G11,
Q1). Decided at the implementation review (2026-11-17): Q4 and Q13 (§5).

### Target Files to Create/Modify

Paths below are relative to the repository root. Android Kotlin packages
are under `com.yk.photocritic`; `android/core/.../net/` means
`android/core/src/main/kotlin/com/yk/photocritic/core/net/`, and
`android/app/.../ui/` means
`android/app/src/main/kotlin/com/yk/photocritic/ui/`. In general,
`android/<module>/.../<package>/` means
`android/<module>/src/main/kotlin/com/yk/photocritic/<module package>/<package>/`,
where the module package is `core` for `:core` and empty for `:app`. iOS
`Core/...` means `ios/Core/Sources/PhotoCriticCore/...`.

#### Repository

##### 1. Expo stack (Delete)

- **Type / Scope**: Removal.
- **Proposed Changes**: Delete `package.json`, `package-lock.json`,
  `node_modules/`, `app.config.ts`, `eas.json`, `tsconfig.json`,
  `eslint.config.js`, `jest.config.js`, `test/`, `src/`, `spikes/`. Before
  deleting `src/`, create `shared/tuning.json` from
  `src/platform/config/tuning.ts` (file 10). Move `firebase/dev/` and
  `firebase/prod/` service files into the file 18 and file 42 locations;
  move `firestore.rules`, `firestore.indexes.json`, `firebase.json` and
  `.firebaserc` into `firebase/` (files 5–7). Rewrite `docs/spikes/phase0.md`
  (file 50) in the same commit. `assets/` stays as the icon source until
  Phase 1.a, when files 22 and 46 hold the final icons; it is deleted then.
  **Why:** the React Native stack is replaced (author decision,
  2026-11-17).

##### 2. `.gitignore` (Modify)

- **Type / Scope**: Ignore rules.
- **Proposed Changes**: Remove `/ios`, `/android`, the root `node_modules/`,
  `.expo/`, `dist/`, `web-build/`, `expo-env.d.ts`, `.metro-health-check*`
  and `*.tsbuildinfo`. Keep `.DS_Store`, `*.p8`, `*.p12`, `*.key`, `*.pem`,
  `*.mobileprovision`, `*.jks`, `*.keystore`, `.env*`, `!.env.example`,
  `firebase-debug.log`, `firestore-debug.log`, `*-service-account.json`,
  `/TokenEconomizer/`, `/research/raw/`. Add `.gradle/`, `build/`,
  `local.properties`, `.idea/`, `*.iml`, `xcuserdata/`, `DerivedData/`,
  `ios/*.xcodeproj` (XcodeGen generates it), `tools/node_modules/`,
  `firebase/rules-test/node_modules/`, `/harness/results/`,
  `android/keystore.properties`, `ios/Config/Signing.xcconfig`.

##### 3. `.github/workflows/ci.yml` (Modify)

- **Type / Scope**: CI pipeline.
- **Proposed Changes**: Three jobs on every push.
  - `android` (Ubuntu, JDK 17, Gradle cache, `working-directory: android`):
    `./gradlew ktlintCheck detekt :core:test :core:koverVerify
    :app:testDevDebugUnitTest :app:assembleDevDebug :app:checkPermissions`.
    The Compose UI tests run inside `testDevDebugUnitTest` (Robolectric).
  - `ios` (macOS): install XcodeGen and SwiftLint from their release
    binaries at the versions in `docs/VERSIONS.md`; `xcodegen` in `ios/`;
    `swiftlint --strict`; `diff -r shared/prompts
    ios/Core/Sources/PhotoCriticCore/Resources/prompts`; `swift test
    --enable-code-coverage` in `ios/Core` with an 80% line-coverage check
    from `llvm-cov report`; `xcodebuild test -project
    ios/PhotoCritic.xcodeproj -scheme PhotoCritic-Dev -destination
    "$(ios/scripts/sim-destination.sh)"`, where the script picks the newest
    available iPhone simulator.
  - `shared` (Ubuntu, Node, JDK 17 for the emulator): `npm ci` and `npm run
    config:validate` in `tools/`; from Phase 1.4, `npm ci` and `npm run
    test:emulator` in `firebase/rules-test/`.
  No provider keys in CI except for file 4. **Why:** lint, unit, contract
  and build checks run for both apps on every push; the manual checks are in
  the Verification Plan.

##### 4. `.github/workflows/canary.yml` (Create, Phase 1.3)

- **Type / Scope**: Scheduled CI job.
- **Proposed Changes**: Daily at 05:00 UTC: `./gradlew :harness:run
  --args="canary"` (working directory `android`) makes one live call per
  provider and per model with `status` `active` in
  `shared/remote-config/template.json`, sending `shared/images/test.jpg`
  through the Kotlin clients, with spend-capped keys from the GitHub Actions
  secrets `PC_CANARY_OPENAI_KEY`, `PC_CANARY_ANTHROPIC_KEY` and
  `PC_CANARY_GEMINI_KEY`. It fails on any error event or a served-model
  mismatch (the served model must equal the requested ID or start with it,
  to allow dated snapshots). The Swift clients are covered by the shared
  recordings and the live tests in file 47. **Why:** providers change models
  and errors without notice (round-2 findings).

#### Firebase

##### 5. `firebase/firebase.json`, `firebase/.firebaserc` (Move, Modify)

- **Type / Scope**: Firebase CLI configuration.
- **Proposed Changes**: Projects `photocritic-dev` and `photocritic-prod`;
  Firestore rules and indexes paths; emulator port. The `react-native`
  section is removed: collection defaults now live in files 17 and 43.

##### 6. `firebase/firestore.rules` (Move, Modify in Phase 1.4)

- **Type / Scope**: Firestore security rules.
- **Proposed Changes**: `reports/{reportId}`: allow `create` only, when the
  document ID equals `reportId`, `createdAt == request.time`, every field
  matches the file 30 table (type and
  size limit), `conversation` is a list of at most `HISTORY_TURNS_CAP × 2 +
  2` items (a literal; rules cannot read app code or Remote Config, and
  cannot check the items inside a list, AR3), `category` is one of the four
  values, no field outside the table, and no image field. Deny read, update,
  delete and list everywhere. Deny-all until Phase 1.4. The header comment
  names `shared/tuning.json` as the source of the literals. **Why:** the app
  has no sign-in and no App Check, so the rules are the only write
  protection (G15).

##### 7. `firebase/firestore.indexes.json` (Move)

- **Type / Scope**: Firestore index file.
- **Proposed Changes**: Empty index list (no queries from the apps); kept so
  the CLI deploy is reproducible.

##### 8. `firebase/rules-test/package.json`, `firebase/rules-test/reports.rules.test.ts` (Create, Phase 1.4)

- **Type / Scope**: Firestore emulator tests (Node).
- **Proposed Changes**: Dependencies `jest`, `ts-jest`,
  `@firebase/rules-unit-testing` 5.0.2, `firebase-tools` 15.36.0. Script
  `test:emulator`: `firebase emulators:exec --only firestore --project
  demo-photocritic "jest"`. Tests: valid create allowed; read, update,
  delete and list denied; missing or extra fields, wrong types, each string
  over its limit, a `conversation` list longer than the limit, wrong
  `createdAt`, and any image field rejected. A
  test reads `shared/tuning.json` and fails if the literals in the rules
  differ (AR3).

#### Shared data (`shared/`)

##### 9. `shared/README.md`, `shared/fixture.schema.json`, `shared/images/test.jpg` (Create)

- **Type / Scope**: Format description, fixture schema, test image.
- **Proposed Changes**: The README describes every file under `shared/` and
  the rule that a change there is followed by both apps in the same pull
  request (until Phase 1.a, iOS only for the parts it already has). `fixture.schema.json` is the JSON Schema for the files in file 13.
  Expected events are written as `{"type": "text", "delta": "…"}`,
  `{"type": "done", "finishReason": "stop" | "length", "servedModel": "…",
  "requestId": "…" | null}` and `{"type": "error", "error": {"phase":
  "before-response" | "mid-stream" | "finish", "httpStatus": int | null,
  "providerCode": string | null, "requestId": string | null}}`; `detail` is
  not compared, only checked to contain no key. `test.jpg` is a 1024 px,
  author-owned photo of printed text with no metadata, used by the request
  fixtures, the canary and the image-pipeline tests.

##### 10. `shared/tuning.json` (Create)

- **Type / Scope**: Data.
- **Proposed Changes**: Created in the teardown commit (file 1) from
  `src/platform/config/tuning.ts` at commit `a1f4c49`. Format: `{"NAME":
  {"value": number, "unit": "ms" | "px" | "chars" | "turns" | "count" |
  "days" | "ratio", "status": "fixed" | "provisional"}}`. The author sets
  `MAX_QUESTION_CHARS`, `MAX_REPORT_CONVERSATION_CHARS`,
  `REPORT_DAILY_LIMIT_PER_INSTALL` and `REPORT_RETENTION_DAYS` before Phase
  1 ends; Phase 0 measurements set `HISTORY_TURNS_CAP`,
  `CONNECT_TIMEOUT_MS`, `REQUEST_TIMEOUT_MS` and `REPORT_WRITE_TIMEOUT_MS`.
  Both apps' constants (files 26 and 45) and the Firestore rules test (file
  8) are checked against it.

##### 11. `shared/prompts/system.txt`, `identify.txt`, `explain.txt`, `extract.txt`, `free.txt`, `VERSION` (Create)

- **Type / Scope**: Prompt text.
- **Proposed Changes**: The fixed base prompt (answer about the photo, plain
  text, wrap verbatim text from the photo in a fenced `text` block) and the
  four prompt-kind texts; `VERSION` is recorded by the harness and in
  reports (`promptVersion`). Android adds `shared/prompts/` to the `core`
  resources through a Gradle `sourceSets` entry. iOS keeps a committed copy
  in `ios/Core/Sources/PhotoCriticCore/Resources/prompts/`, written by
  `tools/sync-shared.sh`; CI fails if the copy differs (file 3). **Why:**
  both apps must send identical prompts, and Swift Package Manager accepts
  resources only inside the target folder.

##### 12. `shared/remote-config/schema.json`, `template.json`, `samples/valid/*.json`, `samples/invalid/*.json` (Create)

- **Type / Scope**: Remote Config schema, source of truth and test samples.
- **Proposed Changes**: JSON Schema for every parameter in §8.
  `template.json` and the samples are plain objects `{parameterName:
  parsedJsonValue}`; `publish-remote-config` converts them to the Remote
  Config REST template shape. `template.json` holds the v1 values (kill
  switch including `reportsDisabled`, providers with allowlisted `baseUrl`s,
  the curated model list with labels, `status`, `maxOutputTokens` and
  `contextTokens`, consent texts, privacy policy URL and version,
  `configVersion`). Invalid samples include a `baseUrl` outside the
  allowlist, a missing consent text, a `replacementId` that names no
  `active` model, a `providers.<p>.consentTextVersion` that differs from
  `consent_texts.<p>.version`, and wrong types. No tuning values and no
  error rules. **Why:** one validated source for every publish (G17), and
  one set of cases both validators must agree on.

##### 13. `shared/provider-streams/<provider>/*.json`, `shared/provider-errors/<provider>/*.json`, `shared/provider-requests/<provider>/*.json` and `*.input.json` (Create, Phase 0)

- **Type / Scope**: Recorded responses and expected requests (generated by
  `fixture:record`, then reviewed).
- **Proposed Changes**: Each fixture follows `shared/fixture.schema.json`:
  `recordedAt`, `provider`, `model`, the HTTP status, response headers (keys
  and request IDs replaced by placeholders), the raw server-sent-event lines
  or error body, and the expected events or `PcError` fields (file 9).
  Streams: normal answer, length limit, content filter or safety block,
  mid-stream error, refusal. Errors: invalid key, no billing or spend
  limit, rate limit (429), overloaded (Anthropic 529, OpenAI 503), unknown
  model, Gemini 403. Each request fixture has a `<name>.input.json`
  (`modelId`, `history`, `question`, `promptKind`, `maxOutputTokens`,
  `imageDetail`, `image`: `shared/images/test.jpg`) and the exact JSON body
  the client must produce, including OpenAI `store: false`. Bodies are
  compared as parsed JSON with key order ignored.

##### 14. `shared/redaction/cases.json`, `shared/token-estimates.json`, `shared/vectors/history-trim.json`, `extracted-text.json`, `app-version.json`, `report-document.json` (Create)

- **Type / Scope**: Test data for logic written in both apps.
- **Proposed Changes**: Redaction, in order: (1) the user's own key string,
  wherever it appears; (2) `sk-ant-[A-Za-z0-9_-]{16,}`,
  `sk-[A-Za-z0-9_-]{16,}`, `AIza[0-9A-Za-z_-]{35}`, `AQ\.[0-9A-Za-z_-]{20,}`
  (the Gemini key format seen on 2026-11-17) and `Bearer\s+\S+`, each
  replaced by `[key]`; (3) the query string of any URL replaced by
  `?[query]`; (4) `file://` and `content://` URIs and absolute paths with
  two or more segments replaced by `[path]`; then cut to 200 characters.
  `cases.json` holds inputs and expected outputs for each rule.
  `token-estimates.json`: per provider `charsPerToken` and the image-token
  rule (OpenAI tile rule parameters, Anthropic width × height ÷ 750, Gemini
  tokens per `mediaResolution`), with values set in Phase 0 from the
  `usage` fields of the recordings. The vectors: history trimming cases;
  extracted-text cases (unclosed fence, several cards, tilde fence, fence in
  the middle of a line); version comparisons (`1.10.0` above `1.9.0`); one
  report input and its expected document without the timestamps.

#### Author tools (`tools/`)

##### 15. `tools/package.json`, `validate-config.ts`, `publish-remote-config.ts`, `record-fixture.ts`, `purge-reports.ts`, `sync-shared.sh` (Create)

- **Type / Scope**: Node scripts, author-run (validation also in CI).
- **Proposed Changes**: Dependencies `typescript`, `tsx` 4.23.15, `ajv` 8,
  `firebase-admin`. Scripts: `config:validate` checks `template.json`
  against the schema with Ajv, plus cross-checks (every `baseUrl` in the
  allowlist, every `replacementId` names an `active` model, every provider
  has a consent text whose version matches `consentTextVersion`), and runs
  the samples. `config:publish -- --project dev|prod` validates, increments
  `configVersion`, sets `publishedAt` and publishes with the Remote Config
  REST API, authenticating with `GOOGLE_APPLICATION_CREDENTIALS` and sending
  the template ETag with `If-Match`. `fixture:record` calls a provider with
  a key from an environment variable, saves the raw response and replaces
  keys and request IDs before writing to `shared/`. `reports:purge`
  (Firebase Admin SDK) deletes reports whose `createdAt` is older than
  `REPORT_RETENTION_DAYS` (Q6).
  `sync-shared.sh` copies `shared/prompts/` into the iOS package (file 11).
  **Why:** with no in-app values, validation before publishing is the only
  protection against a bad publish (AR2).

#### Android (`android/`)

##### 16. `android/settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, Gradle wrapper (Create)

- **Type / Scope**: Build configuration.
- **Proposed Changes**: Modules `:core`, `:app`, `:harness`; every library
  and plugin version in the version catalog (pinned at Phase 0 and copied
  into `docs/VERSIONS.md`); `org.jlleitschuh.gradle.ktlint` and detekt
  applied to all modules; Kover on `:core` with `koverVerify` at 80% line
  coverage; JUnit 5 in `:core` and `:harness`, JUnit 4 in `:app`.

##### 17. `android/app/build.gradle.kts`, `android/app/src/main/AndroidManifest.xml`, `android/app/proguard-rules.pro`, `android/app/src/main/res/values/strings.xml` (Create)

- **Type / Scope**: Application module.
- **Proposed Changes**:
  - Build: `applicationId` and `namespace` `com.yk.photocritic`; minSdk 24;
    targetSdk the latest Play requires; flavors `dev` and `prod`; R8 and
    resource shrinking for release with `proguard-rules.pro`
    (kotlinx.serialization keep rules); Google services and Crashlytics
    plugins (mapping upload on release); Compose on;
    `signingConfigs.release` reads `android/keystore.properties` (ignored by
    git; it holds the path to the upload keystore outside the repository and
    its passwords); without that file, release builds are unsigned (CI).
  - Manifest: `INTERNET` and `ACCESS_NETWORK_STATE` declared by the app;
    `tools:node="remove"` for `com.google.android.gms.permission.AD_ID`,
    `android.permission.ACCESS_ADSERVICES_AD_ID`,
    `android.permission.ACCESS_ADSERVICES_ATTRIBUTION`,
    `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES`,
    `READ_MEDIA_VIDEO` and `RECORD_AUDIO`; no `CAMERA` permission (the
    camera app takes the photo); `allowBackup="false"`,
    `fullBackupContent="@xml/backup_rules"` and
    `dataExtractionRules="@xml/data_extraction_rules"`; a resizable
    activity; `FileProvider` for camera output; Firebase `<meta-data>`
    `firebase_analytics_collection_enabled`,
    `firebase_crashlytics_collection_enabled`,
    `google_analytics_adid_collection_enabled`,
    `google_analytics_ssaid_collection_enabled`,
    `google_analytics_default_allow_ad_storage`,
    `google_analytics_default_allow_ad_user_data`,
    `google_analytics_default_allow_ad_personalization_signals` and
    `google_analytics_automatic_screen_reporting_enabled` all false (names
    checked in Phase 0).
  - `checkPermissions` task: reads the merged `prodRelease` manifest and
    fails on any permission outside the allowlist `INTERNET`,
    `ACCESS_NETWORK_STATE`, `WAKE_LOCK`,
    `com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE`
    and `com.yk.photocritic.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. Phase
    0 confirms the list from the first merged manifest and records it in the
    task.
  - `strings.xml`: every user-visible string, so it can be translated later.
  **Why:** G1, G16, G20 and Q8 are set here.

##### 18. `android/app/src/dev/google-services.json`, `android/app/src/prod/google-services.json` (Move)

- **Type / Scope**: Firebase service files.
- **Proposed Changes**: From `firebase/dev/` and `firebase/prod/`; the
  flavor picks the project. **Why:** development data never mixes with
  tester data.

##### 19. `android/app/src/main/res/xml/data_extraction_rules.xml`, `backup_rules.xml`, `file_paths.xml` (Create)

- **Type / Scope**: Resources.
- **Proposed Changes**: Exclude every domain from cloud backup and device
  transfer (Q8); `FileProvider` paths limited to the camera folder in the
  cache.

##### 20. `android/config/detekt.yml` (Create)

- **Type / Scope**: Lint rules.
- **Proposed Changes**: `ForbiddenImport`: `com.google.firebase.analytics`
  and `com.google.firebase.crashlytics` outside `platform.telemetry`;
  `okhttp3` outside `core.net` (`ProviderHttp` gives providers its own
  request and response types). `ForbiddenMethodCall`: `kotlin.io.println`
  and `android.util.Log.*` outside `platform.telemetry`. **Why:** layering
  and redaction rules (§2, G16).

##### 21. `android/core/build.gradle.kts` (Create)

- **Type / Scope**: Pure Kotlin JVM module.
- **Proposed Changes**: Kotlin JVM, kotlinx.coroutines, kotlinx.serialization,
  OkHttp; `shared/prompts/` as resources; tests read `shared/` fixtures
  from the repository path. No Android or Firebase dependency. **Why:** the
  core builds and tests on the JVM, and the harness and canary reuse it.

##### 22. `android/app/src/main/res/` icons and theme (Create)

- **Type / Scope**: Resources.
- **Proposed Changes**: Adaptive launcher icon (foreground, background,
  monochrome) from the aperture-with-speech-tail design; Lucide icons as
  vector drawables (Camera, Photos, NewPhoto, Send, Stop, Settings, Copy,
  Share, Report, Back); dark theme; splash screen with `#0F1012`.

##### 23. `android/core/.../net/Allowlist.kt`, `ProviderHttp.kt`, `Sse.kt` (Create)

- **Type / Scope**: Network client.
- **Proposed Changes**: The host allowlist constant; one `OkHttpClient`
  built as in §4 (allowlist interceptor, no redirects, no retries, connect
  and call timeouts, no cache, no cookie jar); a function that takes a
  request and the provider's request-ID header name and returns the status,
  headers, request ID and a line stream, using its own request and response
  types; the server-sent-events parser. **Why:** every security rule in §4
  lives in one package (`core.net`), behind one `OkHttpClient`.

##### 24. `android/core/.../providers/Types.kt`, `Registry.kt` (Create)

- **Type / Scope**: Types and lookup.
- **Proposed Changes**: `Provider`, `Capabilities`, `TurnRequest`,
  `TurnEvent`, `KeyCheckResult`, `PcError` (`phase`, `httpStatus`,
  `providerCode`, `requestId`, `detail`), as in §3 and §7. `Registry`
  returns the client for a provider ID and the capabilities for a model by
  merging client defaults with the model's Remote Config entry.

##### 25. `android/core/.../providers/shared/SystemPrompt.kt`, `HistoryTrim.kt`, `ExtractedText.kt`, `ToPcError.kt`, `Redact.kt`, `UserMessages.kt` (Create)

- **Type / Scope**: Shared provider logic.
- **Proposed Changes**: `SystemPrompt` loads file 11. `HistoryTrim`
  estimates tokens per turn with the factors and image-token rules in
  `shared/token-estimates.json` and drops the oldest turns until the request
  fits `contextTokens` minus `maxOutputTokens`, never exceeding
  `HISTORY_TURNS_CAP`. `ExtractedText` splits answer text into `{kind: text
  | card, value}` segments; an unclosed fence stays plain text. `ToPcError`
  turns a non-2xx response, a stream error event or a failing finish reason
  into a `PcError` without classifying it. `Redact` applies the rules in
  file 14. `UserMessages` holds the one generic message, the "Show details"
  labels ("Where", "HTTP status", "Provider code", "Request ID",
  "Details") and the `phase` strings ("Before the answer", "During the
  answer", "At the end of the answer"). Each reads its `shared/vectors/`
  file in tests.

##### 26. `android/core/.../config/ConfigModels.kt`, `ConfigValidator.kt`, `Snapshot.kt`, `KillSwitch.kt`, `Tuning.kt`, `AppVersion.kt` (Create)

- **Type / Scope**: Config logic.
- **Proposed Changes**: Typed models for §8; a validator that returns either
  a snapshot or `Unavailable` with the failing parameter (any invalid value,
  including a `baseUrl` outside the allowlist); the kill-switch evaluation,
  run once at cold start; the §8a constants; semantic version comparison.
  Display rules:

  | Item | Rule |
  | --- | --- |
  | `status` | `active`: shown. `preview`: shown with the label "Preview". `deprecated`: shown with "Ending soon" and "Replaced by <displayName>" from `replacementId`. `retired`: hidden. |
  | Hidden model | `retired`, listed in `disabledModels`, `minAppVersion` above the app version, or its provider disabled |
  | Disabled provider | `providers.<p>.enabled` is false, or listed in `disabledProviders` |
  | Saved model or provider hidden | The G18 path: setup opens with the kill-switch message |
  | Kill-switch message on setup | `kill_switch.message` if not empty, otherwise "The model you used is no longer available. Pick another one." |
  | `bannerMessage` | If not empty, a banner under the home top bar, dismissible for the session |
  | Update required | Shown when the app version is below `minSupportedAppVersion`; the Update button opens `updateUrl` |
  | Consent version | `consent_texts.<p>.version`; must equal `providers.<p>.consentTextVersion` |
  | `privacy_policy.version` | Shown in Settings next to the bundled privacy policy |

##### 27. `android/core/.../providers/gemini/GeminiClient.kt`, `anthropic/AnthropicClient.kt`, `openai/OpenAiClient.kt`, each with `<Provider>Wire.kt` (for example `GeminiWire.kt`) (Create; Gemini in Phase 0 and 1.2, others in Phase 1.3)

- **Type / Scope**: Provider clients.
- **Proposed Changes**: Each implements `Provider` with the request format,
  key header, image placement, `maxOutputTokens` field and end events in
  §3a, sends through file 23 with its request-ID header name, maps events
  to `TurnEvent`s, reports the served model and request ID (Gemini: body
  `responseId`), and ignores unknown events and fields. OpenAI always sends
  `store: false`; its `response.refusal.delta` text is collected, and a
  response that ends with refusal content is a `finish` failure with
  `providerCode` `refusal`, keeping the text as the Failed answer. No
  sampling parameters for Gemini. `checkKey`: model list for OpenAI and
  Anthropic; minimal inference call for Gemini. The wire file holds the
  kotlinx.serialization request and event classes, which stay inside the
  client's package.

##### 28. `android/core/.../providers/fake/FakeProvider.kt` (Create)

- **Type / Scope**: Test provider.
- **Proposed Changes**: Scripted answers, errors and delays; capability set
  `final-turn-only`, `streaming: whole`, no usage, `dataLeavesDevice =
  true` (so the consent flow runs in tests), to test that the screens handle
  a final-turn-only, whole-response provider with no usage data before
  Phase 2. Used only by tests and the `devDebug` fake container (file 37).

##### 29. `android/core/.../session/SessionTypes.kt`, `SessionMachine.kt` (Create)

- **Type / Scope**: Session logic.
- **Proposed Changes**: `SessionState` (the states in §5), `VisibleMessage`
  (ID, question, answer text, segments, status: streaming, done, stopped,
  failed; `error: PcError?`), `NeutralTurn`. A pure reducer for every arrow
  in the §5 diagram and the §5 rules (image error, failed consent write,
  lifecycle), and a controller that runs `Provider.send` in a coroutine:
  text deltas extend the streaming message; `done` adds the turn to
  history; Stop cancels the coroutine and marks the message stopped without
  adding it to history; an `error` event marks it failed and keeps its
  text. There is no retry action (G12). The consent gate (file 30) is
  checked before `sending`.

##### 30. `android/core/.../consent/ConsentRules.kt`, `report/ReportBuilder.kt`, `report/ReportLimit.kt` (Create)

- **Type / Scope**: Consent and report logic.
- **Proposed Changes**:
  - `canSend(provider)` is true when the provider's capabilities say data
    stays on the device, or when there is a granted record at or above
    `consent_texts.<p>.version`. "Not now" is not recorded. Consent history
    keeps the current record and up to 10 earlier ones.
  - `ReportBuilder` builds the report document (§10) with these fields:

    | Field | Type | Limit |
    | --- | --- | --- |
    | `reportId` | string, equals the document ID | 36 (UUID) |
    | `createdAt` | timestamp | set by `ReportService`; rules: `== request.time` |
    | `appVersion`, `osVersion` | string | 32 each |
    | `platform` | `android` or `ios` | |
    | `locale` | string | 35 |
    | `configVersion` | int | |
    | `promptVersion` | string | 16 |
    | `provider` | `openai`, `anthropic` or `gemini` | |
    | `requestedModel` | string | 100 |
    | `servedModel` | string, optional | 100 |
    | `providerRequestId` | string, optional | 200 |
    | `imageSettings` | map: `longEdgePx` int, `detail` string (16), `bytes` int | |
    | `conversation` | list of `{role: user or assistant, text}` | at most `HISTORY_TURNS_CAP × 2 + 2` items |
    | `reportedTurnIndex` | int | |
    | `category` | `offensive`, `wrong`, `unsafe` or `other` | |
    | `reason` | string | 1,000 |
    | `error` | optional map: `phase` (enum), `httpStatus` (int or null), `providerCode` (100), `requestId` (200), `detail` (200) | |
    | `instanceId` | string, optional, only when sharing is on | 64 |

    No image field. The conversation holds the reported turn and the turns
    before it: the oldest whole turns are dropped until both the item limit
    and `MAX_REPORT_CONVERSATION_CHARS` hold; if the reported turn alone is
    too long, its answer is cut at the end. Stopped and failed turns are
    included with their label at the start of the text. Tested against
    `shared/vectors/report-document.json`.
  - `ReportLimit` counts Send report attempts per UTC day against
    `REPORT_DAILY_LIMIT_PER_INSTALL`.

##### 31. `android/app/.../platform/storage/SecureKeys.kt`, `InstallMarker.kt`, `JsonFiles.kt`, `SettingsStore.kt`, `ConsentStore.kt`, `ReportStore.kt` (Create)

- **Type / Scope**: Storage.
- **Proposed Changes**: `SecureKeys`: one AES-GCM Keystore key
  (non-exportable), one encrypted file per provider in `noBackupFilesDir`;
  `get`, `set`, `delete`. `InstallMarker`: if `install.json` is missing
  (not unreadable), delete the key files and the Keystore key, then write
  the marker. `JsonFiles`: `AtomicFile` reads and writes with a per-file
  mutex, defaults on a corrupt or missing file. The three stores read and
  write the files in §9; a failed consent write blocks the send.

##### 32. `android/app/.../platform/config/RemoteConfigLoader.kt` (Create)

- **Type / Scope**: Firebase Remote Config.
- **Proposed Changes**: The load order in §8: fetch and activate with a
  minimum fetch interval of 0 and `CONFIG_FETCH_TIMEOUT_MS`; on
  `FirebaseRemoteConfigFetchThrottledException`, use the last activated
  values if any; pass the values to file 26. No `addOnConfigUpdateListener`.

##### 33. `android/app/.../platform/telemetry/Telemetry.kt`, `Events.kt`, and `shared/telemetry/events.json` (Create)

- **Type / Scope**: Analytics and Crashlytics wrapper and the shared event
  list.
- **Proposed Changes**: `shared/telemetry/events.json` lists every event,
  its parameters with types, and its trigger; both apps' `Events` are
  tested against it:

  | Event | Parameters | Sent when |
  | --- | --- | --- |
  | `pc_session_start` | `provider`, `model`, `config_version` | home first shows after a cold start |
  | `pc_image_prepared` | `source`, `input_format`, `prepare_ms`, `sent_long_edge_px`, `sent_bytes` | a send copy is built |
  | `pc_question_sent` | `provider`, `model`, `turn_index`, `starter_prompt` | Send |
  | `pc_answer_received` | `provider`, `model`, `time_to_first_token_ms`, `total_ms`, `input_tokens`, `output_tokens`, `finish_reason` | a `done` event |
  | `pc_request_failed` | `provider`, `model`, `phase`, `http_status` | an `error` event |
  | `pc_report_submitted` | `provider`, `model`, `category`, `result` (`sent`, `failed`, `limit_reached`) | a report attempt ends |
  | `pc_config_fetch` | `result` (`success`, `throttled`, `failed`, `timeout`, `invalid`), `config_version`, `ms` | the startup fetch ends |

  Brief K.5's `error_class`, `retry_count`, `queued`, `consent_recorded`
  and `cached_tokens` are not used (G12, §10; caching is Q5). `Telemetry`:
  `setSharing(on)` turns collection on or off in both SDKs and sets or
  clears the instance-ID user ID; `instanceIdOrNull()` serves
  `ReportService` and Settings; `log(event, params)` accepts only the listed
  events and drops everything while sharing is off; values pass through
  `Redact` first; an uncaught-exception path redacts before Crashlytics. No
  special handling of crash reports from before opt-in (Q10).

##### 34. `android/app/.../platform/net/Connectivity.kt` (Create)

- **Type / Scope**: Connectivity state.
- **Proposed Changes**: A `StateFlow<Boolean>` from a
  `ConnectivityManager` default-network callback, used by the offline
  banner, the Send button and Send report.

##### 35. `android/app/.../features/image/PhotoRecord.kt`, `Pick.kt`, `ImagePipeline.kt`, `Cleanup.kt` (Create)

- **Type / Scope**: Image feature.
- **Proposed Changes**: `PhotoRecord {originalUri, edits: [], imageVersionId,
  sendCopy?: {file, bytes, width, height, longEdgePx, detail}}`; the send
  copy is rebuilt when the model's target size differs. `Pick`: Photo
  Picker and `TakePicture` as in §6, with the camera URI in
  `SavedStateHandle`. `ImagePipeline`: decode with downsampling, apply
  orientation, encode JPEG at `JPEG_QUALITY`; if over `maxBytes`,
  re-encode once at quality 0.6; if still over, `image-error`. Animated
  images use their first frame. HEIC below API 28 and AVIF below API 31
  give `image-error` (Q2). Records preparation time. `Cleanup`: delete send
  copies and camera files when a conversation ends and at launch.

##### 36. `android/app/.../features/report/ReportService.kt` (Create)

- **Type / Scope**: Firestore write.
- **Proposed Changes**: Sets `MemoryCacheSettings` before first use; takes
  the file 30 document, adds `createdAt` as the Firestore server timestamp
  (no `expireAt`: Phase 0 found `FieldValue` cannot add an offset to a
  server timestamp, so `reports:purge` deletes by `createdAt` plus
  `REPORT_RETENTION_DAYS`), and writes it under a new
  random UUID for each attempt. Returns sent, failed (not confirmed within
  `REPORT_WRITE_TIMEOUT_MS`) or limit reached. A write shown as failed may
  still arrive later, so a second attempt can create a duplicate report;
  this is accepted. Not offered when `kill_switch.reportsDisabled` is true.
  `instanceId` comes from `Telemetry.instanceIdOrNull()`.

##### 37. `android/app/.../features/session/SessionViewModel.kt`, `android/app/.../AppContainer.kt`, `android/app/src/devDebug/kotlin/com/yk/photocritic/FakeContainer.kt` (Create)

- **Type / Scope**: ViewModel and dependency wiring.
- **Proposed Changes**: `AppContainer` is created in a custom `Application`
  and holds the interfaces in the Assumptions. `SessionViewModel` gets it
  through a `viewModelFactory`, owns a file 29 controller, exposes
  `StateFlow` state, and connects consent, image and connectivity. When the
  ViewModel is cleared, the conversation ends. `FakeContainer` (compiled
  only in `devDebug`) supplies `FakeProvider` and fakes for Remote Config,
  key storage and reports; a debug setting switches to it. UI tests build
  `AppContainer` with fakes directly.

##### 38. `android/app/.../ui/MainActivity.kt`, `AppNavigation.kt`, `SetupScreen.kt`, `HomeScreen.kt`, `SettingsScreen.kt`, `DisabledScreen.kt`, `PhotoView.kt`, `ConversationSheet.kt`, `ConversationPane.kt`, `MessageList.kt`, `MessageItem.kt`, `Composer.kt`, `StarterChips.kt`, `ConsentSheet.kt`, `ReportSheet.kt`, `design/Tokens.kt`, `design/Components.kt`, `design/LayoutMode.kt` (Create)

- **Type / Scope**: Screens and components.
- **Proposed Changes**:
  - `MainActivity`, `AppNavigation`: startup in order: install marker and
    key wipe; settings; config fetch and validation (up to
    `CONFIG_FETCH_TIMEOUT_MS`, behind the splash screen); kill switch once;
    telemetry if opted in. Routes to disabled, setup (no provider, model and
    key, or the saved one is hidden; file 26) or home.
  - `DisabledScreen`: config unavailable, app disabled, update required
    (Update opens `updateUrl`); no Try again button.
  - `SetupScreen`: provider buttons; model list with labels by the file 26
    rules; key field (password input) with the bring-your-own-key
    explanation, the key-help link and, for Gemini, "Checking a Gemini key
    uses a few tokens."; states idle, checking, failed (generic message and
    "Show details"), saved; the kill-switch message when opened by G18; the
    sharing switch in first-run mode; Back and the conversation-clearing
    confirmation in edit mode.
  - `HomeScreen`, `PhotoView`, `ConversationSheet`, `ConversationPane`:
    phone layout (photo plus `BottomSheetScaffold`) or wide layout (two
    panes) from `LayoutMode`; photo at its original aspect ratio, fitted
    whole, black around it, pinch to zoom; empty state with Take photo and
    Choose from Photos; top bar with Settings and New photo only; the
    `bannerMessage` banner; hosts the consent sheet, report sheet, new-photo
    dialog, offline banner and snackbars.
  - `MessageList`, `MessageItem`: plain text, text cards with Copy and
    Share, Stopped or Failed tag, the generic error with "Show details",
    Copy answer and Report once done.
  - `Composer`, `StarterChips`: multi-line input limited to
    `MAX_QUESTION_CHARS`, Send or Stop by state, disabled without a photo
    or offline; Identify, Explain, Copy text, hidden while streaming.
  - `ConsentSheet`: provider name, `summary[]` bullets, `effectiveDate`,
    the provider's `policyUrl`, a link to the bundled privacy policy, "Not now" and "Agree and send". `ReportSheet`: category
    chips, note field (1,000 characters), "The photo itself is not sent",
    what is sent, result and a copyable report ID.
  - `SettingsScreen`: provider and model rows (open setup in edit mode), key
    row (replace or remove), sharing switch, privacy policy (the bundled
    page, file 51) with `privacy_policy.version`, support ID when sharing is on, app version and
    flavor.
  - `design/`: tokens from §12; components; `LayoutMode`: at start, wide if
    the width is at least 700 dp, else phone; afterwards it switches to wide
    above 720 dp and to phone below 680 dp, and keeps the current mode in
    between.
  - Every control has a content description and a role for TalkBack; text
    uses sp; strings come from `strings.xml`.

##### 39. Android tests (Create)

- **Type / Scope**: Unit, contract and screen tests.
- **Proposed Changes**: Named by the mirror rule in the Assumptions.
  - `core` (JVM): `SseTest`; `AllowlistTest`; `ProviderHttpTest`
    (`MockWebServer`: allowlist refusal, redirect refusal, exactly one
    request on errors, timeouts, key only in its header);
    `ProviderContractTest` (every client against every `shared/provider-*`
    fixture: events, `PcError` fields, request body equal as parsed JSON,
    served model, request ID, cancellation stops the stream, no key in
    `detail`); `HistoryTrimTest`, `ExtractedTextTest`, `AppVersionTest`,
    `RedactTest`, `ReportBuilderTest` (their `shared/` files);
    `ToPcErrorTest`; `ConfigValidatorTest` (file 12 samples);
    `KillSwitchTest` (file 26 rules); `RegistryTest`; `TuningTest` (file
    10); `SessionMachineTest` (every arrow in §5 and the §5 rules; stopped
    and failed answers excluded from history; a failed answer keeps its
    text); `ConsentRulesTest`; `ReportLimitTest`; `EventsTest` (against
    `shared/telemetry/events.json`).
  - `app` (Robolectric): `JsonFilesTest` (interrupted write, corrupt file),
    `InstallMarkerTest` (missing versus unreadable marker, upgrade without
    wipe), `RemoteConfigLoaderTest` (throttled path, timeout, fake Remote
    Config), `TelemetryTest` (nothing logged while off, user ID cleared),
    `ReportServiceTest` (memory cache, unconfirmed write shown as failed),
    `ImagePipelineTest` (orientation, no EXIF in the output, size cap and
    re-encode, HEIC and AVIF rejected by API level), `CleanupTest`.
  - `SecureKeysTest` is an instrumented test run by hand on an API 24 phone
    and a current phone each phase, because Robolectric has no Keystore.
  - Compose UI tests in `app/src/test` on Robolectric, inside
    `testDevDebugUnitTest`, with the fake container: CUJs 1–9 plus consent
    once per provider (first launch, text card copy, Stop, the generic
    error and its details, new-photo confirmation, report flow, kill switch
    at start, layout switch at the width margin, config-unavailable
    screen). There is no emulator job in v1.

##### 40. `android/harness/build.gradle.kts`, `android/harness/src/main/kotlin/com/yk/photocritic/harness/Main.kt`, `Run.kt`, `Canary.kt`, `Score.kt`; data in `harness/datasets/` (Create)

- **Type / Scope**: JVM command-line tool using `:core`, and its data.
- **Proposed Changes**: `run` (the default) sends a labelled photo set
  through the Kotlin clients at long edges 1024, 1536, 2048 and 3072 px and
  settings OpenAI `low`/`high`, Gemini `MEDIA_RESOLUTION_LOW`/`MEDIUM`/
  `HIGH`, Anthropic none. Photos are in `harness/datasets/<set>/images/`
  (JPEG or PNG only) with `truth.json` (`{file: expectedText}`); a set is
  committed only if the author owns the photos and they show no personal
  data, otherwise `PC_HARNESS_DATASET` points to a folder outside git. The
  harness downsizes with `javax.imageio` (bicubic) and encodes JPEG at
  `JPEG_QUALITY` with no metadata; Phase 0 compares its output bytes with
  the Android pipeline for five photos and records the difference. One CSV
  per run goes to `harness/results/` (ignored by git), with columns
  `run_id, provider, requested_model, served_model, prompt_version, image,
  long_edge_px, detail, bytes, ttft_ms, total_ms, input_tokens,
  output_tokens, finish_reason, error_phase, http_status, cer` (character
  error rate against the truth). `canary` is the file 4 job. Keys come from
  environment variables. **Why:** G23, the primary measure, and the data
  for the image-size decision (Q5). One harness measures the models; the
  shared request fixtures show both apps send the same request bodies.
  Differences between the Android and iOS JPEG encoders are not measured.

#### iOS (`ios/`)

##### 41. `ios/project.yml`, `ios/scripts/sim-destination.sh` (Create)

- **Type / Scope**: XcodeGen project specification and CI helper.
- **Proposed Changes**: Targets `PhotoCritic` (app), `PhotoCriticTests`,
  `PhotoCriticUITests`; deployment target iOS 17.0; Swift 6 language mode;
  configurations `Debug-Dev`, `Release-Dev`, `Release-Prod`, each copying the
  matching `GoogleService-Info.plist` (file 42) in a build phase; scheme
  `PhotoCritic-Dev` (`Debug-Dev`, both test targets) and scheme
  `PhotoCritic-Prod` (`Release-Prod`); the local `Core` package and the
  Firebase Apple SDK package (Remote Config, Analytics without the
  advertising-ID product, Crashlytics, Firestore), versions pinned;
  Crashlytics dSYM upload run script; iPhone and iPad, all orientations,
  `UIRequiresFullScreen` false; automatic signing with `DEVELOPMENT_TEAM`
  from `ios/Config/Signing.xcconfig` (ignored by git). The generated
  `.xcodeproj` is not committed. `sim-destination.sh` prints the
  `-destination` value for the newest available iPhone simulator.

##### 42. `ios/Config/Dev/GoogleService-Info.plist`, `ios/Config/Prod/GoogleService-Info.plist` (Move)

- **Type / Scope**: Firebase service files.
- **Proposed Changes**: From `firebase/dev/` and `firebase/prod/`; selected
  by build configuration.

##### 43. `ios/PhotoCritic/Info.plist`, `PrivacyInfo.xcprivacy` (Create)

- **Type / Scope**: App configuration.
- **Proposed Changes**: `NSCameraUsageDescription`; no photo-library usage
  string (PhotosPicker needs none); `UIUserInterfaceStyle` Dark;
  `ITSAppUsesNonExemptEncryption` NO; Firebase keys
  `FIREBASE_ANALYTICS_COLLECTION_ENABLED`,
  `FirebaseCrashlyticsCollectionEnabled`,
  `GOOGLE_ANALYTICS_IDFV_COLLECTION_ENABLED`,
  `GOOGLE_ANALYTICS_DEFAULT_ALLOW_AD_STORAGE`,
  `GOOGLE_ANALYTICS_DEFAULT_ALLOW_AD_USER_DATA`,
  `GOOGLE_ANALYTICS_DEFAULT_ALLOW_AD_PERSONALIZATION_SIGNALS` and
  `FirebaseAutomaticScreenReportingEnabled` all off (names checked in Phase
  0). The privacy manifest: `NSPrivacyTracking` false; required-reason API
  use for file timestamps only (the app keeps its data in files, not
  `UserDefaults`); collected data types as in `docs/store-forms.md`.
  **Why:** G1, G16 and App Store privacy rules are set here.

##### 44. `ios/.swiftlint.yml` (Create)

- **Type / Scope**: Lint rules.
- **Proposed Changes**: Custom rules with `included` paths
  `ios/Core/Sources/PhotoCriticCore/` and `ios/PhotoCritic/`:
  `import FirebaseAnalytics` and `import FirebaseCrashlytics` are errors
  except under `ios/PhotoCritic/Platform/Telemetry/`; `URLSession(` is an
  error except under `ios/Core/Sources/PhotoCriticCore/Net/`; `print(` and
  `NSLog(` are errors except under `ios/PhotoCritic/Platform/Telemetry/`.
  **Why:** layering and redaction rules (§2, G16).

##### 45. `ios/Core/Package.swift` and `ios/Core/Sources/PhotoCriticCore/` (Create)

- **Type / Scope**: Local Swift package, Foundation only.
- **Proposed Changes**: `// swift-tools-version: 6.0`; `platforms:
  [.iOS(.v17), .macOS(.v14)]` (so `swift test` runs on the CI Mac); Swift 6
  language mode; targets `PhotoCriticCore` (resources:
  `Resources/prompts`), `PhotoCriticCoreTests`, `PhotoCriticCoreLiveTests`.
  The same files and behavior as Android files 23–30, in Swift:
  `Net/Allowlist.swift`, `Net/ProviderHTTP.swift` (an ephemeral
  `URLSession` with a delegate that refuses redirects, `bytes(for:)` for
  streaming, timeouts from the constants, the request-ID header name passed
  in), `Net/SSE.swift`; `Providers/Types.swift`, `Providers/Registry.swift`,
  `Providers/Shared/` (`SystemPrompt.swift`, `HistoryTrim.swift`,
  `ExtractedText.swift`, `ToPcError.swift`, `Redact.swift`,
  `UserMessages.swift`), `Providers/Gemini/`, `Providers/Anthropic/`,
  `Providers/OpenAI/` (each `<Name>Client.swift` plus `<Name>Wire.swift` with
  `Codable` types), `Providers/Fake/FakeProvider.swift`; `Config/`
  (`ConfigModels.swift`, `ConfigValidator.swift`, `Snapshot.swift`,
  `KillSwitch.swift`, `Tuning.swift`, `AppVersion.swift`); `Session/`
  (`SessionTypes.swift`, `SessionMachine.swift`, with the controller running
  the stream in a `Task`); `Consent/ConsentRules.swift`,
  `Report/ReportBuilder.swift`, `Report/ReportLimit.swift`. **Why:** the
  same contract as Android, testable with `swift test` without a simulator.

##### 46. `ios/PhotoCritic/` app sources (Create)

- **Type / Scope**: App target.
- **Proposed Changes**:
  - `PhotoCriticApp.swift` (startup in the same order as file 38; Firebase
    configured from the bundled plist); `AppContainer.swift` (the
    Assumptions interfaces, passed through the SwiftUI environment);
    `Debug/FakeContainer.swift` (compiled only in `Debug-Dev`, chosen by the
    launch argument `-PCUseFakes YES`).
  - `Platform/Storage/`: `Keychain.swift` (`kSecAttrAccessibleWhenUnlockedThisDeviceOnly`
    on every write, `kSecAttrSynchronizable` false), `InstallMarker.swift`
    (deletes all Keychain items for the service when the marker is
    missing), `JSONFiles.swift` (atomic writes in Application Support,
    marked `isExcludedFromBackup`), `SettingsStore.swift`,
    `ConsentStore.swift`, `ReportStore.swift`.
  - `Platform/Config/RemoteConfigLoader.swift` (same load order;
    `minimumFetchInterval = 0`; no real-time listener);
    `Platform/Telemetry/Telemetry.swift` and `Events.swift` (same as file
    33); `Platform/Net/Connectivity.swift` (`NWPathMonitor`).
  - `Features/Image/` (`PhotoRecord.swift`, `Pick.swift` with PhotosPicker
    and the camera wrapper, `ImagePipeline.swift` with ImageIO as in §6 and
    the file 35 rules, `Cleanup.swift`); `Features/Report/ReportService.swift`
    (same as file 36, Firestore `MemoryCacheSettings`);
    `Features/Session/SessionModel.swift` (`@Observable`, wraps the Core
    controller).
  - `UI/`: `SetupView.swift`, `HomeView.swift`, `SettingsView.swift`,
    `DisabledView.swift`, `PhotoView.swift`, `ConversationSheet.swift`,
    `ConversationPane.swift`, `MessageList.swift`, `MessageItem.swift`,
    `Composer.swift`, `StarterChips.swift`, `ConsentSheet.swift`,
    `ReportSheet.swift`, `Design/Tokens.swift`, `Design/Components.swift`,
    `Design/LayoutMode.swift`, with the behavior of file 38 (the sheet in
    §12; `LayoutMode` thresholds in points: 700 at start, 720 and 680
    afterwards); VoiceOver labels and Dynamic Type.
  - `Localizable.xcstrings` (every user-visible string); `Assets.xcassets`
    (app icon, Lucide SVG icons, accent color).

##### 47. iOS tests (Create)

- **Type / Scope**: Unit, contract, live and UI tests.
- **Proposed Changes**:
  - `ios/Core/Tests/PhotoCriticCoreTests/`: the same list as file 39's
    `core` tests, reading the same `shared/` files (the repository root is
    found from `#filePath`), with a `URLProtocol` stub in place of
    `MockWebServer`.
  - `ios/Core/Tests/PhotoCriticCoreLiveTests/`: the client acceptance tests
    against the live APIs, skipped unless `PC_LIVE_OPENAI_KEY`,
    `PC_LIVE_ANTHROPIC_KEY` or `PC_LIVE_GEMINI_KEY` is set; run by hand with
    `swift test --filter Live` before each tester release.
  - `PhotoCriticTests`: Keychain and install marker (missing versus
    unreadable), atomic files, telemetry off, report service, image
    pipeline, cleanup, Remote Config loader.
  - `PhotoCriticUITests`: CUJs 1–9 plus consent once per provider, with the
    fake container (`-PCUseFakes YES`).

#### Documentation

##### 48. `README.md` (Create)

- **Type / Scope**: Project readme.
- **Proposed Changes**: Toolchain setup (Android Studio, Xcode, XcodeGen,
  SwiftLint, Node for `tools/`), how to build and run each app, run tests,
  record fixtures, publish config, and make tester builds; links to this
  design and the brief.

##### 49. `docs/VERSIONS.md` (Modify)

- **Type / Scope**: Version table.
- **Proposed Changes**: Replace the Expo table with the Phase 0 pins:
  Kotlin, Android Gradle Plugin, Compose BOM, OkHttp, kotlinx libraries,
  Firebase Android BoM, Swift tools version, Firebase Apple SDK, XcodeGen,
  SwiftLint, ktlint, detekt; the reason for each; the platform minimums; the
  rule that a provider API version (Anthropic `anthropic-version`, Gemini
  `v1beta`) changes in both apps in the same pull request.

##### 50. `docs/spikes/phase0.md` (Modify)

- **Type / Scope**: Spike results.
- **Proposed Changes**: Rewritten in the same commit as file 1. Replaces the
  AI SDK check matrix with:
  - wire formats confirmed per provider (§3a, Q1), with recording dates;
  - `core/net` and the Gemini client passing the client acceptance tests on
    an Android phone and an iPhone;
  - Firebase behavior checks: collection-off key names on both platforms;
    `getAppInstanceId` with collection off and on; Crashlytics unsent
    reports; the Firestore memory cache leaving no report text on disk;
    server timestamp plus offset for `expireAt` (file 36); Analytics
    reserved event names (file 33);
  - the merged-manifest permission list (file 17);
  - atomic file writes;
  - image-size accuracy, the history cap and the token-estimate values
    (Q5, file 14);
  - harness and Android pipeline output compared (file 40);
  - Anthropic automatic caching fields;
  - HEIC on API 24–27 and AVIF on API 24–30 (Q2);
  - the measured timeouts.
  The Expo-era Gemini Node run (2026-11-17) stays as a historical note.
  **Why:** these confirm §3a (Q1), answer Q5 and the Q2 image limits, and
  confirm the Q10 behavior.

##### 51. `docs/privacy-policy.md` (Create; bundled in both apps)

- **Type / Scope**: Privacy policy.
- **Proposed Changes**: Per provider: what is sent, retention, training and
  human review, region notes; that OpenAI requests use `store: false`, so
  OpenAI keeps abuse-monitoring logs for up to 30 days, or longer where
  required by law, and no application state; what Firebase collects
  (config, opt-in analytics and crash data, report text and its retention,
  linked instance ID when sharing is on); keys and photos stay on the
  device; 18+; deletion requests by report ID through the support contact.
  The policy is bundled with the app (author decision, 2026-11-17): the
  Android build copies it into the `app` assets with a Gradle `sourceSets`
  entry, and `ios/project.yml` adds it as a bundle resource. Settings and
  the consent sheet open it in an in-app text view (plain text; no Markdown
  rendering in v1). `privacy_policy.url` stays empty until a hosted copy is
  needed (for example for the store listing). The support email is supplied
  by the author when application coding is complete.

##### 52. `docs/store-forms.md` (Create)

- **Type / Scope**: Compliance notes.
- **Proposed Changes**: App Privacy label answers, Play Data safety
  answers, age rating answers, and the TestFlight and Play internal test
  notes.

##### 53. `docs/reviewer-key-runbook.md` (Create)

- **Type / Scope**: Operations runbook.
- **Proposed Changes**: Creating a spend-capped key for Beta App Review and
  Play review, where it is given to reviewers, rotation after each review,
  and what to do if a key is reported leaked.

##### 54. `THIRD_PARTY_NOTICES.md` (Create)

- **Type / Scope**: Licence notices.
- **Proposed Changes**: Lucide (ISC), OkHttp and other Apache 2.0
  libraries, Firebase SDKs.

##### 55. `docs/plans/native-migration.md` (Keep)

- **Type / Scope**: Decision record.
- **Proposed Changes**: None; it records the 2026-11-17 decisions and the
  rejected provider layers. This design supersedes it where they differ.

### Verification Plan

- **Compilation/Build**: `./gradlew assembleDevDebug assembleProdRelease
  :app:checkPermissions`; `xcodegen && xcodebuild build` for the
  `PhotoCritic-Dev` and `PhotoCritic-Prod` schemes. A signed Android build
  at the end of every Phase 1 step; a signed iOS build once the Apple
  Developer account exists, and at the latest at the end of Phase 1.a.
- **Unit Tests**: `./gradlew :core:test :app:testDevDebugUnitTest`; `swift
  test` in `ios/Core`; `xcodebuild test` for `PhotoCriticTests`. Both apps
  run the same provider contract tests against `shared/`.
- **Static Analysis & Linting**: `./gradlew ktlintCheck detekt` (layering,
  telemetry and logging rules); `swiftlint --strict`; `npm run
  config:validate` in `tools/`; the prompt-copy check (file 3).
- **Test Coverage Requirement**: 80% line coverage for `android/core`
  (Kover, `koverVerify`) and `ios/Core` (`llvm-cov report`), enforced in
  CI; every fixture in `shared/` is used by both apps' contract tests.
- **Client acceptance tests** (each provider client, both platforms): key
  only in its header, never in the URL, logs or `detail`; text streams in
  more than one delta; the image carries the detail or resolution setting
  from the model's Remote Config entry; a disallowed host and any redirect
  are refused before data is sent; exactly one request per question,
  including on errors; Stop ends the stream and the request; errors carry
  status, provider code and request ID where the provider sends one; every
  recorded stream gives the expected events; OpenAI requests contain
  `store: false`. They run in CI against recordings and a local mock
  server, and before each tester release against the live APIs with
  spend-capped keys (`:harness` for Kotlin, `PhotoCriticCoreLiveTests` for
  Swift).
- **E2E/Integration**: Firestore emulator rules tests; Compose UI tests and
  XCUITests for CUJs 1–9 plus consent once per provider, with the fake
  container; the daily canary from Phase 1.3; an iOS 17.0 simulator run and
  `SecureKeysTest` on an API 24 phone before each tester release; the
  manual smoke test with live keys on both platforms (invalid key, revoked
  key, old unrestricted Gemini key, spend-capped key per provider,
  re-routed Gemini model, oversize image); a proxy check that a build with
  sharing off sends nothing to `app-measurement.com`,
  `firebaselogging-pa.googleapis.com` or
  `crashlyticsreports-pa.googleapis.com` (Remote Config and Installations
  calls are expected; Phase 0 confirms the host list); an iOS reinstall on
  a physical device wipes keys.
- **Measurements**: `./gradlew :harness:run` before Phase 1.2 ends (Gemini
  baseline), before Phase 1.4 ends (all providers) and before each tester
  release; time to first token, photo preparation time, cold start and
  frame rate recorded on one reference phone per platform (chosen in Phase
  0: the oldest test phone available, API 24–27 on Android if possible).

---

_Drafted with EGM_
