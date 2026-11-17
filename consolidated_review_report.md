# Consolidated Review Report: PhotoCritic design

* **Target:** [docs/designs/photocritic.md](./docs/designs/photocritic.md) (1,806 lines)
* **Date:** 2026-11-15
* **Mode:** Consolidated report, all review rubrics (Stages 5–9, run without checkpoints)
* **Split:** FeatureRange lines 1–1072; DetailedRange lines 1073–1806
* **Background context given to reviewers:** concepts_brief.md, mockup/ (4 files), research/README.md, research/callstack/. The private mockup URL was left out at the author's request.
* **Constraint:** Accepted Risks AR1–AR3 were not to be raised. No finding challenges them. Several findings point at text that contradicts AR2 or AR3, and those are kept.

## Stage reports

| Stage | Reviewers | Report | 🔴 | 🟡 | 🟢 |
| --- | --- | --- | --- | --- | --- |
| 5 Initial Zoom Out | zoom-out, inventive-catalyst | [zoom_out_report.md](./zoom_out_report.md) | 0 | 11 | 7 |
| 6 Comprehension | comprehension | [comprehension_report.md](./comprehension_report.md) | 0 | 6 | 5 |
| 7 Critic (feature spec) | design, structural, style | [critic_report.md](./critic_report.md) | 0 | 21 | 20 |
| 8 Intermediate Zoom Out | zoom-out | [intermediate_zoom_out_report.md](./intermediate_zoom_out_report.md) | 0 | 8 | 6 |
| 9 Implementation Readiness | implementation-readiness, codebase-details, style | [implementation_readiness_report.md](./implementation_readiness_report.md) | 2 | 23 | 28 |
| **Total raw** | | | **2** | **69** | **66** |

After merging the overlapping findings across stages: **2 Blockers, 24 Major action items, 6 Nit groups.**

**Verdict.** The design does not pass implementation readiness yet. The architecture, file list and layering hold up. What blocks implementation is missing content:
* the error table and the retry numbers;
* the v1 Remote Config values;
* the exact Firestore rules;
* App Check setup;
* the kill-switch behavior.

Implementing any of these today would mean making decisions the document does not make. The feature spec also contradicts its own no-fallback rule (AR2) in a few places.

Stage 8 reviewed the proposed fixes for over-scoping. Where it found a simpler fix, that fix is the one recommended below, and its source is noted.

## Decisions only the author can make

These affect several action items below. Each lists the options and what the reviewers recommend; nothing has been applied.

| # | Decision | Options | Reviewer recommendation | Affects |
| --- | --- | --- | --- | --- |
| A1 | Where tuning values and user-facing URLs live | (a) Remote Config as now; (b) code constants for the history cap, timeouts, retries, JPEG quality, report limits and every URL a user taps; Remote Config keeps the kill switch, models, error map and consent texts | (b) (Stage 8 Z2, Z3). Fewer values that can stop the app under AR2, no phishing path through config URLs, and about ten findings closed | C3, C4, C8, C15 |
| A2 | Config-unavailable screen | (a) "restart the app" as in AR2; (b) a Try again button that fetches again (AR2 wording changes) | (b) (Stage 5 M10) | C1, C10 |
| A3 | Live kill-switch updates | (a) keep live updates for the kill switch; (b) apply the kill switch only at cold start | Undecided by reviewers (Stage 8 Z7). (b) removes the "activate only one key" problem, because `activate()` applies the whole template | C7 |
| A4 | Message building in v1 | (a) keep option B (each provider builds its messages, token trimming); (b) one shared builder and a turn cap in v1, per-provider builders and token trimming from Phase 5 | Stage 5 (M5, M6) and Stage 8 recommend (b). This reopens the author's drafting decision, so it stays the author's call | C13 |
| A5 | Firestore offline persistence | (a) on (report text stays in an on-device cache); (b) off (queue only in memory; fewer delivery states) | (b) (Stage 8 Z13) | C5 |
| A6 | Android minimum version | (a) minSdk 26 and narrower "AVIF where supported" wording; (b) minSdk 28 | (b) (Stage 8 Z14), since HEIC decoding needs API 28 | C16 |
| A7 | Open-write report risk | (a) add it as AR4; (b) reword the Accepted Risks intro so it does not claim to be complete | Either (Stage 7 M1, Stage 8 Z1) | C23 |
| A8 | Report expiry | (a) client writes `expireAt` and the rules check it; (b) drop `expireAt` and purge by `createdAt` | (b) (Stage 8 Z12) | C4 |

