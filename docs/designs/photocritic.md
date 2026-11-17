# PhotoCritic: Ask Questions About a Photo

- **Author:** yogeshkolte (yogeshkolte@gmail.com)
- **Status:** Proposed
- **Date:** 2026-11-15
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

The app is built with Expo (development build), React Native with the New
Architecture, and TypeScript. It has no backend of its own. Firebase supplies
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
provider interface, implemented with the Vercel AI SDK, directly from the
phone to the provider. Remote Config controls models, limits, error text and
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
- **G10. Keys stay on the device.** Keys are kept in `expo-secure-store`,
  readable only while the device is unlocked and never synced or backed up.
  They are deleted on the first launch after a reinstall. Each key is checked
  when entered (decision 21). The setup screen explains, before key entry,
  that usage is billed to the user's provider account and that the key must
  be re-entered after reinstalling.
- **G11. One provider interface for cloud now and on-device later.** The
  screens talk only to PhotoCritic's own interface. Cloud providers are
  built on the Vercel AI SDK, which is final: no other provider layer is
  evaluated (author decision after review, 2026-11-15). The interface reports
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
  training terms and every piece of data Firebase collects. 18+ age rating.
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
- **Over-the-air JavaScript updates** (EAS Update). Every fix ships as a new
  tester build (decided during drafting).
- **Older phones and OS versions** below the minimums set in D9.

## Open Questions

These are carried from the brief without decisions. The author deferred
several of them to development (decision 27). They stay open until decided.

- **Q1. Resolved: provider layer (D3).** The Vercel AI SDK is final; no
  other option is evaluated (author decision after review, 2026-11-15).
  Phase 0 still runs setup checks on a release build (custom fetch,
  streaming, abort, request-ID header, mid-answer errors, Gemini API surface
  and version). A problem found there is fixed inside the AI SDK setup, not
  by changing the provider layer.
- **Q2. Minimum OS and platform setup (D9).** Recommended iOS 17. Android
  minSdk 24 (Android 7.0), decided by the author after review (2026-11-15):
  the lowest the current Expo SDK and React Native allow. Android cannot
  decode HEIC below API 28 or AVIF below API 31, so on those phones such
  photos show the generic error message (G12). The Expo SDK version is
  pinned at Phase 0 (D26).
- **Q3. Sheet library (D26).** `@gorhom/bottom-sheet` or
  `react-native-true-sheet`, chosen by a Phase 0 test on the pinned SDK.
- **Q4. Session lifecycle (D8).** What happens when the app is backgrounded,
  the process is killed, the Android camera activity is recreated, or the
  device rotates while an answer is arriving.
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
- **Q13. Screen states.** A full state list for the conversation screen
  (no photo, preparing, sending, streaming, stopped, each error type,
  offline, disabled) is still to be written (finding O7).

## Accepted Risks

The author has reviewed and accepted these three risks (2026-11-15).
**Reviewers: do not raise these as findings.** Comment only if the design
contradicts the accepted behavior described here.

- **AR1. The Vercel AI SDK is the only provider layer (Q1).** No other
  option is evaluated and there are no hand-written adapters to fall back
  to. If a Phase 0 setup check fails (custom fetch, streaming, abort,
  readable errors), the problem is worked around inside the AI SDK setup,
  or the feature it affects is dropped from v1. The author accepts this
  (2026-11-15).
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

### 1. Shape of the system

The app talks to three kinds of services directly from the phone. There is no
PhotoCritic server.

```text
                       ┌──────────────────────── Phone ────────────────────────┐
                       │                                                        │
  Camera / Photos ───▶ │  Screens (Expo Router)                                 │
                       │     │  setup · home · settings · consent · report      │
                       │     ▼                                                  │
                       │  Features                                              │
                       │   session ── image ── consent ── report                │
                       │     │                                                  │
                       │     ▼                                                  │
                       │  providers/  PhotoCritic Provider interface            │
                       │     │  openai · anthropic · gemini (AI SDK)            │
                       │     │  (local adapters added in a later phase)         │
                       │     ▼                                                  │
                       │  platform/net  one fetch wrapper (host allowlist)      │
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

```text
src/
  app/                 Expo Router screens: setup, home, settings
                       (consent and report are sheets opened from home)
  design/              tokens.ts, components (Button, Chip, Sheet, TextCard,
                       Banner, Dialog), icons (copied Lucide SVG components)
  features/
    session/           one-photo conversation: state machine, neutral history
    providers/         Provider interface, capability data, adapters, error
                       conversion, extracted-text parser
    image/             original → edits (empty in v1) → send copy
    consent/           consent records and the send gate
    report/            report form logic and Firestore write
  platform/
    net/               the single fetch wrapper all provider calls use
    config/            Remote Config load, validation, session snapshot,
                       tuning constants
    storage/           secure store (keys), plain files (settings, consent,
                       install marker, report counter)
    telemetry/         one logging wrapper for Analytics and Crashlytics
```

Rules:

- Screens call features; features never import screens. `platform/` has no
  user interface.
- Only `providers/` knows vendor names, vendor request formats and AI SDK
  types. Nothing from the AI SDK (messages, usage objects, stream parts,
  errors) leaves an adapter. This keeps vendor details in one folder and lets
  on-device providers be added in Phase 5 behind the same interface.
- Only `platform/telemetry` calls Firebase Analytics or Crashlytics. A lint
  rule bans direct imports elsewhere.
- Only `platform/net` performs network calls to providers.
- Imports go to source files directly, not through `index.ts` barrel files.

### 3. Provider interface (author decision: each provider builds its own messages)

`session/` keeps a provider-neutral history. Each provider adapter turns that
history into its own request, places the image where its model needs it, and
applies its own length limit measured in tokens. The code constant
`HISTORY_TURNS_CAP` is an upper bound, not the rule.

Interface (TypeScript-style pseudocode, not implementation code):

```text
interface Provider {
  id: 'openai' | 'anthropic' | 'gemini'          // local ids added later
  capabilities(modelId): Capabilities
  getAvailability(): Available | NeedsKey | NeedsDownload | Unsupported
  prepare(onProgress?): Promise<void>            // no-op for cloud
  dispose(): Promise<void>                       // no-op for cloud
  checkKey(key, modelId, signal): Promise<KeyCheckResult>
  send(request: TurnRequest, signal: AbortSignal): AsyncIterable<TurnEvent>
}

