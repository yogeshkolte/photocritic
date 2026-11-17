# Goldfish Review Report: Stage 9, Implementation Readiness Test

**Target:** `/Users/yogeshkolte/aiprojects/photocritic/docs/designs/photocritic.md` (lines 1073–1806, "Detailed Implementation Plan" to end)
**Mode:** REVIEW
**Standard:** `implementation-readiness-standard.md` (implementation-readiness, codebase-details); persona only (style)
**Constraint applied:** AR1–AR3 are accepted risks. They are not raised here, except where the plan contradicts them (finding B1).

## Reviewer Status

| Reviewer | Status | Raw findings (Blocker / Major / Nit) |
| --- | --- | --- |
| `egm-implementation-readiness` | completed | 1 / 13 / 10 |
| `egm-codebase-details` | completed (checked against `research/`; the project has no app code yet) | 1 / 19 / 9 |
| `egm-style` | completed | 0 / 8 / 15 |

After merging duplicates there are **2 Blockers, 23 Majors and 28 Nits**.

**Overall verdict:** the plan does not yet pass the Zero-Decision Rule. The file list and layering are complete and well structured. But the error taxonomy, the numeric constants, the Firestore rule details, App Check, the kill switch, and session lifecycle are still left for the implementer to decide.

---

## 🔴 Blockers

### B1. `classify` falls back to "bundled rules", which contradicts AR2 and Q9
* **Reviewer Lens:** `egm-codebase-details` (Blocker), `egm-implementation-readiness` (Major), `egm-style` (Major)
* **Affected Scope:** line 1488 (item 40)
* **Problem:** The text says `classify` "applies the `error_map` rules from the snapshot in order (bundled rules if invalid)". AR2 and Q9 say there are no built-in config values, and that any invalid value shows the config-unavailable screen. Items 16 and 53 say the same (lines 1287, 1601–1603), and so does §8 step 3: "there is no per-value fallback". No file holds bundled rules. The implementer would have to either invent them, which breaks AR2, or guess what was meant.
* **Evidence:** lines 294–298, 1119–1120, 660–661, 1287, 1601–1603.
* **Suggested fix:** Delete "(bundled rules if invalid)". State that `classify` only runs on a validated snapshot, and that a signal no rule matches becomes `unknown`.

### B2. The error table and internal error classes the plan depends on do not exist
* **Reviewer Lens:** `egm-implementation-readiness`. The missing retry constants were also raised by `egm-codebase-details` and `egm-style`.
* **Affected Scope:** lines 1266, 1479–1482, 1484–1491, 1652–1656, 1664–1665, 1792
* **Problem:** Several parts of the plan point to "the error table":
  * the Remote Config template ("the full `error_map` from the error table")
  * retryable classes per provider
  * `classify` and `userMessages`
  * fixtures ("one fixture per row of the error table")
  * contract tests
  * the coverage rule

  The table itself is missing. §7 has only "Example internal classes" and six user messages. The rule shape "provider, phase, status, match, internal class" (line 640) does not say what `match` matches. That matters because the Anthropic spend-limit 400 can only be told apart by its message text. The retry numbers are also missing: backoff base, jitter, total-wait cap, and the `Retry-After` cap (line 1481 says only "up to a cap").
* **Evidence:** §7 lines 613–620. concepts_brief.md:291 (item 23) and :2068 say the table still has to be written. concepts_brief.md:171 (J11) covers the spend-limit case, and :784 notes the missing retry numbers.
* **Suggested fix:** Add the table with these columns: provider, phase (http/stream/finish), status, match field and operator (including a message-substring match), internal class ID, user message, retryable. Make the class IDs a closed TypeScript union in `providers/types.ts` and a `z.enum` in `schema.ts`. Give numeric values for backoff base, jitter, total-wait cap and `Retry-After` cap.

---

## 🟡 Majors