## 🔴 Blockers

### C1. The document contradicts its own no-fallback rule (AR2)

* **Problem:**
  * File 40 says `classify` uses "bundled rules if invalid".
  * The throttled-fetch case reuses the last fetched values.
  * Nothing says what happens when a live update fails validation.
  * The no-fallback rule is stated about eight times, and the copies differ.
* **Fix:**
  * Delete "(bundled rules if invalid)". `classify` only ever sees a validated snapshot; a signal no rule matches becomes `unknown`.
  * Name the throttled case as the one allowed exception, using only values that passed validation, or remove it.
  * Choose one behavior for an invalid live update: ignore it until the next start, or show the unavailable screen.
  * State the rule once, in AR2 and §8, and point to it from everywhere else.
* **Sources:** S5 M1; S6 #4; S7 M19; S8 Z6, Z8; S9 B1.

### C2. The error table and the retry numbers are missing

* **Problem:** The template, `classify`, the retry policy, the fixtures, the contract tests and the coverage rule all depend on "the error table", which does not exist. §7 gives only example classes. Also missing:
  * what `match` can match (the Anthropic spend-limit 400 is recognizable only by its message text);
  * the backoff base, jitter, total wait cap and `Retry-After` cap.

  Error classes are in Remote Config while the retry rules are in code, so the two can disagree.
* **Fix:**
  * Add the table: provider, phase, HTTP status, match field and operator (including a message-substring match), internal class ID, user message, retryable.
  * Make the class IDs a closed union in `providers/types.ts` and a `z.enum` in `schema.ts`.
  * Give the retry numbers.
  * Put the retryable flag next to the class, in one place: in code if A1(b) is chosen, otherwise in the same table.
* **Sources:** S9 B2; S5 M7; S7 M16, N8; S8 Z4, Z6.

## 🟡 Major action items

### Configuration and data

* **C3. Remote Config v1 values, enum values and prompt texts are not given.**
  * Missing: `status`, `label` and `imageDetail` values; the curated model IDs; all numeric limits; the token factors and image-token formulas; the base prompt and the four prompt-kind texts; the collapsed-sheet limit; the meaning of "connect timeout".
  * **Fix:** a "Remote Config v1 template" table (field, type, v1 value, provisional until Phase 0 or not), reduced to what A1 leaves in Remote Config.
  * Sources: S9 M1; S5 N2; S8 Z2.
* **C4. The Firestore rules are not specified exactly.**
  * The field list and size limits are missing (§10 also says "user's note" where file 63 says `reason`, and the four categories are never named).
  * The list cap uses `historyTurnsCap`, which the rules cannot read.
  * `createdAt == request.time` requires `serverTimestamp()`.
  * Nothing sets `expireAt`.
  * "Check every field" overstates what the rules can do under AR3.
  * **Fix:** list every field with its type and limit; use literal numbers in the rules, with a `validate-config` check that Remote Config stays within them (unnecessary under A1(b)); write `createdAt` with `serverTimestamp()`; resolve A8; change the wording to "check every top-level field".
  * Sources: S5 M2, N3; S6 #7, #8; S7 M14; S8 Z12; S9 M2.
* **C5. Report delivery states, and the offline queue that keeps text on the device.**
  * The Firestore write resolves only on server acknowledgement, so "queued" cannot be detected as written. Persistence on by default breaks "PhotoCritic keeps nothing".
  * **Fix:** resolve A5. Race the write against a stated number of seconds: on timeout or offline return "queued", on acknowledgement "sent", on `permission-denied` "failed". Say in the privacy policy what is kept.
  * Sources: S7 M2; S8 Z13; S9 M3.
