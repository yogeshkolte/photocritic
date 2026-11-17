# Goldfish Review Report: Stage 7 (Critic Review)

**Target:** `/Users/yogeshkolte/aiprojects/photocritic/docs/designs/photocritic.md` (lines 1-1072; the Detailed Implementation Plan from line 1073 was out of scope)

**Standards:** egm-design and egm-structural used `resources/document_standards/feature-spec-standard.md`. egm-style used its persona only.

**Constraint applied:** AR1-AR3 (Accepted Risks) were not raised as findings. The only AR-related finding is M14, where the text contradicts AR3.

## Reviewer Status

| Reviewer | Status | Raw findings (Blocker / Major / Nit) |
| --- | --- | --- |
| `egm-design` | completed | 0 / 10 / 6 |
| `egm-structural` | completed | 0 / 6 / 6 |
| `egm-style` | completed | 0 / 9 / 11 |

**After deduplication:** 0 Blockers, 21 Majors, 20 Nits. Where two reviewers raised the same finding, it appears once with both reviewers listed.

**Overall verdict:** No blockers, but the spec does not pass yet. The main gaps:
- there is no Risks and Mitigations section;
- some privacy and security promises rest on platform behavior nobody has checked (Firestore offline cache, URLs from Remote Config, `redirect: 'error'`, HEIC/AVIF on older Android);
- several failure paths are undefined (errors mid-stream, the kill-switch fallback, leftover files after a crash);
- cross-references are inconsistent and some are wrong.

---

## Prioritized Findings (Blockers -> Major -> Nits)

## 🔴 Blockers

None.

## 🟡 Major

### M1. No Risks and Mitigations section - Severity: Major
* **Reviewer Lens:** `egm-design`, `egm-structural`
* **Affected Scope:** the whole document; Accepted Risks (lines 283-302); Q11 (line 273); Q7 (line 260); line 1018
* **Discrepancy Description:** The standard requires failure modes, security implications, scalability limits and their mitigations. The document lists only AR1-AR3. Other risks are scattered across Open Questions, the NFRs, Errors and "Decided during drafting", and none is paired with its mitigation. Q11 defers "Round-4 findings L1-L30" to the brief, and the brief marks some of those as Blockers or Majors that conflict with this spec (see M2, M3, M9). Line 285 says "these three risks", but Q7 and line 1018 describe a fourth accepted risk (open-write reports) that is not in the register.
* **Evidence:** Q7 "The open-write risk is accepted"; Q11 "L1–L30"; Q10 lines 270-272 (crash data recorded before opt-in); line 997 (90-day TestFlight expiry); line 815 (provider silently re-routing to another model).
* **Rationale & Recommendation:** Add `## Risks & Mitigations` as an H2, either between Technical Plan and Alternatives or next to Accepted Risks. Use a table with columns for risk, kind (security / failure / scale / operations), mitigation, and owner or phase. Bring in every L-item still open with its status (fixed here, accepted, or deferred to a named phase). Cover at least:
  - a bad or compromised Remote Config publish;
  - report-write floods using up the Spark-plan quota (L6);
  - crash data captured automatically (L10);
  - the Firestore on-device cache (L5);
  - URLs that come from Remote Config (L11).

  Then either make the open-write report risk AR4, or reword line 285 so it does not claim to list every accepted risk.

### M2. The Firestore offline queue breaks "nothing is stored" - Severity: Major
* **Reviewer Lens:** `egm-design`
* **Affected Scope:** G15; CUJ 5 (lines 351-353); CUJ 6; §10 (lines 694-695); Non-Goals (line 207)
* **Discrepancy Description:** The spec promises that nothing about past conversations is stored on the device, and the dialog says "PhotoCritic keeps nothing from it." But §10 relies on the Firestore SDK to queue writes while offline, and persistence is on by default on native platforms. Report text, which includes the conversation, can therefore stay in an on-device cache indefinitely. The write also only resolves when the server confirms it, and the spec never says how the app tells "sent" apart from "saved to send later".
* **Evidence:** brief L5; `research/rnfb-firestore.txt` ("enabled by default on native platforms").
* **Rationale & Recommendation:** Pick one approach and write it into §10:
  - turn persistence off, so the queue lives only in memory;
  - keep persistence and clear it after `waitForPendingWrites`;
  - keep the cache and disclose it in the privacy policy and the dialog text.

  Also define the timing rule that decides which delivery state the user sees.

