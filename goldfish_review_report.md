# Goldfish Annotated Review Report

Status: resolved (round 1, 2026-11-17). Author chose "auto accept": 46 PROPOSE FIX items applied to the design (and synced to `design_draft.md`); 6 DECLINE items not applied; 1 ESCALATE item (M13) settled by the author: the privacy policy is bundled with the app; the support email is supplied when application coding is complete (file 51). Backup of the pre-fix design: session scratchpad `native/photocritic.pre-stage9.md`.

**Target:** `docs/designs/photocritic.md` (lines 1334–2087, Detailed
Implementation Plan), Stage 9 round 1. Findings and their sources are in
[implementation_readiness_report.md](implementation_readiness_report.md).

**Summary:** 53 consolidated findings (2 Blockers, 28 Majors, 23 Nits).
Suggestions: 46 PROPOSE FIX, 1 ESCALATE, 0 CONTEST, 6 DECLINE. No finding
was contested: each one is either a real gap in the plan or a decision the
plan left open.

Two fixes (B2 and M6) also change text outside the reviewed range (Open
Questions Q4 and Q13, §5, §8), because they close decisions recorded there.

## Escalated (needs your knowledge)

### [M13] [`egm-implementation-readiness`, `egm-codebase-details`] - Severity: Major
*   **Affected Scope:** [Lines 2013-2021](docs/designs/photocritic.md#L2013-L2021) (file 51), line 1843 (Settings)
*   **Discrepancy Description:** The privacy policy's hosting location and the support contact (for deletion requests by report ID; brief L13) are not chosen.
*   **Rationale:** Apple 5.1.1 and the report confirmation both need a reachable policy URL and contact. Firebase Hosting would add a Firebase service the Non-Goals exclude.
*   ***AI Suggestion:*** ESCALATE
*   ***Justification:*** The finding is valid, but the answer is yours: a support email address and where the policy is published (for example GitHub Pages from this public repo, at `https://yogeshkolte.github.io/photocritic/privacy`). I cannot pick a contact address for you.
*   ***Proposed Edit:*** After you answer: name the host and address in file 51, add `docs/index.md` or the Pages source to the file list, and show the contact in Settings.

## Blockers

### [B1] [`egm-codebase-details`, `egm-implementation-readiness`] - Severity: Blocker
*   **Affected Scope:** [Lines 1485-1510](docs/designs/photocritic.md#L1485-L1510) (files 6, 8), 1747–1750 (file 30), 1802–1808 (file 36)
*   **Discrepancy Description:** The report document schema is not defined (fields, types, size limits, `category` values, error fields, `expireAt` bound, server timestamp placement, conversation cutting, resend and daily-limit rules). The item limit in the rules can reject long conversations.
*   **Rationale:** Two apps, the rules and the emulator tests must agree on one schema; choosing it is an automatic fail under the standard.
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Justification:*** Valid. Brief K.6 has a table, but `errorClass`/`errorDetails` conflict with §7 and the limits are missing.
*   ***Proposed Edit:***
    *   Location: file 30, after "`ReportBuilder` builds the §10 document".
    *   Change: add a field table and rules:

        | Field | Type | Limit |
        | --- | --- | --- |
        | `reportId` | string, equals document ID | 36 (UUID) |
        | `createdAt` | timestamp | rules: `== request.time` |
        | `expireAt` | timestamp | rules: `== request.time + duration.value(REPORT_RETENTION_DAYS, 'd')` |
        | `appVersion`, `osVersion` | string | 32 each |
        | `platform` | `android` or `ios` | |
        | `locale` | string | 35 |
        | `configVersion` | int | |
        | `promptVersion` | string | 16 |
        | `provider` | `openai`, `anthropic` or `gemini` | |
        | `requestedModel`, `servedModel` | string (`servedModel` optional) | 100 each |
        | `providerRequestId` | string, optional | 200 |
        | `imageSettings` | map: `longEdgePx` int, `detail` string (16), `bytes` int | |
        | `conversation` | list of `{role: user or assistant, text}` | at most `HISTORY_TURNS_CAP × 2 + 2` items |
        | `reportedTurnIndex` | int | |
        | `category` | `offensive`, `wrong`, `unsafe`, `other` | |
        | `reason` | string | 1,000 |
        | `error` | optional map: `phase` (enum), `httpStatus` (int or null), `providerCode` (100), `requestId` (200), `detail` (200) | |
        | `instanceId` | string, optional, only when sharing is on | 64 |

    *   `ReportBuilder` (core) fills every field except `createdAt` and `expireAt`; `ReportService` (app) sets both from the Firestore server-timestamp sentinel, `expireAt` with the matching offset written by the rules check (if the SDK cannot add an offset to the sentinel, `expireAt` is omitted from the document and `purge-reports` deletes by `createdAt + REPORT_RETENTION_DAYS`; Phase 0 checks which).
    *   Conversation: the reported turn and the turns before it, newest first, dropping the oldest whole turns until both the item limit and `MAX_REPORT_CONVERSATION_CHARS` hold; if the reported turn alone is too long, its answer text is cut at the end. Stopped and failed turns are included with their label in the text.
    *   Each Send report attempt gets a new `reportId`; a write shown as failed after the timeout may still arrive, so duplicates are possible and accepted (the console shows both). `ReportLimit` counts attempts per UTC day.
    *   `shared/vectors/report-document.json` holds one input and the expected document (without the timestamps), used by both apps (see M4).

### [B2] [`egm-implementation-readiness`, `egm-codebase-details`] - Severity: Blocker
*   **Affected Scope:** [Lines 1394-1406](docs/designs/photocritic.md#L1394-L1406), 1728–1739 (file 29), 1810–1844 (files 37–38); also Open Questions Q4 and Q13 and §5
*   **Discrepancy Description:** Session lifecycle (Q4) and the screen-state list (Q13) are open; `image-error` has no exit; failed consent write behavior is undefined.
*   **Rationale:** `SessionMachine`, both ViewModel/model files and their tests cannot be written without these answers; Phase 2 depends on them.
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Justification:*** Valid. Q4 and Q13 were deferred to development (decision 27), and development starts with this plan. The proposal below picks the simplest behavior consistent with "no stored conversation"; it decides product behavior, so check it before accepting.
*   ***Proposed Edit:***
    *   Location: §5 (bullets) and file 29; mark Q4 and Q13 resolved.
    *   Change:
        *   `image-error`: shows the generic message with Dismiss and "Show details"; Dismiss returns to `no-photo`.
        *   Backgrounding: a streaming answer keeps running while the process lives. If the OS stops the connection (iOS suspends network work shortly after backgrounding), the answer ends as Failed with its partial text.
        *   Android rotation and window resize: the ViewModel survives; the stream continues.
        *   Process death (either platform): the conversation is gone; the app starts at `no-photo`. Nothing is restored (Non-Goals: no stored conversation).
        *   Failed consent write: the generic message; the question is not sent and stays in the composer.
        *   Q13 state list = the §5 states plus `offline` (banner) and `disabled` (full screen); `SessionMachineTest` covers every arrow in the §5 diagram and the five rules above.

## Majors

### [M1] - Severity: Major
*   **Affected Scope:** [Lines 1368-1373](docs/designs/photocritic.md#L1368-L1373), 1425–1436, 1523–1528
*   **Discrepancy Description:** Tuning values are deleted before `shared/tuning.json` exists; format and ownership of each value unstated.
*   **Rationale:** Phase 0 CI and three tests need the file.
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Justification:*** Valid.
*   ***Proposed Edit:*** File 10: "Created in the teardown commit (file 1) from `src/platform/config/tuning.ts` at commit `a1f4c49`. Format: `{ "NAME": { "value": number, "unit": "ms" | "px" | "chars" | "turns" | "count" | "days" | "ratio", "status": "fixed" | "provisional" } }`. The author sets `MAX_QUESTION_CHARS`, `MAX_REPORT_CONVERSATION_CHARS`, `REPORT_DAILY_LIMIT_PER_INSTALL` and `REPORT_RETENTION_DAYS` before Phase 1 ends; Phase 0 measurements set `HISTORY_TURNS_CAP`, `CONNECT_TIMEOUT_MS`, `REQUEST_TIMEOUT_MS` and `REPORT_WRITE_TIMEOUT_MS`." Phase 0 step 1 lists the file.

### [M2] - Severity: Major
*   **Affected Scope:** [Lines 1516-1564](docs/designs/photocritic.md#L1516-L1564), 1849–1854
*   **Discrepancy Description:** Fixture format, request inputs, test image, Remote Config sample shape and comparison rule undefined.
*   **Rationale:** Both apps must read the same fixtures the same way (AR4's mitigation).
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Justification:*** Valid.
*   ***Proposed Edit:*** Add `shared/fixture.schema.json` and `shared/images/test.jpg` (a 1024 px photo of printed text, author-owned) to the file list. Fixtures encode events as `{"type":"text","delta":"…"}`, `{"type":"done","finishReason":"stop"|"length","servedModel":"…","requestId":"…"|null}`, `{"type":"error","error":{"phase":"before-response"|"mid-stream"|"finish","httpStatus":int|null,"providerCode":string|null,"requestId":string|null}}`; `detail` is not compared, only checked to contain no key. Each request fixture has a `<name>.input.json` (`modelId`, `history`, `question`, `promptKind`, `maxOutputTokens`, `imageDetail`, `image` path). Bodies are compared as parsed JSON, key order ignored. Remote Config samples and `template.json` are a plain object `{parameterName: parsedJsonValue}`; `publish-remote-config` converts to the REST template shape.

### [M3] - Severity: Major
*   **Affected Scope:** [Lines 1776-1777](docs/designs/photocritic.md#L1776-L1777), 2001–2009
*   **Discrepancy Description:** K.5 events conflict with G12, §7 and G15; no triggers; reserved-name check dropped.
*   **Rationale:** Events must match the design and be identical on both apps.
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Justification:*** Valid.
*   ***Proposed Edit:*** Add `shared/telemetry/events.json` (file list) and reference it from file 33 and the iOS telemetry. Events, all prefixed `pc_`: `pc_session_start` (`provider`, `model`, `config_version`; when home first shows after a cold start), `pc_image_prepared` (`source`, `input_format`, `prepare_ms`, `sent_long_edge_px`, `sent_bytes`; after the send copy is built), `pc_question_sent` (`provider`, `model`, `turn_index`, `starter_prompt`; on Send), `pc_answer_received` (`provider`, `model`, `time_to_first_token_ms`, `total_ms`, `input_tokens`, `output_tokens`, `finish_reason`; on `done`), `pc_request_failed` (`provider`, `model`, `phase`, `http_status`; on `error`), `pc_report_submitted` (`provider`, `model`, `category`, `result`: `sent`, `failed`, `limit_reached`), `pc_config_fetch` (`result`: `success`, `throttled`, `failed`, `timeout`, `invalid`; `config_version`, `ms`). Removed: `error_class`, `retry_count`, `queued`, `consent_recorded`, `cached_tokens` (until Q5 decides caching). File 50 keeps the reserved-names check.

### [M4] - Severity: Major
*   **Affected Scope:** [Lines 1684-1691](docs/designs/photocritic.md#L1684-L1691), 1514–1571
*   **Discrepancy Description:** Logic written twice has no shared constants or vectors.
*   **Rationale:** The two apps will diverge.
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Justification:*** Valid; this is the AR4 mitigation the plan promised but did not list.
*   ***Proposed Edit:*** Add to the file list: `shared/token-estimates.json` (per provider `charsPerToken`, and the image-token rule: OpenAI tile rule parameters, Anthropic width × height ÷ 750, Gemini tokens per `mediaResolution`; values set in Phase 0 from recorded `usage` fields), and `shared/vectors/history-trim.json`, `extracted-text.json` (unclosed fence, several cards, tilde fence, fence in the middle of a line), `app-version.json`, `report-document.json`. File 25 and its iOS counterpart read them; the contract tests list them.

### [M5] - Severity: Major
*   **Affected Scope:** [Lines 1566-1571](docs/designs/photocritic.md#L1566-L1571), 1692–1694
*   **Discrepancy Description:** Redaction patterns not defined.
*   **Rationale:** A security choice left to whoever writes `cases.json`.
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Justification:*** Valid.
*   ***Proposed Edit:*** File 14: "In order: (1) the user's own key string, wherever it appears; (2) `sk-ant-[A-Za-z0-9_-]{16,}`, `sk-[A-Za-z0-9_-]{16,}`, `AIza[0-9A-Za-z_-]{35}`, `AQ\.[0-9A-Za-z_-]{20,}`, `Bearer\s+\S+`, each replaced by `[key]`; (3) the query string of any URL replaced by `?[query]`; (4) `file://`, `content://` and absolute paths with two or more segments replaced by `[path]`; then cut to 200 characters." The `AQ.` pattern is the Gemini key format seen on 2026-11-17.

### [M6] - Severity: Major
*   **Affected Scope:** [Lines 1697-1705](docs/designs/photocritic.md#L1697-L1705), 1823–1826; §8
*   **Discrepancy Description:** Model-list, kill-switch and consent display rules missing; several parameters have no consumer; two consent-version fields.
*   **Rationale:** Implementer would decide user-visible behavior.
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Justification:*** Valid.
*   ***Proposed Edit:*** Add a rules table to file 26:
    *   `status`: `active` (shown), `preview` (shown, label "Preview"), `deprecated` (shown, label "Ending soon", with "Replaced by <displayName>" from `replacementId`), `retired` (hidden). The validator requires `replacementId` to name an `active` model.
    *   A model is hidden when `retired`, listed in `disabledModels`, its `minAppVersion` is above the app version, or its provider is disabled. A provider is disabled when `providers.<p>.enabled` is false or it is in `disabledProviders`. A saved model or provider hidden for any of these reasons takes the G18 path.
    *   Setup's kill-switch message is `kill_switch.message` if not empty, otherwise "The model you used is no longer available. Pick another one."
    *   A non-empty `bannerMessage` shows as a banner under the home top bar, dismissible for the session. The update-required screen's Update button opens `updateUrl`.
    *   Consent version: `consent_texts.<p>.version` is the version the gate compares; the validator requires `providers.<p>.consentTextVersion` to equal it. The consent sheet shows `summary[]` as bullets, `effectiveDate`, and a link to `policyUrl`. `privacy_policy.version` is shown in Settings next to the link.

### [M7] - Severity: Major
*   **Affected Scope:** [Lines 1720-1726](docs/designs/photocritic.md#L1720-L1726), 1810–1815, 1864–1867, 1975–1976
*   **Discrepancy Description:** No dependency-injection approach; fakes and fake selection unspecified; FakeProvider `dataLeavesDevice` unset.
*   **Rationale:** Screen tests for CUJs 1, 6, 7 and 9 cannot be written.
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Justification:*** Valid.
*   ***Proposed Edit:*** Add: each app has a hand-written `AppContainer` (Android `android/app/.../AppContainer.kt`, created in a custom `Application`; iOS `ios/PhotoCritic/AppContainer.swift`, passed through the SwiftUI environment) holding interfaces `ConfigSource`, `KeyStore`, `ReportSink`, `ProviderRegistry`, `Telemetry`, `Connectivity`. ViewModels get it through a `viewModelFactory`. Fakes live in `android/app/src/devDebug/` (`FakeContainer.kt`) and `ios/PhotoCritic/Debug/FakeContainer.swift` (compiled only in `Debug-Dev`). Android UI tests build `AppContainer` with fakes directly; iOS UI tests pass the launch argument `-PCUseFakes YES`. `FakeProvider` has `dataLeavesDevice = true`.

### [M8] - Severity: Major
*   **Affected Scope:** [Lines 1451-1458](docs/designs/photocritic.md#L1451-L1458), 1864–1867, 2073–2074
*   **Discrepancy Description:** No CI runner for UI tests; `xcodebuild` lacks scheme and destination; no schemes.
*   **Rationale:** The CUJ tests would never run in CI.
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Justification:*** Valid.
*   ***Proposed Edit:*** File 39: "Compose UI tests live in `app/src/test` and run on Robolectric inside `testDevDebugUnitTest`; there is no emulator job in v1." File 41: define scheme `PhotoCritic-Dev` (build configuration `Debug-Dev`, tests `PhotoCriticTests` and `PhotoCriticUITests`). File 3: `xcodebuild test -project ios/PhotoCritic.xcodeproj -scheme PhotoCritic-Dev -destination "$(ios/scripts/sim-destination.sh)"`, where the script picks the newest available iPhone simulator. An iOS 17.0 simulator run is part of the manual release checks.

### [M9] - Severity: Major
*   **Affected Scope:** [Lines 1536-1537](docs/designs/photocritic.md#L1536-L1537), 1941–1942, 1970–1972
*   **Discrepancy Description:** Swift Package Manager resources must be inside the target folder; Swift tests' path to `shared/` unstated.
*   **Rationale:** As written, the iOS build cannot bundle the prompts.
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Justification:*** Valid.
*   ***Proposed Edit:*** File 11: "iOS keeps a committed copy in `ios/Core/Sources/PhotoCriticCore/Resources/prompts/`, written by `tools/sync-shared.sh`; CI fails if `diff -r shared/prompts ios/Core/Sources/PhotoCriticCore/Resources/prompts` finds a difference." File 47: Swift tests find the repository root from `#filePath` and read `shared/` from there. Add `tools/sync-shared.sh` to file 15.

### [M10] - Severity: Major
*   **Affected Scope:** [Lines 1606-1621](docs/designs/photocritic.md#L1606-L1621)
*   **Discrepancy Description:** `checkPermissions` ambiguous; Firebase-merged and ad-services permissions not handled; `AD_ID` short name.
*   **Rationale:** G16 and the Data safety answers depend on the merged manifest.
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Justification:*** Valid.
*   ***Proposed Edit:*** File 17: "`tools:node="remove"` for `com.google.android.gms.permission.AD_ID`, `android.permission.ACCESS_ADSERVICES_AD_ID`, `android.permission.ACCESS_ADSERVICES_ATTRIBUTION`, `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `RECORD_AUDIO`. `checkPermissions` reads the merged `prodRelease` manifest and fails on any permission outside an allowlist: `INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE`, `com.yk.photocritic.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. Phase 0 confirms the list from the first merged manifest and records it in the task."

### [M11] - Severity: Major
*   **Affected Scope:** [Lines 1350-1352](docs/designs/photocritic.md#L1350-L1352), 1468–1469, 1599–1605, 1887–1896, 2051–2053
*   **Discrepancy Description:** No signing setup; phase-end signed builds impossible on iOS for now; Swift live run has no runner.
*   **Rationale:** Verification Plan cannot be met as written.
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Justification:*** Valid.
*   ***Proposed Edit:*** File 17: `signingConfigs.release` reads `android/keystore.properties` (ignored by git; holds the path to the upload keystore outside the repository and its passwords); without it, release builds are unsigned (CI). File 41: automatic signing; `DEVELOPMENT_TEAM` from `ios/Config/Signing.xcconfig` (ignored by git). Verification Plan: "a signed Android build at the end of every phase from Phase 1; a signed iOS build once the Apple Developer account exists, and at the latest at the end of Phase 4." File 47: a `PhotoCriticCoreLiveTests` target that is skipped unless `PC_LIVE_OPENAI_KEY`, `PC_LIVE_ANTHROPIC_KEY` or `PC_LIVE_GEMINI_KEY` is set, run by hand with `swift test --filter Live` before each tester release; file 4 points to it.

### [M12] - Severity: Major
*   **Affected Scope:** [Lines 1869-1882](docs/designs/photocritic.md#L1869-L1882), 1445, 1465–1469
*   **Discrepancy Description:** Harness locations, formats, sizes, JVM image encoding, canary inputs undefined; `.gitignore` misses the results path; "one harness is enough" overclaims.
*   **Rationale:** Q5 measurements would be made with undefined encoding.
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Justification:*** Valid.
*   ***Proposed Edit:*** File 40: code in `android/harness/` (`build.gradle.kts`, `Main.kt`, `Run.kt`, `Canary.kt`, `Score.kt`); data in repository-root `harness/`: `harness/datasets/<set>/images/` (JPEG or PNG only) and `truth.json` (`{file: expectedText}`); photos are committed only if the author owns them and they contain no personal data, otherwise `PC_HARNESS_DATASET` points to a folder outside git; results in `harness/results/` (ignored as `/harness/results/`). Sizes: long edge 1024, 1536, 2048, 3072; settings: OpenAI `low`/`high`, Gemini `MEDIA_RESOLUTION_LOW`/`MEDIUM`/`HIGH`, Anthropic none. The harness downsizes with `javax.imageio` (bicubic) and encodes JPEG at `JPEG_QUALITY` with no metadata; Phase 0 compares its output bytes with the Android pipeline for five photos and records the difference. CSV columns: `run_id, provider, requested_model, served_model, prompt_version, image, long_edge_px, detail, bytes, ttft_ms, total_ms, input_tokens, output_tokens, finish_reason, error_phase, http_status, cer`. Canary: models with `status` `active` from `shared/remote-config/template.json`, photo `shared/images/test.jpg`, served model matches when equal or when the requested ID is a prefix of it, secrets `PC_CANARY_OPENAI_KEY`, `PC_CANARY_ANTHROPIC_KEY`, `PC_CANARY_GEMINI_KEY`, schedule daily 05:00 UTC. Replace the "Why" sentence with "One harness measures the models; the shared request fixtures show both apps send the same request bodies. Differences between the Android and iOS JPEG encoders are not measured." Measurements: the baseline before Phase 2 ends covers Gemini; the full baseline is taken before Phase 4 ends.

### [M14] - Severity: Major
*   **Affected Scope:** [Lines 1366-1392](docs/designs/photocritic.md#L1366-L1392), 1448–1460
*   **Discrepancy Description:** No file-to-phase mapping; Phase 0 CI needs files no step creates; Phase 0 Gemini client depends on unscheduled files.
*   **Rationale:** "Each phase ends with both apps passing CI" fails at Phase 0.
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Justification:*** Valid.
*   ***Proposed Edit:*** Add a table after the phase list: Phase 0: files 1–3, 5, 7, 9–13 (schema and template), 15 (`validate-config`, `record-fixture`, `sync-shared.sh`), 16–25, 27 (Gemini only; kept, not thrown away), 41–45 (Net, Providers types and shared, Gemini), 49, 50. Phase 1: 26, 31–35, 38 and 46 (startup, `DisabledScreen` only). Phase 2: 28, 29, 30 (consent part), 37, 38 and 46 (setup, home, consent sheet), 39 and 47 (as their subjects arrive). Phase 3: 27 and 45 (Anthropic, OpenAI), 4, 40. Phase 4: 6, 8, 30 (report part), 36, 38 and 46 (Settings, report sheet, wide layout), 48, 51–54. File 3: the `android` job sets `working-directory: android`.

### [M15] - Severity: Major
*   **Affected Scope:** [Lines 1369-1370](docs/designs/photocritic.md#L1369-L1370), 1431
*   **Discrepancy Description:** `assets/` deletion depends on files created later.
*   **Rationale:** The one-commit teardown cannot honor it.
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Justification:*** Valid.
*   ***Proposed Edit:*** File 1: remove `assets/` from the delete list; "`assets/` stays as the icon source until Phase 4, when files 22 and 46 hold the final icons; it is deleted then."

### [M16] - Severity: Major
*   **Affected Scope:** [Lines 1441-1446](docs/designs/photocritic.md#L1441-L1446)
*   **Discrepancy Description:** `.gitignore` rewrite could keep `/ios` and `/android` and drop secret rules.
*   **Rationale:** Either ignores the new apps or loses protection for signing secrets.
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Justification:*** Valid; `.gitignore` today has `/ios` and `/android`.
*   ***Proposed Edit:*** File 2: "Remove `/ios`, `/android`, `node_modules/` at the root, `.expo/`, `dist/`, `web-build/`, `expo-env.d.ts`, `.metro-health-check*`, `*.tsbuildinfo`. Keep `.DS_Store`, `*.p8`, `*.p12`, `*.key`, `*.pem`, `*.mobileprovision`, `*.jks`, `*.keystore`, `.env*`, `!.env.example`, `firebase-debug.log`, `firestore-debug.log`, `*-service-account.json`, `/TokenEconomizer/`, `/research/raw/`. Add `.gradle/`, `build/`, `local.properties`, `.idea/`, `*.iml`, `xcuserdata/`, `DerivedData/`, `ios/*.xcodeproj`, `tools/node_modules/`, `firebase/rules-test/node_modules/`, `/harness/results/`, `android/keystore.properties`, `ios/Config/Signing.xcconfig`."

### [M17] - Severity: Major
*   **Affected Scope:** [Lines 1928-1934](docs/designs/photocritic.md#L1928-L1934), 1454–1455
*   **Discrepancy Description:** `Package.swift` platforms and tools version missing.
*   **Rationale:** `swift test` builds for macOS and needs the async APIs.
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Justification:*** Valid.
*   ***Proposed Edit:*** File 45: "`// swift-tools-version: 6.0`; `platforms: [.iOS(.v17), .macOS(.v14)]`; Swift 6 language mode; targets `PhotoCriticCore` (resources: `Resources/prompts`), `PhotoCriticCoreTests`, `PhotoCriticCoreLiveTests`."

### [M18] - Severity: Major
*   **Affected Scope:** [Lines 1641-1645](docs/designs/photocritic.md#L1641-L1645), 1923–1926
*   **Discrepancy Description:** Lint rules cannot be built as written or contradict §2.
*   **Rationale:** The layering rules would not be enforced.
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Justification:*** Valid.
*   ***Proposed Edit:*** File 20: "`ForbiddenImport`: Analytics and Crashlytics outside `platform.telemetry`; `okhttp3` outside `core.net` (`ProviderHttp` exposes its own request and response types to providers). `ForbiddenMethodCall`: `kotlin.io.println`, `android.util.Log.*` outside `platform.telemetry`." File 44: custom rules with `included` paths `ios/Core/Sources/PhotoCriticCore/` and `ios/PhotoCritic/`, and `excluded` paths for `Net/` and `Platform/Telemetry/`. File 33 and its iOS counterpart expose `instanceIdOrNull()`, used by `ReportService` and Settings.

### [M19] - Severity: Major
*   **Affected Scope:** [Lines 1575-1597](docs/designs/photocritic.md#L1575-L1597), 1358–1360, 1453, 1505, 1988–1996, 2060–2062
*   **Discrepancy Description:** Build, coverage and Node tools unnamed.
*   **Rationale:** Implementer would pick tools.
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Justification:*** Valid.
*   ***Proposed Edit:*** File 16: ktlint through `org.jlleitschuh.gradle.ktlint`; coverage with Kover on `:core`, `koverVerify` at 80% lines in CI; JUnit 5 in `:core` and `:harness`, JUnit 4 in `:app`. File 45/47: `swift test --enable-code-coverage` and a CI step that fails below 80% (from `llvm-cov report`). File 15: npm scripts `config:validate`, `config:publish -- --project dev|prod` (authenticates with `GOOGLE_APPLICATION_CREDENTIALS`, sends the template ETag with `If-Match`), `fixture:record`, `reports:purge`; dependencies `typescript`, `tsx` 4.23.15, `ajv` 8, `firebase-admin`. File 8: `jest`, `@firebase/rules-unit-testing` 5.0.2, `firebase-tools` 15.36.0; run with `firebase emulators:exec --only firestore --project demo-photocritic "npm test"`; the `shared` job installs JDK 17. File 3: XcodeGen and SwiftLint installed from their release binaries at the versions in `docs/VERSIONS.md`.

### [M20] - Severity: Major
*   **Affected Scope:** [Lines 1848-1863](docs/designs/photocritic.md#L1848-L1863), 1970–1974
*   **Discrepancy Description:** Missing tests for privacy-critical code.
*   **Rationale:** Metadata removal, key wipe and kill switch are untested.
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Justification:*** Valid.
*   ***Proposed Edit:*** File 39 adds `AllowlistTest`, `KillSwitchTest`, `RegistryTest`, `ReportLimitTest` (core); `ImagePipelineTest` (Robolectric: orientation, no EXIF in output, size cap, re-encode, HEIC and AVIF rejection by API level), `CleanupTest`, `RemoteConfigLoaderTest` (throttled path moves here, with a fake Remote Config), `SecureKeysTest` (instrumented, run by hand on an API 24 and a current device each phase, because Robolectric has no Keystore). `checkPermissions` runs in CI. File 47 mirrors these (Keychain tests run in the simulator).

### [M21] - Severity: Major
*   **Affected Scope:** Lines 1540, 1655, 1846, 1869, 1928, 1945, 1967
*   **Discrepancy Description:** Directory or glob entries; missing string resources, `proguard-rules.pro`, schemes, harness files.
*   **Rationale:** The standard needs exact paths for the write allowlist.
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Justification:*** Valid in part: recorded fixtures and sample folders are generated or open-ended sets, and listing each test file name is covered by a naming rule.
*   ***Proposed Edit:*** Add `android/app/src/main/res/values/strings.xml`, `ios/PhotoCritic/Localizable.xcstrings`, `android/app/proguard-rules.pro` (kotlinx.serialization keep rules); harness files as in M12; schemes as in M8. Add the rule: "Each source file has one test file in the mirror test path, named `<Source>Test.kt` or `<Source>Tests.swift`." File 46: list the store files (`SettingsStore.swift`, `ConsentStore.swift`, `ReportStore.swift`) and screen files by name, matching file 38.

### [M22] - Severity: Major
*   **Affected Scope:** [Lines 1710-1716](docs/designs/photocritic.md#L1710-L1716), 1560
*   **Discrepancy Description:** OpenAI refusal events not mapped.
*   **Rationale:** A refusal would show as an empty normal answer.
*   ***AI Suggestion:*** PROPOSE FIX
*   ***Justification:*** Valid.
*   ***Proposed Edit:*** File 27 and §7: "OpenAI `response.refusal.delta` text is collected; a response that ends with refusal content is a `finish` failure with `providerCode` `refusal`, and the refusal text stays visible as the Failed answer." Add the event to the §3a end-events row.

### [M23] - Severity: Major
*   **Affected Scope:** [Lines 1744-1747](docs/designs/photocritic.md#L1744-L1747)
*   **Discrepancy Description:** Consent gate worded backwards.
*   ***AI Suggestion:*** PROPOSE FIX
*   **Rationale:** Read literally, on-device providers could never send.
*   ***Justification:*** Valid.
*   ***Proposed Edit:*** "`canSend(provider)` is true when the provider's capabilities say data stays on the device, or when there is a granted record at or above `consent_texts.<p>.version`. 'Not now' is not recorded."

### [M24] - Severity: Major
*   **Affected Scope:** [Lines 1797-1799](docs/designs/photocritic.md#L1797-L1799)
*   **Discrepancy Description:** Re-encode quality, over-size outcome and animated GIF handling missing.
*   ***AI Suggestion:*** PROPOSE FIX
*   **Rationale:** Implementer would decide.
*   ***Justification:*** Valid.
*   ***Proposed Edit:*** File 35: "re-encode once at quality 0.6; if still over `maxBytes`, `image-error` with the generic message. Animated images use their first frame."

### [M25] [`egm-style`] - Severity: Major
*   **Affected Scope:** [Lines 1459-1460](docs/designs/photocritic.md#L1459-L1460)
*   **Discrepancy Description:** "each guarantee has an automatic check on both platforms" overclaims.
*   ***AI Suggestion:*** PROPOSE FIX
*   **Rationale:** Several checks are manual.
*   ***Justification:*** Valid.
*   ***Proposed Edit:*** "**Why:** lint, unit, contract and build checks run for both apps on every push; the manual checks are in the Verification Plan."

### [M26] [`egm-style`] - Severity: Major
*   **Affected Scope:** [Lines 1670-1671](docs/designs/photocritic.md#L1670-L1671)
*   **Discrepancy Description:** "lives in one file" is false; three files.
*   ***AI Suggestion:*** PROPOSE FIX
*   **Rationale:** Inaccurate.
*   ***Justification:*** Valid.
*   ***Proposed Edit:*** "**Why:** every security rule in §4 lives in one package (`core.net`), behind one `OkHttpClient`."

### [M27] [`egm-style`] - Severity: Major
*   **Affected Scope:** [Lines 1820-1844](docs/designs/photocritic.md#L1820-L1844)
*   **Discrepancy Description:** File 38 is one 25-line paragraph for 18 files.
*   ***AI Suggestion:*** PROPOSE FIX
*   **Rationale:** Hard to check against the CUJs.
*   ***Justification:*** Valid.
*   ***Proposed Edit:*** Split into sub-bullets: `MainActivity` and `AppNavigation`; `SetupScreen`; `HomeScreen`, `PhotoView`, `ConversationSheet`, `ConversationPane`; `MessageList` and `MessageItem`; `Composer` and `StarterChips`; `ConsentSheet` and `ReportSheet`; `SettingsScreen`; `DisabledScreen`; `design/`. Content unchanged except the fixes from M6, M28 and N5.

### [M28] [`egm-style`, `egm-implementation-readiness`, `egm-codebase-details`] - Severity: Major
*   **Affected Scope:** [Lines 1831-1832](docs/designs/photocritic.md#L1831-L1832), 1963
*   **Discrepancy Description:** 680–720 band undefined.
*   ***AI Suggestion:*** PROPOSE FIX
*   **Rationale:** The layout-switch test depends on it.
*   ***Justification:*** Valid.
*   ***Proposed Edit:*** "`LayoutMode`: at start, wide if the width is at least 700 dp (points on iOS), else phone; afterwards it switches to wide above 720 and to phone below 680, and keeps the current mode in between. The same numbers in points apply on iOS."

## Nits

| ID | AI suggestion | Proposed edit or reason |
| --- | --- | --- |
| N1 | PROPOSE FIX | "API 24–30" for AVIF in Phase 0 step 6, file 35 and file 50. |
| N2 | PROPOSE FIX | File 17: `android:fullBackupContent="@xml/backup_rules"` next to `dataExtractionRules`. |
| N3 | PROPOSE FIX | File 43: `ITSAppUsesNonExemptEncryption` = NO; privacy manifest lists file-timestamp use only (the app uses files, not `UserDefaults`), `NSPrivacyTracking` false, collected data types per `docs/store-forms.md`. |
| N4 | PROPOSE FIX | Verification Plan: traffic to `app-measurement.com`, `firebaselogging-pa.googleapis.com` and `crashlyticsreports-pa.googleapis.com` counts; Remote Config and Installations calls are expected. Phase 0 confirms the host list. |
| N5 | PROPOSE FIX | `UserMessages`: labels "Where", "HTTP status", "Provider code", "Request ID", "Details"; phase strings "Before the answer", "During the answer", "At the end of the answer". File 38 setup: the Gemini note "Checking a Gemini key uses a few tokens." |
| N6 | PROPOSE FIX | Drop `expo-env.d.ts` from file 1. |
| N7 | PROPOSE FIX | File 6: rewrite the rules file header comment to cite `shared/tuning.json`; §8a: "Firestore rules use `HISTORY_TURNS_CAP` (list length); `MAX_REPORT_CONVERSATION_CHARS` is enforced by the apps only." |
| N8 | PROPOSE FIX | Path note: "`<module>/.../<package>/` means `<module>/src/main/kotlin/com/yk/photocritic/<module-package>/<package>/`." |
| N9 | DECLINE | The Firebase Apple product name is pinned in Phase 0 with the version (file 49). |
| N10 | PROPOSE FIX | File 50: "Lands in the same commit as file 1." |
| N11 | PROPOSE FIX | File 23: the client passes the request-ID header name to `ProviderHttp`. |
| N12 | DECLINE | The label groups decisions by topic; dates are in the Open Questions entries. |
| N13 | DECLINE | One sentence listing decisions is short enough; the codes point to the full text. |
| N14 | PROPOSE FIX | "so iOS starts from Android code that already passes the shared contract tests (AR4)." |
| N15 | DECLINE | The repetition is one clause and names AR3. |
| N16 | DECLINE | Same: one clause pointing at AR2's rule. |
| N17 | PROPOSE FIX | File 38: "up to `CONFIG_FETCH_TIMEOUT_MS`". |
| N18 | PROPOSE FIX | File 28: "to test that the screens handle a final-turn-only, whole-response provider with no usage data." |
| N19 | PROPOSE FIX | File 37: "When the ViewModel is cleared, the conversation ends." |
| N20 | PROPOSE FIX | "CUJs 1–9, plus consent once per provider." |
| N21 | PROPOSE FIX | "the image carries the detail or resolution setting from the model's Remote Config entry." |
| N22 | DECLINE (partly fixed) | Term changed to "time to first token" everywhere; reference devices are chosen in Phase 0 when the test phones are known. |
| N23 | PROPOSE FIX | File 51: "OpenAI keeps abuse-monitoring logs for up to 30 days, or longer where required by law." |
