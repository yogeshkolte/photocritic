# Goldfish Review Report: Stage 8 (Intermediate Zoom Out)

**Target:** `/Users/yogeshkolte/aiprojects/photocritic/docs/designs/photocritic.md` (lines 1-1806)
**Mode:** REVIEW
**Standard:** `zoom-out-standard.md`
**Focus:** the additions and mitigations proposed in Stages 6 and 7. Would they over-scope a technology demonstrator? Which Stage 5-7 findings can be cut, merged or simplified? The document has not been edited since Stage 5, so every fix in the earlier reports is still only a proposal.
**Constraint applied:** AR1-AR3 are not raised. Where a proposal would change what an accepted risk says, this report notes it.

## Reviewer Status

| Reviewer | Status | Raw findings |
| --- | --- | --- |
| `egm-zoom-out` | completed | 0 Blocker, 8 Major, 6 Nit |

Coverage notes:
- The reviewer read the whole target, the three earlier reports, and brief sections K.4-K.6 and L1-L30 (`concepts_brief.md` lines 941-1171).
- It did not open the mockup or research files, because no finding depended on them.
- There is no PhotoCritic code yet, so it had no code to search for reuse.
- Spot checks on the target matched these lines: 296 and 1020 ("restart screen"), 1112 ("Decisions made during drafting"), 1421 (`AbortController` in the store), 1482 ("retries after the first streamed text") and 1544 ("at launch").

## Summary

**0 Blockers, 8 Majors, 6 Nits.**

Stages 6 and 7 mostly found real gaps. But about a third of their fixes add process, parameters or text that a demonstrator shipped only to TestFlight and Play internal testing does not need, and several of them work against Stage 5's own simplifications. Two "missing" mechanisms from Stage 7 are already in the Detailed Plan. One proposed security fallback (Stage 7 M6) would not protect anything.

Four changes resolve most findings from all three stages:
1. Move tuning values and user-facing URLs out of Remote Config into code constants.
2. Classify errors in code.
3. Use a one-step publish rule for the kill-switch fallback.
4. Keep a single list of decisions made during drafting.

## Prioritized Findings (Blockers -> Major -> Nits)

### 🔴 Blockers

None.

### 🟡 Major

#### Z1. A full Risks and Mitigations register (Stage 7 M1) is more than the gap needs - Severity: Major
* **Reviewer Lens:** `egm-zoom-out`
* **Affected Scope:** Q11 (line 273); the Accepted Risks intro (line 285); Q7 (line 260); Stage 7 M1; Stage 6 #3.
* **Problem:** Stage 7 M1 asks for a new section with a risk table that brings in every open L-item. But the design already settles most of L1-L30, so a register would argue them again and become a second copy that drifts from the brief:
  * L1 is settled by Q6.
  * L2 is settled by AR3.
  * L3 is settled by §9 and item 55.
  * L4 and L8 are settled by §8 and AR2.
  * L6 is accepted in Q7.
  * L10 is settled by the error handler in item 57.
  * L11 is settled by §4 (the rest of it is Stage 7 M3).
  * L9, L13 and L16 are Phase 4 compliance and wording work, not architecture.
* **Evidence:** Line 273: "Round-4 findings L1–L30 (deferred). Listed in the brief, section L." Line 285 says "these three risks", while line 260 says "The open-write risk is accepted."
* **Suggested fix:** Don't add a register. Replace Q11 with a short grouped list:
  * absorbed into this design: L1, L2, L3, L4, L8, L10, L11;
  * accepted: L6 (in Q7);
  * Phase 4 compliance: L9, L13, L16, L17;
  * still open: L5, L7, L12, L14.

  This also covers Stage 6 #3. For the open-write risk, either add Q7 to Accepted Risks as one line, or reword line 285 so it no longer says "these three". That choice belongs to the author.

#### Z2. Moving tuning values out of Remote Config resolves many findings, and Stage 6 and 7 fixes push the other way - Severity: Major
* **Reviewer Lens:** `egm-zoom-out`
* **Affected Scope:** §8 parameter table (lines 636-643); §7 retry (line 622); item 9 (lines 1227-1229); item 15 (lines 1280-1283). Related findings: Stage 5 N2, M2, M8; Stage 6 #7; Stage 7 M16, N15, N16.
* **Problem:** Several later fixes add parameters, defaults or cross-checks that exist only because these values live in Remote Config:
  * Stage 7 M16 proposes "`maxRetries` (default 2)". AR2 allows no defaults (lines 294-296), so "default 2" contradicts the accepted behavior.
  * Stage 5 M8 adds `firstByteTimeoutMs` and `idleTimeoutMs` to Remote Config. Stage 5's own N2 moves timeouts into code.
  * Stage 5 M2 and Stage 6 #7 add a check that `historyTurnsCap` matches the number hard-coded in the Firestore rules.

  Under AR2, every extra parameter is one more way for a single publish to stop every tester's app.
