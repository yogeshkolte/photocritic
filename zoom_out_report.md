# Goldfish Review Report: Stage 5 (Initial Zoom Out)

**Target:** `/Users/yogeshkolte/aiprojects/photocritic/docs/designs/photocritic.md` (lines 1-1806)
**Mode:** REVIEW
**Constraint applied:** The risks under "## Accepted Risks" (AR1-AR3) were not raised as findings. The only comments on them are about places where the design contradicts what it accepted.

## Reviewer Status

| Reviewer | Status | Raw findings |
| --- | --- | --- |
| `egm-zoom-out` | completed | 0 Blocker, 6 Major, 7 Nit |
| `egm-inventive-catalyst` | completed | 0 Blocker, 7 Major, 1 Nit |

Coverage notes:
- The zoom-out reviewer read the whole target and the standard. It found no app code in the project to reuse (only the unrelated `TokenEconomizer/` folder), so it checked reuse against the chosen libraries instead.
- The inventive-catalyst reviewer read the whole target. From the background material it only searched `concepts_brief.md`. It did not open the mockup or the research files.

## Summary

After merging duplicates there are **0 Blockers, 11 Majors and 7 Nits**. The two reviewers agreed on two issues, which were merged: the contradictions with AR2, and the report rules' dependence on `historyTurnsCap`.

The zoom-out reviewer found one main theme: v1 pays now for flexibility it does not use yet. Examples are on-device support (one message builder per adapter, token-based trimming, screen paths for weaker providers) and remote rule editing (`error_map`).

The inventive-catalyst reviewer found compromises that known patterns can remove. Examples are the request start that should stay the same on every turn, the timeouts on streamed answers, and recovery when a request fails.

Line numbers come from the reviewers. Spot checks at lines 543, 657, 864, 1228, 1360 and 1488 matched the target.

## Prioritized Findings (Blockers -> Major -> Nits)

### 🔴 Blockers

None.

### 🟡 Major

#### M1. The text contradicts AR2 ("no configuration fallback") in three places - Severity: Major
* **Reviewer Lens:** `egm-zoom-out`, `egm-inventive-catalyst` (both)
* **Affected Scope:** Item 40 `errors/classify.ts` (line 1488); §8 load order step 2 (lines 655-659); §8 step 5, live updates (lines 664-665)
* **Problem:**
  - (a) `classify.ts` applies `error_map` "from the snapshot in order (bundled rules if invalid)". That is a bundled fallback, which AR2, Q9 and G17 rule out.
  - (b) A throttled fetch uses "the values activated by this install's last successful fetch". That is a last-known-good path for one kind of failure only. It also sits awkwardly next to "no fallback values".
  - (c) The design does not say what a running app does when a live update delivers a template that fails validation.
* **Evidence:**
  - Line 1488: "(bundled rules if invalid)".
  - Line 661: "If any value is invalid, show the same screen; there is no per-value fallback (Q9)".
  - Line 1287: "(Removed: no bundled config defaults, Q9)".
* **Suggested fix:**
  - Delete "bundled rules if invalid". State that `classify` only ever sees a validated snapshot.
  - Either remove the throttled-fetch exception, or name it as the one allowed exception. If it stays, say that it uses only values that passed validation. With `minimumFetchIntervalMillis: 0`, throttling should be rare anyway.
  - Choose one behavior for an invalid live update: ignore it until the next cold start, or show the unavailable screen. Add a `remoteConfig.test.ts` case for it.

#### M2. The report rules cap the conversation length using a Remote Config value the rules cannot read - Severity: Major
* **Reviewer Lens:** `egm-zoom-out`, `egm-inventive-catalyst` (both)
* **Affected Scope:** Item 9 `firestore.rules` (lines 1225-1233); §10 (lines 689-691); G15
* **Problem:**
  - The rules cap `conversation` at `historyTurnsCap × 2 + 2`. Firestore rules are deployed on their own and cannot read Remote Config, so the number has to be hard-coded. If the author later raises `historyTurnsCap` without redeploying the rules, reports from long conversations are rejected and show as "failed".
  - The visible conversation also includes stopped answers and error messages that are not in `history`. A report built from what is on screen can therefore be longer than the limit, even without a config change.
  - In-app reporting is a Play requirement, so these silent failures matter.