* **C6. No file sets up App Check.**
  * Unassigned: the startup order before Firestore, the provider per build profile, the App Attest production entitlement, debug tokens, the check that production has no debug provider, console enforcement, and the Play signing fingerprints.
  * **Fix:** add `src/platform/firebase/appCheck.ts`, called first in startup, plus the `app.config.ts`, `eas.json` and runbook entries.
  * Sources: S9 M4.
* **C7. The kill-switch fallback and live updates are undefined.**
  * The fallback may be on another provider (no key or consent), disabled itself, or missing when a whole provider is off.
  * Nothing aborts a running stream, no component shows the inline notice or `bannerMessage`, and `activate()` applies the whole template.
  * **Fix:** a publish rule that each `fallbackId` names an active model from the same provider (no chain-following, Stage 8 Z7). Abort the stream on any kill switch. Name the components that show the notice and banner. Resolve A3. Give each test its expected result.
  * Sources: S5 M11; S6 #5; S7 M8; S8 Z7; S9 M5.
* **C8. Streaming timeouts and partial answers after errors.**
  * One total timeout cannot both catch a stalled stream and allow long answers.
  * What happens to a partial answer after a mid-stream error or retry is undefined.
  * **Fix:** three timeout constants (connect, gap between streamed chunks, total) per Stage 8 Z4. A partial answer that ends in an error is kept and labelled like "Stopped", is not added to history, and is never retried automatically after the first text.
  * Sources: S5 M8, M9; S7 M5, M6 (timeout part); S8 Z4.

### Session and screens

* **C9. The session lifecycle (Q4) and the state-to-screen mapping (Q13) are not decided.**
  * CUJ 8 promises that streaming survives a layout change while Q4 leaves rotation open. The "spikes answer Q1–Q5" claim covers more than the spikes do.
  * **Fix:** state the v1 behavior on backgrounding, process death, activity recreation and rotation. Add a table of what each state shows. Narrow CUJ 8. Correct the spike claim.
  * Sources: S7 M10, N14; S9 M6.
* **C10. Restart or Try again on the config-unavailable screen.**
  * **Fix:** resolve A2. Add a CUJ for the config-unavailable screen.
  * Sources: S5 M10; S7 N14; S8 Z8.
* **C11. The served model differs from the requested model, but no outcome is defined.**
  * **Fix:** record the served model (in the report and in telemetry) and take no other action in v1 (Stage 8 Z9). Define the `status` values and how `replacementId` differs from `fallbackId`.
  * Sources: S6 #6; S7 N17; S8 Z9.
* **C12. Sheet and list wiring.**
  * `@gorhom/bottom-sheet` v5 targets Reanimated 3, while the SDK may ship Reanimated 4. FlashList inside the sheet needs the sheet library's scroll component, and the wide pane must not use it.
  * The photo view does not state `cachePolicy="memory"`, and zoom has no chosen implementation.
  * **Fix:** make Reanimated compatibility a pass criterion of the Q3 spike. Give `MessageList` a scroll-component prop and `Composer` an input-component prop. State `cachePolicy="memory"` and `contentFit="contain"`, and the zoom approach and limits.
  * Sources: S9 M16, M17, M18.

### Providers and network

* **C13. Message building and trimming may be too much for v1.**
  * Three copies of the message builder and per-provider token trimming serve a Phase 5 need. The v1 cloud models have contexts far larger than a capped history.
  * **Fix:** resolve A4. If B stays, record in Alternatives why the duplication is accepted.
  * Sources: S5 M5, M6, N1; S7 M18 (options A–C are not defined in the document); S8 minimal-fix set.
* **C14. A system prompt that changes per question type breaks the stable request start that justified re-sending the image.**
  * **Fix:** a fixed start (constant system prompt plus an image-only first message), a trimmable middle, and the prompt-kind instruction placed with the new question. Add a contract test that the start of the request is identical across turns. Measure accuracy before and after in the harness.
  * Sources: S5 M4.