### M1. Remote Config v1 values, formulas and prompt texts are not given
* **Reviewer Lens:** `egm-implementation-readiness`, `egm-codebase-details`, `egm-style`
* **Affected Scope:** lines 1228, 1263–1268, 1273–1275, 1329–1330, 1387, 1456–1482, 1543–1544
* **Problem:** The plan says "Every parameter in Technical Plan §8 with v1 values" and "one zod schema per parameter", but never supplies any of these:
  * **Enum values:** for `models.status`, `label` and `imageDetail`, and which statuses setup shows (line 1330, "filtered by status").
  * **Curated model IDs** for each provider.
  * **Numeric values:** `historyTurnsCap`, `maxQuestionChars`, `requestTimeoutMs`, `connectTimeoutMs`, `contextTokens`, `maxOutputTokens` and `maxBytes` per model, `maxConversationChars`, `dailyLimitPerInstall`, `retentionDays`.
  * **Token estimates:** the characters-per-token factor for each provider (line 1466), and the image-token formulas (OpenAI patches, Anthropic, Gemini resolution table).
  * **Prompt texts:** the base system prompt and the four prompt-kind texts.
  * **Line 1387:** "collapsed sized to content up to a limit" does not give the limit.
  * **Undefined meanings:** what "connect timeout" means for `fetch`, and whether a per-model `minAppVersion` filters the model list.
* **Evidence:** openai-vision.txt:3412–3457 and :3558; gemini-media-resolution.txt:220–224; concepts_brief.md item 34 lists only candidate models and things to verify.
* **Suggested fix:** Add a "Remote Config v1 template" table with these columns: field, type/constraint, v1 value, and whether the value is provisional until Phase 0. Write out the token formula for each provider, the prompt texts, and the definition of the connect timeout.

### M2. Firestore rules are not specified in enforceable detail; `expireAt` and `createdAt` have no owner
* **Reviewer Lens:** `egm-implementation-readiness`, `egm-codebase-details`, `egm-style`
* **Affected Scope:** lines 1114–1115, 1225–1233, 1303–1307, 1570–1573, 1695–1702
* **Problem:**
  * **Field limits:** "Every field has the expected type and size limit" comes with no field list and no limits, apart from `reason` at 1,000.
  * **Conversation length cap:** the rule `historyTurnsCap × 2 + 2` uses a Remote Config value, and Firestore rules cannot read Remote Config. The rule needs a literal number, and nothing keeps that number in step with Remote Config: `validate-config` has no such check. If the cap is later raised, every report fails with permission-denied.
  * **`expireAt`:** Q6 says reports carry it, and `purge-reports.ts` deletes by it. But item 49 never sets it, the rules never require it, and its link to `retentionDays` is not stated. A report without `expireAt` is never purged.
  * **`createdAt`:** the check `createdAt == request.time` only passes if the client writes `serverTimestamp()`, and the plan does not say so.
* **Evidence:** concepts_brief.md:986–1000 (K.6) and :1047–1049 (L2, which calls for constants and a CI cross-check); rnfb-firestore.txt:434.
* **Suggested fix:**
  * List every field with its rule type and `size()` limit, and give the `role` values.
  * Hard-code the list cap and size limits in `firestore.rules`, and add a `validate-config` check that Remote Config stays within them.
  * Set `createdAt: serverTimestamp()` and `expireAt = now + retentionDays` in `reportService`. Have the rules check `expireAt` against `request.time` plus the retention duration written as a literal.
  * Add `expireAt` cases to `reports.rules.test.ts`.

### M3. The plan never says how report delivery states are detected
* **Reviewer Lens:** `egm-implementation-readiness`, `egm-codebase-details`
* **Affected Scope:** lines 1570–1573, 1683–1684, 1744
* **Problem:** `reportService` returns sent, queued (offline), failed, or limit reached. The plan does not say how "queued" is detected. The Firestore write promise resolves only when the server acknowledges the write. It also does not say whether Firestore offline persistence stays on (which keeps report text on the device), or how `permission-denied` (from App Check or the rules) maps to a state. The Phase 0 spike "offline write after app kill" has no follow-up decision.
* **Evidence:** rnfb-firestore.txt:1401 (`waitForPendingWrites` resolves on acknowledgement); concepts_brief.md:1077–1078 (L5).
* **Suggested fix:** Do not await the write. Race it against a stated number of seconds: on timeout (or if NetInfo reports offline) return "queued", on acknowledgement return "sent", on `permission-denied` return "failed". Choose the persistence setting and state it in `privacy-policy.md`.