* **Evidence:** Line 1228: "length is at most `historyTurnsCap × 2 + 2`".
* **Suggested fix:**
  - Define one constant (for example `REPORT_MAX_ITEMS`) used by both the rules and `reportService`.
  - Have `reportService` drop the oldest items to fit, as it already does for `maxConversationChars`.
  - Have `validate-config.ts` fail when `historyTurnsCap` does not fit within the constant.
  - This does not touch AR3, which is about the text inside each item.

#### M3. The harness's accuracy numbers may describe images the app never sends - Severity: Major
* **Reviewer Lens:** `egm-zoom-out`
* **Affected Scope:** G23 (lines 193-197); item 64, the harness (lines 1706-1715); Technical Plan §6 (image pipeline); §2 (module boundaries)
* **Problem:** The harness is the demonstrator's main measure, and it drives the image-size decision (Q5). It runs "through the same adapters (under Node)". There are two problems with that:
  - (a) The app makes its send copy with `expo-image-manipulator` on the device, which cannot run under Node. The harness would need a different resizer and JPEG encoder, so its results would describe different images.
  - (b) Nothing keeps the adapters free of React Native and Expo imports. They depend on `platform/net`, on the Remote Config snapshot through `registry.ts`, and on `SendCopyRef` file URIs.
* **Evidence:**
  - Line 1709: "Runs a labelled photo set through the same adapters (under Node) at several long-edge sizes".
  - Line 594: "Conversion uses `expo-image-manipulator`".
  - Line 1451: the registry merges Remote Config entries.
* **Suggested fix:**
  - Add a boundary rule to §2: adapters and `providers/shared/` take plain inputs (bytes, a capabilities object, a fetch function) and import nothing from React Native or Expo.
  - Then choose one approach and state it:
    - make the harness send copies on a device with the real pipeline and save them as fixtures; or
    - say that the harness uses a different encoder and that the Q5 results are approximate.

#### M4. The system prompt changes with each question type, which breaks the stable request start used to justify re-sending the image - Severity: Major
* **Reviewer Lens:** `egm-inventive-catalyst`
* **Affected Scope:** Technical Plan §3, cloud adapter steps 1-2 (lines 504-510); item 36 `shared/systemPrompt.ts` (lines 1453-1459); Alternative D2 (lines 858-865)
* **Problem:** Decision 11 re-sends the image on every turn. The reason given is that "a stable request start" lets automatic caching help. But:
  - The system prompt is "a fixed base prompt ... plus the prompt-kind text". It comes before the image, so switching between Identify, Explain and Copy text changes the very start of the request.
  - Trimming the oldest turns changes or moves the first user message, which holds the image.
* **Evidence:**
  - Line 864: "a stable request start".
  - `concepts_brief.md` line 1610 had already flagged this: "changes the system prompt per turn. That breaks the stable prefix and caching."
* **Suggested fix:** Split the request into three parts:
  - a fixed start: a constant system prompt plus an anchor user message that holds only the image;
  - a middle that can be trimmed: the earlier turns;
  - a variable end: the prompt-kind instruction and the new question, both in the final user turn.

  Trim only the middle. Add a contract-test check that the start of the request is byte-identical across turns and question types. Use the harness to measure text-extraction accuracy before and after moving the instruction out of the system prompt, since it may be followed a little less strictly.