### M3. URLs from Remote Config are not allowlisted, so a bad publish could phish for keys - Severity: Major
* **Reviewer Lens:** `egm-design`
* **Affected Scope:** §8 (`keyHelpUrl`, `updateUrl`, `policyUrl`, `privacy_policy.url`); the §7 "open billing page" action; Alternative E2 (lines 884-890)
* **Discrepancy Description:** E2 rejects signed config on the grounds that "the real risk, a wrong provider host, is covered by the in-code host allowlist." That allowlist only covers provider fetch calls. The URLs the user taps (key help, Update, policy, billing) come from Remote Config, and validation only checks `baseUrl`. A compromised console account or a careless publish could send users to a fake page that asks for their key.
* **Evidence:** brief L11.
* **Rationale & Recommendation:** Make config validation require https for every user-facing URL and check its host against an in-code allowlist (domains per provider, plus TestFlight and Play). Then update the E2 rejection reason to match.

### M4. Android minSdk 26 conflicts with "convert every format, HEIC and AVIF included" - Severity: Major
* **Reviewer Lens:** `egm-design`
* **Affected Scope:** G7 (lines 104-109); Q2 (line 248); §6
* **Discrepancy Description:** Android's platform HEIC decoder arrives at API 28 and AVIF at API 31. The picker returns originals, HEIC and AVIF included (brief item 9). Whether `expo-image-manipulator` can decode them on API 26-30 is marked unverified in the brief. On those devices the main use case could end at "Image could not be read".
* **Rationale & Recommendation:** Do one of the following:
  - raise minSdk to 28, or 31 if AVIF must work;
  - narrow G7 to the formats each OS version can decode;
  - make the Q2 decision depend on the fixture tests.

  State that "Image could not be read" is the planned result on older devices.

### M5. What happens to a partial answer after a mid-stream error or retry is undefined - Severity: Major
* **Reviewer Lens:** `egm-design`
* **Affected Scope:** CUJ 4 (line 340); §3 TurnEvent (line 499); §5 state machine (line 563); §7 retry policy (lines 622-626)
* **Discrepancy Description:** The state machine has a `streaming --error--> error(class)` transition and retries "at most twice". The spec does not say:
  - whether a retry is allowed after text has already streamed, which would bill the user again and duplicate the text on screen;
  - whether the partial text is kept, labelled or thrown away;
  - whether it enters `history` (only stopped answers are excluded).

  Anthropic commonly returns `overloaded_error` mid-stream, after a 200 response.
* **Rationale & Recommendation:** Allow retries only before the first text event. A mid-stream error ends the turn and keeps the partial text under a label such as "Interrupted". Leave it out of history, the same as Stopped. Add this transition to §5.

### M6. The redirect and connect-timeout promises rest on unchecked fetch behavior - Severity: Major
* **Reviewer Lens:** `egm-design`
* **Affected Scope:** §4 (lines 532-545); the "connect timeout" row in §7
* **Discrepancy Description:** The wrapper "refuses redirects (`redirect: 'error'`)", but the brief marks support for that option in `expo/fetch` as "verify", and §4 never names which fetch it uses. This is a security control: custom key headers (`x-api-key`, `x-goog-api-key`) follow cross-origin redirects, so if the option is silently ignored, keys could leak. The fetch API also has no separate connect timeout, only abort, so the "connect timeout" error class may not be detectable.
* **Rationale & Recommendation:** Name `expo/fetch` in §4. Make redirect refusal an explicit Phase 0 pass/fail check, with a fallback that checks the response URL against the allowlist before reading the body. Replace "connect timeout" with a time-to-response-headers timeout, or drop that error class.