### M4. No file owns App Check setup
* **Reviewer Lens:** `egm-implementation-readiness`, `egm-codebase-details`
* **Affected Scope:** items 2, 3, 20, 49, 67 (lines 1116, 1143–1144, 1156–1168, 1175–1178, 1233, 1311–1318, 1743–1753)
* **Problem:** `@react-native-firebase/app-check` is a dependency and §10 says App Check is enforced, yet none of these is assigned to a file:
  * initializing it before Firestore;
  * choosing the provider per build profile (debug provider for development and preview, App Attest and Play Integrity for production);
  * the App Attest entitlement set to production;
  * debug tokens for simulators and CI;
  * a check that production has no debug provider;
  * turning on console enforcement in each Firebase project and registering the Play signing SHA-256 fingerprints.

  With enforcement on and any of these missing, every report write fails.
* **Evidence:** app-check-firestore.txt:71–77 (App Attest sandbox tokens are rejected) and :227–243; rnfb-getting-started.txt:202–205; concepts_brief.md:1141–1144 (L16) and :2072.
* **Suggested fix:** Add `src/platform/firebase/appCheck.ts`, called first in the `_layout.tsx` startup sequence, with the provider chosen per `APP_ENV`. Add the entitlement to `app.config.ts`. Add debug-token handling for each `eas.json` profile, a CI check that production has no debug provider, and a runbook step for enforcement and fingerprints.

### M5. Kill switch and Remote Config live-update behavior is undefined
* **Reviewer Lens:** `egm-implementation-readiness`, `egm-codebase-details`, `egm-style` (throttled case)
* **Affected Scope:** lines 1314–1318, 1355–1362, 1597–1605, 1675–1676
* **Problem:**
  * **Inline notice:** CUJ 7 requires "Start over with <fallback model>". `disabled.tsx` covers only app-wide modes, and no file handles the inline notice or picks `fallbackId`.
  * **Active stream:** nothing says whether a stream already running is aborted.
  * **Live updates:** `activate()` activates the whole template, so "only kill-switch changes apply immediately" has no mechanism behind it.
  * **Banner:** `bannerMessage` has no component that shows it.
  * **Throttled fetch:** item 53 omits it, although §8 step 2 and the test at line 1675 require it.
  * **Fetch timeout:** the 10-second limit needs `fetchTimeoutMillis = 10000`, because the default is 60 s.
  * **Tests:** the kill-switch tests have no expected outcome.
* **Evidence:** rnfb-remote-config.txt:185, :201, :267, :407; concepts_brief.md:1066–1071 (L4).
* **Suggested fix:** In `killSwitch.ts` and `sessionStore.ts`, specify each case:
  * `appDisabled` or app version below the minimum: abort the stream, then show `disabled`.
  * Provider or model disabled: abort the stream, then `reset('killSwitch')` with an inline notice offering `fallbackId`.
  * Live update: activate, then validate and re-read only `kill_switch`.

  Also handle `'throttled'` by using the last activated values, set `fetchTimeoutMillis: 10000`, name the component that shows the banner, and give each test its expected assertion.

### M6. Session lifecycle (Q4) and screen states (Q13) are not decided, and the spikes are said to cover Q1–Q5
* **Reviewer Lens:** `egm-implementation-readiness`, `egm-codebase-details`, `egm-style`
* **Affected Scope:** lines 1097–1100, 1112–1124, 1337–1345, 1414–1424, 1682, 1745–1746
* **Problem:**
  * **Session lifecycle:** `sessionStore` does not define what happens on backgrounding, process death, Android activity recreation, or rotation during a stream, yet tests require "every state transition in §5".
  * **Q4:** it is not in the spike list. The line "these answer Q1–Q5" claims more than the spikes cover: none addresses Q4 or the Q5 history-cap value.
  * **Q13:** its states (preparing, sending, each error, offline, disabled) are not mapped to what the user sees.