#### M5. Three copies of the message-building logic, one per adapter, for a need that only arrives in Phase 5 - Severity: Major
* **Reviewer Lens:** `egm-zoom-out`
* **Affected Scope:** Technical Plan §3 (lines 456-528); Alternatives C2 and C3 (lines 832-846); items 37 and 41
* **Problem:**
  - Under option B, each adapter builds its own messages. Turning one message format into each vendor's request is exactly the job of the Vercel AI SDK, and all three v1 providers accept images in any turn, so the copies would be nearly identical.
  - The design admits the duplication by adding a contract test to stop the three copies from drifting apart.
  - It is already moving toward option C, with shared `systemPrompt.ts` and `historyTrim.ts`.
  - The only reason given for B is on-device models, which are Phase 5.
* **Evidence:**
  - Line 522: "so the three copies of the message-building logic cannot drift apart unnoticed".
  - Line 835: "fails for on-device models".
* **Suggested fix:** In v1, use one shared builder in `providers/shared/`. Each adapter passes in only its real differences, such as the image detail setting and the token factor. Keep the `Provider` interface boundary, so a Phase 5 on-device adapter can still bring its own builder. This also makes the fixed request start in M4 easier to enforce. If B stays, state that it costs more in v1 and that the cost is paid for Phase 5.

#### M6. Token-based history trimming is premature for v1 cloud models - Severity: Major
* **Reviewer Lens:** `egm-zoom-out`
* **Affected Scope:** §3 step 2 (lines 508-510); item 37 `historyTrim.ts` (lines 1461-1468); the `contextTokens` field in §8
* **Problem:**
  - Trimming by token count needs a token estimator for each provider, including image-token estimates from each provider's image sizing rules.
  - The v1 cloud models have context windows of 128k tokens or more. A text history already capped by `historyTurnsCap` will almost never get near that, so this code would essentially never run in v1.
  - It also adds `contextTokens` as one more Remote Config field that must be valid or the app will not start.
* **Evidence:**
  - Line 461: "historyTurnsCap is an upper bound, not the rule".
  - Line 1465: "plus the image's token estimate from the provider's image sizing rule".
  - Line 768: "llama.rn has a 4,096-token default context".
* **Suggested fix:** In v1, trim by `historyTurnsCap` and `maxQuestionChars` only. Add token-based trimming in Phase 5, inside the on-device adapter that needs it.

#### M7. Error classification is in Remote Config while retry rules are in code, so the two can disagree - Severity: Major
* **Reviewer Lens:** `egm-zoom-out`
* **Affected Scope:** §7 (lines 606-626); the `error_map` row in §8; items 39-40; fixtures (item 59); contract test (item 60)
* **Problem:**
  - A rule language published through Remote Config maps raw error signals to classes, while `retry.ts` fixes retry behavior in code. A Remote Config change that reclassifies a signal therefore silently changes whether it is retried.
  - The contract test has to load the Remote Config template to know the expected classes, which ties the tests to published data.
  - The rule schema cannot express Anthropic's spend-limit 400, which can only be told apart by its message text (brief J11).
  - For a TestFlight-only app where a new build ships in about a day, the benefit of a rule language interpreted on the device is not shown.
* **Evidence:**
  - Line 609: "maps to a fine-grained internal class through the Remote Config `error_map`".
  - Lines 622-626: retry rules in code.
  - `concepts_brief.md` lines 171 and 779 (J11).
* **Suggested fix:** Classify errors in code, per adapter, next to the retry rules, and test that directly against the recorded fixtures. Keep only the user-facing message text (and maybe help URLs) in Remote Config. If remote rules stay, state which layer decides whether an error is retried, and make sure the rules can match on message text.

#### M8. One total timeout on a streamed answer cannot both catch stalls and allow long answers - Severity: Major
* **Reviewer Lens:** `egm-inventive-catalyst`
* **Affected Scope:** §4 Network wrapper (lines 543-544); §8 `conversation_policy.requestTimeoutMs` (line 639)
* **Problem:**
  - The wrapper applies "a connect timeout and a total timeout". Set short, it cuts off long but healthy answers, such as a full receipt transcription. Set long, a stalled stream leaves the user watching a frozen answer.
  - The design does not say which error class a total timeout that fires mid-stream becomes. Only "connect timeout" appears in the error table.