* **Evidence:** Line 639: "`historyTurnsCap` (upper bound), `maxQuestionChars`, `requestTimeoutMs`, `connectTimeoutMs`, `maxRetries`". Line 1228: "at most `historyTurnsCap × 2 + 2`". Lines 636 and 641 define the consent version twice.
* **Suggested fix:** Make these code constants: `historyTurnsCap`, `maxQuestionChars`, all timeouts, `maxRetries`, the retry wait cap, `jpegQuality`, the report daily limit and `maxConversationChars`. A new tester build ships in about a day (lines 234-235), so this costs little. Then:
  * The rules and `reportService` share one constant, and the cross-check is no longer needed.
  * Stage 7 M16 becomes "at most 2 retries".
  * `providers.baseUrl` is dropped (Stage 7 N15), together with its validation step and test.
  * Only one consent version is kept, `consent_texts.version` (Stage 7 N16).

  Remote Config keeps only the kill switch, the model list, consent texts, error message text and the privacy policy version.

#### Z3. Put the user-facing URLs in code instead of allowlisting them in Remote Config (Stage 7 M3) - Severity: Major
* **Reviewer Lens:** `egm-zoom-out`
* **Affected Scope:** §8 `keyHelpUrl`, `updateUrl`, `policyUrl`, `privacy_policy.url` (lines 635-642); §7 "open billing page" (line 617); Alternative E2 (lines 889-890); Stage 7 M3.
* **Problem:** Stage 7 M3 would add a second allowlist and more validation, which means more ways for a publish to fail. The URLs it protects almost never change. The billing-page URL does not appear in the §8 table at all, so where it comes from is already undefined.
* **Evidence:** Line 889: "The real risk, a wrong provider host, is covered by the in-code host allowlist".
* **Suggested fix:** Keep every user-facing URL in code, one set per provider plus the store links. Then Remote Config offers no phishing path, and the reason E2 gives becomes true as written. Only `privacy_policy.version` stays remote.

#### Z4. Three stages propose three overlapping timeout schemes; use one small set of constants - Severity: Major
* **Reviewer Lens:** `egm-zoom-out`
* **Affected Scope:** §4 (lines 543-544); the §7 "connect timeout" row (line 615); CUJ 4 (line 340); NFR (lines 1039-1040); Stage 5 M8; Stage 7 M6 (timeout half); Stage 7 M16.
* **Problem:** If all three proposals were applied separately, the design would have four or five timeout settings and two error classes for one failure: no answer arrives in time.
* **Evidence:** Line 543: "applies a connect timeout and a total timeout from `conversation_policy`". Line 622: "at most 2 retries with a cap on total wait".
* **Suggested fix:** Use three constants in `platform/net`:
  * a first-response timeout;
  * an idle timeout that restarts with every chunk received;
  * a cap on total retry wait.

  Delete `connectTimeoutMs` and `requestTimeoutMs`. A timeout before the first text maps to "No connection". A stall after the first text is handled as an interruption (Z9). This closes Stage 5 M8, half of Stage 7 M6, and Stage 7 M16.

#### Z5. The proposed fallback for an unsupported `redirect: 'error'` (Stage 7 M6) would not protect the key - Severity: Major
* **Reviewer Lens:** `egm-zoom-out`
* **Affected Scope:** §4 (line 538); item 51 (lines 1587-1589); Stage 7 M6 (redirect half).
* **Problem:** If fetch quietly follows a redirect, the key header has already gone to the new host before the app can see the final response URL. A check on that URL runs too late to help. In any case, a redirect could only come from the provider's own fixed HTTPS host.
* **Evidence:** Stage 7 M6: "a fallback that checks the response URL against the allowlist before reading the body". Line 538: "refuses redirects (`redirect: 'error'`)".
* **Suggested fix:** Keep the Phase 0 pass/fail check (add it to item 67) and name `expo/fetch` in §4. If `redirect: 'error'` is not supported, use `redirect: 'manual'` where it works. Otherwise record this as a known limit. Do not add the response-URL check.