* **Evidence:** lines 252, 575–576.
* **Suggested fix:** Decide v1 behavior in the plan, for example: "on background, the stream continues; failure is classified as a network error; after process death the app restarts at home with no photo". Add an AppState handling line and a state-to-UI table. Change the spike claim to "Q1–Q3 and the image-size and caching parts of Q5".

### M7. The telemetry event list is inherited unresolved, and no code emits the events
* **Reviewer Lens:** `egm-implementation-readiness`
* **Affected Scope:** lines 1632–1638, 1603, 1737–1745
* **Problem:** `events.ts` is defined as "brief K.5 without `tier`", which brings three problems with it:
  * It keeps `session_start`, which is probably a reserved, automatically collected Google Analytics name.
  * It keeps `consent_recorded`, which contradicts "consent records never leave the device".
  * It keeps `config_fetch`, which can never be sent, because config loads before telemetry starts.

  No module is named to call `log` for `image_prepared`, `question_sent`, `answer_received`, `request_failed` or `report_submitted`. The reserved-name check from the brief's spike list is also missing from the plan's spike list.
* **Evidence:** concepts_brief.md:952–961, :1113–1117, :2030.
* **Suggested fix:** Write out the final event names (for example with a `pc_` prefix) and their parameters. Drop or justify `consent_recorded` and `config_fetch`. Name the module that emits each event, and add the reserved-name spike.

### M8. Dependencies are missing from `package.json`
* **Reviewer Lens:** `egm-implementation-readiness`, `egm-codebase-details`
* **Affected Scope:** item 1, lines 1133–1151
* **Problem:** The standard forbids adding dependencies the plan does not list, but the plan's own files need these:
  * `expo-dev-client`, because Firebase requires a dev client (expo-using-firebase.txt:173–182);
  * `expo-splash-screen`, to hold the launch screen during the fetch of up to 10 s;
  * `expo-blur`, for the Sheet;
  * `expo-application` or `expo-constants`, for the app version;
  * `expo-crypto` or a verified `crypto.randomUUID`, for report IDs;
  * `expo-localization`, for the report locale;
  * `expo-status-bar`;
  * `firebase-admin`, for purging reports and publishing templates;
  * an OAuth library for the Remote Config REST API;
  * an ESLint import plugin;
  * `eslint-config-expo`, which includes the React Compiler lint rules (callstack js-react-compiler.md:117);
  * `typescript-eslint` and `@types/jest`.
* **Suggested fix:** Add each package with the file that uses it, or state the built-in alternative.

### M9. Some ESLint rules cannot be written as specified
* **Reviewer Lens:** `egm-implementation-readiness`, `egm-codebase-details`
* **Affected Scope:** item 5, lines 1191–1197
* **Problem:** The rule "`fetch` to provider hosts only via `src/platform/net/`" is listed under `no-restricted-imports`, but `fetch` is a global, not an import, so that rule cannot enforce it. "No imports from `index` files" needs a pattern or a plugin, and the plan names neither.
* **Suggested fix:** Use `no-restricted-globals: ['fetch']` everywhere except `src/platform/net/**`, and keep the import ban on `expo/fetch`. Add `no-restricted-imports` patterns `['**/index']`, or name the plugin rule to use.

### M10. `check-permissions.ts` reads a manifest that lacks library permissions
* **Reviewer Lens:** `egm-implementation-readiness`, `egm-codebase-details`
* **Affected Scope:** item 18 (lines 1299–1301), lines 1246–1247, 1785
* **Problem:** `expo prebuild` writes only the app's own `AndroidManifest.xml`. Permissions that libraries add (image-picker adds CAMERA, storage and RECORD_AUDIO) are merged later, by Gradle. The prebuild manifest also lists blocked permissions with `tools:node="remove"`, so a naive search fails a correct build, and a search that ignores those entries misses real leaks. Either way, the G1 promise is not actually tested.
* **Evidence:** expo-imagepicker.txt:51 and :1136–1147.
* **Suggested fix:** In CI, run `./gradlew :app:processReleaseMainManifest` and parse the merged output, or run `aapt2 dump permissions` on the built APK. Alternatively, check that each blocked permission appears with `tools:node="remove"`. State the exact command and path.