* **Evidence:** Line 543.
* **Suggested fix:**
  - Replace the total timeout with a time-to-first-byte timeout and an idle timeout that restarts on every received chunk, plus an optional generous ceiling.
  - Add `firstByteTimeoutMs` and `idleTimeoutMs` to `conversation_policy`.
  - Map a mid-stream stall to a named internal class.

#### M9. What happens to a partial answer when a request fails mid-answer is not defined - Severity: Major
* **Reviewer Lens:** `egm-inventive-catalyst`
* **Affected Scope:** §5 state machine (lines 553-566); item 39 `retry.ts` (line 1482); item 32 `sessionStore.ts` (lines 1414-1424); Open Question Q4
* **Problem:**
  - The state machine goes "streaming ─error─▶ error(class)", and retries correctly stop after the first streamed text.
  - Nothing says whether the partial answer the user already paid for is kept, shown, copyable, or kept out of history.
  - Backgrounding during an answer (Q4) and the stall from M8 fall into the same gap.
* **Evidence:** Line 1482: "Never retries after the first streamed text".
* **Suggested fix:** Handle every interruption after the first text the way Stop is handled. That covers a stream error, a stall timeout, and backgrounding:
  - keep the partial text with an "Interrupted" status;
  - allow copying;
  - leave it out of history;
  - offer Retry, which re-sends the question.

  Add the status to `VisibleMessage` and the transition to §5. This also settles the backgrounding part of Q4.

#### M10. The config-unavailable screen tells the user to restart the app instead of offering Try again - Severity: Major
* **Reviewer Lens:** `egm-inventive-catalyst`
* **Affected Scope:** G17 (lines 165-168, and line 268); §8 load order step 2 (lines 655-659); item 25 `disabled.tsx` (lines 1358-1362)
* **Problem:** The kill-switch screen already has a "Try again" button. The config-unavailable screen says "restart the app", which on a phone means force-quitting, and many users do not know how to do that. Offline cold starts are common, so this blocks users more often than necessary.
* **Evidence:** Lines 657 and 1360: "restart the app."
* **Suggested fix:** Give the config-unavailable screen the same Try again button. It should re-run `fetchAndActivate` and validation, with a debounce or backoff between taps and a visible "trying again" state. This is still consistent with AR2, because there are still no bundled values.

#### M11. The kill-switch fallback model may itself be unusable - Severity: Major
* **Reviewer Lens:** `egm-inventive-catalyst`
* **Affected Scope:** G18 and CUJ 7 (lines 171-173, 364-371); §8 `models.fallbackId` (line 637); item 15 `validate-config.ts` (lines 1280-1285)
* **Problem:** The validator checks only that `fallbackId` names a listed model. In any of these cases, "Start over with <fallback model>" leads nowhere:
  - the fallback belongs to a provider for which the user has no key or no consent;
  - it is itself listed in `disabledModels` or `disabledProviders`;
  - its status or `minAppVersion` hides it on this build;
  - the fallbacks form a loop.
* **Evidence:** Line 637; lines 1280-1285.
* **Suggested fix:**
  - When publishing, require the fallback to be from the same provider, active, not disabled, and not part of a loop.
  - At runtime, follow the fallback chain and skip models that are filtered out. If nothing usable remains, open setup in edit mode with a plain explanation.
  - Test both checks.

### 🟢 Nits

#### N1. Screen paths for weaker providers are built and tested in v1 although no v1 provider uses them - Severity: Nit
* **Reviewer Lens:** `egm-zoom-out`
* **Affected Scope:** §3 interface (lines 466-484); G11; item 42, the fake adapter (lines 1508-1513)
* **Problem:** `getAvailability`/`NeedsDownload`, `prepare`/`dispose`, `imagePlacement: 'final-turn-only'` and `streaming: 'whole'` exist only for Phase 5. The fake adapter is set up as a weaker provider so that v1 screens and tests cover branches no v1 provider uses. The author decided this (D37), so it is rated a Nit.
* **Suggested fix:** Keep the interface signatures, which are cheap. Give the fake adapter only v1 capabilities, and build the weaker-provider screen handling in Phase 5.