#### Z6. Classifying errors in code (Stage 5 M7) makes Stage 6 #4 and Stage 5 M1(a) unnecessary - Severity: Major
* **Reviewer Lens:** `egm-zoom-out`
* **Affected Scope:** item 40 (lines 1487-1489); item 15 (lines 1282-1283); §7 (lines 608-610); item 60 (lines 1664-1665); Stage 5 M1(a) and M7; Stage 6 #4.
* **Problem:** Stage 6 #4 and Stage 5 M1(a) both edit "(bundled rules if invalid)". That edit only matters while `error_map` stays in Remote Config. If classification moves into code:
  * `classify.ts` stops reading the snapshot;
  * the validator check on `error_map` goes away;
  * the contract test stops depending on the published template;
  * error classes, such as the timeout class, become a code decision.
* **Evidence:** Line 1488: "applies the `error_map` rules from the snapshot in order (bundled rules if invalid)". Line 1282: "every error class in `error_map` has a message".
* **Suggested fix:** Apply Stage 5 M7 and keep only the message text for the six user-facing classes in Remote Config. Stage 6 #4 and Stage 5 M1(a) are then resolved with no separate edit.

#### Z7. All three stages raise the kill-switch fallback, and Stage 5's fix over-scopes it - Severity: Major
* **Reviewer Lens:** `egm-zoom-out`
* **Affected Scope:** G18 (lines 171-173); CUJ 7 (lines 364-371); §5 diagram (line 565); §8 step 5 (lines 664-665); item 15 (lines 1281-1282); item 61 (lines 1675-1676); Stage 5 M11 and M1(c); Stage 6 #5; Stage 7 M8.
* **Problem:** Stage 5 M11 asks for loop detection when publishing, chain-following at runtime, and tests for both. A simpler rule makes chains impossible.
* **Evidence:** Line 1281: "every `fallbackId` and `replacementId` names a listed model". Line 565: "kill switch (confirm if any turn)".
* **Suggested fix:**
  * When publishing, require each `fallbackId` to be an active model from the same provider that is not itself disabled. A model that is not disabled cannot start a chain, so nothing needs to follow one.
  * At runtime, if the fallback is filtered out on this build, open setup in edit mode, which item 22 already provides.
  * Explain how `replacementId` differs from `fallbackId`, or drop `replacementId` if nothing reads it.
  * Fix the diagram label.
  * State once that a live update re-validates only `kill_switch`.

  An optional question for the author: would applying the kill switch only at cold start be enough for testers? Dropping `onConfigUpdate` would remove the late-activation race (L4), the live-update validation case, aborting a stream on a remote kill, and two tests. Q9 currently keeps live updates (lines 268-269).

#### Z8. The "decided during drafting" list exists three times, and Stage 7 M21 would make it longer - Severity: Major
* **Reviewer Lens:** `egm-zoom-out`
* **Affected Scope:** Q6-Q10 (lines 258-272); Alternatives "Decided during drafting" (lines 1011-1022); Implementation "Decisions made during drafting" (lines 1112-1124). The no-fallback rule is repeated at lines 165-168, 266-269, 294-298, 1019-1022, 1042-1044, 1119-1120, 1284-1285 and 1358-1360. Related findings: Stage 7 M13, M19, M21; Stage 5 M1(b), M10.
* **Problem:**
  * Stage 7 M21 wants every drafting decision turned into a full Pros/Cons entry, plus five more items.
  * No earlier report noticed the third copy at lines 1112-1124.
  * The repeated text makes Stage 5 M10 (Try again instead of restart) costly, because the restart wording appears in at least five places. AR2's own wording at line 296 is one of them.
* **Evidence:** Line 1112: "Decisions made during drafting (Open Questions Q6–Q10, Q12)". Line 240: "These are carried from the brief without decisions". Lines 296 and 1020: "restart screen".
* **Suggested fix:**
  * Keep one short "Resolved during drafting" list and delete the other two copies. Don't expand the items into full entries.
  * State the no-fallback rule once, including the throttled-fetch exception and a single script name, and point to it from the other places.
  * Do this before applying Stage 5 M10. If M10 is applied, the author should also update the "restart screen" wording in AR2.

### 🟢 Nits

