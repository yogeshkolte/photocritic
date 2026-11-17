# Stage 6: Comprehension Test

* **Target:** docs/designs/photocritic.md, read in full (lines 1–1806)
* **FeatureRange:** 1–1072. **DetailedRange:** 1073–1806.
* **Reviewer:** egm-comprehension. No document standard applies. Accepted risks AR1–AR3 are not raised as findings.
* **Counts:** 0 Blocker, 6 Major, 5 Nit

## What this proposes

PhotoCritic is a free iOS and Android app built with Expo, React Native and TypeScript. The user takes or picks one photo and asks questions about it: Identify, Explain, Copy text, or a free question. The app streams the answer from a cloud vision model (OpenAI, Anthropic or Gemini) that the user picks, using the user's own API key. The call goes straight from the phone to the provider through one provider interface built on the Vercel AI SDK, with a host-allowlisted fetch wrapper in front. Text found in the photo appears as a separate copyable card.

Firebase is the only other service. Remote Config is fetched at every cold start and controls models, limits, error mapping, consent text and a kill switch, with no built-in fallback values. Firestore stores text-only answer reports. Analytics and Crashlytics run only after opt-in. The app is a technology demonstrator: it measures extraction accuracy, time to first answer and error handling (mainly through a separate benchmark harness) before a later on-device model phase. It ships only to TestFlight and Play internal testing.

## How the existing system works, per this document

There is no existing system. This is a new app with no PhotoCritic backend. The only prior artifacts are the source brief (concepts_brief.md, with decisions 1–37), a clickable mockup, a research folder, and an unrelated `TokenEconomizer/` folder that the app's tooling excludes. The document builds on the brief and often points to it for detail instead of restating it.

## Findings

### 1. 🟡 Major: two decision-numbering schemes clash

* **Scope:** the whole document. For example G10 line 124, Non-Goals line 218, Q6–Q10 lines 258–272.
* **Passage:** "Each key is checked when entered (decision 21)" and "Keeping keys … across reinstall (decisions 17, 21)", but also "Q6. Resolved: report retention (D21)". Likewise "decision 24" (Analytics opt-in for everyone) vs "Q9 … (D24)" (config failure), and "decision 22" (Firestore reports) vs "Q7 … (D22)" (report write protection).
* **Why it is unclear:** "decision 21" and "D21" look alike but are unrelated. D-numbers are sometimes sub-items of decision 36 ("decision 36, D34") and sometimes open brief items (D3, D9, D26). The reader cannot tell which list a number belongs to, so the citations cannot be trusted as cross-references.
* **Suggested fix:** add a short note near the top: "decision N" means a numbered decision in the brief, and "DN" means a row of the brief's gate table. Better, rename one scheme, or add a table mapping each cited number to a one-line summary.

### 2. 🟡 Major: privacy-relevant content lives only in the brief

* **Scope:** §8 line 630 ("shapes as in brief section I.3"), §8 line 650 ("brief K.4"), §10 line 686 ("the fields in brief K.6"), §11 line 701 and file 57 ("Events and parameters are those in brief K.5").
* **Why it is unclear:** a reader cannot tell which Analytics events and parameters are logged, although that is what G16 and the privacy policy promise to limit. The full Firestore report field list is also missing, although the security rules must "check every field". The privacy claims cannot be checked from this document.
* **Suggested fix:** list the K.5 events and parameters, and the K.6 report fields with types and size limits, in the document. An appendix table is enough.

### 3. 🟡 Major: the deferred findings L1–L30 have no content

* **Scope:** Open Question Q11 (line 273): "Round-4 findings L1–L30 (deferred). Listed in the brief, section L."
* **Why it is unclear:** there are 30 unresolved items, and the reader cannot judge whether any of them affects the design.
* **Suggested fix:** give one line per item, or group them by topic and say which sections each could affect.

### 4. 🟡 Major: `classify.ts` keeps a bundled fallback

* **Scope:** file 40, `errors/classify.ts` (line 1488): "applies the `error_map` rules from the snapshot in order (bundled rules if invalid)".
* **Why it is unclear:** this contradicts G17, Q9, §8 step 3 and file 16 ("Removed: no bundled config defaults"). An invalid value shows the config-unavailable screen, so classification can never run on invalid rules. The reader cannot tell whether bundled error rules exist.
* **Suggested fix:** delete "(bundled rules if invalid)". If a built-in rule set is intended for errors only, say so in G17 and Q9.

### 5. 🟡 Major: the kill-switch fallback path is undefined