#### N2. Too many parameters live in Remote Config, so one bad publish has many ways to stop every tester's app - Severity: Nit
* **Reviewer Lens:** `egm-zoom-out`
* **Affected Scope:** G17 (line 162 onward); §8 parameter table
* **Problem:** `jpegQuality`, timeouts, `maxRetries`, the report daily limit, `retentionDays`, and `imageDetail` and `maxBytes` per model are all in Remote Config. Under AR2 (accepted), every extra parameter is one more way for a single publish to stop every tester's app.
* **Suggested fix:** Keep only the values that really need changing without a new build: the kill switch, the model list, consent texts, error text and the privacy policy version. Move the rest to constants in code. AR2 stays accepted but becomes less likely to happen.

#### N3. `expireAt` and the report purge are underspecified - Severity: Nit
* **Reviewer Lens:** `egm-zoom-out`
* **Affected Scope:** Q6 resolution (line 1114); §10 report fields; `report_policy.retentionDays`; item 19 `purge-reports.ts`
* **Problem:** Reports carry `expireAt`, but §10 does not list it, and the rules reject any field outside the list. How `retentionDays` relates to `expireAt` is also unclear. A purge script may not be needed for a small tester collection.
* **Suggested fix:** Add `expireAt` to the §10 field list and to the rules. Alternatively, drop `expireAt`, `retentionDays` and the script, and delete reports by hand in the console.

#### N4. Implementation-level detail inside the architecture section - Severity: Nit
* **Reviewer Lens:** `egm-zoom-out`
* **Affected Scope:** §12 design tokens (lines 724-728); item 27 (the 720/680 thresholds); item 54 (`writeJsonAtomic`)
* **Problem:** Hex values, radii, exact thresholds and helper names appear in the architecture section. The contrast rule is also stated twice: G19 says "at least 65%", while §12 gives 72% and 94%.
* **Suggested fix:** Move the token values into `design/tokens.ts` or the mockup's design-system file and point to them. Keep §12 to layout structure and the contrast rule.

#### N5. `react-native-performance` is listed as a dependency but nothing uses it - Severity: Nit
* **Reviewer Lens:** `egm-zoom-out`
* **Affected Scope:** Item 1 `package.json` (line 1145)
* **Suggested fix:** Remove it, or name the measurement that uses it (for example, the time from Send to the first word).

#### N6. The image edit list and `imageVersionId` serve an editing feature that is not planned - Severity: Nit
* **Reviewer Lens:** `egm-zoom-out`
* **Affected Scope:** G8 (lines 110-113); §6; item 43
* **Problem:** The edit list is always empty and `imageVersionId` never changes within a v1 conversation. The author decided this (decision 34).
* **Suggested fix:** Keep only `originalUri` and `sendCopy`. Add the other fields when editing is designed. Editing starts a new conversation anyway, so nothing would need migrating.

#### N7. A single re-encode attempt can reject valid photos - Severity: Nit
* **Reviewer Lens:** `egm-inventive-catalyst`
* **Affected Scope:** Item 45 `pipeline.ts` (lines 1536-1537)
* **Problem:** If the second encode is still over `maxBytes`, as can happen with a detailed panorama or a noisy low-light photo, a valid photo fails as "Image could not be read".
* **Suggested fix:** Use a bounded loop: lower the quality first, then the long edge, down to a floor set in `image_policy`. Record the final size and quality in the send-copy record, so harness rows and reports show what was actually sent.

## What the design gets right (zoom-out lens)

- No backend of its own.
- One network wrapper with a host allowlist.
- Nothing from the AI SDK leaves an adapter, which keeps AR1 contained.
- Plain files instead of a database.
- The choice of state-store library is deferred to a Phase 0 spike.
- A clear list of non-goals.