Capabilities {
  acceptsImages: boolean
  imagePlacement: 'any-turn' | 'final-turn-only'
  maxImageLongEdgePx, maxImageBytes
  contextTokens
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

What each cloud adapter does inside `send`:

1.  Build the system prompt from a fixed base prompt (identical on every turn,
    so the start of the request stays the same) plus the prompt-kind text. The
    base prompt tells the model to wrap verbatim text from the photo in a
    fenced block tagged `text` (D36).
2.  Put the image in the first user message, then earlier turns, then the new
    question. Drop the oldest turns until the estimated token count fits the
    model's `contextTokens` and `HISTORY_TURNS_CAP`.
3.  Call the AI SDK's `streamText` with the provider factory configured with
    the `platform/net` fetch wrapper, `maxRetries: 0` (no automatic retries
    anywhere, G12), and the per-model image detail setting.
4.  Translate AI SDK stream parts to `TurnEvent`s. Compare the served model
    with the requested one. Convert every error, before the response, inside
    the stream, or as a failing finish reason, into a `PcError` (§7).

`checkKey`: OpenAI and Anthropic list models (no tokens used). Gemini makes a
minimal inference call, because a working model list does not prove inference
is enabled; the setup screen says this uses a few tokens.

A shared test suite runs against every adapter with recorded fixtures, so the
three copies of the message-building logic cannot drift apart unnoticed.

**Extracted text.** A parser in `providers/` (shared by all adapters) splits
answer text into normal text and `text` cards. It runs on the finished answer
and on each streamed update, and shows an unfinished fence as normal text until
it closes.

### 4. Network wrapper (`platform/net`)

Every provider request passes through one `fetch` function given to the AI SDK
provider factories through their `fetch` option (confirmed in the Phase 0
setup checks, Q1). It:

- refuses any host not in the in-code allowlist (`api.openai.com`,
  `api.anthropic.com`, `generativelanguage.googleapis.com`; decision 23);
- refuses redirects (`redirect: 'error'`);
- removes any `Origin` header (Anthropic answers an `Origin` header with a
  CORS 401);
- sends keys only in headers: OpenAI `Authorization: Bearer`, Anthropic
  `x-api-key`, Gemini `x-goog-api-key` (never `?key=` in the URL);
- applies a connect timeout and a total timeout from the tuning constants
  (`CONNECT_TIMEOUT_MS`, `REQUEST_TIMEOUT_MS`), combined with the user's
  Stop signal;
- records the provider's request-ID header for "show details".

### 5. Session state machine (`features/session`)

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
- The state store is Zustand with selectors, so a streaming answer
  re-renders only the message being written.
- What happens to the state on backgrounding, process death and rotation
  mid-stream is Open Question Q4.

### 6. Image pipeline (`features/image`)

```text
picker / camera ──▶ original (URI, never changed, displayed full quality)
                       │
                       ▼
                    edits (list of operations; empty in v1)
                       │
                       ▼
                    send copy: decode with downsampling to the model's long-edge
                    target (DEFAULT_LONG_EDGE_PX, 2048) ─▶ apply EXIF orientation ─▶
                    encode JPEG (quality from JPEG_QUALITY) ─▶ no metadata kept
```

- Picking uses `expo-image-picker` through the system picker, with
  microphone permission off and no broad storage permission.
- Conversion uses `expo-image-manipulator` with decode-time downsampling, so
  a 48–200 MP photo is never fully loaded into memory.
- Display uses `expo-image` with its cache set to memory only, so photos are
  not written to the disk cache.
- Android camera recovery uses `ImagePicker.getPendingResultAsync` when the
  activity was destroyed during capture.
- The send copy and temporary files are deleted when the conversation ends.
- Each send copy has an `imageVersionId`, so later image editing can start a
  new conversation instead of changing the image under old answers.

### 7. Errors

Author decision after review (2026-11-15): there is no error table, no
error classification and no retry policy. Every failure shows one generic
message:

> Something went wrong. Please try again.

with Dismiss and "Show details". Nothing is retried automatically, in
PhotoCritic or in the AI SDK (`maxRetries: 0`).

A failure is caught at one of three points, and the point is recorded as
`phase` in the details:

- `before-response`: the request failed or returned a non-2xx status;
- `mid-stream`: an error event arrived after a 200;
- `finish`: the answer ended with finish reason `error` or `content-filter`.
  Any other finish reason ends the answer normally.

Each failure becomes a `PcError` with `phase`, `httpStatus`, `providerCode`
(the provider's error code or type string, if any), `requestId` and a
redacted `detail` (the provider's message with keys and URLs removed, at most
200 characters). "Show details" shows these fields. The same message and
details are used for key-check failures on the setup screen and for photos
that cannot be read. The offline state is not an error: it is a banner, and
Send is disabled.

If text had already arrived, it stays on screen labelled "Failed" and is
not added to `history`, the same as a stopped answer.

### 8. Remote configuration (`platform/config`)

Parameters (shapes as in brief section I.3, with round-4 changes):

| Parameter             | Holds                                                                                                                                                                                                                       |
| --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `config_meta`         | `configVersion`, `publishedAt`                                                                                                                                                                                              |
| `kill_switch`         | `appDisabled`, message, `minSupportedAppVersion`, `updateUrl`, `disabledProviders[]`, `disabledModels[]`, `bannerMessage`, `reportsDisabled`                                                                                |
| `providers`           | per provider: `enabled`, `displayName`, `baseUrl` (must match the allowlist), `keyHelpUrl`, `consentTextVersion`                                                                                                            |
| `models`              | per model: `provider`, `id`, `displayName`, `label` (`Fast`/`Best`, display only), `maxLongEdgePx`, `maxBytes`, `imageDetail`, `maxOutputTokens`, `contextTokens`, `status`, `replacementId`, `minAppVersion`              |
| `consent_texts`       | per provider: `version`, `effectiveDate`, `summary[]`, `policyUrl`                                                                                                                                                          |
| `privacy_policy`      | `url`, `version`                                                                                                                                                                                                            |

Changes from the brief: `models.tier` becomes `label` (display only) and the
`defaults` parameter is removed, because the user always picks the model
(decision 36). `contextTokens` is added for the provider interface.

Changes after review (author decisions, 2026-11-15): `image_policy`,
`conversation_policy` and the numeric part of `report_policy` move to code
constants (§8a); `report_policy.enabled` becomes `kill_switch.reportsDisabled`;
`error_map` is removed (§7); `models.fallbackId` is removed, because the
kill switch applies only at start and the user always picks the model.

#### 8a. Tuning constants (`src/platform/config/tuning.ts`)

These are TypeScript constants, changed only by a new build. Values marked
"Phase 0" are set from the Phase 0 measurements and recorded in this table
before Phase 1 ends.

| Constant                         | v1 value | Used by                                |
| -------------------------------- | -------- | -------------------------------------- |
| `CONFIG_FETCH_TIMEOUT_MS`        | 10000    | startup config fetch (§8)              |
| `DEFAULT_LONG_EDGE_PX`           | 2048     | send copy, when a model sets no limit  |
| `JPEG_QUALITY`                   | 0.8      | send copy encoding                     |
| `HISTORY_TURNS_CAP`              | Phase 0  | history trimming upper bound (§3)      |
| `MAX_QUESTION_CHARS`             | Phase 0  | composer input limit                   |
| `CONNECT_TIMEOUT_MS`             | Phase 0  | `platform/net`                         |
| `REQUEST_TIMEOUT_MS`             | Phase 0  | `platform/net`                         |
| `MAX_REPORT_CONVERSATION_CHARS`  | Phase 0  | report text cap (§10), Firestore rules |
| `REPORT_DAILY_LIMIT_PER_INSTALL` | Phase 0  | `reportLimit`                          |
| `REPORT_RETENTION_DAYS`          | Phase 0  | report `expireAt` (Q6)                 |
| `REPORT_WRITE_TIMEOUT_MS`        | Phase 0  | `reportService` (§10)                  |

Because the report limits are code constants, `firestore.rules` uses the same
literal numbers; a unit test reads both and fails if they differ.

Load order (brief K.4):

1.  At cold start, show the launch screen and call `fetchAndActivate` with
    `minimumFetchIntervalMillis: 0` and a 10-second timeout. There are no
    bundled default values.
2.  If the fetch fails or times out, show the config-unavailable screen
    (AR2, CUJ 9). If Firebase reports the fetch as throttled, use the values
    activated by this install's last successful fetch, if there are any;
    otherwise show the same screen. This is the only case in which earlier
    values are used, and they go through the same check in step 3.
3.  Check every value against its expected shape. If any value is invalid,
    show the same screen; there is no per-value fallback (Q9). A `baseUrl`
    outside the allowlist counts as invalid.
4.  Build the in-memory session snapshot. Screens read only the snapshot.
5.  Evaluate the kill switch once. There is no live-update listener: a
    change published while the app runs takes effect at the next cold start
    (G18).

### 9. Local storage (`platform/storage`)

| Item                   | Where                                                                | Contents                                                                                                                |
| ---------------------- | -------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| `pc.apikey.<provider>` | `expo-secure-store`, `WHEN_UNLOCKED_THIS_DEVICE_ONLY` on every write | API key only                                                                                                            |
| `install.json`         | app document directory                                               | `installedAt`, `appVersionAtInstall`                                                                                    |
| `settings.json`        | app document directory                                               | `schemaVersion`, selected provider, selected model per provider, analytics opt-in, `onboardingCompletedAt`, `updatedAt` |
| `consent.json`         | app document directory                                               | per provider: current record and up to 10 earlier ones (`consentTextVersion`, `decision`, `recordedAt` UTC)             |
| `reports.json`         | app document directory                                               | sent report IDs and the daily counter                                                                                   |

- At launch, if `install.json` is missing, delete every key in the fixed
  list of key names, then write the marker. A read error is not treated as
  "missing". No backup restore is supported (Q8).
- Files are written to a temporary file, then renamed.
- Sending is blocked unless `consent.json` has a granted record for the
  provider at or above the current `consentTextVersion`.

### 10. Reports (`features/report`)

A report is one Firestore document `reports/{reportId}` with the fields in
brief K.6: IDs and times, app and device info, `configVersion`, provider,
requested and served model, provider request ID, image settings (size and
detail, not the image), the conversation as a list of `{role, text}` up to
`MAX_REPORT_CONVERSATION_CHARS` in total,
the reported turn, category, the user's note, an optional redacted error, and
`instanceId` only when sharing is on. Security rules allow `create` only and
check every field. There is no App Check. Firestore offline persistence is
turned off, so report text is never cached on the device. Send report is
disabled while offline; a write the server has not confirmed within
`REPORT_WRITE_TIMEOUT_MS` is shown as failed (author decisions after review,
2026-11-15).

### 11. Telemetry (`platform/telemetry`)

Analytics and Crashlytics collection is off in the build configuration and
turned on only by the "Share usage and crash data" switch. Events and
parameters are those in brief K.5, with `tier` removed from `session_start`.
The wrapper removes any text, key, image path or provider error body before
logging. When sharing turns on, the app reads the Analytics app instance ID and
sets it as the user ID for Analytics and Crashlytics; when it turns off, the
user ID is cleared. iOS builds upload debug symbols for Crashlytics.

### 12. Screens and look

- **Setup** (first launch and Settings → Provider or Model): provider
  buttons, model list with Fast/Best labels, key field with checking and
  error states, the sharing switch (first launch only).
- **Home**: photo full screen; top bar with Settings and New photo only. A
  bottom sheet (library chosen by the Phase 0 test, Q3) with two heights:
  collapsed, sized to content up to a limit, and expanded. Tint of at least
  72% under text when collapsed and 94% when expanded; controls on the photo
  use at least 65%. Solid when Reduce Transparency is on, and on Android
  devices without blur. The conversation list uses FlashList v2. The input
  uses the sheet library's keyboard-aware text input.
- **Wide windows** (over about 700 points, with a margin to prevent
  flipping): photo pane on the left, a fixed-width conversation pane on the
  right. Layout follows `useWindowDimensions`, not device type.
- **Settings**: provider, model, key (replace), sharing switch, privacy
  policy, support ID (only when sharing is on), app version.
- **Design tokens** (`design/tokens.ts`): colors `bg #0F1012`, `surface
#17181B`, `field #1C1D21`, `text #F4F4F2`, `muted` (text at 68%), `accent
#F5B13D`, `accentOnLight #8A5A00`, `onAccent #1A1206`, `danger #FF8A7A`;
  radii 12 / 14–16 / 24 / 999; spacing 4–32; system fonts with Dynamic Type
  scaling; touch targets at least 44 points.

### 13. Libraries and versions

Exact versions are pinned at Phase 0 (D26) and recorded in a version table in
the repository. Fixed choices: Expo (latest stable SDK at Phase 0, dev client,
New Architecture), Expo Router (native stack), React Compiler on, Zustand,
FlashList v2, `expo-image-picker`, `expo-image-manipulator`, `expo-image`,
`expo-secure-store`, `expo-file-system`, Vercel AI SDK with
`@ai-sdk/openai`, `@ai-sdk/anthropic` and `@ai-sdk/google`, React Native
Firebase (Remote Config, Analytics, Crashlytics, Firestore),
`react-native-svg` for icons, React Native Testing Library. The sheet library
is chosen in Phase 0 (Q3). Android release builds use R8, and every native
library is checked for 16 KB page alignment.

## Alternatives Considered

Each entry gives the main benefit, the main cost, and why it was not chosen.
"Rejected by author" means the author decided it during brainstorming; the
decision numbers refer to [concepts_brief.md](../../concepts_brief.md).

### Platform and framework

#### Alternative A1: Flutter, Kotlin Multiplatform, or fully native apps

- **Pros**: Native apps get the newest platform AI features first; Flutter
  gives one rendering engine on both platforms.
- **Cons**: Two codebases for fully native; a different language from the
  author's TypeScript and React Native skills for all three.
- **Reason for Rejection**: Rejected by author: the team works in TypeScript
  and React Native (answer 7). Expo was chosen over the bare React Native
  workflow because a development build still gives full native-module
  access, which llama.rn and ExecuTorch need later.

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

#### Alternative C1: Thin hand-written adapters instead of the Vercel AI SDK

- **Pros**: Full control of requests, headers, streaming parsing and error
  bodies; no dependency on whether the AI SDK supports direct calls from
  the phone.
- **Cons**: Three streaming parsers and three request formats to write and
  keep current; no shared path to Callstack's on-device providers.
- **Reason for Rejection**: Rejected by author (decision 35; after review,
  2026-11-15): the Vercel AI SDK is final and hand-written adapters are not
  kept as a fallback (Q1, AR1).

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
  quality; `expo-image` already downscales at render time.

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

#### Alternative E4: Settings and consent in secure store, AsyncStorage or SQLite

- **Pros**: Secure store encrypts; SQLite allows queries.
- **Cons**: Secure store has a size limit too small for consent history;
  neither adds value for a few small files.
- **Reason for Rejection**: Rejected by author (decision 17): plain files in
  the app document directory, keys only in secure store.

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

- Another provider layer, or hand-written adapters as a fallback: not
  evaluated; the Vercel AI SDK is final (Q1, AR1).
- An error table, error classes and automatic retries: not chosen; one
  generic message with details (G12, §7).
- Tuning values in Remote Config: not chosen; they are code constants (§8a).
- A Try again button on the config-unavailable screen: not chosen; the user
  restarts the app (AR2, CUJ 9).
- Live kill-switch updates and a fallback model: not chosen; the kill switch
  applies at cold start only (G18).

## Non-Functional Requirements

- **Security and privacy.** API keys only in `expo-secure-store` and only in
  request headers to the three allowlisted hosts. No key, photo, question,
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
  No over-the-air JavaScript updates in v1; `expo-updates` is not installed.

---

### Additional Resources

- [concepts_brief.md](../../concepts_brief.md): all decisions, findings and
  evidence citations.
- [research/README.md](../../research/README.md): fetched provider, Apple, Play,
  Expo and Firebase documentation used as evidence.
- [research/callstack/](../../research/callstack/): Callstack React Native best
  practices, testing rules and React Native AI documentation.
- [mockup/](../../mockup/): clickable mockup source (phone, tablet, design
  system).

### Future Improvements

- **On-device models** (Phase 5): image-capable on-device providers first,
  with a download and storage feature (`features/models/`), then the
  local-versus-cloud benchmark.
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

- The app lives at the root of this project folder (next to
  `concepts_brief.md`). `TokenEconomizer/` and `research/` are left as they
  are and excluded from the app's TypeScript, lint and test runs.
- App identifiers: iOS bundle ID and Android package
  `com.yk.photocritic`; display name "PhotoCritic". These become
  permanent once a build is uploaded.
- Two Firebase projects: `photocritic-dev` and `photocritic-prod`.
- CI runs on GitHub Actions; app binaries are built with EAS Build.
- Remote Config values are validated with `zod` (one schema per parameter),
  shared by the app and the publishing scripts.
- Connectivity comes from `@react-native-community/netinfo`.
- Tests use Jest (`jest-expo` preset) and React Native Testing Library.

### Implementation order

Each phase ends with its tests passing in CI. Phase numbers match the brief.

1.  **Phase 0, setup and spikes.** Scaffold, CI, Firebase projects, version
    pin, the AI SDK setup checks (Q1), the spikes that answer Q3 (sheet library),
    Q2 (minimum OS), the image-size accuracy test (Q5), and the Firebase
    behavior checks listed under the Phase 0 files below.
2.  **Phase 1, foundations.** Storage, install marker, Remote Config with
    snapshot and validation, telemetry wrapper, image pipeline.
3.  **Phase 2, Gemini end to end.** Provider interface, Gemini adapter, error
    conversion, consent, setup, home screen, Stop, text cards.
4.  **Phase 3, Anthropic then OpenAI.** Same contract tests.
5.  **Phase 4, polish and tester release.** Reports, Settings, wide layout,
    accessibility, privacy documents, store forms, TestFlight and Play
    internal track.
6.  **Phase 5, on-device evaluation.** Starts after the first external tester
    build ships or a harness baseline is recorded (D40). Not detailed here.

Decisions made during drafting (Open Questions Q6–Q10, Q12):

- **Q6 report retention:** free Spark plan; reports carry `expireAt`; old
  reports are removed with a manual script.
- **Q7 report writes:** create-only rules, no App Check, no sign-in.
- **Q8 backup:** all app files excluded from iOS and Android backup; no
  restore handling.
- **Q9 config failure:** no bundled values; a failed or invalid fetch shows
  the config-unavailable screen.
- **Q10 crashes before opt-in:** no special handling.
- **Q12 photo display:** original aspect ratio, fitted whole, black around
  it. Controls use safe-area insets until the author writes the rest of the
  full-screen decision.

Author decisions after review (2026-11-15): Vercel AI SDK final (Q1); one
generic error message and no retries (G12); tuning values in code (§8a);
restart instead of Try again (AR2); kill switch at cold start only (G18).

### Target Files to Create/Modify

#### Project configuration

##### 1. `package.json` (Create)

- **Type / Scope**: Node package manifest.
- **Proposed Changes**: Exact versions for every dependency, pinned at Phase
  0 to the latest stable Expo SDK and the versions `npx expo install` picks
  for it: `expo`, `expo-router`, `react-native-screens`,
  `react-native-reanimated`, `react-native-gesture-handler`,
  `react-native-safe-area-context`, the sheet library chosen in Phase 0,
  `@shopify/flash-list` (v2), `zustand`, `zod`, `expo-image-picker`,
  `expo-image-manipulator`, `expo-image`, `expo-secure-store`,
  `expo-file-system`, `expo-clipboard`, `expo-sharing`,
  `expo-build-properties`, `@react-native-community/netinfo`, `ai` (AI SDK
  v7) with `@ai-sdk/openai`, `@ai-sdk/anthropic`, `@ai-sdk/google`,
  `@react-native-firebase/app`, `/remote-config`, `/analytics`,
  `/crashlytics`, `/firestore` (all one version),
  `react-native-svg`, `react-native-performance`. Dev: `typescript`,
  `jest`, `jest-expo`, `@testing-library/react-native`, `eslint`,
  `@firebase/rules-unit-testing`, `firebase-tools`, `tsx`. Scripts:
  `typecheck`, `lint`, `test`, `test:rules`, `config:validate`,
  `config:publish`, `check:permissions`, `harness`.
  **Why:** one place records the version pin (D26), and scripts are what CI
  runs.

##### 2. `app.config.ts` (Create)

- **Type / Scope**: Expo app configuration.
- **Proposed Changes**: Name, slug, `com.yk.photocritic`
  identifiers; `newArchEnabled: true`; `orientation: "default"`;
  `ios.requireFullScreen: false` and a resizable Android activity (for Split
  View, G20; verify key names); `userInterfaceStyle: "dark"`; iOS deployment
  target and Android `minSdkVersion` from Q2 (iOS 17, minSdk 24) via
  `expo-build-properties`, with `ios.useFrameworks: "dynamic"` for React
  Native Firebase; `expo-image-picker` plugin with `microphonePermission:
false` and camera and photo usage strings; blocked Android permissions
  (`READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, `RECORD_AUDIO`,
  `READ_MEDIA_IMAGES`); React Native Firebase plugins with advertising-ID
  collection off; Google services files chosen by `APP_ENV`
  (`development` or `production`); React Compiler on; Android R8 on for
  release; all app files excluded from iOS and Android backup (Q8).
  **Why:** every platform-level decision (G1, G10, G16, G20, G21)
  is set here.

##### 3. `eas.json` (Create)

- **Type / Scope**: EAS Build profiles.
- **Proposed Changes**: `development` (dev client, `APP_ENV=development`),
  `preview` (internal distribution, development Firebase), `production`
  (store builds, production Firebase, TestFlight and Play internal track
  submit settings). Crashlytics debug-symbol upload on iOS builds.
  **Why:** separates tester builds from development data (G24).

##### 4. `tsconfig.json` (Create)

- **Type / Scope**: TypeScript configuration.
- **Proposed Changes**: Strict mode; path alias `@/` to `src/`; excludes
  `TokenEconomizer/`, `research/`, `mockup/`. **Why:** strict types on the
  provider and config boundaries catch shape errors early.

##### 5. `eslint.config.js` (Create)

- **Type / Scope**: Lint rules.
- **Proposed Changes**: Expo and TypeScript rules, plus
  `no-restricted-imports`: `@react-native-firebase/analytics` and
  `/crashlytics` only inside `src/platform/telemetry/`; `ai` and `@ai-sdk/*`
  only inside `src/features/providers/`; `fetch` to provider hosts only via
  `src/platform/net/`; no imports from `index` files; no `console.*` in
  `src/`. **Why:** enforces the redaction and layering rules (Technical Plan
  §2, G16).

##### 6. `jest.config.js` and `test/setup.ts` (Create)

- **Type / Scope**: Test configuration.
- **Proposed Changes**: `jest-expo` preset; mocks for React Native Firebase,
  secure store, file system and NetInfo; React Native Testing Library
  matchers. **Why:** unit and screen tests run without a device.

##### 7. `firebase.json`, `.firebaserc` (Create)

- **Type / Scope**: Firebase CLI and React Native Firebase build defaults.
- **Proposed Changes**: Projects `photocritic-dev` and `photocritic-prod`;
  Firestore rules and emulator ports; React Native Firebase keys that set
  Analytics and Crashlytics collection off by default (exact key names
  verified in Phase 0). **Why:** collection must be off before any code runs
  (decision 24).

##### 8. `firebase/dev/` and `firebase/prod/` service files (Create)

- **Type / Scope**: `GoogleService-Info.plist` and `google-services.json`
  per project.
- **Proposed Changes**: Downloaded from each Firebase project; selected by
  `app.config.ts`. **Why:** development data never mixes with tester data.

##### 9. `firestore.rules` (Create)

- **Type / Scope**: Firestore security rules.
- **Proposed Changes**: `reports/{reportId}`: allow `create` only, when the
  document ID equals `reportId`, `createdAt == request.time`, every field
  has the expected type and size limit, `conversation` is a list whose
  length is at most `HISTORY_TURNS_CAP × 2 + 2`, written as a literal number
  (rules cannot read app code or Remote Config, and cannot check the items
  inside a list; the app caps total text at `MAX_REPORT_CONVERSATION_CHARS`,
  and the 1 MiB document limit bounds the rest), `category` is one of four
  values, no field outside the list, and no image field. Deny read, update,
  delete and list everywhere. **Why:** the app has no sign-in and no App
  Check, so the rules are the only write protection (G15).

##### 10. `firestore.indexes.json` (Create)

- **Type / Scope**: Firestore index file.
- **Proposed Changes**: Empty index list (no queries from the app); kept so
  the CLI deploy is reproducible.

##### 11. `.github/workflows/ci.yml` (Create)

- **Type / Scope**: CI pipeline.
- **Proposed Changes**: On every push: install, `typecheck`, `lint`,
  `test`, `config:validate`, `test:rules` (Firestore emulator),
  `npx expo install --check` (versions match the SDK), and
  `check:permissions` on a prebuilt Android manifest. **Why:** each
  guarantee in this design has an automatic check.

##### 12. `.github/workflows/canary.yml` (Create, Phase 3)

- **Type / Scope**: Scheduled CI job.
- **Proposed Changes**: Daily: one live call per provider and per listed
  model with spend-capped keys from CI secrets, through the same adapters;
  fails on any error event or a served-model mismatch. **Why:** providers
  change models and errors without notice (round-2 findings).

#### Remote configuration and scripts

##### 13. `config/remoteconfig.template.json` (Create)

- **Type / Scope**: Remote Config template, source of truth.
- **Proposed Changes**: Every parameter in Technical Plan §8 with v1 values:
  kill switch (including `reportsDisabled`), providers (allowlisted
  `baseUrl`s), the curated model list with labels and `contextTokens`,
  consent texts per provider, privacy policy URL and version,
  `config_meta.configVersion`. No tuning values (§8a) and no error rules.
  **Why:** one validated source for every publish (G17).

##### 14. `src/platform/config/schema.ts` (Create)

- **Type / Scope**: TypeScript module.
- **Proposed Changes**: One `zod` schema per parameter, exported for the app
  and the scripts. **Why:** the same checks run before publishing and on
  the device.

##### 15. `scripts/validate-config.ts` (Create)

- **Type / Scope**: Node script.
- **Proposed Changes**: Validates the template against the schemas, plus
  cross-checks: every `baseUrl` in the allowlist, every `replacementId`
  names a listed model, every provider has a consent text. **Why:** with no bundled
  values (Q9), a bad publish would stop every tester's app, so validation
  before publishing is the only protection.

##### 16. `src/platform/config/tuning.ts` (Create)

- **Type / Scope**: TypeScript constants.
- **Proposed Changes**: The constants in Technical Plan §8a, exported and
  typed `as const`. These are not defaults for Remote Config: no Remote
  Config parameter has the same name or overrides them. **Why:** tuning
  values change rarely, and a bad value can no longer stop every install
  through a config publish (author decision after review, 2026-11-15).

##### 17. `scripts/publish-remote-config.ts` (Create)

- **Type / Scope**: Node script (manual, author-run).
- **Proposed Changes**: Validates, increments `configVersion`, sets
  `publishedAt`, and publishes to the chosen Firebase project with the
  Remote Config REST API. **Why:** publishing goes through validation.

##### 18. `scripts/check-permissions.ts` (Create)

- **Type / Scope**: Node script.
- **Proposed Changes**: Reads the merged Android manifest from
  `expo prebuild` and fails if a blocked permission is present. **Why:** G1
  promises no broad media or audio permission.

##### 19. `scripts/purge-reports.ts` (Create)

- **Type / Scope**: Node script using the Firebase Admin SDK (author-run).
- **Proposed Changes**: Deletes `reports` documents whose `expireAt` has
  passed. **Why:** reports are removed on the free plan without billing (Q6).

#### Screens (`src/app/`)

##### 20. `src/app/_layout.tsx` (Create)

- **Type / Scope**: Root layout.
- **Proposed Changes**: Runs startup in order: install-marker check and key
  wipe; load settings; fetch and validate config (up to 10 seconds) and
  build the snapshot; apply the kill switch once; start telemetry if opted
  in. Shows the disabled screen when config is unavailable, or when the
  whole-app kill switch or `minSupportedAppVersion` requires it. If only the
  saved provider or model is disabled, opens setup with the kill-switch
  message (G18). Registers no Remote Config update listener. Native stack navigator, dark status bar style, safe-area provider.

##### 21. `src/app/index.tsx` (Create)

- **Type / Scope**: Route.
- **Proposed Changes**: Redirects to `setup` if no provider, model and key
  are stored, else to `home`.

##### 22. `src/app/setup.tsx` (Create)

- **Type / Scope**: Screen.
- **Proposed Changes**: Provider buttons; model list from the snapshot,
  filtered by status and kill switch, with Fast/Best labels; key field
  (secure text entry) with the BYOK explanation and "where do I get a key"
  link; states: idle, checking, failed (the generic message with "Show
  details", G12), saved. When opened at startup because the saved model or
  provider is disabled, shows the kill-switch message above the list. In
  first-run mode also shows the sharing switch. In edit mode (from
  Settings) shows Back, keeps the stored key if the provider is unchanged,
  and confirms before ending an open conversation (decision 30).

##### 23. `src/app/home.tsx` (Create)

- **Type / Scope**: Screen.
- **Proposed Changes**: Chooses the phone layout (photo plus sheet) or the
  wide layout (two panes) from `useLayoutMode`. Photo view with pinch to
  zoom and pan, at its original aspect ratio, fitted whole with black around
  it (Q12). Empty state with Take photo and
  Choose from Photos. Top bar: Settings and New photo only. Hosts the
  consent sheet, report sheet, new-photo dialog, offline banner and toasts.

##### 24. `src/app/settings.tsx` (Create)

- **Type / Scope**: Screen.
- **Proposed Changes**: Provider and Model rows (open setup in edit mode),
  API key row (replace or remove), sharing switch, privacy policy link,
  support ID (instance ID, only when sharing is on), app version and build
  channel.

##### 25. `src/app/disabled.tsx` (Create)

- **Type / Scope**: Screen.
- **Proposed Changes**: Three full-screen modes, none with a Try again
  button: config unavailable ("PhotoCritic couldn't load its settings.
  Check your connection and restart the app."; AR2, CUJ 9), whole app
  disabled (message from Remote Config), and update required (Update opens
  `updateUrl`) for an app version below the minimum.

#### Design system (`src/design/`)

##### 26. `src/design/tokens.ts` (Create)

- **Type / Scope**: Design tokens.
- **Proposed Changes**: Colors, tints (`chromeTint` 0.65, `sheetTint`
  0.72, `sheetTintExpanded` 0.94), radii, spacing, type scale using the
  system font with font scaling, minimum touch size 44. Values from
  Technical Plan §12.

##### 27. `src/design/useLayoutMode.ts` (Create)

- **Type / Scope**: Hook.
- **Proposed Changes**: Returns `phone` or `wide` from window width, with a
  margin (switch to wide above 720 points, back to phone below 680) so the
  layout does not flip during resizing (D29).

##### 28. `src/design/components/Button.tsx`, `Chip.tsx`, `Banner.tsx`, `Dialog.tsx`, `Toast.tsx`, `TextCard.tsx`, `Sheet.tsx` (Create)

- **Type / Scope**: UI components.
- **Proposed Changes**: Primary and secondary buttons; chips; an error and
  offline banner; a confirmation dialog; a toast with screen-reader
  announcement; the text card with Copy and Share and preserved line breaks;
  `Sheet` wraps the Phase 0 sheet library with two heights (collapsed sized
  to content up to a limit, expanded), tint from tokens, solid when Reduce
  Transparency is on or blur is unavailable, and the library's
  keyboard-aware input. Every control has an accessibility role and label.

##### 29. `src/design/icons/CameraIcon.tsx`, `PhotosIcon.tsx`, `NewPhotoIcon.tsx`, `SendIcon.tsx`, `StopIcon.tsx`, `SettingsIcon.tsx`, `CopyIcon.tsx`, `ShareIcon.tsx`, `ReportIcon.tsx`, `BackIcon.tsx` (Create)

- **Type / Scope**: Icon components.
- **Proposed Changes**: Lucide SVG paths copied into `react-native-svg`
  components (ISC licence notice in `THIRD_PARTY_NOTICES.md`), 24-point
  grid, color from props.

##### 30. `assets/icon.png`, `assets/adaptive-icon.png`, `assets/splash.png` (Create)

- **Type / Scope**: App icon and splash.
- **Proposed Changes**: The aperture-with-speech-tail icon from the design
  board, dark background, amber mark. **Why:** G21.

#### Session (`src/features/session/`)

##### 31. `src/features/session/types.ts` (Create)

- **Type / Scope**: Types.
- **Proposed Changes**: `SessionState` (the states in Technical Plan §5),
  `VisibleMessage` (question, answer text, cards, status: streaming, done,
  stopped, failed; `error?: PcError`), `NeutralTurn`.

##### 32. `src/features/session/sessionStore.ts` (Create)

- **Type / Scope**: Zustand store.
- **Proposed Changes**: Holds state, visible messages, neutral history and
  the current image version. Actions: `setPhoto`, `ask(promptKind,
text)`, `stop`, `dismissError`, `reset(reason)`. There is no `retry`
  action (G12). `ask` checks
  the consent gate, builds a `TurnRequest`, calls the provider's `send`
  with an `AbortController`, appends text deltas to the streaming message,
  and on `done` adds the turn to history. `stop` aborts and marks the
  message stopped without adding it to history. An `error` event marks the
  message failed, keeps any text received, and does not add it to history. Selectors so only the
  streaming message re-renders.

##### 33. `src/features/session/ConversationSheet.tsx`, `MessageList.tsx`, `MessageItem.tsx`, `Composer.tsx`, `StarterChips.tsx`, `ConversationPane.tsx` (Create)

- **Type / Scope**: UI components.
- **Proposed Changes**: Sheet content for phone and the right pane for wide
  windows share `MessageList` (FlashList v2), `MessageItem` (plain text,
  text cards from the extracted-text parser, Stopped or Failed tag, the
  generic error message with "Show details", Copy answer and
  Report actions once done), `Composer` (multi-line input, Send or Stop
  button by state, disabled without a photo or while offline) and
  `StarterChips` (Identify, Explain, Copy text; hidden while streaming).

#### Providers (`src/features/providers/`)

##### 34. `src/features/providers/types.ts` (Create)

- **Type / Scope**: Types.
- **Proposed Changes**: `Provider`, `Capabilities`, `TurnRequest`,
  `TurnEvent`, `KeyCheckResult`, `PcError` (`phase`, `httpStatus`,
  `providerCode`, `requestId`, `detail`), as in Technical Plan §3 and §7.

##### 35. `src/features/providers/registry.ts` (Create)

- **Type / Scope**: Module.
- **Proposed Changes**: Returns the adapter for a provider ID and the
  capabilities for a model by merging adapter defaults with the model's
  Remote Config entry.

##### 36. `src/features/providers/shared/systemPrompt.ts` (Create)

- **Type / Scope**: Module.
- **Proposed Changes**: The fixed base prompt (answer about the photo,
  plain text, wrap verbatim text from the photo in a fenced `text` block)
  and the four prompt-kind texts. Versioned with a constant so harness runs
  record it.

##### 37. `src/features/providers/shared/historyTrim.ts` (Create)

- **Type / Scope**: Module.
- **Proposed Changes**: Estimates tokens per turn (characters divided by a
  per-provider factor, plus the image's token estimate from the provider's
  image sizing rule) and drops the oldest turns until the request fits
  `contextTokens` minus `maxOutputTokens`, never exceeding
  `HISTORY_TURNS_CAP`. Each adapter calls it with its own estimates.

##### 38. `src/features/providers/shared/extractedText.ts` (Create)

- **Type / Scope**: Module.
- **Proposed Changes**: Splits answer text into segments `{kind: 'text' |
'card', value}`; an unclosed fence stays plain text until it closes.

##### 39. (Removed: no automatic retries, G12)

##### 40. `src/features/providers/errors/toPcError.ts` and `errors/userMessages.ts` (Create)

- **Type / Scope**: Modules.
- **Proposed Changes**: `toPcError(phase, signal)` turns an HTTP response,
  a stream error event or a failing finish reason into a `PcError`
  (Technical Plan §7), reading the provider's error code and request ID and
  redacting the message. It does not classify. `userMessages` holds the one
  generic message and the "Show details" labels, in English strings kept in
  one file for later translation.

##### 41. `src/features/providers/gemini/adapter.ts`, `anthropic/adapter.ts`, `openai/adapter.ts` (Create; Gemini in Phase 2, others in Phase 3)

- **Type / Scope**: Provider adapters.
- **Proposed Changes**: Each implements `Provider` with the AI SDK provider
  factory given `providerFetch`, `maxRetries: 0`, and the per-model image
  setting (OpenAI `detail`, Gemini resolution, Anthropic none). Builds
  messages as in Technical Plan §3, translates stream parts to
  `TurnEvent`s, captures the request ID header (`request-id` for
  Anthropic; OpenAI and Gemini headers confirmed in the Phase 0 setup
  checks), and
  reports the served model. No sampling parameters for Gemini. `checkKey`:
  model list for OpenAI and Anthropic; minimal inference call for Gemini.

##### 42. `src/features/providers/fake/adapter.ts` (Create)

- **Type / Scope**: Test adapter (development builds and tests only).
- **Proposed Changes**: Scripted answers, errors and delays; capability set
  `final-turn-only`, `streaming: 'whole'`, no usage, to prove the screens
  cope with a weaker provider before Phase 5.

#### Image (`src/features/image/`)

##### 43. `src/features/image/types.ts` and `imageStore.ts` (Create)

- **Type / Scope**: Types and store.
- **Proposed Changes**: `PhotoRecord {originalUri, edits: [], imageVersionId,
sendCopy?: {uri, bytes, width, height, longEdgePx, detail}}`. The send
  copy is rebuilt when the model's target size differs.

##### 44. `src/features/image/pick.ts` (Create)

- **Type / Scope**: Module.
- **Proposed Changes**: Camera and system picker through
  `expo-image-picker` (single image, original asset), Android pending
  result recovery, and picker failures shown as the generic error message
  (G12) or the permission message.

##### 45. `src/features/image/pipeline.ts` (Create)

- **Type / Scope**: Module.
- **Proposed Changes**: Decode with downsampling to the target long edge,
  apply orientation, encode JPEG at `JPEG_QUALITY`, verify the output is
  under `maxBytes` (re-encode at lower quality once if not), and confirm no
  metadata remains. Records preparation time for telemetry and the harness.

##### 46. `src/features/image/cleanup.ts` (Create)

- **Type / Scope**: Module.
- **Proposed Changes**: Deletes send copies and picker temporary files when
  a conversation ends and at launch.

#### Consent (`src/features/consent/`)

##### 47. `src/features/consent/consentStore.ts` and `gate.ts` (Create)

- **Type / Scope**: Modules.
- **Proposed Changes**: Read and write `consent.json` (current record plus
  up to 10 earlier per provider). `canSend(provider)` is true only for a
  granted record at or above the snapshot's `consentTextVersion` for a
  provider whose capabilities say data leaves the device. A failed write
  blocks the send.

##### 48. `src/features/consent/ConsentSheet.tsx` (Create)

- **Type / Scope**: UI component.
- **Proposed Changes**: Provider name, what is sent, the provider's
  retention and training summary from `consent_texts`, privacy policy link,
  "Not now" and "Agree and send". On agree, records consent and resumes the
  pending question.

#### Reports (`src/features/report/`)

##### 49. `src/features/report/reportService.ts` and `reportLimit.ts` (Create)

- **Type / Scope**: Modules.
- **Proposed Changes**: Builds the report document (Technical Plan §10),
  writes it with a client-generated UUID as the document ID, and returns
  sent, failed or limit reached. Turns Firestore offline persistence off
  before the first write, and treats a write not confirmed within
  `REPORT_WRITE_TIMEOUT_MS` as failed. Does not offer Report
  when `kill_switch.reportsDisabled` is true. `reportLimit` keeps the daily
  count (against `REPORT_DAILY_LIMIT_PER_INSTALL`) and sent IDs in
  `reports.json`.

##### 50. `src/features/report/ReportSheet.tsx` (Create)

- **Type / Scope**: UI component.
- **Proposed Changes**: Category chips, note field (1,000 characters) with
  "The photo itself is not sent", a list of what is sent, result state and a
  copyable report ID.

#### Platform (`src/platform/`)

##### 51. `src/platform/net/allowlist.ts` and `providerFetch.ts` (Create)

- **Type / Scope**: Modules.
- **Proposed Changes**: The host allowlist constant and the fetch wrapper in
  Technical Plan §4 (allowlist check, `redirect: 'error'`, Origin removal,
  timeouts combined with the Stop signal, request-ID capture).

##### 52. `src/platform/net/connectivity.ts` (Create)

- **Type / Scope**: Hook and store.
- **Proposed Changes**: Online or offline from NetInfo, used by the banner
  and the Send button.

##### 53. `src/platform/config/remoteConfig.ts`, `snapshot.ts`, `killSwitch.ts` (Create)

- **Type / Scope**: Modules.
- **Proposed Changes**: Startup sequence from Technical Plan §8, with no
  update listener; `snapshot` exposes typed, validated values; a failed
  fetch or any invalid value returns `unavailable`, which shows the
  config-unavailable screen (Q9), and is logged through telemetry;
  `killSwitch` evaluates app, provider and model disabling, reports
  disabling and the minimum app version, once at cold start.

##### 54. `src/platform/storage/files.ts` (Create)

- **Type / Scope**: Module.
- **Proposed Changes**: `readJson`, `writeJsonAtomic` (write a temporary
  file, then move over the target; handle a leftover temporary file at
  launch), and a per-file write queue so writes do not overlap.

##### 55. `src/platform/storage/secureKeys.ts` and `installMarker.ts` (Create)

- **Type / Scope**: Modules.
- **Proposed Changes**: `getKey`, `setKey`, `deleteKey` per provider, always
  with `WHEN_UNLOCKED_THIS_DEVICE_ONLY`. `installMarker` checks
  `install.json`; if it is missing (not unreadable), deletes the fixed list
  of key names and writes the marker.

##### 56. `src/platform/storage/settings.ts` (Create)

- **Type / Scope**: Module.
- **Proposed Changes**: `settings.json` read and write with defaults on a
  corrupt or missing file: selected provider, model per provider, sharing
  opt-in, onboarding time.

##### 57. `src/platform/telemetry/telemetry.ts`, `events.ts`, `redact.ts`, `errorHandler.ts` (Create)

- **Type / Scope**: Modules.
- **Proposed Changes**: `setSharing(on)` turns collection on or off in both
  SDKs and sets or clears the instance-ID user ID; `log(event, params)`
  accepts only the events and parameters in `events.ts` (brief K.5 without
  `tier`) and drops everything while sharing is off; `redact` removes keys,
  URLs with query strings, file paths and any text field; `errorHandler`
  is the global JavaScript error handler that redacts before Crashlytics.
  No special handling of crash reports from before opt-in (Q10).

#### Tests and fixtures

##### 58. `test/fixtures/images/` (Create)

- **Type / Scope**: Image fixtures.
- **Proposed Changes**: HEIC with GPS, AVIF, DNG, animated GIF, corrupt
  JPEG, 48 MP JPEG, extreme panorama, rotated EXIF, a sign and a receipt for
  text-extraction checks.

##### 59. `test/fixtures/providers/<provider>/*.json` (Create)

- **Type / Scope**: Recorded responses.
- **Proposed Changes**: Error responses per provider (for example Gemini
  403 and safety 400, Anthropic spend-limit 400, 429, 529 and CORS 401,
  OpenAI billing 429 and 503), plus normal streams, mid-stream errors and
  refusals. Each carries its recording date.

##### 60. `src/features/providers/__tests__/contract.test.ts` (Create)

- **Type / Scope**: Shared adapter contract test.
- **Proposed Changes**: Runs every adapter against its fixtures: message
  order and image placement, history trimming, no AI SDK types in output,
  request ID captured, served model reported, abort stops the stream, each
  error fixture produces one `error` event with the expected `phase`,
  `httpStatus`, `providerCode` and `requestId`, no key in `detail`, and no
  second request (no retries).

##### 61. Unit tests next to each module (Create)

- **Type / Scope**: Jest tests.
- **Proposed Changes**: `extractedText.test.ts`, `toPcError.test.ts`,
  `tuning.test.ts` (report limits match the literals in `firestore.rules`),
  `historyTrim.test.ts`, `providerFetch.test.ts` (allowlist, redirects,
  Origin removed), `pipeline.test.ts` (fixtures, metadata removed, size
  cap), `remoteConfig.test.ts` (offline first launch shows the
  config-unavailable screen, an invalid value shows it too, throttled fetch
  uses the last activated values, `baseUrl` outside allowlist rejected, a
  disabled saved model opens setup, no update listener registered, `1.10.0`
  versus `1.9.0`), `files.test.ts`
  (interrupted write, leftover temporary file, corrupt files),
  `installMarker.test.ts` (missing versus unreadable marker, upgrade without
  wipe), `consentStore.test.ts` (write failure blocks send, version bump
  re-asks), `telemetry.test.ts` (nothing logged while off, user ID cleared,
  marker string never reaches Crashlytics), `sessionStore.test.ts`
  (every state transition in §5, stopped and failed answers excluded from
  history, a failed answer keeps its partial text),
  `reportService.test.ts` (no image field, `instanceId` only when sharing,
  limit reached, unconfirmed write shown as failed, persistence off).

##### 62. Screen tests: `src/app/__tests__/setup.test.tsx`, `home.test.tsx`, `settings.test.tsx` (Create)

- **Type / Scope**: React Native Testing Library tests.
- **Proposed Changes**: Written with `screen`, `getByRole` with names and
  `findBy*` for async content, no side effects in `waitFor`. Cover CUJs
  1–9 with the fake provider: first launch, consent once per provider, copy
  text card, Stop, the generic error message and its details, new photo
  confirmation, report flow, kill switch at start, layout switch at the
  width margin, config-unavailable screen.

##### 63. `test/rules/reports.rules.test.ts` (Create)

- **Type / Scope**: Firestore emulator test.
- **Proposed Changes**: Valid create allowed; read, update, delete and list
  denied; missing or extra fields, wrong types, oversize `reason`, a
  `conversation` list longer than the limit, wrong `createdAt`, and any
  image field rejected. (Oversize text inside a list item cannot be
  rejected by rules; this is the accepted trade-off of the list shape.)

#### Benchmark harness

##### 64. `harness/run.ts`, `harness/score.ts`, `harness/README.md`, `harness/datasets/` (Create)

- **Type / Scope**: Node tool outside the app.
- **Proposed Changes**: Runs a labelled photo set through the same adapters
  (under Node) at several long-edge sizes and detail settings; records
  served model, settings, prompt version, time to first token, total time,
  tokens, and error `phase` and HTTP status; scores extracted text against ground truth
  (character error rate). Writes one CSV per run to `harness/results/`
  (ignored by git). **Why:** G23, the primary measure, and the data for
  the image-size decision (Q5).

#### Documentation

##### 65. `README.md` (Create)

- **Type / Scope**: Project readme.
- **Proposed Changes**: How to set up, run the dev client, run tests,
  publish config, and build for TestFlight and Play internal; links to this
  design and the brief.

##### 66. `docs/VERSIONS.md` (Create)

- **Type / Scope**: Version table.
- **Proposed Changes**: The Phase 0 pins (Expo SDK, React Native,
  Reanimated, sheet library, AI SDK and provider packages, React Native
  Firebase), the reason for each, and the rule that the AI SDK and React
  Native AI are upgraded together in Phase 5.

##### 67. `docs/spikes/phase0.md` (Create)

- **Type / Scope**: Spike results.
- **Proposed Changes**: Pass or fail with evidence for: AI SDK setup checks
  from a release build for all three providers (custom fetch accepted,
  streaming, abort, error bodies and headers readable, mid-stream errors,
  no Origin header, polyfills needed or not, Gemini endpoint and version,
  OpenAI API choice); sheet library comparison; image-size accuracy;
  Anthropic caching fields; `getAppInstanceId` with collection off and on;
  Crashlytics unsent reports; Firestore persistence off leaves no report
  text on disk; atomic file move;
  whether pre-opt-in crash reports are sent after opting in. **Why:** these
  confirm the AI SDK setup (Q1), answer Q2, Q3 and Q5, and confirm the Q10
  behavior. A failed AI SDK check is worked around inside the AI SDK setup
  (AR1).

##### 68. `docs/privacy-policy.md` (Create; hosted copy linked from `privacy_policy.url`)

- **Type / Scope**: Privacy policy.
- **Proposed Changes**: Per provider: what is sent, retention, training and
  human review, region notes; what Firebase collects (config, opt-in
  analytics and crash data, report text and retention, linked
  instance ID when sharing is on); keys and photos stay on the device;
  18+; deletion requests by report ID through the support contact.

##### 69. `docs/store-forms.md` (Create)

- **Type / Scope**: Compliance notes.
- **Proposed Changes**: App Privacy label answers, Play Data safety
  answers, age rating answers, and the TestFlight and Play internal test
  notes.

##### 70. `docs/reviewer-key-runbook.md` (Create)

- **Type / Scope**: Operations runbook.
- **Proposed Changes**: Creating a spend-capped key for Beta App Review,
  where it is given to reviewers, rotation after each review, and what to do
  if a key is reported leaked.

##### 71. `THIRD_PARTY_NOTICES.md` (Create)

- **Type / Scope**: Licence notices.
- **Proposed Changes**: Lucide (ISC) and other copied assets.

##### 72. `.gitignore` (Create)

- **Type / Scope**: Ignore rules.
- **Proposed Changes**: Node modules, native build folders, `.env*`,
  `harness/results/`, CI key files.

### Verification Plan

- **Compilation/Build**: `npm run typecheck`; `npx expo install --check`;
  `npx expo prebuild --clean` then `npm run check:permissions`;
  `eas build --profile preview` for iOS and Android on each phase end.
- **Unit Tests**: `npm test` (Jest with `jest-expo`), including the shared
  provider contract test.
- **Static Analysis & Linting**: `npm run lint` (layering, telemetry and
  barrel rules); `npm run config:validate`.
- **Test Coverage Requirement**: 80% line coverage for `src/features/` and
  `src/platform/`; every recorded error fixture has a contract test.
- **E2E/Integration**: `npm run test:rules` (Firestore emulator); screen
  tests for CUJs 1–9 with the fake provider; the daily canary from Phase 3;
  the manual smoke test with live keys (invalid key, revoked key, old
  unrestricted Gemini key, spend-capped key per provider, re-routed Gemini
  model, oversize image); a proxy check that a build with sharing off sends
  no Analytics or Crashlytics traffic; an iOS reinstall on a physical device
  wipes keys.
- **Measurements**: `npm run harness` before Phase 2 ends (baseline) and
  before each tester release; time to first word, photo preparation time,
  cold start and frame rate recorded on the oldest supported phone.

---

_Drafted with EGM_