#### Z9. Duplicate findings across stages to merge - Severity: Nit
* **Reviewer Lens:** `egm-zoom-out`
* **Affected Scope:** Stage 7 M5 and Stage 5 M9; Stage 7 M15, Stage 6 #9 and Stage 5 N4; Stage 7 N17 and Stage 6 #6; Stage 7 N1 and Stage 5 N4.
* **Problem:**
  * Stage 7 M5 duplicates Stage 5 M9. Its question about retrying after text has already arrived is answered at line 1482: "Never retries after the first streamed text".
  * The tint finding appears in three stages. Stage 7 rates it Major, but it is a Nit.
  * The served-model mismatch appears twice. `servedModel` is already a report field (line 688) and a canary failure (line 1255), so recording it is enough.
  * Stage 7 N1 wants exact breakpoints in the goals, while Stage 5 N4 wants thresholds kept out of the architecture. Item 27 already holds 720/680 (line 1378).
* **Suggested fix:** Apply each fix once:
  * Use Stage 5 M9's wording for interrupted answers, with the stall case from Z4.
  * Put one tint rule in G19.
  * For a served-model mismatch, record it in reports, the harness and the canary, with no action shown to the user.
  * Have G20 and CUJ 8 point to item 27 for the breakpoints.

#### Z10. Two mechanisms that Stage 7 calls missing are already in the Detailed Plan - Severity: Nit
* **Reviewer Lens:** `egm-zoom-out`
* **Affected Scope:** Stage 7 M9 (cleanup sweep at launch); Stage 7 M10 (who owns the AbortController); §6 (line 600); §5; CUJ 8 (line 380).
* **Evidence:**
  * Lines 1543-1544: cleanup deletes send copies and picker temporary files "when a conversation ends and at launch".
  * Line 1611: the plan handles a leftover temporary file at launch.
  * Line 1421: the store calls `send` "with an `AbortController`".
* **Suggested fix:** Build nothing new.
  * Add "and at launch" to line 600.
  * Add one line to §5 saying that the session store owns the request.
  * Narrow CUJ 8 until Q4 is decided.

#### Z11. Appendix tables, a glossary and new ID schemes add weight to the document - Severity: Nit
* **Reviewer Lens:** `egm-zoom-out`
* **Affected Scope:** Stage 6 #1, #2, #10; Stage 7 M11, M12, N7, N13, N18; §10 (lines 686-692); §11 (lines 700-701).
* **Problem:**
  * Stage 7 N13 adds three ID schemes, while Stage 7 M11 says the document already has too many.
  * A glossary would define process-history terms ("round 3", "finding O7", "contradiction 6") that a reader of the design does not need.
  * Appendix tables with types and sizes would be a third copy of the report schema, after the zod schema and the rules (brief L20 warns about this kind of drift).
* **Suggested fix:**
  * Drop Stage 7 N13.
  * Delete the process-history references instead of defining them.
  * Add a two-line key explaining "decision N" versus "DN", and fix the wrong citations (Stage 7 M12, and "answer 7" and "answer 2").
  * Explain the few remaining terms inline.
  * In §10, list the report field names and the four category values (`offensive`, `wrong`, `unsafe`, `other`). In §11, list the event names on one line. Types and size limits stay in the schema file.

#### Z12. Stages 5 and 6 point in opposite directions on `expireAt` - Severity: Nit
* **Reviewer Lens:** `egm-zoom-out`
* **Affected Scope:** Q6 (line 1114); item 19 (lines 1306-1307); `report_policy.retentionDays` (line 643); Stage 5 N3; Stage 6 #8.
* **Problem:** Stage 6 #8 adds `expireAt` to the field list and the rules, while Stage 5 N3 offers to drop it. The client sets `expireAt`, so it can be left out or set far in the future (brief L2). On the Spark plan, only the manual script reads it.
* **Suggested fix:** Drop `expireAt` and `retentionDays`. The manual purge script deletes reports whose `createdAt` is older than a number of days set in the script. The same edit to §10 can fix the naming mismatch between `reason` and "the user's note".