### M7. The benchmark harness cannot measure "on current phones", and the performance "targets" have no values - Severity: Major
* **Reviewer Lens:** `egm-design`, `egm-style`
* **Affected Scope:** Overview (lines 21-22); G23 (lines 193-197); NFR Performance (lines 1032-1037)
* **Discrepancy Description:** The Overview says the app measures, on current phones, how well vision models read photos and how long the first answer takes. G23 makes the harness "the main measure". But the harness is a Node tool that runs the adapters under Node. Its resized images do not come from `expo-image-manipulator`, so the Q5 accuracy-per-size data measures a different image pipeline. Its timings reflect a desktop network and runtime, not a phone. Separately, the NFR heading promises "targets", yet it lists only metrics, with values "set in Phase 0", and it refers to "the oldest supported phone" while Q2 is still open.
* **Rationale & Recommendation:** Do one of the following:
  - generate the send copies with the app's own pipeline on a device (for example, through a dev-build export mode) and record timing on the device;
  - reword the Overview and G23 to say the harness measures model accuracy, not phone performance.

  Add measurable acceptance thresholds, or at least the method for setting them. Retitle the NFR "Performance metrics (numeric targets set in Phase 0 after first measurements on the oldest phone allowed by Q2)".

### M8. The kill-switch fallback and live-update rules are underspecified - Severity: Major
* **Reviewer Lens:** `egm-design`, `egm-style`
* **Affected Scope:** G18 (lines 171-173); CUJ 7; §5 (line 565); §8 load-order steps 1 and 5 (lines 650-665); `fallbackId` / `replacementId`
* **Discrepancy Description:**
  - Validation does not require `fallbackId` to point to a model that is enabled and usable.
  - The case where the fallback's provider has no key or consent is not covered.
  - The difference between `fallbackId` and `replacementId` is not explained.
  - The state-diagram arrow "kill switch (confirm if any turn)" reads as if a remote disable asks the user to confirm, which conflicts with G18, CUJ 7 and line 570.
  - An in-flight stream is not explicitly aborted.
  - "10-second timeout" could mean the native `fetchTimeoutMillis` or a JavaScript race. These behave differently (brief L4): under a race, a late native activation can stop the listener from delivering a kill switch.
  - "Only kill-switch changes apply immediately" sits uneasily with `activate()`, which activates every changed value.
* **Rationale & Recommendation:**
  - Validate that every `fallbackId` resolves to a usable model, preferably from the same provider. Route to Setup if that provider has no key.
  - Define a kill-switch transition from every state that aborts the request, with no confirmation, and label it separately in the diagram.
  - Name which timeout is meant.
  - State that live updates re-validate only `kill_switch` and never rebuild the snapshot.

### M9. Files left behind by a crash or kill are never cleaned up - Severity: Major
* **Reviewer Lens:** `egm-design`
* **Affected Scope:** CUJ 5; §6 (line 600); §9 (line 680); Q4
* **Discrepancy Description:** Cleanup only runs when a conversation ends. Files are written to the app cache regardless: the picker and camera copy originals there, `expo-image-manipulator` writes its output there, and §9 writes temp files before renaming them. If the app crashes or is killed, the photos stay on disk, which contradicts "PhotoCritic keeps nothing". The picker's copy of the original is not treated as a file the app owns.
* **Rationale & Recommendation:** Sweep the app's image and temp directories at every launch. Count the picker/camera copy as a conversation file. Promote or delete stray `.tmp` files (brief L5).

### M10. CUJ 8 promises streaming survives a layout change, but Q4 leaves rotation mid-stream open - Severity: Major
* **Reviewer Lens:** `egm-design`
* **Affected Scope:** CUJ 8 (line 380); Q4 (lines 252-254); §5 (lines 575-576)
* **Discrepancy Description:** CUJ 8 says "The conversation and any streaming answer continue" across the sheet/split switch, while Q4 lists rotation mid-answer as undecided. The switch unmounts and remounts the sheet. If a component owns the stream iterator or the AbortController, the stream dies with it.
* **Rationale & Recommendation:** State in §5 that the session store, not a screen or component, owns the request and its AbortController. Then either resolve the rotation and layout parts of Q4, or narrow CUJ 8 to what is guaranteed.