### M11. The harness and canary cannot run "the same adapters" under Node as described
* **Reviewer Lens:** `egm-implementation-readiness`, `egm-codebase-details`
* **Affected Scope:** item 12 (lines 1250–1256), item 64 (lines 1706–1716)
* **Problem:** The adapters depend on `expo/fetch`, on a `SendCopyRef` URI from `expo-image-manipulator`, on the React Native Firebase config snapshot, and on telemetry. None of these load under `tsx`/Node. The plan also leaves these undefined:
  * the Node image library;
  * the dataset and ground-truth format;
  * the grid of image sizes and detail settings ("several");
  * where API keys come from;
  * the canary's photo and secret names.
* **Suggested fix:** Define an adapter dependency interface, `{fetch, errorMap, now}`, with raw image bytes as input, and ban `@react-native-firebase/*` and `expo-*` imports in adapters (enforced by lint). Add `sharp` (or another Node image library) to dev dependencies. Specify the `harness/datasets/<name>/{images/, truth.json}` layout, the size and detail grid, and the environment-variable names.

### M12. Files have no phases, and Phase 0 spikes have no code location or pass criteria
* **Reviewer Lens:** `egm-implementation-readiness`, `egm-codebase-details`
* **Affected Scope:** lines 1093–1110, file list 1126–1780, item 67 (1734–1746)
* **Problem:**
  * **Phases:** only items 12 and 41 name a phase, so a per-phase write allowlist cannot be built.
  * **Files that span phases:** `home.tsx` (Phase 2) hosts the report sheet (Phase 4). `ci.yml` (Phase 0) runs `test:rules` and `config:validate` before their inputs exist.
  * **Spike code:** it has no folder.
  * **Pass criteria:** the sheet-library choice (Q3), the atomic-move test and the Q1 checks have none. If `move` is not atomic, no fallback is given.
* **Suggested fix:** Tag each file with its phase and say which CI steps are on in each phase. Add a `spikes/` folder that is excluded from tsconfig and lint, or state that spike code lives on a throwaway branch. Give each spike a pass/fail criterion and a fallback decision.

### M13. The plan does not say which `fetch` the network wrapper uses, or whether it honors `redirect: 'error'`
* **Reviewer Lens:** `egm-codebase-details`; `egm-implementation-readiness` (Nit)
* **Affected Scope:** item 51 (lines 1587–1589), item 67 (lines 1737–1741)
* **Problem:** The wrapper does not say whether it uses `expo/fetch`, which supports streaming unless `EXPO_PUBLIC_USE_RN_FETCH=1` is set. Research does not document `redirect: 'error'` support in `expo/fetch`, and the spike list does not test it. If it is silently ignored, the redirect protection disappears. The allowlist matching rules are also not stated: https only, exact lowercase host, no userinfo, no port.
* **Evidence:** expo-fetch.txt:66; aisdk-expo.txt:334; concepts_brief.md L11.
* **Suggested fix:** Import `fetch` from `expo/fetch` and forbid `EXPO_PUBLIC_USE_RN_FETCH`. Add "`redirect: 'error'` honored" to the Q1 spike, with a fallback that checks `response.redirected`/`response.url` against the allowlist. State the matching rules.

### M14. iOS backup exclusion is assigned to `app.config.ts`, but no config key exists for it
* **Reviewer Lens:** `egm-codebase-details`
* **Affected Scope:** item 2, line 1168 (Q8)
* **Problem:** iOS has no app-config key that excludes files from backup. Files must be marked at runtime. On Android, `allowBackup: false` interacts with the expo-secure-store plugin's automatic backup rules.
* **Evidence:** expo-securestore.txt:72–77, :142–146.
* **Suggested fix:**
  * Android: set `android.allowBackup: false` and `configureAndroidBackup: false` in the expo-secure-store plugin.
  * iOS: in `files.ts`, mark files as excluded from backup after each write, and add a test for it.