* **Scope:** G18, CUJ 7 and the `models.fallbackId` parameter (lines 171–173, 364–371, 637): "the conversation ends with 'Start over with <fallback model>'".
* **Why it is unclear:** the document does not say what happens when:
  * the fallback model belongs to a different provider, for which the user has no key or no consent record;
  * a whole provider is disabled, since `fallbackId` is per model;
  * the fallback model is itself disabled.

  Validation only checks that `fallbackId` names a listed model, and the flow after the user taps "Start over" is not described.
* **Suggested fix:** say whether the fallback must be from the same provider (and make validation enforce it), or describe the path through key setup and consent. Also define the behavior when a whole provider is disabled.

### 6. 🟡 Major: no behavior when the served model differs from the requested one

* **Scope:** §3 step 4 ("Compare the served model with the requested one"), Alternative B5 ("Models are also re-routed silently"), and the `status` and `replacementId` model fields.
* **Why it is unclear:** the document says the app detects when the provider served a different model, but not what it does about it (show a notice, log an event, or raise an error). It never defines the possible `status` values, or how `replacementId` differs from `fallbackId`. Only the canary says a mismatch fails.
* **Suggested fix:** state what the user sees, or what is recorded, on a mismatch. Define the `status` values, and explain how `replacementId` differs from `fallbackId`.

### 7. 🟢 Nit: the rules' list limit cannot read Remote Config

* **Scope:** file 9, `firestore.rules` (line 1228): "`conversation` is a list whose length is at most `historyTurnsCap × 2 + 2`".
* **Why it is unclear:** Firestore rules cannot read Remote Config, so the limit must be a hard-coded number, and the document does not give it. If the author later raises the cap in Remote Config, the rules would silently reject reports.
* **Suggested fix:** give the literal value used in the rules, and add a `config:validate` cross-check that `historyTurnsCap` matches it.

### 8. 🟢 Nit: report field names are inconsistent

* **Scope:** §10 and file 63, vs Q6 and file 19.
* **Passage:**
  * The rules test rejects "oversize `reason`", but §10 calls the field "the user's note"; there is no `reason`.
  * `expireAt`, used by the purge script (Q6), and `retentionDays` are not in the §10 field list.
  * "`category` is one of four values" never names the four values.
* **Suggested fix:** use one field name throughout. Add `expireAt` to §10 with how it is computed (presumably `createdAt` + `retentionDays`), and list the four categories.

### 9. 🟢 Nit: tint values disagree

* **Scope:** G19 (line 180) vs §12 (lines 714–716) and file 26.
* **Passage:** G19 says "Any text over the photo sits on at least a 65% dark tint". §12 says 72% collapsed and 94% expanded under text, and 65% for controls on the photo.
* **Suggested fix:** state the three values in G19, or say that 65% applies to controls only.

### 10. 🟢 Nit: undefined project-specific terms

* **Terms:**
  * "Gemini key type" (error table)
  * "old unrestricted Gemini key" (Verification Plan)
  * "Callstack's on-device providers" and "React Native AI" (Alternative C1, file 66)
  * "the design board" (file 30)
  * "contradiction 6" (AR3; noted only as a term, not as a risk)
  * "finding O7", "round-2 findings", "round 3" (process history)
  * `TokenEconomizer/` (what it is, and why it sits in this folder)
* **Suggested fix:** add a short inline explanation for each, or a small glossary. Examples: "Gemini key type: a key without Generative Language API access"; "React Native AI: Callstack's library of AI SDK providers for on-device models".

### 11. 🟢 Nit: the phase that builds the harness is unclear

* **Scope:** Implementation order Phase 0 (line 1099) vs file 64 and the Verification Plan (line 1800).
* **Passage:** Phase 0 includes "the image-size accuracy test (Q5)", but the harness that measures accuracy per image size is not placed in any phase, and its first run is described as "before Phase 2 ends (baseline)".
* **Why it is unclear:** the reader cannot tell whether Phase 0 builds a throwaway accuracy test or an early harness. It is also unclear how the 2048 px default is set "after measuring accuracy in the harness" (Alternative D1) when the harness comes later.
* **Suggested fix:** name the phase in which the harness is built, and say whether the Phase 0 accuracy test uses it.

## Overall

The reviewer could paraphrase every section. The feature spec and the implementation plan connect well, and the architecture, state machine, image pipeline and config load order are clear on their own.

The main self-containment gaps are findings 1–3:
* the two clashing decision-numbering schemes;
* the privacy-relevant content (telemetry events, report fields) that lives only in the brief;
* the deferred findings L1–L30, which have no content in this document.

Findings 4–6 are internal contradictions or unexplained behavior that a new implementer would trip over.