### M11. Reference schemes are undefined, and some references point to nothing - Severity: Major
* **Reviewer Lens:** `egm-style`, `egm-structural`
* **Affected Scope:** throughout. Examples: line 6 "decisions 1-37"; line 96 "(decision 36, D34)"; line 98 "(D41)"; A1 line 758 "answer 7"; A2 line 771 "answer 2"; line 881 "round 3"; Q11 line 273 "L1–L30"; Q13 line 281 "finding O7"; AR3 line 299 "contradiction 6"; lines 630 and 650 "brief section I.3 / K.4 / K.5 / K.6"
* **Discrepancy Description:** At least seven ID schemes are used: decision N, DN gate rows, answer N, round N, ON or LN findings, contradiction N, and brief section letters. Only "decision numbers" are explained (lines 746-747). "Answer 7" and "answer 2" do not exist in the brief: decision 7 is the demonstrator rationale, and cloud-first is decision 1. "Contradiction 6" presumably means brief section F, row 6.
* **Rationale & Recommendation:** Add a short "References and notation" key under Source (line 6) that defines each prefix and links to the matching brief section. Fix "answer 7" and "answer 2" to the correct decision numbers (cloud-first is decision 1; Expo over bare React Native is decision 2). Write "contradiction 6" as "brief section F, row 6". Ideally, use fewer schemes.

### M12. Some decision citations are wrong - Severity: Major
* **Reviewer Lens:** `egm-structural`
* **Affected Scope:** G6 (line 103); G12 (line 139); Alternative D3 (line 871)
* **Discrepancy Description:** Brief decision 8 is "Three providers in v1" (brief line 1954). The rule "original shown at full quality, only the send copy is downscaled" is decision 6 (brief line 1952). Decision 9 is the Gemini free-tier pointer and privacy policy (brief line 1955), not plain-language errors with request IDs.
* **Rationale & Recommendation:** Change lines 103 and 871 to decision 6. For line 139, cite the correct source, probably brief items 7 and 13.

### M13. Open Questions contains resolved items - Severity: Major
* **Reviewer Lens:** `egm-style`, `egm-structural`
* **Affected Scope:** Open Questions (lines 238-281); intro at lines 240-241; "Decided during drafting" (lines 1011-1022)
* **Discrepancy Description:** The intro says the items are "carried from the brief without decisions" and "stay open until decided". But Q6-Q10 are labelled "Resolved", Q12 is "partly resolved", and some resolutions are repeated under "Decided during drafting". Q10 is marked "Resolved" yet still carries an open check ("check in the Phase 0 spike").
* **Rationale & Recommendation:** Move Q6-Q9 into a single "Resolved during drafting" list. Keep Q10's open part (crash reports sent once sharing turns on) and Q12's open part in Open Questions. Make the intro match what stays.

### M14. "Check every field" overstates the Firestore rules and contradicts AR3 - Severity: Major
* **Reviewer Lens:** `egm-style`
* **Affected Scope:** §10 Reports (lines 692-693)
* **Discrepancy Description:** The text says "Security rules allow `create` only and check every field." AR3 (lines 299-302) says the rules "can check the list's length but not the text inside each item". This is a contradiction of accepted behavior, so it is in scope.
* **Rationale & Recommendation:** Reword to: "Security rules allow `create` only and check every top-level field; for `conversation`, only the list length is checked (AR3)."

### M15. The tint percentages disagree across sections - Severity: Major
* **Reviewer Lens:** `egm-style`
* **Affected Scope:** G19 (lines 180-181); §12 (lines 714-716); Alternative F1 (line 945)
* **Discrepancy Description:** G19 and F1 set a 65% floor under text. §12 says 72% (collapsed) and 94% (expanded) under text, and 65% under controls.
* **Rationale & Recommendation:** State one rule in G19 (72% / 94% under text, 65% under controls) and have F1 and §12 point to it.