### M15. The image pipeline leaves the API calls and failure path undefined
* **Reviewer Lens:** `egm-codebase-details`, `egm-style`
* **Affected Scope:** item 45, lines 1535–1538
* **Problem:**
  * **Decode:** the downsampling options (`maxWidth`/`maxHeight`) are not named.
  * **Orientation:** it is not said whether EXIF orientation is applied by the library or by the app.
  * **Metadata:** "confirm no metadata remains" has no method.
  * **Re-encode:** "re-encode at lower quality once" gives no quality value and no outcome if the result is still over `maxBytes`.
* **Evidence:** expo-imagemanipulator.txt:495–507.
* **Suggested fix:** Specify the call chain: `manipulate(uri, {maxWidth, maxHeight})`, then `renderAsync`, then `saveAsync({format: JPEG, compress})`. Give the second-attempt quality and the error class if it still fails ("Image could not be read"). Define the metadata check as a scan for APP1 Exif/XMP markers, and add orientation and metadata checks to Phase 0.

### M16. The bottom-sheet choice may conflict with the SDK's Reanimated version
* **Reviewer Lens:** `egm-codebase-details`
* **Affected Scope:** item 1 (lines 1133–1137), item 11 (`npx expo install --check`)
* **Problem:** `@gorhom/bottom-sheet` v5 targets Reanimated v3. If the SDK default is Reanimated 4, then either the `--check` step fails or the sheet runs on an unsupported combination. The plan does not say which wins.
* **Evidence:** callstack js-bottomsheet.md:58–61.
* **Suggested fix:** Make Reanimated compatibility a pass/fail criterion of the Q3 spike. Record any exclusion from `--check` in `docs/VERSIONS.md`.

### M17. The message list inside the sheet needs library-specific wiring that is not stated
* **Reviewer Lens:** `egm-codebase-details`
* **Affected Scope:** item 28 (`Sheet`), item 33 (lines 1429–1434)
* **Problem:** With gorhom, FlashList v2 needs `renderScrollComponent={useBottomSheetScrollableCreator()}`, and the input should be `BottomSheetTextInput`. The wide-pane layout must not use either. `MessageList` is shared by both layouts, but the plan does not say how each layout passes its own scroll and input components. Auto-scrolling while an answer streams is not specified.
* **Evidence:** callstack js-bottomsheet.md:200–214, :282.
* **Suggested fix:** Give `MessageList` an optional `renderScrollComponent` prop and `Composer` an input-component prop, both supplied by `Sheet.tsx`. Specify the scrolling behavior during streaming.

### M18. Photo display omits the memory-only cache, and zoom has no implementation
* **Reviewer Lens:** `egm-codebase-details`
* **Affected Scope:** item 23, lines 1340–1343
* **Problem:** The rule "Photos never written to the image disk cache" (line 1030) depends on `cachePolicy="memory"`. That setting appears in §6 but not in the file that renders the photo, and the default policy is `'disk'`. Pinch-to-zoom and pan have no chosen implementation.
* **Evidence:** expo-image.txt:190, :314.
* **Suggested fix:** State `<Image cachePolicy="memory" contentFit="contain">` in `home.tsx`, and specify the zoom approach and its limits (minimum and maximum scale, double-tap behavior).

### M19. React Native Firebase native settings are left to the implementer
* **Reviewer Lens:** `egm-codebase-details`, `egm-style`, `egm-implementation-readiness` (AD_ID Nit)
* **Affected Scope:** item 2 (lines 1159, 1164–1166), item 3 (line 1178), item 7 (lines 1211–1212)
* **Problem:**
  * **Plugins:** "React Native Firebase plugins with advertising-ID collection off" lists neither the plugins nor the keys.
  * **Deferred keys:** the firebase.json collection keys and the Expo resizable-activity / `requireFullScreen` keys are "verified in Phase 0", but neither check is in the spike list.
  * **Symbol upload:** Crashlytics symbol upload is assigned to `eas.json`, but it is handled by the Crashlytics config plugin, not by an `eas.json` setting.
  * **Build image:** no EAS build image is pinned, although React Native Firebase requires Xcode 26.2 or later.
  * **AD_ID permission:** `com.google.android.gms.permission.AD_ID`, which Firebase Analytics adds, is not in the blocked-permissions list.