* **C15. The network wrapper.**
  * It does not say which `fetch` it uses, or whether `expo/fetch` honors `redirect: 'error'`. The host-matching rules are not stated. URLs from Remote Config can send users to any page.
  * **Fix:** import from `expo/fetch` and forbid `EXPO_PUBLIC_USE_RN_FETCH`. Add "redirect refused" as a Phase 0 pass/fail check; a response-URL check is not useful, because the key has already been sent by then (Stage 8 Z5). State exact-host matching. Move the user-facing URLs to code (A1).
  * Sources: S7 M3, M6; S8 Z3, Z5; S9 M13.
* **C16. Android minimum version and image formats.**
  * minSdk 26 conflicts with converting HEIC and AVIF. A Non-Goal cites minimums that are not set.
  * **Fix:** resolve A6. Narrow the AVIF wording.
  * Sources: S7 M4, M17; S8 Z14.
* **C17. Image pipeline calls and failure path.**
  * Not named: the downsampling options, where orientation is applied, how "no metadata remains" is checked, the second-attempt quality, and the outcome if the image is still too large. A single re-encode can reject valid photos.
  * **Fix:** state the `expo-image-manipulator` call chain, the APP1 Exif/XMP marker scan, the quality steps and the error class.
  * Sources: S9 M15; S5 N7.
* **C18. The AI SDK's per-image detail and resolution settings are not verified.**
  * **Fix:** add "detail or resolution reaches the request body" to the Q1 spike for each provider.
  * Sources: S9 M21.

### Measurement and privacy

* **C19. The harness may not measure what the app sends.**
  * It runs the adapters under Node, but the app makes its send copies with `expo-image-manipulator`, and the adapters import React Native, Expo and Firebase code. No phase builds the harness, and the dataset format is not defined.
  * **Fix:** a boundary rule that adapters take plain inputs (`{fetch, errorMap, now}` and image bytes) and import nothing from React Native, Expo or Firebase, enforced by lint. Either save on-device send copies as harness fixtures, or state that the harness uses a different encoder. Name the harness phase and the dataset format. Retitle the performance NFR as a baseline, with no target values.
  * Sources: S5 M3; S6 #11; S7 M7; S8 Z14; S9 M11.
* **C20. Telemetry events and Crashlytics redaction.**
  * The event list lives only in the brief. It includes `session_start` (probably reserved by Analytics), `consent_recorded` (which contradicts "consent never leaves the device") and `config_fetch` (which can never be sent). No module is named to emit each event. React Native Firebase's own JavaScript error handler may send unredacted errors.
  * **Fix:** list the final events inline (for example with a `pc_` prefix), drop or justify those two, name the emitting module for each, turn off the automatic JavaScript handler, and add the reserved-name check to the spikes.
  * Sources: S6 #2; S7 N18; S9 M7, M20.
* **C21. The consent rule is ambiguous, and consent versions have two sources.**
  * **Fix:** "if `dataLeavesDevice` is false, `canSend` is true; otherwise it needs a granted record at or above `consentTextVersion`". Keep one version source and have `validate-config` check it.
  * Sources: S9 M23, N4; S7 N16.

### Build and project setup

* **C22. Dependencies are missing and some tooling cannot work as specified.**
  * Missing: `expo-dev-client`, `expo-splash-screen`, `expo-blur`, `expo-application` or `expo-constants`, a UUID source, `expo-localization`, `expo-status-bar`, `firebase-admin`, an OAuth library for the Remote Config REST API, an ESLint import plugin, `eslint-config-expo`, `typescript-eslint`, `@types/jest`, and a Node image library for the harness.
  * `fetch` is a global, so `no-restricted-imports` cannot ban it; use `no-restricted-globals`.
  * `check-permissions` reads a manifest that lacks library permissions; read the merged Gradle manifest or the APK instead.
  * iOS has no app-config key for backup exclusion; files must be marked at runtime, and Android needs `allowBackup: false` plus the secure-store plugin setting.
  * React Native Firebase plugins, keys, the EAS build image, the `AD_ID` permission and Crashlytics symbol upload are not specified.
  * `react-native-performance` is listed but not used by any file.
  * Sources: S9 M8, M9, M10, M14, M19, N11; S5 N5.