### M16. The retry limit and retry time cap are not stated precisely - Severity: Major
* **Reviewer Lens:** `egm-style`
* **Affected Scope:** CUJ 4 (line 340); §7 (line 622); NFR (lines 1039-1040); §8 table (line 639)
* **Discrepancy Description:** It is unclear whether "2" is fixed in code or is the default of `conversation_policy.maxRetries`. The "time cap" has no value and no config parameter. `conversation_policy` lists only `requestTimeoutMs` and `connectTimeoutMs`.
* **Rationale & Recommendation:** Write "at most `conversation_policy.maxRetries` (default 2) retries". Name the parameter or constant that holds the total-wait cap, or state that the cap equals `requestTimeoutMs`. See also M5.

### M17. A Non-Goal cites minimums that have not been set - Severity: Major
* **Reviewer Lens:** `egm-style`
* **Affected Scope:** Non-Goals (line 236); Q2 (lines 248-249)
* **Discrepancy Description:** Line 236 says "below the minimums set in D9", but Q2 says D9 is still open ("Recommended iOS 17 and Android minSdk 26").
* **Rationale & Recommendation:** Reword to "below the minimums to be set in Q2 (D9)". See also M4.

### M18. The Alternatives section cites message-building options A, B and C that are never defined - Severity: Major
* **Reviewer Lens:** `egm-style`, `egm-structural`
* **Affected Scope:** C2 and C3 (lines 832-846); §3 heading (line 456)
* **Discrepancy Description:** "Option A", "option B" and "option C" are not defined anywhere in the document. "Rejected by author (Technical Plan §3)" is circular, because §3 states the outcome, not the decision. No brief decision number is cited.
* **Rationale & Recommendation:** In §3, add one sentence naming options A, B and C, and cite the brief decision or round where option B was chosen. Otherwise, drop the letters.

### M19. The "no config fallback" rule is stated five times, and the copies drift - Severity: Major
* **Reviewer Lens:** `egm-style` (the `egm-design` reviewer noted the same script-name difference as wording only)
* **Affected Scope:** G17 (lines 165-168); Q9 (lines 266-269); AR2 (lines 294-298); "Decided during drafting" (lines 1019-1022); NFR Rollout (lines 1042-1044)
* **Discrepancy Description:** AR2 names `config:publish` ("which validates before publishing"), while the NFR says every publish "must pass `config:validate` first". G17 and AR2 leave out the throttled-fetch exception in §8 step 2 (lines 657-659), where the app uses its last activated values. This finding is about redundancy and drift; it does not challenge AR2.
* **Rationale & Recommendation:** State the rule once (in AR2 or G17), including the throttled exception and a single script name, and point to it from the other four places.

### M20. Additional Resources and Future Improvements are nested under the wrong parent - Severity: Major
* **Reviewer Lens:** `egm-structural`
* **Affected Scope:** "### Additional Resources" (line 1049); "### Future Improvements" (line 1060)
* **Discrepancy Description:** Both are H3s, so they become children of "## Non-Functional Requirements", although the `---` at line 1047 shows they were meant as separate closing sections.
* **Rationale & Recommendation:** Promote both to H2, or group them under a new "## Appendix".

### M21. "Decided during drafting" breaks the Alternatives format and is incomplete - Severity: Major
* **Reviewer Lens:** `egm-structural`, `egm-style`
* **Affected Scope:** lines 1011-1022
* **Discrepancy Description:** The Alternatives intro (line 745) promises a main benefit, a main cost and a reason for every entry, but these three bullets have no Pros or Cons. The list also leaves out items described elsewhere as decided during drafting: low-data mode (line 233), over-the-air updates (lines 234-235), no backup restore (Q8), no special crash handling (Q10), and Q9 (line 168). The lead-in at line 1013 repeats the heading.
* **Rationale & Recommendation:** Either turn each item into a full entry (Pros / Cons / Reason for Rejection), or retitle the section as a summary list and explain the lighter format. Add the missing items and drop the line-1013 lead-in. Coordinate with M13.

## 🟢 Nits