#### Z13. Firestore persistence (Stage 7 M2): choose the option that removes a delivery state - Severity: Nit
* **Reviewer Lens:** `egm-zoom-out`
* **Affected Scope:** §10 (lines 694-695); CUJ 6 (lines 361-362); item 49 (line 1572); item 67 (line 1743); Stage 7 M2.
* **Problem:** Of Stage 7's three options, only turning persistence off also reduces work. Clearing the cache adds lifecycle code. Disclosing the cache contradicts "PhotoCritic keeps nothing" (line 352). The fix itself is small, so this is a Nit, although the privacy gap Stage 7 found is real.
* **Suggested fix:**
  * Turn Firestore persistence off.
  * Disable Send report while the device is offline, using the connectivity store (item 52).
  * Show "sent" when the server confirms. If it does not confirm within a fixed time, show "failed" with a copyable report ID.
  * Remove the "saved to send later" state and its Phase 0 check.

#### Z14. Keep the performance-threshold and minSdk fixes small - Severity: Nit
* **Reviewer Lens:** `egm-zoom-out`
* **Affected Scope:** Stage 7 M7 and M4; Stage 6 #11; NFR (lines 1032-1037); Q2 (lines 248-249).
* **Problem:** The demonstrator exists to measure, not to meet acceptance thresholds. The harness half of Stage 7 M7 duplicates Stage 5 M3.
* **Suggested fix:**
  * Retitle the NFR as "metrics" and drop the request for thresholds.
  * Merge the harness half of Stage 7 M7 and Stage 6 #11 into the Stage 5 M3 decision, including which phase builds the harness.
  * Resolve Q2 to minSdk 28. Reword G7 to say that AVIF works where the operating system can decode it, and that "Image could not be read" is the planned result elsewhere.

## Recommended Minimal Fix Set

**Apply as stated:**
* Stage 5: M3, M4, M5, M7, M9, M10 (after Z8), N1, N5, N6. Applying M5 also removes Stage 7 N4 and reduces Stage 7 M18 to "option C chosen".
* Stage 6: #11, merged into Stage 5 M3.
* Stage 7: M10 (narrow CUJ 8), M12, M14, M17, M20, N2, N3, N5, N6, N8, N11, N12, N19, N20.

**Apply in simpler form:**

| Findings | Simpler fix |
| --- | --- |
| Stage 5 N2, extended to URLs | Move values and URLs into code constants (Z2, Z3). This closes Stage 5 M2 and M8, Stage 6 #7, and Stage 7 M3, M16, N15 and N16. |
| Stage 5 M11, Stage 6 #5, Stage 7 M8 | One-step publish rule plus setup in edit mode (Z7). |
| Stage 5 M8, Stage 7 M6 (timeout half), Stage 7 M16 | Three timeout constants (Z4). |
| Stage 7 M6 (redirect half) | Phase 0 check only (Z5). |
| Stage 7 M1, Stage 6 #3 | A grouped list of L-item outcomes in Q11 (Z1). |
| Stage 7 M13, M19, M21 | One list of drafting decisions and one statement of the no-fallback rule (Z8). |
| Stage 6 #1, #2, #10; Stage 7 M11, N7, N18 | A notation key, with process references deleted and short lists in §10 and §11 (Z11). |
| Stage 7 M2 | Persistence off (Z13). |
| Stage 7 M4 | Resolve Q2 to minSdk 28 and narrow the AVIF wording (Z14). |
| Stage 7 M7 | Retitle the NFR only (Z14). |
| Stage 7 M9 | One sentence in §6 (Z10). |
| Stage 7 M15, Stage 6 #9, Stage 5 N4, Stage 7 N1 | One tint rule, with breakpoints pointing to item 27 (Z9). |
| Stage 6 #6, Stage 7 N17 | Record the served model only (Z9). |
| Stage 5 N3, Stage 6 #8 | Drop `expireAt` and purge by `createdAt` (Z12). |

**Drop:**
* Stage 7 N13 (new ID schemes)
* A glossary section, a Risks & Mitigations section and appendix sections
* Stage 5 M11's loop detection and chain-following
* Stage 5 M8's new Remote Config parameters
* Stage 7 M3's URL allowlist
* Stage 7 M6's response-URL fallback
* Stage 7 M7's acceptance thresholds
* Stage 7 M21's full Pros/Cons expansion
* Stage 6 #4 and Stage 5 M1(a) as separate edits, once Stage 5 M7 is applied
* The cross-check in Stage 6 #7 and Stage 5 M2, once `historyTurnsCap` is a code constant

**For the author to decide:**
* Keep live kill-switch updates (Q9), or apply the kill switch only at cold start (Z7).
* Add the open-write risk to Accepted Risks, or reword line 285 (Z1).
* Update AR2's "restart screen" wording if Stage 5 M10 is applied (Z8).