* **C23. Phases and spikes.**
  * Files carry no phase tags. Phase 0 spikes have no code location and no pass criteria (including the atomic-move test and its fallback). CI steps run before their inputs exist.
  * **Fix:** tag each file with its phase, add a `spikes/` folder or throwaway branch, and give each spike a pass/fail rule and a fallback.
  * Sources: S9 M12.

### Document structure

* **C24. Structure and references.**
  * No risk register. A grouped list of what happened to each L-item, placed in Q11, closes this without a full register (Stage 8 Z1).
  * Two numbering schemes clash ("decision 21" vs "D21"); add a short notation key and remove process references such as "round 3" and "finding O7".
  * Some decision citations are wrong.
  * Resolved items still sit in Open Questions, and "decided during drafting" appears three times; keep one list.
  * Additional Resources and Future Improvements are nested under the wrong heading.
  * The tint values disagree across G19, §12 and file 26; state one rule.
  * "Each guarantee has an automatic check" overclaims, because several checks are manual.
  * Sources: S6 #1, #3, #9; S7 M1, M11, M12, M13, M15, M19, M20, M21; S5 N4; S8 Z1, Z8, Z11; S9 M22, N21.

## 🟢 Nit groups

* **G1. Wording:**
  * "about" used for exact values;
  * hedges with no decision attached;
  * absolute claims and claims about other apps without evidence;
  * the contrast threshold stated too strongly;
  * units missing ("44 points");
  * "dark status bar style" (it should be light status-bar content on a dark background);
  * "redact removes any text field", which should say free-text fields;
  * "time to first word" vs "time to first token";
  * "nothing outside this folder changes", which should say no code outside it changes.

  Sources: S7 N1–N6; S9 N14–N26.
* **G2. Undefined terms:** "Gemini key type", "old unrestricted Gemini key", "React Native AI", "the design board", "marker string", `TokenEconomizer/`. Define them inline; no glossary section is needed (Stage 8 Z11). Sources: S6 #10; S7 N7; S9 N18, N19, N20.
* **G3. Structure:**
  * IDs for Non-Goals and NFRs (Stage 8 recommends dropping new ID schemes);
  * the §3 heading carries a decision note;
  * uneven line wrapping;
  * "English only" sits in the tablet goal;
  * some Reason for Rejection fields give no trade-off.

  Sources: S7 N9–N13, N19.
* **G4. Small missing behaviors:**
  * camera permission denied, and New photo pressed during a stream;
  * whether a leftover temporary file is promoted or deleted, how a corrupt `consent.json` is handled, and what happens if the marker write fails;
  * config failures "logged through telemetry" before telemetry starts;
  * the support contact for deletion requests;
  * the setup redirect condition;
  * which `status` values the model list hides.

  Sources: S7 N20; S9 N3, N6, N7, N26, N27.
* **G5. Tooling details:**
  * screen tests inside `src/app/` may be treated as routes;
  * no `coverageThreshold`;
  * no separate Jest config for the rules tests, and no Java for the emulator in CI;
  * script credentials and the Remote Config ETag;
  * no rules deploy step;
  * `usesNonExemptEncryption`;
  * the R8 key names;
  * the Lucide icon names and fixture file names;
  * "no polyfills needed".

  Sources: S9 N1, N2, N5, N8–N10, N12, N13, N28.
* **G6. Already covered, or can be cut:**
  * cleanup of leftover files is already in file 46, and the store already owns the AbortController (Stage 8 Z10);
  * the image edit list and `imageVersionId` could be cut (Stage 5 N6; the author decided to keep them);
  * the fake adapter's weaker-provider paths (Stage 5 N1).

  Sources: S7 M9; S5 N1, N6; S8 Z10.

## Traceability matrix

Raw finding → consolidated item. S5 = zoom_out_report.md, S6 = comprehension_report.md, S7 = critic_report.md, S8 = intermediate_zoom_out_report.md, S9 = implementation_readiness_report.md.