* **Evidence:** rnfb-getting-started.txt:40, :47, :93–104, :121–134 (`withoutAdIdSupport`), :138; concepts_brief.md L9.
* **Suggested fix:** List the plugins in order (`app`, `analytics` with `{ios:{withoutAdIdSupport:true}}`, `crashlytics`, `app-check`, `expo-build-properties`). Name the firebase.json keys. Pin the EAS `image`. Block `AD_ID`. Add the remaining key checks to item 67, and replace the inline caveats with "see docs/spikes/phase0.md".

### M20. Crashlytics may record unredacted JavaScript errors
* **Reviewer Lens:** `egm-codebase-details`
* **Affected Scope:** item 57, lines 1636–1637
* **Problem:** The plan installs a global error handler that "redacts before Crashlytics". It does not address the JavaScript exception handler that React Native Firebase Crashlytics installs by default. If both run, unredacted messages reach Crashlytics.
* **Suggested fix:** Name the firebase.json key that turns off React Native Firebase's automatic JavaScript exception handling, so that `errorHandler.ts` is the only path. Add a spike item to confirm it.

### M21. Per-image detail and resolution settings through the AI SDK are not verified
* **Reviewer Lens:** `egm-codebase-details`
* **Affected Scope:** item 41 (lines 1497–1498), item 67
* **Problem:** The plan assumes that OpenAI `detail` and Gemini per-image resolution can be passed through `@ai-sdk/*`. Gemini documents per-image resolution on the Interactions API, which may not be the endpoint `@ai-sdk/google` calls.
* **Evidence:** gemini-media-resolution.txt:8–18, :161–180; openai-vision.txt:3367.
* **Suggested fix:** Add "per-image detail/resolution reaches the request body" to the Q1 spike for each provider.

### M22. The plan overclaims that "each guarantee has an automatic check"
* **Reviewer Lens:** `egm-style`
* **Affected Scope:** lines 1247–1248
* **Problem:** The Verification Plan (lines 1795–1799) lists several manual checks: the proxy check that sharing-off sends no traffic, the iOS reinstall key wipe, and the live-key smoke tests.
* **Suggested fix:** Write: "CI runs every automated check in the Verification Plan; the manual checks are listed under E2E/Integration."

### M23. The `canSend` consent rule can be read backwards
* **Reviewer Lens:** `egm-style` (Major), `egm-codebase-details` (Nit)
* **Affected Scope:** item 47, lines 1552–1554
* **Problem:** As written, `canSend` is true only for providers whose data leaves the device. Read literally, a provider that keeps data on the device could never send.
* **Suggested fix:** "If `dataLeavesDevice` is false, `canSend` returns true. Otherwise it returns true only when a granted record exists at or above `consentTextVersion`."

---

## 🟢 Nits