### N1. "About" is used for exact values - Severity: Nit
* **Reviewer Lens:** `egm-style`
* **Affected Scope:** line 107 and line 855 ("a fixed target of about 2048 px"); lines 182, 376 and 719 ("about 700 points"); lines 379 and 719 ("with a margin")
* **Discrepancy Description:** `defaultLongEdgePx` is exactly 2048, and "a fixed target of about" contradicts itself. The layout breakpoint and margin have no numbers.
* **Rationale & Recommendation:** Write "2048 px by default (`image_policy.defaultLongEdgePx`)". Give the breakpoint and its margin as numbers (for example, switch above 700 pt and back below 660 pt), or say they are set in Phase 0.

### N2. Claims about other apps and Apple have no evidence - Severity: Nit
* **Reviewer Lens:** `egm-style`
* **Affected Scope:** lines 37-39 (general chat apps hide the model); line 25 (TestFlight intent); line 989 (demo-mode rule)
* **Rationale & Recommendation:** Cite a research file or brief section for each claim, or narrow the wording (for example, name the specific apps).

### N3. Hedges with no decision attached - Severity: Nit
* **Reviewer Lens:** `egm-style`
* **Affected Scope:** line 1005 ("App Distribution can skip Play review (not verified)"); line 780 ("(as of mid-2026; not re-verified)")
* **Rationale & Recommendation:** Verify the G2 Pro or drop it. For B1, keep the parenthesis only if someone must re-check before Phase 5.

### N4. A claim stronger than the tests can support - Severity: Nit
* **Reviewer Lens:** `egm-style`
* **Affected Scope:** lines 522-523 ("cannot drift apart unnoticed")
* **Rationale & Recommendation:** Reword to "drift in the behaviors covered by the fixtures is caught in CI".

### N5. The contrast threshold is overstated - Severity: Nit
* **Reviewer Lens:** `egm-style`
* **Affected Scope:** lines 942-943 ("far below the 4.5:1 needed to read it")
* **Rationale & Recommendation:** Reword to "below the WCAG AA minimum of 4.5:1 for body text".

### N6. Absolute or loaded wording - Severity: Nit
* **Reviewer Lens:** `egm-style`
* **Affected Scope:** line 22 ("every kind of failure"); line 41 ("hard numbers"); line 889 ("The real risk")
* **Rationale & Recommendation:** Use "provider, network and image errors", "measurements" and "The remaining risk".

### N7. Terms used without explanation - Severity: Nit
* **Reviewer Lens:** `egm-style`
* **Affected Scope:** G21 "Darkroom v1 look" (line 186); line 209 "conversation log is withdrawn"; line 533 "v7 factories"; line 273 "section L"; line 1062 "(Phase 5)"; Q10 "spike"; Q2 "Recommended"
* **Rationale & Recommendation:** Define each term briefly where it first appears, or cut it. For example: "Darkroom (the mockup's visual theme)", "AI SDK v7", "a Phase 0 test".

### N8. G12, CUJ 4 and §7 describe the six errors differently - Severity: Nit
* **Reviewer Lens:** `egm-style`
* **Affected Scope:** G12 (line 137, "six user actions"); CUJ 4 (line 341, "six plain messages"); §7 table (separate "User message" and "One-tap action" columns)
* **Rationale & Recommendation:** In G12, write "one of six user messages, each with one action".

### N9. The design-token list item is badly formatted and uses loose ranges - Severity: Nit
* **Reviewer Lens:** `egm-style`, `egm-structural`
* **Affected Scope:** lines 724-728
* **Discrepancy Description:** The continuation lines are not indented, and code spans break across lines (`surface`/`#17181B`, `accent`/`#F5B13D`), so some lines begin with "#". "radii 12 / 14–16 / 24 / 999; spacing 4–32" gives ranges, not exact values.
* **Rationale & Recommendation:** Indent the continuation lines and keep each token on one line, or use a table. List exact values, or point to `design/tokens.ts` or `mockup/System.dc.html`.