| Stage | Finding | → | Stage | Finding | → | Stage | Finding | → |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| S5 | M1 | C1 | S7 | M1 | C24 | S9 | B1 | C1 |
| S5 | M2 | C4 | S7 | M2 | C5 | S9 | B2 | C2 |
| S5 | M3 | C19 | S7 | M3 | C15 | S9 | M1 | C3 |
| S5 | M4 | C14 | S7 | M4 | C16 | S9 | M2 | C4 |
| S5 | M5 | C13 | S7 | M5 | C8 | S9 | M3 | C5 |
| S5 | M6 | C13 | S7 | M6 | C8, C15 | S9 | M4 | C6 |
| S5 | M7 | C2 | S7 | M7 | C19 | S9 | M5 | C7 |
| S5 | M8 | C8 | S7 | M8 | C7 | S9 | M6 | C9 |
| S5 | M9 | C8 | S7 | M9 | G6 | S9 | M7 | C20 |
| S5 | M10 | C10 | S7 | M10 | C9 | S9 | M8 | C22 |
| S5 | M11 | C7 | S7 | M11 | C24 | S9 | M9 | C22 |
| S5 | N1 | C13, G6 | S7 | M12 | C24 | S9 | M10 | C22 |
| S5 | N2 | C3 | S7 | M13 | C24 | S9 | M11 | C19 |
| S5 | N3 | C4 | S7 | M14 | C4 | S9 | M12 | C23 |
| S5 | N4 | C24 | S7 | M15 | C24 | S9 | M13 | C15 |
| S5 | N5 | C22 | S7 | M16 | C2 | S9 | M14 | C22 |
| S5 | N6 | G6 | S7 | M17 | C16 | S9 | M15 | C17 |
| S5 | N7 | C17 | S7 | M18 | C13 | S9 | M16 | C12 |
| S6 | #1 | C24 | S7 | M19 | C1 | S9 | M17 | C12 |
| S6 | #2 | C20 | S7 | M20 | C24 | S9 | M18 | C12 |
| S6 | #3 | C24 | S7 | M21 | C24 | S9 | M19 | C22 |
| S6 | #4 | C1 | S7 | N1–N6 | G1 | S9 | M20 | C20 |
| S6 | #5 | C7 | S7 | N7 | G2 | S9 | M21 | C18 |
| S6 | #6 | C11 | S7 | N8 | C2 | S9 | M22 | C24 |
| S6 | #7 | C4 | S7 | N9–N13 | G3 | S9 | M23 | C21 |
| S6 | #8 | C4 | S7 | N14 | C9, C10 | S9 | N1, N2, N5 | G5 |
| S6 | #9 | C24 | S7 | N15 | C15 | S9 | N3, N6, N7 | G4 |
| S6 | #10 | G2 | S7 | N16 | C21 | S9 | N4 | C21 |
| S6 | #11 | C19 | S7 | N17 | C11 | S9 | N8–N10, N12, N13 | G5 |
| S8 | Z1 | C24 | S7 | N18 | C20 | S9 | N11 | C22 |
| S8 | Z2, Z3 | A1, C3, C15 | S7 | N19 | G3 | S9 | N14–N17, N22–N25 | G1 |
| S8 | Z4 | C2, C8 | S7 | N20 | G4 | S9 | N18–N20 | G2 |
| S8 | Z5 | C15 | S8 | Z9 | C11, C24 | S9 | N21 | C24 |
| S8 | Z6 | C1, C2 | S8 | Z10 | G6 | S9 | N26, N27 | G4 |
| S8 | Z7 | C7, A3 | S8 | Z11 | C24, G2 | S9 | N28 | G5 |
| S8 | Z8 | C1, C10, C24 | S8 | Z12 | C4, A8 | | | |
| S8 | Z13 | C5, A5 | S8 | Z14 | C16, C19, A6 | | | |

## Suggested order of work

1. Decide A1–A8. Most other fixes depend on A1, A2 and A3.
2. Fix the two Blockers: C1, a deletion plus one statement of the rule, and C2, the error table and retry numbers.
3. Fill in the missing content: C3, C4, C6, C7, C20.
4. Clarify behavior: C5, C8, C9, C11, C12, C14, C15, C17.
5. Fix setup and tooling: C16, C18, C19, C21, C22, C23.
6. Clean up the document (C24) and the nits.