| # | Location | Problem | Suggested fix | Reviewer |
| --- | --- | --- | --- | --- |
| N1 | lines 1686–1693 | Screen tests in `src/app/__tests__/` sit inside the Expo Router directory, where files may be treated as routes. The reviewer is going from memory; research does not cover this. | Move them to `test/screens/` or verify. | implementation-readiness |
| N2 | line 1791; item 6 (1199–1204) | The 80% coverage target is not set as `coverageThreshold`. The rules tests need a separate Node-environment Jest config. CI needs Java for the Firestore emulator. | Add all three. | implementation-readiness, codebase-details |
| N3 | lines 1551–1555, 1610–1612, 1619–1620 | It is not said whether a leftover temporary file is promoted or deleted, how a corrupt `consent.json` is handled, or what happens if `installMarker` fails during the key wipe. | State each case. Do not write the marker if the wipe fails, to avoid a wipe loop. | implementation-readiness |
| N4 | line 1553 | There are two consent-version sources: `providers.consentTextVersion` and `consent_texts.version`. | Name the authoritative one and add an equality check to `validate-config`. | implementation-readiness |
| N5 | items 17, 19 (1291–1294) | The scripts do not say where credentials come from, how the project is chosen, whether ETag/If-Match is handled, or whether the bumped `configVersion` is committed back to git. | Specify each. | implementation-readiness, codebase-details |
| N6 | line 1603 vs 1316 | Config failures are "logged through telemetry", but telemetry starts after config loads. | Say this is a no-op, or log locally. | implementation-readiness |
| N7 | lines 1754–1755 | The "support contact" for deletion requests is not defined. | Give an address or URL. | implementation-readiness |
| N8 | lines 1392–1403, 1642–1647 | No Lucide icon names, no app-icon source file, and no names for the image fixture files. | List them. | implementation-readiness |
| N9 | line 1740 | Research already says Expo provides `structuredClone` and the text streams, so no polyfills are needed. | State "no polyfills". | codebase-details |
| N10 | item 2 | R8 keys are not named (`android.enableMinifyInReleaseBuilds`, `enableShrinkResourcesInReleaseBuilds`). `newArchEnabled` may be obsolete. | Name the keys, and confirm in Phase 0. | codebase-details |
| N11 | line 1145 | `react-native-performance` has no file that uses it. | Name the module that records the time-to-interactive and photo-preparation marks. | codebase-details |
| N12 | — | No step deploys `firestore.rules` to the dev and prod projects. | Add a `rules:deploy` script and a README step. | codebase-details |
| N13 | item 2 | `ios.config.usesNonExemptEncryption: false` is missing. | Add it. | codebase-details |
| N14 | line 1150 | "One place records the version pin" overlooks `docs/VERSIONS.md` (item 66). | "`package.json` holds the pins; `docs/VERSIONS.md` records why." | style |
| N15 | line 1318 | "Dark status bar style" is ambiguous: in Expo, `style="dark"` means dark text, which would be unreadable on `#0F1012`. The item also has no **Why**. | Say "light status bar content" and add a Why. | style |
| N16 | line 1371 | "Minimum touch size 44" has no unit. | Add "points". | style |
| N17 | line 1512 | "Capability set `final-turn-only`" gives a value without its field name. | Write `imagePlacement: 'final-turn-only'`. | style |
| N18 | line 1681 | "Marker string" is not defined. | Name the sentinel, for example a planted string shaped like an API key. | style |
| N19 | line 1732 | "React Native AI" is an undefined term. | Define it or drop it. | style |
| N20 | line 1256 | "(round-2 findings)" points to something outside the document. | State the concrete reason. | style |
| N21 | line 1213 | Two numbering schemes are mixed: "D24" and "decision 24". | Use one prefix, or add the topic. | style |
| N22 | lines 1505–1506 | "Nothing outside this folder changes" is not true if the SDK is dropped, because `package.json` and the lint rule also change. | Write "no code outside this folder changes". | style |
| N23 | line 1636 | "`redact` removes any text field" read literally removes enum values too. | Write "any free-text field (questions, answers, notes, provider error bodies)". | style |
| N24 | line 1800 vs 1712 | "Time to first word" vs "time to first token". "Oldest supported phone" names no device and gives no threshold. | Use one term, and name the device and threshold, or say the numbers are a baseline only. | style |
| N25 | line 1186 | "Catch shape errors early" is vague. | Write "fail type-checking when a provider or config shape changes". | style |
| N26 | lines 1323–1324 | "If no provider, model and key are stored" could mean all three are missing or any one is. | Write "if any of provider, model or key is missing". | style |
| N27 | line 1330 | "Filtered by status" does not say which values are hidden. | List the hidden `status` values. | style |
| N28 | item 64 | `harness/datasets/` has no ground-truth format. | Define it; see M11. | codebase-details |