### N10. Some Reason for Rejection fields give no trade-off - Severity: Nit
* **Reviewer Lens:** `egm-structural`
* **Affected Scope:** B3 (line 799); E3 (line 897); E6 (line 920); G2 (lines 1008-1009)
* **Rationale & Recommendation:** Add one clause naming the deciding trade-off. For example, G2: "keeps Android on the same Play pipeline a public release will use".

### N11. The §3 heading carries a decision note - Severity: Nit
* **Reviewer Lens:** `egm-structural`
* **Affected Scope:** line 456
* **Rationale & Recommendation:** Rename the heading to "### 3. Provider interface" and move the decision note into the body. See M18.

### N12. Line wrapping is uneven - Severity: Nit
* **Reviewer Lens:** `egm-structural`
* **Affected Scope:** lines 255, 690 and 1035
* **Rationale & Recommendation:** Rewrap these lines to the document's usual width of about 80 columns.

### N13. Non-Goals, NFRs and Future Improvements have no IDs - Severity: Nit
* **Reviewer Lens:** `egm-structural`
* **Affected Scope:** lines 207-236, 1026-1045 and 1062-1069
* **Rationale & Recommendation:** Add NG#, NFR# and FI# IDs so the plan and reviews can cite these items.

### N14. Q13 says the state list is unwritten although §5 lists it; config-unavailable has no CUJ - Severity: Nit
* **Reviewer Lens:** `egm-structural`
* **Affected Scope:** Q13 (lines 279-281); §5 (lines 547-576); CUJs (lines 304-380)
* **Rationale & Recommendation:** Point Q13 to §5 and narrow it to what is actually missing (for example, the exact text and controls for each state). Consider adding a CUJ for "configuration cannot be loaded at launch". It would document what AR2 accepts, not challenge it.

### N15. `providers.baseUrl` duplicates the hosts fixed in code - Severity: Nit
* **Reviewer Lens:** `egm-design`
* **Affected Scope:** G17 (lines 166-170); §8 (line 636)
* **Rationale & Recommendation:** With one allowed host per provider, the field can only repeat the built-in value. Drop it (brief L11).

### N16. The consent version is defined twice - Severity: Nit
* **Reviewer Lens:** `egm-design`
* **Affected Scope:** §8 (lines 636 and 641); §9 (line 682)
* **Rationale & Recommendation:** Keep one of `providers.consentTextVersion` and `consent_texts.version`, or make validation require them to be equal.

### N17. A mismatch between the served and requested model has no defined outcome - Severity: Nit
* **Reviewer Lens:** `egm-design`
* **Affected Scope:** §3 (line 514)
* **Rationale & Recommendation:** Say what happens on a mismatch: record it in the report, add a telemetry flag, or show it to the user. This matters because the main screen never shows the model name (decision 37).

### N18. The report fields and telemetry events are only cited, never listed - Severity: Nit
* **Reviewer Lens:** `egm-design`
* **Affected Scope:** §10 and §11 (cite brief K.5 and K.6)
* **Rationale & Recommendation:** List the fields and events inline or in an appendix, so a reader can check them against the personal-data promises and the security rules.

### N19. "English only" sits in the tablets goal - Severity: Nit
* **Reviewer Lens:** `egm-design`
* **Affected Scope:** G20 (line 185)
* **Rationale & Recommendation:** Remove it from G20. It already appears in Non-Goals.

### N20. Camera permission and "New photo" during a stream are not covered - Severity: Nit
* **Reviewer Lens:** `egm-design`
* **Affected Scope:** §6; §12
* **Rationale & Recommendation:** State how the camera-permission prompt and a denial are handled, and that tapping New photo during a stream aborts the request. Both cases touch the privacy and billing paths.

---

**Checked and fine (egm-design):**
- Keys travel only in headers to fixed hosts.
- No AI SDK types leave an adapter, so AR1 stays limited to `providers/`.
- The reinstall marker is kept out of the secure store.
- Downsampling happens at decode time, with evidence.
- Every alternative has a concrete rejection reason.
- Nothing else contradicts AR1-AR3.

**Checked and fine (egm-structural):** all relative links resolve. The private claude.ai mockup link on line 6 has a local copy linked next to it.
