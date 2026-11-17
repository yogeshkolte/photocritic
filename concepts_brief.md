# PhotoCritic — Image Q&A Mobile App

* **Author:** yogeshkolte (yogeshkolte@gmail.com)
* **Date:** 2026-11-14
* **Mode:** BRAINSTORM (multi-lens concept review), round 4 design review (decisions 17–26: local-only user data, configuration fetched at every start, opt-in Firebase Analytics and Crashlytics, in-app text reports in Cloud Firestore)

## Reviewer Status

| Lens | Role | Round 1 | Round 2 (evidence) | Round 3 (design, I.1–I.8) | Round 4 (K.1–K.7, decisions 17–26) |
| --- | --- | --- | --- | --- | --- |
| `egm-design` | Skeptical Architect Brainstormer | completed | completed | completed | completed |
| `egm-codebase-details` | Technical Accuracy Brainstormer (library/API/platform claims; no code exists) | completed | completed | completed | completed |
| `egm-zoom-out` | Simplicity & Scope Brainstormer | completed | completed | completed | completed |
| `egm-comprehension` | Naive Reader & UX Ergonomics Brainstormer | completed | completed | completed | completed |
| `egm-implementation-readiness` | Implementation Readiness Brainstormer | completed | completed | completed | completed |
| `egm-structural` | Structural Brainstormer | completed | completed | completed | completed |
| `egm-inventive-catalyst` | Inventive Catalyst Brainstormer | completed | completed | completed | completed |

Notes on how to read this brief:

* Severity labels are Blocker, Major and Nit. "(resolved)" marks a finding the author's decisions have closed.
* Round 1 had no web access. Round 2 checked claims against public documentation fetched on 2026-11-14 into `research/` (index: `research/README.md`). Citations like "(research/anthropic-vision.txt, Request limits)" name the file and section. The OpenAI Responses API reference could not be fetched (HTTP 403), so OpenAI request shapes and refusal fields remain unverified.
* Round 3 reviewed sections I.1–I.8 and decisions 10–16 against Firebase, React Native Firebase (RNFB), expo-mail-composer and Apple docs added to `research/`. No Play testing-track document and no TestFlight help page was fetched, so the Play internal-track and TestFlight limit claims in I.8 remain "verify". Round 3 findings are in section J.
* Round 4 reviewed section K and decisions 17–26 against Firestore, App Check and pricing docs added to `research/` (firestore-quotas, firestore-ttl, firestore-rules-conditions, app-check-firestore, rnfb-firestore, storage-faq-blaze, firebase-pricing-plans). No Google Analytics data-collection page, no Apple ATT page, no RNFB Analytics or Crashlytics API page, no RNFB JSON-config page and no expo-file-system page was fetched, so claims about those stay "verify". Round 4 findings are in section L. Section K opens with a map of which parts of I and J it supersedes.
* "verify" marks only claims the fetched evidence does not cover. Section H summarizes every claim checked.
* Model IDs, prices and dates below are as of the fetch date. Several change within weeks (see items 12 and 30).
* The project directory also contains an unrelated folder, `TokenEconomizer/` (Python benchmark tooling), which has no bearing on this concept.
* Round 5 (2026-11-15) reviewed sections M and N and decisions 28–35 against `research/callstack/` and the clickable mockup (Main, Tablet and System `.dc.html`). All seven lenses completed. Findings are in section O; L1–L30 and D21–D25 were not re-raised (decision 27).

## Overview

PhotoCritic is a free iOS/Android app built with Expo (development build), React Native and TypeScript. The user takes or picks one photo and asks questions about it: identify objects, explain the scene, extract and copy text. The app connects to the user's own cloud AI key (bring-your-own-key, BYOK) for OpenAI, Anthropic or Google Gemini. There is no free-tier pointer (decision 10); onboarding links to each provider's key page. Each turn re-sends a downscaled, metadata-stripped JPEG of the photo (decision 11).

API keys stay in the device's secure store and are wiped on the first launch after a reinstall, detected by a plain-file install marker (decision 21). Settings and versioned consent records are plain app files that may be lost on reinstall (decision 17). Remote behavior (kill switch, model allowlist, limits, error map, consent texts) comes from Firebase Remote Config, fetched at every cold start (decisions 13, 19); provider hosts are allowlisted in code and the key header style is set in code (decision 23). Users report an AI answer from inside the app; the report is text only (the user's comment plus the reported question and answer, no photo) and is written to Cloud Firestore with create-only rules and App Check (decisions 22, 25). Firebase Analytics and Crashlytics run only after the user turns on "Share usage and crash data", which is off by default for everyone (decisions 20, 24); the Analytics app instance ID is then used as an install-level ID (decision 26). The first release goes only to TestFlight (iOS) and the Google Play internal testing track (Android) (decisions 15–16). An on-device vision model is a later evaluation phase.

The author has set the product rationale as a **technology demonstrator** (decision 7). Success is measured technically: OCR accuracy at the chosen resolution, time to first answer, error-mapping coverage, and later a local-versus-cloud comparison. The app does not try to compete with the ChatGPT, Gemini or Claude apps, Lens or Visual Intelligence. Apple 2.2 says TestFlight apps "should be intended for public distribution and should comply with the App Review Guidelines" (research/apple-review-guidelines.txt, 2.2), so the tester build must still work as a complete app, and a later public release should be recorded as planned, not abandoned.

The reviewers agree the concept can be built on this stack. Round 4 resolves the round-3 Blockers J1–J5, but introduces new ones (section L). The main risks after round 4:

* **The 90-day report retention does not work on the free Spark plan.** Firestore TTL deletes "don't include free usage. You must enable billing" (research/firestore-quotas.txt, Free quota). Decision 25 keeps the project on Spark and the privacy policy promises 90-day deletion (L1, objection).
* **The report write path is open to anyone with the app.** There is no sign-in, rules cannot loop over the `conversation` array or bind `expireAt` to the server clock, and the daily limit is client-side only. On Spark, exhausting a free quota can switch Firestore off for the project (research/firebase-pricing-plans.txt; the reset period conflicts with research/firestore-quotas.txt, verify), which would remove the reporting Play requires (L2, L6).
* **The reinstall key wipe can delete keys on a normal upgrade and is defeated by backups.** The marker must ship in the first build any tester installs, a read error must not count as "missing", and Android Auto Backup restores the plain files (L3).
* **The K.4 "3-second timeout" and late activation can miss a kill switch for a whole session** (L4).
* **Opt-in analytics gives a small, self-selected sample.** The K.5 events cannot be the demonstrator's main measure; the offline benchmark harness stays primary (L7).
* **Firebase data collection now spans Remote Config, Installations, Analytics, Crashlytics, the Firebase Sessions SDK, App Check and Firestore.** All must appear in the privacy policy, the App Privacy label and the Play Data safety form, and the first Remote Config fetch currently runs before the onboarding notice (L9).
* **Image cost and provider churn (from round 2) remain.** Re-sending the image each turn makes the explicit per-model image setting, the fixed send target and the history cap required. Gemini model IDs are re-routed on 2026-12-01 and Gemini prices change on 2026-12-01 and 2027-01-01.
* **Gemini's terms still apply to paid keys.** The 18+ rule and "not for consumer use" are not removed by dropping the free-tier pointer (research/gemini-terms.txt).

## Analysis

### A. Product rationale and scope

1. **Blocker (resolved, D1): no stated reason to choose this over existing free tools.** (zoom-out; structural)
   * Round 1: the first-party apps and OS features already do single-image Q&A, BYOK narrows the audience, and the one strong differentiator (on-device analysis) is deferred.
   * **Resolved by author (round 2):** the app is a technology demonstrator. Follow-on consequences:
     * Any release, including TestFlight external testing and the Play internal track (decisions 15–16), still carries most of the compliance load (sections B, E, G, I.8).
     * Apple 2.2: "Demos, betas, and trial versions of your app don't belong on the App Store – use TestFlight instead" (research/apple-review-guidelines.txt, 2.2). Keep "demo" out of all store copy.
     * Apple 4.2: "If your app is not particularly useful, unique, or 'app-like,' it doesn't belong on the App Store" (research/apple-review-guidelines.txt, 4.2). A BYOK client with the distinctive local phase deferred is weakest on exactly this test. 2.2 also says TestFlight apps "should comply with the App Review Guidelines", so the test-track decision reduces this risk but does not remove it. See item 26.
     * The thing most worth demonstrating, local versus cloud, is Phase 5. Give Phase 5 an entry criterion or date, or the demonstrator shows only a BYOK client.

2. **Major (resolved, D2): v1 scope is wide for a free app.** (zoom-out, design, implementation-readiness)
   * Each provider has its own auth, request format, error format, caching rules and model churn.
   * **Resolved by author (round 2):** three providers in v1. Zoom-out's "Gemini only" recommendation is obsolete.
   * Still recommended: build Gemini end to end first, then add OpenAI and Anthropic against the same contract tests. Gemini goes first because it has the most churn. Anthropic is the most stable adapter for checking the contract: current models are not retired before September 2027 (research/anthropic-models.txt).

3. **Major: the user's cost is invisible and grows faster than turn count.** (design, comprehension, structural, inventive-catalyst, zoom-out)
   * Turn N re-sends the image plus all earlier turns, so total session cost grows roughly quadratically.
   * Per-image cost per turn, corrected from the evidence:
     * **Anthropic:** `ceil(w/28) × ceil(h/28)` visual tokens. Claude 4.7 and later models, including all current 5.x models, are on the high-resolution tier: 2,576 px long edge and up to 4,784 tokens per image. The 1,568 px / about 1.6k-token figure applies only to older Standard-tier models (research/anthropic-vision.txt, Resolution and token cost). A 2048×1536 send costs about 4.1k tokens.
     * **OpenAI:** `ceil(w/32) × ceil(h/32)` patches, capped by the `detail` budget, times a per-model multiplier (1.2 on gpt-6-astra, gpt-5.6-*, gpt-5.5, gpt-5.4-*, gpt-5.2). `detail` defaults to `auto`, which on gpt-6-astra, gpt-5.6-* and gpt-5.5 behaves like `original`. A 12 MP photo is then about 12k patches, roughly 14k tokens per turn. `high` caps at 2,500 patches (about 3k tokens) (research/openai-vision.txt, Choose an image detail level; Patch-based image tokenization).
     * **Gemini:** on Gemini 3 and 4 models the cost is a fixed per-image amount set by media resolution: 1,120 tokens by default and at `high`, 280 at `low`, 560 at `medium`, 2,240 at `ultra_high`. The 258-tokens-per-768-px-tile rule is the legacy calculation (research/gemini-media-resolution.txt, Token counts; research/gemini-image-understanding.txt, Token calculation).
   * Zoom-out's round-1 claim that a session costs "fractions of a cent" holds for Gemini Flash models (about 0.08 cents per image per turn at $0.75/MTok) and Claude Haiku 5.5. It does not hold for claude-opus-5-5 ($4/MTok input), at about 1.9 cents per image per turn (research/anthropic-models.txt; research/gemini-pricing.txt).
   * Recommendation:
     * Set the image budget explicitly per model (item 11).
     * Cap or truncate history after a stable prefix (item 29).
     * Tell the user that each follow-up re-sends the photo.
     * Zoom-out now recommends dropping any per-session cost display, because prices change on 2026-12-01 and 2027-01-01 and the estimate would go stale.

### B. Compliance and privacy (items the concept lists as open but which are required)

4. **Blocker: Apple guideline 5.1.2(i) third-party AI consent.** (all lenses)
   * Confirmed wording: "You must clearly disclose where personal data will be shared with third parties, including with third-party AI, and obtain explicit permission before doing so" (research/apple-review-guidelines.txt, 5.1.2 Data Use and Sharing). The file is dated "Last Updated: October 16, 2026". The evidence does not show the round-1 "November 2025 revision" date (verify, or drop the date).
   * Related requirements the brief did not list (research/apple-review-guidelines.txt):
     * 5.1.1(i): a privacy policy link in App Store Connect metadata and inside the app. The policy covers retention and deletion, how to revoke consent and request deletion, and confirms that third parties give "the same or equal protection of user data".
     * 5.1.1(ii): an "easily accessible and understandable way to withdraw consent".
     * 5.2.2: the app must be "specifically permitted" to use a third-party service under that service's terms. This brings Gemini's terms into App Review (item 25).
     * 1.5: a support URL with contact information.
   * Recommendation:
     * Show a per-provider consent screen before the first send. It names the provider and what is sent: the photo with location and camera details removed, your questions, and earlier turns, which are re-sent with every follow-up.
     * Ask again when the provider changes or the consent text version changes (item 32, section I.6).
     * Offer revocation in Settings, and publish the privacy policy.
     * Make the App Privacy label and the Play Data safety form match.
     * The "same or equal protection" clause rules out one uniform privacy claim. State the protection level per provider and tier.

5. **Blocker (resolved, D10): the Gemini free-tier pointer.** (all lenses)
   * Round 1 framed this as a conflict with the privacy message. The evidence makes it a conflict with Google's terms. See item 25 for the full finding.
   * Confirmed: on unpaid use, Google uses content "to provide, improve, and develop Google products", and "human reviewers may read, annotate, and process your API input and output". The terms also say, in bold: "Do not submit sensitive, confidential, or personal information to the Unpaid Services" (research/gemini-terms.txt, Unpaid Services > How Google Uses Your Data).
   * Corrected, EEA/UK/CH: Gemini is available there (research/gemini-available-regions.txt). The terms contain two separate rules:
     * "You may use only Paid Services when making API Clients available to users in the European Economic Area, Switzerland, or the United Kingdom" (Use Restrictions).
     * For users in those regions, paid-service data terms apply even to unpaid quota (Unpaid Services).
   * Usage counts as paid "only when accessing the API through a Cloud Project associated with an active billing account" (research/gemini-terms.txt, Paid Services). The app cannot tell which applies to a pasted key. Confirmed.
   * Which models are free: gemini-4-hydrogen, gemini-3.8-flash, gemini-3.6-flash, gemini-3.5-flash-lite, gemini-3.1-flash-lite and gemini-3-flash-preview. gemini-4-carbon, gemini-4-argon and gemini-3.1-pro-preview are not on the free tier (research/gemini-pricing.txt). "Do not hardcode which models are free" is confirmed as necessary.
   * Free-tier per-minute and per-day numbers are no longer published. The doc says to view them in AI Studio. Limits apply per project, not per key, and the daily quota resets at midnight Pacific (research/gemini-rate-limits.txt). The round-1 claim that Google cut free-tier quotas in late 2025 is not covered (verify).
   * Free-tier eligibility is decided per project. A project can return `403 PERMISSION_DENIED` with `FREE_TIER_VERIFICATION_REQUIRED`, or be in a "Free tier unavailable" state that a new key in the same project does not fix (research/gemini-troubleshooting.txt, Project restricted or unavailable).
   * **Author decision 9 (round 2), recorded:** keep the pointer; cover retention, training, human review and region limits in an app privacy policy and in the in-app consent screen.
   * **Reviewer position after round 2:** decision 9 is necessary but not sufficient. A disclosure cannot cure a use restriction. See item 25 and gate item D10.
   * **Round 3:** decision 10 removes the pointer. A user can still paste an unbilled Gemini key, so the Gemini consent text keeps the unpaid-tier facts (item 28).

6. **Major: providers keep data even though the app does not.** (codebase-details, comprehension, design)
   * OpenAI, confirmed: "the Responses API has a 30 day Application State retention period by default, or when the store parameter is set to true … stored for at least 30 days." Abuse-monitoring logs are kept "up to 30 days" whatever `store` is set to. Image inputs are scanned for CSAM, and flagged images are kept for manual review even under Zero Data Retention. Prompt-cache key/value state can persist up to 24 hours. API data is not used for training "unless you explicitly opt in" (research/openai-your-data.txt, Storage requirements and retention controls per endpoint; Image and file inputs).
   * Anthropic, confirmed: API inputs and outputs are deleted "within 30 days". Exceptions are the Files API, ZDR agreements, Usage Policy enforcement and legal holds. Flagged content is kept up to 2 years and classifier scores up to 7 years. "Covered Models" have additional retention and review, and the evidence does not say which models those are (verify). "Anthropic does not use uploaded images to train models" (research/anthropic-data-retention.txt; research/anthropic-vision.txt, FAQ).
   * Gemini: paid use logs prompts and responses "for a limited period of time", with no day count, so the policy must not state one. Unpaid use: training and human review (research/gemini-terms.txt). Gemini Files API retention is not covered (verify).
   * New, on-device: `expo-image` defaults to `cachePolicy: 'disk'`. On iOS the disk cache keeps images up to a week and "has no size limit by default" (research/expo-image.txt). This contradicts "nothing is stored". Set `cachePolicy` to `'memory'` or `'none'` for user photos, or clear the disk cache when the session ends.
   * Round 4: the Firestore SDK keeps written report documents in its on-device cache, because persistence is "enabled by default on native platforms" (research/rnfb-firestore.txt, Offline Capabilities). See L5.
   * Recommendation: send `store: false` on the Responses API, or use Chat Completions, which keeps no application state (research/openai-your-data.txt). Send images inline. Generate the privacy text from the per-provider facts above.

7. **Major: the API key can leak through "show details", logs and crash reports.** (design, comprehension, structural)
   * The `x-goog-api-key` header is confirmed as the documented way to send Gemini keys (research/gemini-api-key.txt). The round-1 claim that Gemini also accepts `?key=` is not covered (verify). The recommendation does not depend on it.
   * New: Gemini blocks keys reported as leaked (research/gemini-troubleshooting.txt). Never put the reviewer key anywhere public.
   * Recommendation: send keys only as headers. Redact auth headers and query strings everywhere. Never log request bodies. The round-3 remote-config path for key leakage (J3) is closed by decision 23; round 4 adds Crashlytics as a leak path (L10).

8. **Major: Google Play photo permissions and AI-content policy.** (codebase-details, zoom-out, implementation-readiness)
   * Confirmed: apps targeting API 33+ "may only request READ_MEDIA_IMAGES … if system pickers … are not sufficient". The Android photo picker is backported to older versions (research/play-photo-permissions.txt).
   * Corrected: `expo-image-picker` adds CAMERA, READ_EXTERNAL_STORAGE and WRITE_EXTERNAL_STORAGE to the manifest, and adds RECORD_AUDIO by default (set `microphonePermission: false`). On iOS it also sets NSMicrophoneUsageDescription by default (research/expo-imagepicker.txt, Permissions). Block the storage and audio permissions with a config plugin, use `mediaTypes: 'images'`, and add a CI assertion on the merged manifest.
   * The library says "No permissions request is necessary for launching the image library". Do not copy its sample code that requests media-library permission first.
   * Confirmed: "Apps that generate content using AI must contain in-app user reporting or flagging features that allow users to report or flag offensive content to developers without needing to exit the app" (research/play-ai-content.txt). Resolved by decision 22 (in-app report written to Firestore, K.6–K.7).

### C. Technical accuracy corrections

9. **Blocker: treating HEIC as an unsupported format breaks the main use case.** (design, codebase-details, zoom-out, implementation-readiness)
   * Confirmed: with SDK 54+ defaults, `expo-image-picker` returns the original asset, "including HEIC and AVIF files" (research/expo-imagepicker.txt).
   * Gemini accepts HEIC/HEIF natively (research/gemini-image-understanding.txt, Supported image formats). Anthropic accepts only JPEG, PNG, GIF and WebP (research/anthropic-vision.txt, Supported formats). OpenAI accepts only PNG, JPEG, WEBP and non-animated GIF (research/openai-vision.txt, Image input requirements). Converting to JPEG is still required, and AVIF must be in the conversion path too.
   * Corrected, animated GIFs: Anthropic uses the first frame. Android's picker returns the animated GIF itself when quality is 1.0 and editing is off, and 1.0 is the default (research/expo-imagepicker.txt). OpenAI rejects animated GIFs. Always flatten to JPEG.
   * Not covered: native HEIC decode on Android since API 28, the ProRAW/DNG behavior, and whether `expo-image-manipulator` applies EXIF orientation or decodes HEIC (verify all three with fixtures).
   * Recommendation: decode every image, apply orientation to the pixels, and re-encode to JPEG. "Unsupported" then means only "failed to decode".

10. **Major: EXIF/GPS stripping must be tested, not assumed.** (codebase-details, implementation-readiness)
    * Confirmed: `exif` only controls whether EXIF is included in the returned object. On iOS camera captures, the returned EXIF has no GPS tags (research/expo-imagepicker.txt). Whether the file itself changes is not covered. Anthropic and OpenAI say the model does not read metadata, but the bytes are still sent and kept by the provider.
    * Confirmed: `expo-image-manipulator` can downsample while decoding (`maxWidth` / `maxHeight` in ImageLoadOptions, "the full-sized image is never loaded into memory"). This addresses the 48–200 MP out-of-memory risk. `manipulateAsync` is deprecated; use `ImageManipulator.manipulate` or `useImageManipulator`. Output goes to the cache directory (research/expo-imagemanipulator.txt).
    * Metadata stripping on re-encode is not covered (verify). The CI test is mandatory: convert a GPS-tagged HEIC fixture and assert the output has no GPS data and correct orientation. Reports carry no image (decision 25), so this test protects only what is sent to the provider.

11. **Major: per-model image limits and an explicit image budget.** (all lenses)
    * Anthropic, corrected: 10 MB per image (base64) on the Claude API; 5 MB applies only to Bedrock and Google-managed hosting. 8000×8000 px max (confirmed). 128 MB per request. Above 20 images in one request, every image must be 3,000 px or less, and re-sent history counts toward the 20 (research/anthropic-vision.txt, Request limits).
    * OpenAI, corrected: 512 MB payload, up to 1,500 images. "Images that exceed the 30,000-patch limit after processing are rejected, not automatically resized." With the default `auto` (behaving like `original`), a 48 MP photo of about 47k patches fails. On gpt-5.4-*, `low` uses a 6,144-patch budget "so it can use more tokens than high". The doc recommends `original` for OCR (research/openai-vision.txt).
    * Gemini, confirmed with scope: the 20 MB inline limit covers the "total request size (text prompts, system instructions, and inline bytes)", so it applies to the whole re-sent history. Per-item `resolution` exists; `high` is the recommended setting (research/gemini-image-understanding.txt; research/gemini-media-resolution.txt).
    * Base64 adds about 33% (arithmetic, not from the evidence).
    * Evidence-backed default (zoom-out, implementation-readiness, design): one on-device send target of about a 2048 px long edge at JPEG quality about 0.8. Then send an explicit setting per model:
      * OpenAI: `detail: "high"` (about 3k tokens), or `original` with a client-side patch cap for an OCR boost.
      * Gemini: `resolution: "high"` (1,120 tokens), with `ultra_high` as an OCR-floor experiment.
      * Anthropic: about 4.1k tokens at 2048×1536 on the high-resolution tier.
    * On-device downscaling is now required, not a tuning choice. Never rely on `detail: auto`. Add an internal error class "image rejected (size/patch limit)".
    * Low-data mode: zoom-out recommends cutting it. If it stays, it needs a measured OCR floor (D7).
    * Provider prompt order conflicts. Anthropic advises the image before the text; Gemini advises the text before the image (research/anthropic-vision.txt; research/gemini-image-understanding.txt, Tips). Each adapter owns message assembly.

12. **Major: model-list endpoints do not reliably say which models fit this app.** (codebase-details, design, zoom-out, inventive-catalyst)
    * Anthropic, corrected: "All current models support text and image input" (research/anthropic-models.txt). The "no vision" error class is obsolete for current Anthropic models.
    * Gemini: "All Gemini model versions are multimodal", but the list includes TTS, Live, Transcribe, image-generation, embedding and Decision models. An allowlist is still needed (research/gemini-models.txt; research/gemini-image-understanding.txt).
    * OpenAI: the models page was not captured; vision support is inferred from the openai-vision.txt sizing table.
    * Corrected, "404 = model retired": from 2026-12-01, `gemini-2.5-flash` is automatically routed to `gemini-3.5-flash-lite` and `gemini-2.5-pro` to `gemini-3.8-flash`. `-latest` aliases are hot-swapped with 2 weeks' notice (research/gemini-models.txt). Anthropic can also fall back to a different serving model mid-stream, and the final `message_delta` reports which model served (research/anthropic-streaming.txt, Event types).
    * Retirement can therefore arrive as a silent switch with no error. Pin stable IDs, never use `-latest`, and compare the model the response reports with the one requested.
    * Curated allowlist candidates: item 34.

13. **Major: each error class needs provider-specific parsing.** (all technical lenses)
    * **OpenAI**, corrected (research/openai-errors.txt, API errors):
      * Billing 429s are identified by `error.code`: `credit_balance_exhausted`, `organization_spend_limit_exceeded`, `project_spend_limit_exceeded`, `organization_usage_limit_exceeded`. `error.type` can still be `insufficient_quota`, so branch on code first. "Retrying billing, spend, or quota errors won't restore API access."
      * `429 slow_down` is a ramp-rate limit.
      * Overload is now `503 server_is_overloaded`, not 429.
      * New classes: `403` "Country, region, or territory not supported" and `401` "IP not authorized".
      * Refusal and `content_filter` fields are not covered (verify; Responses reference not fetched).
    * **Anthropic**, corrected (research/anthropic-errors.txt, HTTP errors):
      * `400 invalid_request_error` when an org or workspace spend limit you set is reached. Its type is the same as a malformed request, so it can only be told apart by message text. The round-3 `error_map` rule schema cannot express this yet (J11).
      * `402 billing_error` for billing or payment problems.
      * `429 rate_limit_error` for rate limits and for the usage tier's monthly spend cap. A spend-cap 429 "has no retry-after header and keeps failing".
      * `413 request_too_large`. Cloudflare may return it before the API, so its body may not be the documented JSON.
      * `529 overloaded_error`. `504` / `500 timeout_error`.
      * A `401` beginning "CORS requests are not allowed" means an `Origin` header was sent; it is not a bad key.
      * Error shape `{type:"error", error:{type,message}, request_id}`, plus a `request-id` header. "New type values can appear", so the parser needs a default branch.
      * Per-model 400s: assistant prefill is rejected on 4.6+. `thinking: {type: "disabled"}` is rejected on Opus 5.5, Fable 5.2 and Sonnet 5.5, and accepted on Haiku 5.5 (Common validation errors).
      * `stop_reason: "refusal"` is not covered (verify).
    * **Gemini**, corrected:
      * `429 RESOURCE_EXHAUSTED` for rate limits and spend-based limits (confirmed).
      * `402` for depleted Prepay credits.
      * `403 PERMISSION_DENIED` variants: free-tier verification required, free tier unavailable, payment needed, leaked key (research/gemini-troubleshooting.txt).
      * Unrestricted standard keys are rejected. Dormant unrestricted keys have been blocked since 2026-05-07, and new AI Studio keys have been "auth keys" since 2026-05-28 (research/gemini-api-key.txt, API key types).
      * Safety, on the Interactions API: a blocked request "fails with an HTTP 400 error". `error.code` is `safety`, `jailbreak`, `prohibited_content` or `blocklist`, and when streaming it arrives as an `error` event (research/gemini-safety-settings.txt, When content is blocked). A generic "400 = bad request" mapping would mislabel refusals.
      * The round-1 `finishReason: SAFETY` / `promptFeedback.blockReason` on a 200 belongs to `generateContent`. The evidence does not show it (verify if that surface is chosen). RECITATION stops are confirmed.
      * Region errors (`FAILED_PRECONDITION`) are not covered (verify).
      * New: Gemini 3.6+ and 4 ignore `temperature`, `top_p` and `top_k`, and "will return 400 errors in a future release". Adapters must not send sampling parameters (research/gemini-troubleshooting.txt).
    * Truncation is reported as a stop reason, not an HTTP status (Anthropic `max_tokens`, confirmed).
    * Missing classes, still not covered: app backgrounded mid-request (verify iOS behavior). iCloud-only photos: the picker downloads them from iCloud automatically (research/expo-imagepicker.txt, shouldDownloadFromNetwork), so the failure case is the network.
    * New class: Android can destroy MainActivity during camera capture. Recover with `ImagePicker.getPendingResultAsync` (research/expo-imagepicker.txt, launchCameraAsync).

14. **Major (de-risked): streaming.** (codebase-details, design, zoom-out, implementation-readiness)
    * Corrected: on Android and iOS, `expo/fetch` "is also installed as the global fetch" (opt out with `EXPO_PUBLIC_USE_RN_FETCH=1`). Streaming works through `resp.body.getReader()`. ReadableStream, TextDecoderStream and TextEncoderStream are built in (research/expo-fetch.txt). "RN's built-in fetch cannot stream" no longer describes the default runtime.
    * AbortController and `EventSource` are not covered (verify that abort cancels the native request). An SSE parser is still needed, unless the AI SDK provides one.
    * Confirmed: errors after a 200 OK. Anthropic sends `event: error` with `overloaded_error` and also sends `ping` events (research/anthropic-streaming.txt). Gemini sends safety blocks as `error` events. The AI SDK exposes `StreamProviderError` (research/aisdk-errors.txt). OpenAI is not covered.
    * Classification at three points stands: before the response, in-stream events, final stop reason.
    * Implementation-readiness now rates streaming low-risk for v1.

15. **Nit: secure storage details.** (codebase-details, comprehension, implementation-readiness)
    * Confirmed: iOS Keychain items survive uninstall; Android data does not (research/expo-securestore.txt, Data persistence). The same section says survival "is not guaranteed and you should never rely on this".
    * Corrected in round 3: the round-2 claim that `AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY` is deprecated is doubtful. In the extracted text the deprecation notes appear shifted by one entry (the note under `ALWAYS` recommends `AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY`), so the deprecated constants are most likely `ALWAYS` and `ALWAYS_THIS_DEVICE_ONLY` (verify). The choice of `WHEN_UNLOCKED_THIS_DEVICE_ONLY` (the default is `WHEN_UNLOCKED`) is unaffected. `keychainAccessible` is iOS-only.
    * `THIS_DEVICE_ONLY` blocks migration through backup to a new device. It does not stop items surviving uninstall and reinstall, so a first-launch rule is needed. Round 4: wipe chosen (decision 21, K.2); see L3 for the sequence.
    * Overwriting an item without the option keeps its old accessibility, so pass it on every write. If `keychainService` is set, every later read needs it.
    * Corrected, Android backup: `configureAndroidBackup` defaults to true and excludes SecureStore automatically if the app has no custom backup configuration. A custom configuration needs `exclude domain="sharedpref" path="SecureStore"`. Round 4: the plain files in K.3 are not excluded by this (L3).
    * `requireAuthentication` exists but is not needed for v1.
    * Settings must still define masked display, replace, delete, and one key per provider.

16. **Nit: the DeepSeek claim is undated and still unverified.** (codebase-details, design, structural, zoom-out)
    * No DeepSeek evidence was fetched. Mid-2026 knowledge: the hosted `deepseek-chat` / `deepseek-reasoner` models are text-only, and DeepSeek's vision models are open weights only (verify).
    * Restate the rejection as a scope decision, or record the date it was checked.

### D. Architecture and maintenance

17. **Major (resolved, D13): with no backend, there was no way to change behavior remotely.** (design, inventive-catalyst, structural, implementation-readiness)
    * The evidence shows scheduled churn: Gemini re-routing and price changes on 2026-12-01, a Gemini Flash price rise on 2027-01-01, future 400s for sampling parameters, and an `Api-Revision` header on the Gemini Interactions API.
    * Round-2 recommendation: deliver the model allowlist, the capability table and the error mapping as a versioned remote JSON manifest. It should be independent of `runtimeVersion` so it reaches every installed binary, and fall back to a copy bundled at build time. Keep EAS Update for code. Compatibility rules: item 33 and I.3–I.4.
    * Resolved: Firebase Remote Config (decision 13, sections I.2–I.5), fetched at every start (decision 19, K.4). Signing was dropped in round 3 (Alternatives); J3 is closed by decision 23.
    * Confirmed: the `fingerprint` runtime-version policy "will increment the runtime version whenever anything that may impact the native runtime changes". Corrected: for an incompatible update "expo-updates may detect an error and attempt to roll back". That is unsafe, but not a guaranteed crash. A channel per build profile is suggested, not required (research/expo-updates-runtime.txt).
    * Adding llama.rn or ExecuTorch changes the fingerprint, so Phase 5 needs a new binary.

18. **Major: the provider abstraction risks both over-engineering and a dead end.** (design, structural, zoom-out, inventive-catalyst, codebase-details)
    * Keep a small interface built around capabilities, as agreed in round 1: `send(history, image, signal) -> AsyncIterable<delta>`, a capabilities object, and one normalized error type.
    * New (design): key the capability table by **model**, not provider. It must carry request-shaping fields:
      * allowed thinking/effort values;
      * sampling-parameter policy;
      * image budget parameter (`detail` / `resolution`) and send target;
      * API version header (`anthropic-version`, Gemini `Api-Revision`);
      * prompt order;
      * re-route dates.
    * Round 3: the I.3 `models` parameter carries only some of these fields. Either add them or state that adapter code owns them (J12).
    * Each provider owns its preprocessing and message assembly.
    * Local models: no on-device code in v1. Corrected: llama.rn also requires the New Architecture from v0.10, and it works with Expo through a config plugin (research/llama-rn-readme.txt). ExecuTorch requires the New Architecture, RN 0.83+ or Expo SDK 55+ with a dev build, iOS 17.0+ and Android 8.0+ (minSdk 26). It also sends an anonymous model-download telemetry event, which must be disclosed or turned off (research/executorch-rn-readme.txt). llama.rn "recommends" the increased-memory-limit entitlement. Whether ExecuTorch needs it is not covered (verify). RNFB v27 requires dynamic iOS frameworks (research/rnfb-getting-started.txt); whether llama.rn and ExecuTorch build with them is not covered (verify).
    * The lenses still split on build versus buy (Alternatives Weighed). The evidence weakens the AI SDK option (item 31).

19. **Major: the reviewer key is ongoing operational work.** (all lenses)
    * Spend-cap behavior, corrected:
      * Anthropic: hard. A spend limit you set returns a 400 (research/anthropic-errors.txt).
      * OpenAI: project and organization spend limits are "enforced" and return 429 `project_spend_limit_exceeded` (research/openai-errors.txt). The round-1 "may be soft" is wrong.
      * Gemini: Tier 1 has a $250 billing-tier cap and a $10 per rolling 10 minutes spend-based rate limit returning 429 (research/gemini-rate-limits.txt, Usage tiers; Spend-based rate limits). How Google Cloud budgets themselves behave is not covered (verify).
    * The round-1 idea of a free-tier Gemini key as the primary reviewer key is weakened (inventive-catalyst, codebase-details, design):
      * The quota size is not published.
      * Eligibility can be withdrawn per project or require verification.
      * Limits are per project.
      * The terms issues in item 25 apply to the reviewer too.
    * Recommendation: the primary reviewer key is a capped paid key (Gemini Tier 1, or an OpenAI project with an enforced limit, or an Anthropic workspace limit). A free-tier key is a backup at most.
    * Round 3: under decision 15 the reviewer key is still needed for TestFlight Beta App Review, for the first external build and for "significant updates" (research/apple-review-guidelines.txt, 2.2).
    * Write a runbook covering provider, cap, monitoring, rotation per submission, owner, and the review window relative to monthly and daily resets. The reviewer types the key into the normal screen; it is never shipped in the binary. State in the review notes that the app is BYOK. gemini-api-key.txt warns developers never to expose keys client-side, which a reviewer may misread.

20. **Major: the session lifecycle is undefined.** (comprehension, structural, inventive-catalyst, implementation-readiness)
    * Recommendation, unchanged from round 1:
      * a state machine (idle, image loaded, prepared, in-turn, error);
      * a decision on backgrounding;
      * confirmation before a new image discards a non-empty conversation;
      * delete the manipulator's cache-directory JPEG when the session ends.
    * New:
      * Recover a pending camera result after Android activity destruction (item 13).
      * Clear the `expo-image` disk cache (item 6).
      * A provider or tier switch mid-session starts a new session, because it changes the image target and invalidates any cache.
    * Round 4: K.4 uses "session" for the config snapshot's lifetime (process lifetime), while this item means one conversation. Use two names (L4).

### E. Onboarding and ergonomics

21. **Blocker: end users are not told what BYOK means.** (comprehension)
    * Users will assume a ChatGPT Plus or Claude Pro subscription works. It does not. Anthropic treats consumer plans and the API as separate products (research/anthropic-data-retention.txt). There is no OpenAI evidence.
    * Corrected: "Usage is billed to your account" is wrong for a Gemini key without billing. That key is not billed; the user pays in data use instead (research/gemini-pricing.txt, Free Tier "Used to improve our products: Yes"). The app no longer suggests a free key (decision 10), but a pasted unbilled key still behaves this way, so the Gemini consent line states it.
    * Recommendation:
      * Separate "this app is free" from "the AI service may charge you or use your data", with one line per provider, before key entry.
      * Use "location and camera details", not "EXIF".
      * State the 18+ requirement (item 25).
      * Round 4: say on key entry that the key is not backed up and must be re-entered after deleting the app (L13).

22. **Major: asking for a model ID at first launch is an expert decision.** (comprehension, inventive-catalyst, zoom-out)
    * **Rejected by author (decision 36, final): the user always chooses both provider and model.** "Fast" and "Best" may remain as labels next to model names in the list, not as the choice itself. Each model's request settings (image target, `detail` / `resolution`, effort, cache policy) still come from Remote Config per model.
    * Original recommendation:
      * The provider is the only first-launch choice.
      * Tiers ("Fast" / "Best") resolve to bundles in Remote Config: model, fallback chain, image target, `detail` / `resolution`, effort, cache policy. The I.3 `defaults` parameter currently holds only model IDs (J12).
      * Raw model IDs live under Advanced.
      * The Gemini default is chosen for OCR quality and price, not free-tier eligibility (free-tier pointer removed, decision 10).
    * Proposed bundles: item 34.

23. **Major: the 14-class error list needs a user action for each class, or fewer classes.** (comprehension, zoom-out, inventive-catalyst, implementation-readiness)
    * Six user-facing classes stand (no connection, key problem, limit reached, content refused, image could not be read, something went wrong), each with a one-tap route to its fix. Fine-grained classes stay internal.
    * Corrected retry policy:
      * OpenAI: retry 429 rate limit, `slow_down` and 503. Use Retry-After, or exponential backoff with jitter when it is absent. Never retry the billing codes.
      * Anthropic: retry 429 only when `retry-after` is present. A 429 without it may be the tier spend cap. Retry 529 and 5xx.
      * Gemini: no Retry-After is documented. Use exponential backoff with jitter on 429, 408 and 5xx. Never retry 400, 402 or 403 (research/gemini-troubleshooting.txt, Retry strategy). How to tell a per-minute 429 from a per-day 429 is not covered (verify).
      * At most 2 retries, and a cap on total wait. If Retry-After exceeds the cap, show "try again in N s".
      * Turn off SDK-level retries (the Anthropic SDK retries twice by default, Gemini's SDK more), or retries multiply.
    * New user actions for Gemini 403s: verify the account, add billing, create a new auth key, or restrict an old key to the Gemini API.
    * A provider × raw signal → error class → message ID table, with one recorded-fixture test per row. Rows listed in Phase 2.

24. **Nit: copy/share, key validation, accessibility, offline and i18n can be decided now.** (zoom-out, comprehension, implementation-readiness)
    * Copy/share: in scope.
    * Key validation, corrected for Gemini: "A 200 OK response from models.list doesn't guarantee that inference calls … are enabled for your project" (research/gemini-troubleshooting.txt). Use a minimal inference call, a few tokens on paid keys (disclose this), or treat the first send as the real check. The round-1 claim that list-models validation "costs no tokens" holds, but that check is not enough for Gemini.
    * Basic accessibility: in scope. Offline: a no-connection state. i18n: deferred, strings kept translatable.
    * Minimum OS: see D9. Implementation-readiness recommends iOS 17 and Android minSdk 26 from v1, matching the ExecuTorch floor, so Phase 5 does not strand users. Both are above RNFB's floors (iOS 15, minSdk 24; research/rnfb-getting-started.txt).

### F. Inventive resolutions of core contradictions (inventive-catalyst; updated round 2, rows 3 and 6 updated round 3 and 4, rows 7–12 added round 4)

| Contradiction | Proposed resolution | Trade-off |
| --- | --- | --- |
| OCR fidelity vs per-turn image cost | **Revised.** Build a stable prefix: fixed instructions, then the image block, then turns. Truncate history only after the prefix. Turn on Anthropic automatic caching (one top-level `cache_control`). Keep effort and thinking constant within a session. Move the "image digest" idea to a Phase 5 experiment. | The first-turn cache write costs 1.25x, so break-even is turn 2. The 5-minute TTL means long pauses pay full price again. OpenAI caching mechanics are not covered. Cache retention must appear in the privacy text. |
| Zero setup vs BYOK | On-device text extraction (Apple Vision / ML Kit) with no key; paste from clipboard; deep-link to each provider's key page (decision 10). | A second OCR path with different quality. On-device OCR is not covered by the evidence. Key-prefix detection is unverified because no key formats are documented, including Gemini's new auth keys. |
| Model choice vs model churn | Tier bundles in Firebase Remote Config (decision 13; signing dropped; hosts allowlisted in code, decision 23). A scheduled CI canary runs fixture tests plus one live smoke call per provider and tier on capped keys, and alerts before users hit breakage. | A Remote Config template to keep current; Firebase SDK and data collection. A malicious publish can still change models, limits, error map, consent texts and user-facing URLs (L11). Keys in CI secrets. Bundled fallbacks go stale. |
| Future local model vs lightweight v1 | Capability-based interface, proven in v1 with a fake in-process provider. No native ML in v1. | Risk of over-abstracting. |
| Demonstrator vs App Store eligibility (new) | Keep the demonstrator story in the repo and benchmark results, never in store metadata. Optional: an in-app "Compare" action that asks a second configured provider the same question. This is both a distinct user feature (helps with 4.2) and the core demonstrator metric. Lower priority while the release is test-track only (decisions 15–16), but TestFlight builds must still comply with 4.2 (2.2). | Doubles cost per compared question, needs consent for both providers, adds UI. |
| "Nothing stored" vs provider retention (new) | Split the claim: "this app stores keys, settings and consent records on the device; reports you send (text, kept for the stated period) and, if you opt in, usage and crash data go to Firebase", plus a per-provider retention table in the consent screen, generated from the Remote Config `consent_texts` / `privacy_policy` values. | Legal text delivered as data needs a consent-version and re-consent mechanism (item 32, I.6) and a content hash (J10). The Firestore on-device cache must be turned off or disclosed (L5). |
| Cross-tester metrics vs analytics off by default (round 4) | The offline harness is the primary measure; K.5 is supporting field telemetry. Ask testers in the invitation to turn sharing on. Optional: a per-report "include anonymous performance summary" checkbox. | Self-selected sample; a second telemetry path must be disclosed. |
| Crash visibility vs crash consent (round 4) | Keep collection off; if Crashlytics holds unsent reports (verify), ask on next launch "Send the crash report?" and send or delete. | One extra prompt; API availability in RNFB unverified. |
| Actionable report vs no photo and often no ID (round 4) | Structured, non-content metadata (source, input format, starter prompt, turn index, image settings) plus "What was in the photo?". On iOS, point to a TestFlight feedback screenshot joined by report ID. | Two channels on iOS; nothing equivalent confirmed on Android. |
| Open writes vs abuse on Spark (round 4) | Rules pin shape, size, `createdAt == request.time` and the TTL field; Spark's hard quota caps cost at zero; optional deterministic per-install-per-day document IDs; Anonymous Auth as the stronger variant. | Rules test suite; worst case is reporting unavailable until the quota resets (L6). |
| Fresh config every start vs throttling and slow networks (round 4) | Separate the 3 s first-screen race from the native `fetchTimeoutMillis`; re-read only `kill_switch` after any late activation; alternatively rely on the real-time listener's version check on connect with a longer interval. | Two activation paths share one kill-switch handler. |
| Keys wiped on reinstall vs backup restore (round 4) | Bind the marker to the Keychain: a nonce in both `install.json` and a secure-store item; compare at launch to tell same install, reinstall and restored-from-backup apart. | One more permanently frozen Keychain item. |

### G. New findings from round 2 (decisions 7–9 and the evidence)

25. **Blocker (partly resolved, D10): Gemini's terms restrict the free-tier pointer and constrain Gemini itself in a free consumer app.** (all seven lenses)
    * Clauses (research/gemini-terms.txt):
      * Use Restrictions: AI Studio and the Gemini API are "for developers building with Google AI models for professional or business purposes, not for consumer use."
      * Age Requirements: users must be 18+, and the API must not be used in an API client "directed towards or likely to be accessed by individuals under the age of 18". This covers paid use too.
      * Use Restrictions: "You may use only Paid Services when making API Clients available to users in the European Economic Area, Switzerland, or the United Kingdom."
      * Unpaid Services: "Do not submit sensitive, confidential, or personal information." A photo-Q&A app's main inputs (documents, receipts, faces) are personal information.
    * Apple 5.2.2 asks whether the app is "specifically permitted" under these terms.
    * Whether a BYOK end user counts as the "developer" or as a "consumer" is a legal question the evidence does not settle.
    * An honest Gemini free-tier consent screen ends up telling users not to do what the app is for (comprehension). Users will tap through it.
    * Options for D10:
      * (a) Drop the free-tier pointer. Keep Gemini as a normal BYOK provider and point to billing setup. Zoom-out, design and implementation-readiness favor this, because it removes the region- and tier-dependent consent text.
      * (b) Keep it, framed for developers and "non-personal test photos". Hide it for EEA/UK/CH. Quote Google's "do not submit personal information" and human-review wording in the Gemini consent screen.
      * (c) Get a legal reading and record it.
      * **Resolved: option (a), decision 10.**
    * Still open whatever was chosen:
      * Set an 18+ App Store age rating and Play target audience before the first submission. Ratings are hard to tighten later.
      * Restrict distribution territories to Gemini's available regions, or hide Gemini where it is unavailable. On the test tracks, control who is invited instead (I.8).
      * The App Store Connect / Play Console territory choice is the only enforceable regional control, because device region and locale are spoofable.

26. **Major (resolved, D15/D16): the demonstrator framing is exposed to Apple 4.2, and a public release may not be needed.** (zoom-out, inventive-catalyst)
    * If success is purely technical, TestFlight and Play testing tracks are what 2.2 directs demos to. That reduces 2.2 and 4.2 risk. The reviewer key is still needed for Beta App Review (item 19).
    * Trade-offs: TestFlight builds expire after 90 days (verify) and still need beta review, and there is no store discovery. Consent and privacy obligations remain.
    * If a public release follows, consider bringing on-device OCR or the Compare action (section F) into it so the app has something distinctive.
    * Resolved: TestFlight (decision 15) and the Play internal testing track (decision 16); see I.8.

27. **Major (resolved, D17 by decision 22): AI-output reporting needs a delivery path, which conflicted with "no backend".** (codebase-details)
    * Play requires reporting "to developers without needing to exit the app" (research/play-ai-content.txt). A mailto link or share intent leaves the app.
    * Apple 1.2 now explicitly includes "AI content creation apps" (filtering, reporting, blocking, published contact info). Whether single-user image Q&A counts is a judgment call. Prepare a review-notes rationale.
    * Round 3 chose email (decision 14, I.7); J4 showed the Android mail intent leaves the app. Superseded by decision 22: the report is written from inside the app to Cloud Firestore, text only (decision 25; K.6–K.7). Open issues in L1, L2, L6.

28. **Major: three providers mean three consent texts tied to policy pages that change on their own schedules.** (comprehension, zoom-out)
    * The policy pages changed often in 2026 alone: Gemini key types on 2026-05-28, dormant-key blocking on 2026-05-07, the Anthropic retention page dated 2026-07-01, and Apple's guidelines on 2026-10-16.
    * Facts each consent screen must state, from the evidence:
      * **All providers:** provider name; what is sent; that the app keeps nothing beyond keys, settings and consent records on the device, and its author receives nothing unless the user sends a report (text only, stored in Firebase, K.6–K.7) or turns on usage and crash sharing (decision 24); that the app cannot delete the provider's copy, and where to request deletion; how to withdraw consent; a link to the privacy policy.
      * **OpenAI:** not used for training unless the user opted in on their account; abuse logs up to 30 days; possible-CSAM images kept for review; Responses state kept 30 days unless `store: false`.
      * **Anthropic:** deleted within 30 days; up to 2 years if flagged; images not used for training; "Covered Models" have extra retention (verify which).
      * **Gemini:** for unpaid outside EEA/UK/CH: product improvement, human review, and "do not submit personal information". For paid, or in EEA/UK/CH: no product improvement, logged for a limited period, may be cached in any country. 18+. Available regions only.
    * The app cannot know billing status. With the pointer removed (decision 10), use one worst-case Gemini text; the round-2 "my project has billing enabled" self-declaration is dropped (it has no field in I.6). Round 3 evidence: Remote Config `device.country` conditions use the request IP address, which a VPN can change (research/firebase-rc-condition-reference.txt).

29. **Major: the image digest and the history cap both conflict with provider caching.** (inventive-catalyst, design)
    * Anthropic, confirmed and corrected (research/anthropic-prompt-caching.txt):
      * Minimum cacheable length is 512 tokens on Opus 5.5, Sonnet 5.5, Haiku 5.5 and Fable 5.2. One image clears it. The "a single image may fall below" risk applies only to Haiku 4.5 and Opus 4.5/4.6 (4,096) and Opus 4.7 (2,048).
      * TTL is 5 minutes by default, refreshed free on each hit; a 1-hour TTL is optional. Writes cost 1.25x (5 min) or 2x (1 h). Reads cost 10% of base input, 5% on Opus and Sonnet 5.5 (research/anthropic-models.txt).
      * Automatic caching uses one top-level field.
      * "The presence/absence of images anywhere in the prompt" invalidates the cache. A prompt below the minimum is "processed without caching, and no error is returned".
    * Gemini: every current model lists "Implicit caching only". Minimum length and prefix rules are not covered (research/gemini-pricing.txt).
    * OpenAI: prompt-cache state can persist up to 24 hours (research/openai-your-data.txt). Prefix rules are not covered.
    * Consequence: re-attaching the image only on NEED_IMAGE breaks the Anthropic cache on every toggle. Truncating history before the image does too. See section F row 1 and D11.

30. **Major: the demonstrator's measurements are not reproducible unless the served model is pinned and recorded.** (design, codebase-details)
    * Silent re-routing (Gemini), mid-stream fallback (Anthropic), `-latest` alias swaps, and per-model defaults change what a benchmark measures:
      * OpenAI `auto` = `original`;
      * Gemini 4 Hydrogen defaults to thinking level "high";
      * Opus 5.5 always thinks.
    * Recommendation:
      * Run the benchmark as an offline harness outside the shipped app, on the author's keys, with fixture images and scripted questions.
      * Record the served model ID, the API version header and the image settings with every run.
      * Cost estimate: about 50 fixtures × 3 providers × 2–3 settings × 2k–5k tokens.
    * Round 4: the harness stays the primary measure; K.5 field events come only from testers who opt in (L7).

31. **Major: the AI SDK's documented Expo path does not match a no-backend BYOK design.** (design, zoom-out, implementation-readiness, codebase-details)
    * aisdk-expo.txt runs `streamText` in an Expo API route (`app/api/chat+api.ts`), needs `EXPO_PUBLIC_API_BASE_URL` to point at a server, and uses the Vercel AI Gateway as the default provider. String model IDs resolve through the gateway, which would send photos to a fourth processor not named in the consent screen.
    * The guide's polyfills (`@ungap/structured-clone`, `@stardazed/streams-text-encoding`) conflict with expo-fetch.txt, which says these APIs are built in and tells readers to remove custom `structuredClone` polyfills.
    * The AI SDK error types are transport-level only: `APICallError`, `RetryError`, `StreamProviderError`, `NoSuchModelError`, `UnsupportedFunctionalityError`. There are no quota or safety classes, so the mapping table is needed either way (research/aisdk-errors.txt).
    * Whether `@ai-sdk/google` targets the Gemini Interactions API or `generateContent` is not covered (verify).
    * The D3 spike must:
      * call all three vendors directly from a release dev build with user keys, with streaming and abort;
      * ban string model IDs;
      * confirm no gateway traffic and no `Origin` header (the Anthropic CORS 401);
      * confirm that raw provider error bodies are reachable.
    * If any of these fails, choose thin adapters.

32. **Major (resolved, D12; storage superseded by decisions 17 and 21): persisted state and consent records are frozen at the first tester release; migrations now move to Phase 1 (I.6, K.3).** (implementation-readiness)
    * Every later binary and OTA bundle must read what v1 wrote: settings, selected provider and model, consent records, keychain item names, service and accessibility.
    * Requirement (D12):
      * Store an explicit `schemaVersion`.
      * Readers tolerate unknown fields and higher versions, so an OTA rollback does not wipe settings.
      * Migrations are additive and keep the previous record until the new one is written.
      * Keychain names and `keychainService` are fixed permanently.
      * Pass `WHEN_UNLOCKED_THIS_DEVICE_ONLY` on every write.
      * Store consent as `{provider, consentTextVersion, timestamp}`. Allow a send only if the stored version is at least the required version, and gate sends on consent, never on whether a key exists.
      * After a reinstall (detected by a non-keychain flag), keys survive on iOS. Round 4 resolution: keys are wiped and settings and consent files are gone after a reinstall (decisions 17, 21; K.2, K.3). The marker file name, directory and key-name list are now frozen too (L3).

33. **Major (resolved, D13): the remote manifest needs a compatibility contract and a kill switch.** (implementation-readiness, inventive-catalyst)
    * Requirement (D13):
      * Version the schema, with `minAppVersion` and a maximum schema version. Round 3 used a per-value `schema` integer plus `app.version` conditions (I.3). Round 4: decision 19 drops version negotiation; validation and a fallback remain (K.2, L8).
      * Bundle a last-known-good copy. Validate downloads, and keep the last good copy on failure. Round 4: the fallback rule is open (L8).
      * Never delete an ID; mark it retired with a replacement, so persisted selections migrate with a one-time notice.
      * Unknown error codes map to "something went wrong" (internal class `unknown` in I.3) and are counted in a local diagnostic log.
      * Include a per-provider and per-model kill switch. Store releases cannot be rolled back, so this is the only fast rollback for a broken adapter.
    * Implemented as the I.3 `kill_switch` parameter and the K.4 load order.
    * Local observability (round 2; now alongside opt-in Analytics, decision 24) (codebase-details):
      * Show the provider request ID (Anthropic `request-id`; others not covered) in "show details" and include it in report payloads.
      * Keep an opt-in local ring buffer per turn: provider, requested and served model, API version, status, error code, internal class, stop reason, image size and setting, token usage including cache fields, and retries. Never bodies or keys. Round 4: zoom-out would drop it now that nothing exports it; decide (L15).

34. **Major: curated default allowlist candidates (image input, per the evidence).** (all technical lenses)
    * **Anthropic** ("All current models support text and image input", research/anthropic-models.txt):

      | ID | Notes | Proposed tier |
      | --- | --- | --- |
      | `claude-haiku-5-5` | Fastest; from $0.10/$0.50 per MTok; thinking can be disabled; not retired before 2027-10-07 | Fast |
      | `claude-sonnet-5-5` | $2/$10; thinking cannot be disabled (lowest setting `between_tools`); not retired before 2027-09-28 | Best (avoids always-on thinking latency) |
      | `claude-opus-5-5` | $4/$20; thinking always on; Anthropic's recommended starting model; not retired before 2027-09-22 | Advanced (inventive-catalyst would make this Best if latency matters less) |
      | `claude-fable-5-2` | $8/$40; thinking always on | Advanced |

      * Exclude `claude-mythos-*` (limited access) and retired or deprecated 4.x IDs. `claude-sonnet-4-5-20250929` is marked deprecated.
    * **OpenAI** (inferred from the openai-vision.txt sizing tables; the models and pricing pages were not captured):
      * Current: `gpt-6-astra`, `gpt-5.6-sol`, `gpt-5.6-terra`, `gpt-5.6-luna`, `gpt-5.5`, `gpt-5.4`, `gpt-5.4-mini`, `gpt-5.4-nano`, `gpt-5.2`, `gpt-5.1`, `gpt-4.1`, `gpt-4.1-mini`, `gpt-4o`, `gpt-4o-mini`.
      * Deprecated (exclude): `gpt-5`, `gpt-5-mini`, `gpt-5-nano`, `gpt-4.1-nano`, `o4-mini`, `o1`, `o1-pro`, `o3`.
      * Avoid `gpt-4o-mini` as a default (2,833 base + 5,667 per tile). `gpt-image-*` models generate images and do not answer questions about them.
      * Best: `gpt-6-astra` with explicit `detail: "high"`.
      * Fast: one of `gpt-5.6-luna` or `gpt-5.4-mini`. Their price and latency are not covered, so verify before choosing.
    * **Gemini** (research/gemini-models.txt; research/gemini-pricing.txt):

      | ID | Free tier | Notes | Proposed tier |
      | --- | --- | --- | --- |
      | `gemini-4-hydrogen` | yes | $1.50/$7.50 paid; image modality stated; defaults to thinking level "high"; description stresses coding and agentic work, so OCR quality must be measured | Best |
      | `gemini-3.5-flash-lite` | yes | $0.30 input; re-route target for 2.5 Flash | Fast |
      | `gemini-3.8-flash`, `gemini-3.6-flash` | yes | $0.75 input until 2026-12-31, $1.50 from 2027-01-01; image input inferred from "All Gemini model versions are multimodal" (verify) | Fallbacks |
      | `gemini-3.1-flash-lite` | yes | stable | Fallback |
      | `gemini-4-argon`, `gemini-4-carbon` | no | paid only; Carbon price doubles 2026-12-01 | Advanced only (Argon); not a default |
      | `gemini-3.1-pro-preview`, `gemini-3-flash-preview` | pro: no; flash: yes | preview; can be deprecated with 2 weeks' notice | Exclude from defaults |

      * The "Free tier" column is informational after decision 10; it no longer drives the default.
      * Exclude the deprecated 2.5 family, image-generation (Nano Banana, `*-image`), TTS, Live, Transcribe, Omni, embedding and Decision models.
      * Send `resolution: "high"` explicitly.

### H. Evidence verification (sources fetched 2026-11-14/15)

Sources: `research/README.md`. The OpenAI Responses API reference was not captured (HTTP 403). No Play testing-track page and no TestFlight help page were captured.

**Confirmed**

* Apple 5.1.2(i) third-party AI consent wording; Apple 2.2 demo exclusion; Apple 4.2 wording (apple-review-guidelines.txt).
* Apple 2.2 TestFlight wording (round 3): TestFlight apps "should be intended for public distribution and should comply with the App Review Guidelines"; "Significant updates to your beta build should be submitted to TestFlight App Review" (apple-review-guidelines.txt, 2.2).
* Play READ_MEDIA_IMAGES restriction and photo-picker backport (play-photo-permissions.txt). Play in-app AI-content reporting (play-ai-content.txt).
* Gemini unpaid-tier training and human review; app cannot detect paid vs unpaid (gemini-terms.txt).
* Gemini key in the `x-goog-api-key` header (gemini-api-key.txt).
* Gemini 20 MB inline limit, which covers the whole request (gemini-image-understanding.txt).
* Gemini 429 RESOURCE_EXHAUSTED (gemini-troubleshooting.txt, gemini-rate-limits.txt).
* Anthropic 529 overloaded and mid-stream error events after a 200 (anthropic-errors.txt, anthropic-streaming.txt).
* Anthropic 8000×8000 px max; first frame only for animations (anthropic-vision.txt).
* Anthropic 5-minute cache TTL (anthropic-prompt-caching.txt).
* OpenAI Responses 30-day retention by default unless `store: false` (openai-your-data.txt, line 1190). Round-2 reviewers disagreed on this until the line was checked.
* Anthropic 30-day deletion; Files API exception (anthropic-data-retention.txt).
* iOS Keychain survives uninstall, "not guaranteed" (expo-securestore.txt).
* `fingerprint` runtime policy (expo-updates-runtime.txt).
* `expo/fetch` streaming (expo-fetch.txt).
* AI SDK Expo 52+ and polyfills as written (aisdk-expo.txt).
* ExecuTorch New Architecture requirement (executorch-rn-readme.txt). llama.rn multimodal and Expo plugin (llama-rn-readme.txt).
* HEIC returned as-is by the picker (expo-imagepicker.txt).
* Round 3, Firebase and RNFB:
  * Remote Config lists `@react-native-firebase/analytics` as a peer dependency; New Architecture required from RNFB v26 (rnfb-remote-config.txt, Prerequisites).
  * 12-hour default and recommended production minimum fetch interval; Firebase "may reject requests if values are requested too frequently"; `fetchTimeoutMillis` defaults to 60 s; `LastFetchStatus` includes throttled (rnfb-remote-config.txt, Settings and caching).
  * Real-time updates bypass the minimum fetch interval, stop in the background and restart in the foreground, and do not activate on their own; `activate()` makes the whole last-fetched config live (firebase-rc-realtime.txt; rnfb-remote-config.txt, Fetch and activate).
  * `app.version` numeric comparison works only for plain semantic versions; `device.os` supports `==` and `!=`; `device.country` comes from the request IP (firebase-rc-condition-reference.txt).
  * Rollback publishes the old content as a new version (firebase-rc-templates.txt).
  * Remote Config SDK collects country code, language, time zone, OS version, app ID and bundle ID (firebase-ios-data-collection.txt, FirebaseRemoteConfig); Installations registers an installation ID (firebase-installations.txt; firebase-android-data-disclosure.txt).
  * RNFB v27 is SPM-only on iOS and needs `expo-build-properties` `ios.useFrameworks: "dynamic"` and Xcode 26.2 or later (rnfb-getting-started.txt).
* Round 3, other:
  * expo-secure-store: "some iOS releases refused values above roughly 2048 bytes. Expo does not enforce a limit." (expo-securestore.txt).
  * expo-mail-composer facts (no longer used after decision 22): `attachments` are app-internal file URIs; `isAvailableAsync()` is true only with a default mail account; Android status "is always set as if the email were sent" (expo-mailcomposer.txt).
* Round 4, Firestore, App Check and Firebase:
  * Firestore free quota: 1 GiB stored, 50,000 reads, 20,000 writes and 20,000 deletes per day, reset around midnight Pacific; "exactly one free database per project"; 1 MiB per document (firestore-quotas.txt).
  * TTL deletion "typically within 24 hours after its expiration date"; the TTL field must be a "Date and time" value, and an absent or null field disables expiry for that document; a TTL policy can carry an expiration offset (firestore-ttl.txt).
  * Cloud Storage requires Blaze (storage-faq-blaze.txt); Cloud Functions require Blaze (firebase-pricing-plans.txt). App Check, Crashlytics and Authentication (most options) are no-cost products (firebase-pricing-plans.txt).
  * Rules functions "cannot execute loops"; at most 1,000 expressions per request (firestore-rules-conditions.txt; firestore-quotas.txt).
  * App Check "doesn't accept tokens generated in the App Attest sandbox environment"; initialize App Check before other Firebase SDKs; debug provider for simulators and CI; token TTL 30 minutes to 7 days, default 1 hour; monitor metrics before enforcing (app-check-firestore.txt). App Check "prevents some, but not all, abuse"; Play Integrity Standard quota 10,000 calls per day (firebase-app-check.txt).
  * RNFB Firestore offline persistence is "enabled by default on native platforms"; `persistence: false` disables the cache; `waitForPendingWrites`; `serverTimestamp()` is recommended for stored timestamps (rnfb-firestore.txt).
  * `withoutAdIdSupport: true` opts out of iOS Ad ID support (rnfb-getting-started.txt). It is iOS-only.
  * Crashlytics collects "any custom keys, logs, and free-text user IDs", uses Analytics breadcrumbs when present, collects Remote Config rollout metadata, and depends on the Firebase Sessions SDK, which collects app and device metadata, network type and foreground times (firebase-android-data-disclosure.txt; firebase-ios-data-collection.txt). Crashlytics custom logs are limited to 64 kB (firebase-pricing-plans.txt).
  * `minimumFetchIntervalMillis: 0` is allowed; "catch throttle failures and back off"; RN `FetchStatus` literals are `'success'`, `'failure'`, `'no_fetch_yet'`, `'throttled'` (rnfb-remote-config.txt). Fetch-once-per-launch has a low throttling risk in production (firebase-rc-loading.txt).

**Corrected**

| Brief item | Round-1 claim | Corrected fact | Source |
| --- | --- | --- | --- |
| 3, 11 | Anthropic about 5 MB, 1568 px, about 1.6k tokens, w·h/750 | 10 MB on the Claude API (5 MB is Bedrock); 4.7+ models are high-res: 2,576 px, up to 4,784 tokens; `ceil(w/28)×ceil(h/28)` | anthropic-vision.txt, Request limits; Resolution and token cost |
| 3, 11 | OpenAI a few hundred to over 1k tokens; `low` ruins OCR | Default `auto` = `original` on newest models (about 14k tokens for 12 MP); >30,000 patches rejected; `high` caps at about 3k; `low` can cost more than `high` on gpt-5.4 | openai-vision.txt, detail level; patch tokenization |
| 3, 11 | Gemini 258 tokens per 768 px tile | Gemini 3/4: 280 / 560 / 1,120 / 2,240 per image by resolution; tile rule is legacy | gemini-media-resolution.txt, Token counts |
| 5 | Free tier "restricted" in EEA/UK/CH | Gemini is available there; only paid services may be offered to those users; paid data terms apply there even on unpaid quota | gemini-terms.txt, Use Restrictions; Unpaid Services; gemini-available-regions.txt |
| 5, 25 | (not in brief) | "not for consumer use"; 18+; "do not submit … personal information" | gemini-terms.txt |
| 12 | 404 means model retired | Gemini silently re-routes 2.5 IDs from 2026-12-01; Anthropic can fall back mid-stream | gemini-models.txt; anthropic-streaming.txt |
| 12 | Anthropic list cannot show vision | All current Anthropic models accept images | anthropic-models.txt |
| 13 | OpenAI quota told apart by `insufficient_quota` | Branch on `error.code` (four billing codes); overload is 503; region 403 | openai-errors.txt, API errors |
| 13 | Anthropic 400 for low credit | 400 = spend limit you set; 402 = billing; tier spend-cap 429 has no retry-after | anthropic-errors.txt, HTTP errors |
| 13 | Gemini safety as `finishReason` on 200 | On the Interactions API: HTTP 400 with `error.code` safety/jailbreak/prohibited_content/blocklist | gemini-safety-settings.txt |
| 14 | RN fetch cannot stream | `expo/fetch` is the global fetch on iOS/Android | expo-fetch.txt |
| 15 | `THIS_DEVICE_ONLY`; manual backup exclusion | Use `WHEN_UNLOCKED_THIS_DEVICE_ONLY`; backup exclusion is automatic without a custom config. Round 3: which constant is deprecated is unclear in the extracted text (verify) | expo-securestore.txt |
| 9 | Animated GIF gives first frame | Android picker can return animated GIF; OpenAI rejects animated GIF | expo-imagepicker.txt; openai-vision.txt |
| 8 | Remove media permissions | Library adds CAMERA, READ/WRITE_EXTERNAL_STORAGE, RECORD_AUDIO | expo-imagepicker.txt, Permissions |
| 17 | Incompatible OTA will crash | "may detect an error and attempt to roll back" | expo-updates-runtime.txt |
| 18 | Only ExecuTorch needs New Architecture | llama.rn too, from v0.10; ExecuTorch also needs iOS 17 / minSdk 26 / SDK 55+ | llama-rn-readme.txt; executorch-rn-readme.txt |
| 19 | OpenAI budgets may be soft; Google only alerts | OpenAI limits enforced; Gemini Tier 1 $250 cap and $10/10 min spend limit | openai-errors.txt; gemini-rate-limits.txt |
| 24 | Validate key with one list-models call | Gemini models.list 200 does not prove inference works | gemini-troubleshooting.txt |
| 29, Alternatives | Anthropic minimum may exceed one image | 512 tokens on 5.x models; automatic caching exists | anthropic-prompt-caching.txt |
| Alternatives | 2.1 "explicitly accepts" a demo mode | Only "in lieu of a demo account", with prior approval by Apple | apple-review-guidelines.txt, 2.1(a) |
| 31 | AI SDK Expo guide fits | The guide uses a server route and AI Gateway | aisdk-expo.txt |
| 6 (new) | Nothing stored on device | `expo-image` disk cache keeps images up to a week on iOS | expo-image.txt |
| I.3, D13 (round 3) | Parameter and template size limits not in the fetched docs | 3,000 parameters, 2,000 conditions, 256-character keys, 1,000,000 characters across all values per project; per-value limit still not covered | firebase-rc-parameters.txt, Quotas and limits |
| I.2, I.5 (round 3) | Firebase keeps 300 versions | 300 is a lifetime cap per template type; the earliest versions are deleted | firebase-rc-templates.txt |
| I.4 (round 3) | `setDefaults` | RNFB sets in-app defaults with `remoteConfig.defaultConfig` (string, number or boolean values) or `setDefaultsFromResource` (native plist/XML) | rnfb-remote-config.txt, Default values |
| I.4 (round 3) | 3 s fetch timeout from the loading doc | The doc gives no number; 3 s is the author's choice | firebase-rc-loading.txt |
| I.5 (round 3) | App Check not required for Remote Config | Remote Config is not on App Check's supported-services list; App Check cannot protect it | firebase-app-check.txt |
| I.1 (round 3) | Firebase JS SDK lacks Remote Config support | expo-using-firebase.txt names Analytics, Dynamic Links and Crashlytics as unsupported, not Remote Config. The web Remote Config SDK depends on IndexedDB (rnfb-remote-config.txt, isSupported; React Native behavior: verify) | expo-using-firebase.txt; rnfb-remote-config.txt |
| I.8 (round 3) | 4.2 risk drops away on TestFlight | TestFlight apps "should comply with the App Review Guidelines"; risk reduced, not removed | apple-review-guidelines.txt, 2.2 |
| K.6, K.7, decision 25 (round 4) | 90-day TTL on the free Spark plan | TTL deletes "don't include free usage. You must enable billing" | firestore-quotas.txt, Free quota |
| K.6 (round 4) | Rules can check every field's type and size | Rules cannot loop, so an array of `{role, text}` cannot be checked per element | firestore-rules-conditions.txt |
| K.6 (round 4) | App Check means "only genuine builds of the app can write" | "prevents some, but not all, abuse vectors" | firebase-app-check.txt |
| K.6 (round 4) | Queued writes may be lost when the app is killed | Persistence is on by default on native platforms, so writes are kept in a local database (survival across a kill still to be tested) | rnfb-firestore.txt, Offline Capabilities |
| K.2 (round 4) | `withoutAdIdSupport` "per J-section notes; verify" | Confirmed, iOS only | rnfb-getting-started.txt |
| K.2 (round 4) | Fetch at every start "works against" throttling | Allowed with interval 0; throttling risk low for fetch-once-per-launch | rnfb-remote-config.txt; firebase-rc-loading.txt |

**Not covered (still "verify")**

* Anthropic: `stop_reason: "refusal"`; which models are "Covered Models"; supported-country list.
* OpenAI: refusal and `content_filter` fields; request-ID header; models and pricing pages.
* Gemini:
  * `finishReason` / `promptFeedback` on `generateContent`;
  * `FAILED_PRECONDITION` for region;
  * the `?key=` parameter;
  * free-tier RPM/RPD values and the late-2025 quota cut;
  * paid-tier log retention in days;
  * Files API retention;
  * explicit image modality for 3.8/3.6 Flash;
  * which surface `@ai-sdk/google` uses.
* Apple: the date of the 5.1.2(i) AI wording.
* OpenAI and Anthropic age or consumer-use terms.
* Platform and libraries:
  * `expo/fetch` AbortController and `redirect: 'error'`;
  * iOS behavior when the app is backgrounded mid-request;
  * HEIC decode, EXIF orientation and metadata stripping in `expo-image-manipulator`;
  * native HEIC on Android API 28;
  * ProRAW;
  * the iOS increased-memory-limit entitlement for ExecuTorch;
  * on-device OCR (Vision / ML Kit).
* Round 3:
  * the per-value Remote Config size limit (quotas page not fetched);
  * RNFB `firebase.json` key names for collection defaults (`analytics_auto_collection_enabled`, `google_analytics_adid_collection_enabled`, `crashlytics_auto_collection_enabled`, `crashlytics_debug_enabled`);
  * what the linked Google Analytics SDK collects, and its privacy manifest;
  * whether llama.rn and ExecuTorch build with dynamic iOS frameworks;
  * TestFlight 90-day expiry, the 100-internal-tester cap, internal testers having to be team members, and the external-tester cap;
  * Play internal track: 100-tester cap, adding testers by email or Google Group, review depth, build expiry, required Console declarations (privacy policy, Data safety, content rating, target audience), first-AAB manual upload;
  * Play closed-test requirement (tester count and duration) for new personal developer accounts;
  * Firebase App Distribution "skips Play review".
  * Obsolete after decision 22 (email path removed): whether Play accepts a mail intent; Data safety treatment of user-sent email; Gmail on the iOS mail sheet; `mailto:` attachments; Android attachment URI access; `expo-sharing` text plus image; whether Remote Config works without the Analytics native SDK (moot after decision 20).
* Round 4:
  * `getAppInstanceId` behavior while Analytics collection is off, and the instance-ID lifecycle (reinstall, clear data, `resetAnalyticsData`);
  * Google Analytics reserved event names (for example `session_start`) and name, parameter and value limits;
  * Google Analytics user deletion and retention;
  * Crashlytics unsent-report behavior and APIs in RNFB (`checkForUnsentReports`, `sendUnsentReports`, `deleteUnsentReports`), the Crashlytics `setUserId` clear value, Hermes JS stack symbolication, dSYM upload from EAS through the config plugin;
  * whether the Firebase Sessions SDK honors Crashlytics collection being off;
  * Apple's definition of tracking for ATT with advertising ID off; the Android AD_ID permission and Play advertising-ID declaration;
  * rules syntax for `request.time`, `duration`, `keys().hasOnly`, `size()`;
  * whether a retried create after a lost acknowledgement is denied as an update;
  * the Spark quota-exhaustion period (rest of the month per firebase-pricing-plans.txt vs daily reset per firestore-quotas.txt);
  * whether TTL can be configured on Spark at all;
  * Remote Config Spark quota;
  * the RNFB App Check module, the App Attest production entitlement through an Expo config plugin, Play Integrity for internal-track and sideloaded builds;
  * expo-file-system atomic write and move-over-existing behavior; iOS file protection before first unlock;
  * Android Auto Backup and iOS backup restore of document-directory files;
  * Firestore database location being permanent;
  * Play internal-testing feedback channel.
* Other: Google Cloud budget behavior; DeepSeek.

### I. Round 3 design: remote configuration on Firebase, settings and consent storage, reporting and distribution (author decisions 10–16)

Sources for this section were fetched on 2026-11-14 and are listed in `research/README.md` (`firebase-*.txt`, `rnfb-*.txt`, `expo-using-firebase.txt`, `expo-securestore.txt`, `expo-mailcomposer.txt`, `apple-review-guidelines.txt`, `play-*.txt`). Round 3 reviewer findings on this section are in section J; inline "(J…)" pointers mark the passages they affect. Round 4 supersedes parts of this section; the map is at the start of section K.

#### I.1 Problems with the round-3 decisions

* **Firebase Remote Config adds Google SDKs and data collection to an app that otherwise has no backend.**
  * The React Native Firebase Remote Config package lists `@react-native-firebase/analytics` as a peer dependency (research/rnfb-remote-config.txt, prerequisites). Analytics collection must be turned off by default (`analytics_auto_collection_enabled: false` and `google_analytics_adid_collection_enabled: false` in `firebase.json`, verify the key names in the RNFB docs before use). The Analytics config plugin also has an iOS option `withoutAdIdSupport: true` (research/rnfb-getting-started.txt). (Round 4: Analytics is in scope, opt-in and off by default for everyone; decisions 20, 24.)
  * The Remote Config SDK always collects the device country code, language code, time zone, OS version, app ID and bundle ID, so it can target conditions (research/firebase-ios-data-collection.txt, `FirebaseRemoteConfig`). Android adds platform version, Android app ID, package name and SDK version (research/firebase-android-data-disclosure.txt, Remote Config). Firebase Installations also creates an installation ID, kept until deleted and then removed within 180 days (research/firebase-installations.txt; research/firebase-privacy.txt). These must appear in the App Privacy label, the Play Data safety form and the privacy policy.
  * From RNFB v26, the New Architecture is required (research/rnfb-remote-config.txt). This matches D9, which already turns the New Architecture on from v1.
  * The Firebase JS SDK is not a replacement: Expo's guide says it does not support all mobile services and points to React Native Firebase for native SDK features (research/expo-using-firebase.txt). The guide does not name Remote Config among the unsupported services; the web Remote Config SDK depends on IndexedDB (research/rnfb-remote-config.txt, isSupported; React Native behavior: verify).
* **Remote Config is not instant.** The recommended production minimum fetch interval is 12 hours, and Firebase may throttle frequent fetches (research/rnfb-remote-config.txt, caching section). A kill switch is therefore delivered through real-time Remote Config (`onConfigUpdate`) while the app is open, and through the startup fetch otherwise. A device that stays offline keeps the last activated values. (Round 4: interval 0, fetch at every cold start, K.2 and K.4.)
* **Secure storage has a size problem for consent history.** `expo-secure-store` warns that some iOS releases rejected values above about 2048 bytes (research/expo-securestore.txt). (Round 4: consent moved to plain files, K.3; J5 resolved.)
* **Sending the image on every turn (D11) is the most expensive option.** With the round-2 numbers, one photo costs up to about 4.8k tokens per turn on Anthropic and about 14k on OpenAI at default `detail`. The fixed send target (about a 2048 px long edge), the explicit per-model `detail`/`resolution` setting and the history cap are now required, not optional.
* **Removing the free-tier pointer (D10) does not remove the other Gemini terms.** The 18+ rule and "not for consumer use" apply to paid keys too (research/gemini-terms.txt, Age Requirements, Use Restrictions). The app still needs an 18+ age rating, and the onboarding should describe the app as a tool for people who hold a developer API key.

#### I.2 Why Remote Config rather than a JSON file on Firebase Hosting

Both are Firebase. Remote Config was chosen because it gives four things a static file does not:

1. conditions on `app.version`, platform and country, so a schema change can target only new builds (research/firebase-rc-condition-reference.txt);
2. template version history with one-step rollback; rolling back creates a new version with the old content. Firebase keeps up to 300 versions per template type as a lifetime cap, deleting the earliest (research/firebase-rc-templates.txt);
3. percentage rollouts for a new model or error mapping (research/firebase-rc-rollouts.txt). Rollout monitoring needs Crashlytics or Analytics; under decision 24 it covers only opted-in installs, so a staged rollout means the `percent` condition (research/firebase-rc-condition-reference.txt);
4. real-time updates for the kill switch (research/firebase-rc-realtime.txt).

The cost is the SDK and data collection in I.1. A static versioned JSON file on Firebase Hosting, fetched with plain `fetch`, avoids the SDK, but every condition, rollback and staged rollout would have to be written by hand. It is listed in Alternatives Weighed. Zoom-out and inventive-catalyst question whether reasons 1–3 carry weight for 100–300 testers (J14).

#### I.3 Remote Config parameters

Each parameter holds a JSON value. Parameter keys may be up to 256 characters. A project may hold up to 3,000 parameters, 2,000 conditions and 1,000,000 characters across all parameter values (research/firebase-rc-parameters.txt, Quotas and limits). The limit for one parameter value is not in the fetched docs (verify before relying on a large `error_map`).

Every JSON value carries `"schema": <integer>`. The app accepts a value only if its schema is one it knows; otherwise it keeps the last good value. (J7. Round 4: decision 19 drops schema negotiation; the fallback rule is open, L8.)

| Key | Purpose | Shape (main fields) |
| --- | --- | --- |
| `config_meta` | Identifies the published set | `schema`, `configVersion` (integer, increased on each publish), `publishedAt` (ISO 8601 UTC) |
| `kill_switch` | Fast rollback without a store release | `appDisabled` (bool), `appDisabledMessage`, `minSupportedAppVersion`, `updateUrl`, `disabledProviders[]`, `disabledModels[]`, `bannerMessage` (optional notice) |
| `providers` | One entry per provider | `id` (`openai`, `anthropic`, `gemini`), `enabled`, `displayName`, `baseUrl` (round 4: must match the host allowlist in code, decision 23), `apiVersion` (for example a pinned `anthropic-version`), `keyHelpUrl`, `keyPrefixHint`, `consentTextVersion`, `privacyFactsUrl`. (Round 4: `authStyle` removed; header style is set in code, decision 23.) |
| `models` | Curated vision-model allowlist | per model: `provider`, `id`, `displayName`, `tier` (`fast`, `best`, or `advanced`), `acceptsImages: true`, `maxLongEdgePx`, `maxBytes`, `imageDetail` (OpenAI `detail` or Gemini `resolution` value; the Gemini field name depends on the D3 API surface), `maxOutputTokens`, `status` (`active`, `deprecated`, `retired`), `replacementId`, `rerouteDate`, `minAppVersion`. IDs are never deleted; a retired ID points to its replacement. |
| `defaults` | Default model per provider and tier | `{provider: {fast: modelId, best: modelId}}` |
| `image_policy` | Send-time image settings | `defaultLongEdgePx` (2048), `jpegQuality` (0.8), `lowDataLongEdgePx` (only if D7 keeps low-data mode) |
| `conversation_policy` | Cost limits | `historyTurnsCap`, `maxQuestionChars`, `requestTimeoutMs`, `connectTimeoutMs`, `maxRetries`, `retryableClasses[]` |
| `error_map` | Provider error to internal class | ordered rules: `provider`, `phase` (`http`, `stream`, `finish`), `httpStatus`, `match` on `error.type` / `error.code` / `finishReason` / `stop_reason`, result `errorClass`. Unmatched errors become `unknown`. |
| `error_message_overrides` | Optional wording fixes | `{errorClass: {en: text}}`. Messages ship in the app bundle; this only overrides them. |
| `consent_texts` | Current consent text per provider | `provider`, `version` (integer), `effectiveDate`, `summary[]` (what is sent, retention, training and human review, region notes), `policyUrl` |
| `privacy_policy` | Hosted policy | `url`, `version` |
| `report_policy` (round 4; replaces round-3 `report_channel`) | In-app report limits (K.6–K.7) | `enabled`, `maxConversationChars`, `dailyLimitPerInstall`, `retentionDays` (text shown to users only). Round-3 `report_channel` (`type`, `to`, `subjectPrefix`, `includeImageDefault`, `maxBodyChars`) is removed by decisions 22, 23 and 25. Zoom-out would make these constants in code (L15). |

Conditions used:

* `app.version >= X` conditions when a parameter's `schema` increases, so older builds keep the value they understand (research/firebase-rc-condition-reference.txt notes numeric comparison works only for plain semantic versions such as `1.2.3`). (Round 4: dropped with schema negotiation, decision 19; the version comparison is still needed for `minSupportedAppVersion`.)
* `device.os` (platform) when iOS and Android need different image or model settings.
* No Analytics-audience conditions, because Analytics data exists only for opted-in users (decision 24).

#### I.4 App-side behavior (load order, fetch interval and fallback superseded by K.2 and K.4)

* **Bundled defaults.** The build embeds a copy of every parameter, generated from the same template file, through `remoteConfig.defaultConfig` (JSON values stored as strings) or `setDefaultsFromResource` (native plist/XML resource) (research/rnfb-remote-config.txt, Default values). A first launch with no network still works with the bundled copy.
* **Load order** (superseded by K.4; kept for history):
  1. At startup, activate the values fetched during the previous run.
  2. Read `kill_switch` and stop here if the app is disabled or below `minSupportedAppVersion`. (J2.)
  3. Fetch in the background with a short timeout (about 3 seconds, the author's choice). The RNFB default `fetchTimeoutMillis` is 60 seconds (research/rnfb-remote-config.txt).
  4. Subscribe to `onConfigUpdate`. Apply `kill_switch` immediately. Apply other parameters on the next cold start, or between sessions, never during an open conversation. (J2.)
* **Validation.** Parse each JSON value with a runtime schema validator (for example `zod`). Discard a value that fails validation or has an unknown `schema`, keep the last good value, and record the failure in the local diagnostic log. (Round 4: K.2 keeps validation with bundled defaults as the fallback; per-value vs whole-set is open, L8.)
* **Fetch interval.** (Superseded by K.2: `minimumFetchIntervalMillis` 0 and a fetch at every cold start.) Round 3: 12 hours in production, lower in development builds only. Handle the `throttled` fetch status by backing off (research/rnfb-remote-config.txt).
* **Model selection.** The models shown are the `models` entries that are active, allowed for this app version, not in `disabledModels`, and available to the user's key when the provider's model-list call is possible. A stored selection pointing to a retired model moves to `replacementId` with a one-time notice.

#### I.5 Publishing and operations

* Keep the Remote Config template as a JSON file in the repository. CI validates it against the same schemas the app uses, checks that every model ID in `defaults` exists in `models`, and checks that every error class in `error_map` has a bundled message.
* Publish with the Firebase CLI or the Remote Config REST API from CI. Do not edit in the console, except for an emergency kill switch, which must then be copied back to the repository.
* Rollback means publishing an earlier template version. Firebase keeps up to 300 versions as a lifetime cap (research/firebase-rc-templates.txt), so the repository remains the source of truth. (J8: a Firebase rollback makes `configVersion` go backwards.)
* A scheduled CI canary (Phase 3) checks that the published `defaults` models still answer a one-image request on each provider.
* Firebase App Check is not used for Remote Config: Remote Config is not on App Check's list of supported services (research/firebase-app-check.txt). Values are not secret, and the worst a forged client can do is read them. (Round 4: App Check is enforced on Firestore for report writes, decision 22, K.6. J3's malicious-publish threat is reduced by decision 23.)

#### I.6 Local settings and consent storage (D12; storage layout and reinstall rule superseded by K.2 and K.3; record format and the send-gate and revocation rules still apply)

Round 3 text: all persisted state lives in `expo-secure-store` with `keychainAccessible: WHEN_UNLOCKED_THIS_DEVICE_ONLY` on every write. (Superseded by K.3: secure store holds API keys only; settings and consent are plain files.)

| Item name | Contents |
| --- | --- |
| `pc.apikey.<provider>` | The API key only. One item per provider. (Still used, K.3.) |
| `pc.settings` | (Replaced by `settings.json`, K.3.) `schemaVersion`, `selectedProvider`, `selectedModel` (`{provider: modelId}`), `selectedTier` (`{provider: tier}`), `lowDataMode`, `onboardingCompletedAt`, `lastConfigVersionSeen`, `updatedAt` |
| `pc.consent.<provider>` | (Replaced by `consent.json`, K.3.) `schemaVersion`, `provider`, `current` (latest record), `history[]` (earlier records, newest first, at most 10) |
| `pc.install` | (Replaced by `install.json`, K.3; J1.) `installId`, `firstLaunchAt`, `appVersionAtInstall` |

A consent record:

```jsonc
{
  "recordId": "6f1c…",                 // random UUID
  "provider": "anthropic",
  "decision": "granted",               // or "revoked"
  "consentTextVersion": 3,             // from consent_texts.version
  "privacyPolicyVersion": 2,           // from privacy_policy.version
  "configVersion": 41,                 // config_meta.configVersion in force
  "recordedAt": "2026-11-14T10:32:05Z",// UTC, ISO 8601
  "appVersion": "1.0.0",
  "platform": "ios",
  "locale": "en-US"
}
```

Rules:

* Before every send, the app checks the provider's `current` consent record. A send is allowed only when `decision` is `granted` and `consentTextVersion` is at least the current `consent_texts.version` for that provider. Otherwise the consent screen is shown again, and the new record is written before the request starts.
* Revoking consent in Settings writes a `revoked` record. It does not delete the key; the user deletes the key separately.
* Writes are additive: write the new record, then trim `history` to 10. A reader ignores unknown fields and accepts higher `schemaVersion` values in read-only mode, so an OTA rollback does not wipe consent. (J21's downgrade rule replaces "read-only mode": write at your own schema and keep unknown fields.)
* Reinstall (superseded by decision 21 and K.2: keys are wiped, not kept). Round 3 text: if `pc.install` is missing while a `pc.apikey.*` item exists, treat it as a reinstall; keep the keys and mark all consent as needing renewal.
* Consent records never leave the device. They exist so the app can enforce "no send without current consent", not as legal proof held by the developer. If proof is needed later, that needs a backend and changes the privacy policy. (Round 4: the K.5 `consent_recorded` event would send the decision and text version for opted-in users; drop it or amend this rule, L12.)

#### I.7 AI-output reporting by email (D14, author decision 14; superseded by decisions 22 and 25, K.6–K.7)

Kept as history; nothing in this subsection is to be built. Author decision 14: a "Report" action opened the user's mail app with the details, the AI answer and the image, addressed through Remote Config `report_channel.to` (default `yogeshkolte@gmail.com`). Round 3 found that `mailto:` cannot carry the image, so the design used `expo-mail-composer` (research/expo-mailcomposer.txt) with share-sheet and `mailto:` fallbacks; that some users have no mail account set up; that Android reports every result as sent; that the developer's inbox would receive users' photos, names and email addresses; and that the address in a readable remote value attracts spam. J4 showed the Android mail intent leaves the app, against Play's in-app reporting rule. Decision 22 moved reporting into the app (Firestore) and decision 25 removed the photo. Two round-3 rules carry over to K.6: the report never contains the API key or request headers, and it uses the same redaction as "show details".

#### I.8 TestFlight-only initial release (D15, author decision 15)

Problems with this decision:

* **TestFlight covers iOS only, and the requirements name iOS and Android.** Android needs its own channel (D16, resolved below by decision 16). Options considered: the Play internal testing track (chosen), a Play closed test, or Firebase App Distribution (not chosen; whether it skips Play review: verify). Google requires new personal Play developer accounts to run a closed test with a minimum number of testers for a minimum period before a production release; the current numbers are not in the fetched docs (verify).
* **External TestFlight testing still goes through Apple review.** Apple 2.2: "Any app submitted for beta distribution via TestFlight should be intended for public distribution and should comply with the App Review Guidelines", and "Significant updates to your beta build should be submitted to TestFlight App Review before being distributed to your testers" (research/apple-review-guidelines.txt, 2.2). The reviewer key, the 5.1.2(i) consent screen, the privacy policy and the 18+ framing are still needed. What drops away for now is the public listing: store screenshots and store discovery. The 4.2 "useful or unique" test still applies through 2.2, with less exposure than a public listing. Whether the App Privacy label must be completed for TestFlight-only distribution is not covered (verify).
* **Internal testers avoid review but are limited.** Internal TestFlight testers must be members of your App Store Connect team (up to 100; verify). Use them for every build, and send only selected builds to external testers.
* **Builds expire after 90 days (verify).** Testers lose the app unless a new build is uploaded. Plan a build at least every 80 days. EAS Update can change JavaScript between builds, but a new binary is still needed to reset the expiry and for native changes. A JavaScript change that alters behavior may count as a "significant update" under 2.2.
* **TestFlight gives you crash reports and tester feedback screenshots, which partly overlap the in-app report.** Keep the in-app report (text only, decision 25; K.6–K.7). Tell testers to use TestFlight feedback for app bugs and the in-app Report for bad AI answers. TestFlight crash reports cover testers who have not opted in to Crashlytics (decision 24).
* **The Gemini terms still apply.** A tester group in the EEA, Switzerland or the UK is covered by the paid-services-only rule. Limit external tester invitations, or tell those testers to use paid keys.

Android on the Play internal testing track (author decision 16). Problems (no Play testing-track document was fetched; the claims below are all "verify"):

* **The two platforms get different audience sizes.** The internal track is capped at 100 testers, added by email address or Google Group. TestFlight external testing allows far more. Plan the tester list per platform.
* **Play policies still apply on the internal track.** The AI-generated content policy (in-app reporting, research/play-ai-content.txt) and the photo permission policy (research/play-photo-permissions.txt) are not limited to production. Which Play Console declarations (privacy policy, Data safety, content rating, target audience) must be finished before an internal release is not in the fetched docs (verify).
* **Internal testing does not count toward a later production release.** If the developer account is a new personal account, Google's closed-test requirement for production still has to be met later (verify the current tester count and duration).
* **Build expiry differs.** Play internal builds do not expire like TestFlight builds, so the 80-day rebuild rule is iOS only. Keep both platforms on the same app version anyway, because Remote Config conditions use `app.version`.

What it gives the demonstrator: a controlled audience, faster iteration without public review, and a later public release once the local-model phase gives the app something first-party apps don't offer.

### J. Round 3 review findings

All seven lenses completed. Findings are deduplicated; the lenses that raised each one are in brackets. Decisions 1–16 are not re-opened except where a finding is recorded as an **objection**, with evidence. Round-4 status tags are in each heading where decisions 17–26 changed the finding.

#### Blockers

**J1. Blocker (resolved, K.2 and K.3): reinstall detection cannot fire on iOS (I.6).** [design, codebase-details, zoom-out, comprehension, implementation-readiness, inventive-catalyst]
* `pc.install` is a secure-store item. On iOS, secure-store data "will persist across app uninstallations" (research/expo-securestore.txt, Data persistence), so after a reinstall `pc.install` is still present, the check "`pc.install` missing while a key exists" is never true, and old consent records are reused. Item 32 required "a non-keychain flag". On Android everything is wiped, so the case does not arise.
* The same evidence says Keychain survival "is not guaranteed", so the app must also handle keys gone while the marker exists (show key entry, no error).
* Recommendation: store the install marker in storage that uninstall removes, for example `install.json` in `FileSystem.documentDirectory` or AsyncStorage. Inventive-catalyst would go further: keep only keys in secure store, and put settings and consent records in uninstall-wiped storage (consent records are not secrets). Round 4 adopted this, with a wipe instead of keeping the keys (decision 21). New defects in the round-4 sequence: L3.

**J2. Blocker (resolved by K.4; new gaps in L4): the I.4 load order cannot be built as written and can lock the app out.** [codebase-details, implementation-readiness, design, inventive-catalyst]
* Real-time updates fetch but do not activate; `activate()` makes the whole last-fetched config live and has no per-key option (research/rnfb-remote-config.txt, Fetch and activate; research/firebase-rc-realtime.txt, Selectively activate). "Apply `kill_switch` immediately, others on next cold start" cannot be done with Remote Config activation alone.
* Step 2 stops before the fetch (step 3) and the listener (step 4). Once `appDisabled: true` is active, the app never fetches again and never sees the switch lifted.
* Recommendation:
  * Always run steps 3–4. The disabled screen has a Retry that calls `fetchAndActivate`. Call `ensureInitialized()` before reading values.
  * Add an app-side session snapshot: at session start, copy the validated non-kill-switch values into memory. On `onConfigUpdate`, call `activate()` and re-read only `kill_switch` (filter with `updatedKeys`). Replace the snapshot only between sessions.
  * Define in-flight behavior: `appDisabled`, a disabled provider or a disabled model aborts the running stream and shows the message; `bannerMessage` waits for the session end.
  * Design nit: a 3 s timeout on a background fetch (Strategy 3) makes updates fail on slow networks; about 30 s is enough because the real-time listener delivers newer versions on connect (research/firebase-rc-realtime.txt).

**J3. Blocker (resolved by decision 23; residual risk in L11) (objection to dropping the signed manifest, Alternatives): Remote Config can redirect every tester's API key, photos and reports.** [design, zoom-out]
* `providers.baseUrl`, `providers.authStyle` and `report_channel.to` were remote values. Anyone who can publish a template (a compromised Firebase account, CI secret or console session) could send every installed app's requests, keys and photos to another host, or every report to another inbox, in one publish (research/rnfb-remote-config.txt; research/firebase-rc-templates.txt). The Alternatives reason for dropping signing ("not secret … the worst a forged client can do is read them") covers forged reads, not a malicious publish.
* Recommendation: build base URLs and auth style into the binary, or check every remote URL against a fixed host allowlist (`api.openai.com`, `api.anthropic.com`, `generativelanguage.googleapis.com`). Record the publisher-compromise threat in the Alternatives row. Decision 23 adopted the allowlist and in-code header style; the report address no longer exists (decision 22).

**J4. Blocker (resolved by decision 22) (objection to decision 14 on Android): the email report leaves the app, against Play's in-app reporting rule.** [comprehension, inventive-catalyst, zoom-out, structural]
* Play requires reporting "to developers without needing to exit the app" (research/play-ai-content.txt). `composeAsync` opens "a mail app intent for Android" (research/expo-mailcomposer.txt). Both fallbacks (share sheet, `mailto:`) also hand off to another app.
* Options were (a) treat the in-app Report screen as the flag and email as transport only, or (b) a minimal text-only endpoint. Decision 22 chose an in-app write to Firestore; decision 25 made it text only.

**J5. Blocker (resolved, K.3): the consent item exceeds the secure-store size limit (I.6).** [codebase-details, implementation-readiness, design, inventive-catalyst]
* The sample record is about 250–280 bytes as compact JSON. `current` plus 10 `history` records plus the wrapper is about 2.7–3 KB, above the roughly 2048 bytes some iOS releases rejected (research/expo-securestore.txt). A failed write blocks the send under the I.6 rules.
* Recommendation was to cap `history` at 4 or drop it. Round 4 moved consent to a plain file, which removes the limit. Zoom-out still recommends dropping `history` (L15).

#### Major

**J6. Major (partly addressed by K.2; fallback rule open, L8): "last good value" has nowhere to live, and validation per parameter breaks cross-references (I.3, I.4, I.6).** [design, implementation-readiness]
* After activation the previous activated value is gone; Remote Config keeps only fetched, active and default values (research/rnfb-remote-config.txt). `models` and `error_map` are far over 2 KB.
* `defaults`, `kill_switch.disabledModels` and `error_map` reference `models`, `providers` and error classes. If `models` fails and its old copy is kept, a new `defaults` can point at missing models.
* Recommendation: validate the whole set, all or nothing, keyed by `configVersion`, and fall back to the last good full set stored in a non-secure file (for example `documentDirectory/config-lastgood.json`).

**J7. Major (dropped by decision 19, except the `minSupportedAppVersion` comparison): schema compatibility is undefined and does not survive OTA updates (I.3, I.4).** [design, implementation-readiness]
* "A schema it knows" is not defined (exact match or range). `app.version` is the native version string (research/firebase-rc-condition-reference.txt), but schema readers are JavaScript shipped by EAS Update, so an OTA update or rollback changes what the app understands without changing `app.version`. A broken OTA bundle cannot be targeted by the kill switch.
* The first launch after a store upgrade activates values fetched by the old binary under the old `app.version`.
* Recommendation: each build declares `supportedSchemas: {min, max}` per parameter. Old-schema values stay as the default, new schema goes in a conditional value. Send `setCustomSignals` with the max schema and update ID and condition on the custom signal (research/rnfb-remote-config.txt exports `setCustomSignals`; condition support per research/firebase-rc-condition-reference.txt). Add `disabledUpdateIds[]` or `minRuntimeVersion` to `kill_switch`. Compare versions numerically (1.10.0 > 1.9.0), reject `-beta`/`-rc` suffixes in CI, and use `app.build` to tell same-version builds apart. Round 4: decision 19 makes compatibility the author's job; inventive-catalyst recommends CI validate each publish against the exported schema of every live build.

**J8. Major: rollback, version numbering and environments (I.5).** [design, codebase-details]
* A Firebase rollback republishes old content as a new version (research/firebase-rc-templates.txt), so `config_meta.configVersion` goes backwards and `lastConfigVersionSeen` and the consent record's `configVersion` become ambiguous. The 300-version history is a lifetime rolling cap.
* There is one client template per project, and limits are per project (research/firebase-rc-parameters.txt), so development, preview and production EAS profiles would publish into the same template.
* Recommendation: roll back by reverting in the repository and republishing with a higher `configVersion`; use Firebase rollback only in an emergency and then reconcile. CI fails on a console-originated version it has not seen. Use separate Firebase projects for development and production. Zoom-out: publishing from CI needs a Firebase service-account secret; one developer could validate in CI and publish by hand.

**J9. Major: duplicated sources of truth in I.3.** [design, structural, comprehension, zoom-out]
* `providers.consentTextVersion` and `consent_texts.version` both define the required consent version; the I.6 gate reads only the second.
* `kill_switch.disabledProviders` duplicates `providers.enabled`; `disabledModels` duplicates `models.status`.
* Three policy URLs: `providers.privacyFactsUrl`, `consent_texts.policyUrl`, `privacy_policy.url`.
* Two version gates: `kill_switch.minSupportedAppVersion` and per-model `minAppVersion`. Two version counters: `configVersion` and Firebase's template version.
* `appDisabledMessage` and `bannerMessage` are plain strings, while `error_message_overrides` is keyed by language.
* Recommendation: one field each, a written precedence rule, and a CI check that they agree.

**J10. Major: consent text delivered as remote data needs integrity and a clear re-prompt rule (I.3, I.6).** [design, inventive-catalyst, comprehension]
* The record stores only a version number. A console edit to `consent_texts.summary[]` without a version bump changes legal text silently, and I.5 allows emergency console edits.
* `privacyPolicyVersion` is stored but the gate ignores it; say whether a policy bump re-prompts.
* Recommendation: add `consentTextHash` to the record; CI fails if the text hash changes without a version bump, and rejects console edits to `consent_texts`. Inventive-catalyst alternative: ship consent texts in the binary and let Remote Config only raise the required version. Re-prompt heading: "<Provider>'s terms for PhotoCritic changed on <effectiveDate>", with changed bullets first.

**J11. Major: the error_map rule schema cannot express the Anthropic spend-limit 400 (I.3).** [design]
* That error has the same type as a malformed request and is told apart only by message text (item 13; research/anthropic-errors.txt). The rule schema matches only type, code, finishReason and stop_reason.
* Recommendation: add `messageContains` (plain substring; no remote regex, to avoid regex denial of service). Document rule order and first-match semantics. Zoom-out would keep the error map in code with fixture tests and correct it through EAS Update.

**J12. Major: I.3 `models` and `defaults` do not carry the request-shaping fields item 18 and item 22 require.** [structural, implementation-readiness]
* Missing: thinking/effort values, sampling-parameter policy, prompt order, fallback chain, cache policy. `conversation_policy` lacks backoff base and maximum total wait. `historyTurnsCap` has no value yet (D11).
* Recommendation: add them to `models` / `defaults`, or state in item 18 that adapter code owns them.

**J13. Major: React Native Firebase native setup is missing (I.1, D9, Phase 0).** [codebase-details, implementation-readiness]
* RNFB v27 is SPM-only on iOS and needs `expo-build-properties` `ios.useFrameworks: "dynamic"`; static frameworks or `disableSPM` make prebuild fail. It needs Xcode 26.2 or later, iOS 15+ and minSdk 24 (research/rnfb-getting-started.txt).
* Also to specify: `expo.ios.googleServicesFile` / `expo.android.googleServicesFile`; the `@react-native-firebase/app`, `/analytics`, `/crashlytics`, `/firestore` and `/app-check` config plugins and packages (round 4; `withoutAdIdSupport: true` on Analytics); whether the config files are committed or EAS file secrets; whether dynamic frameworks conflict with llama.rn or ExecuTorch in Phase 5 (verify).
* Bundled defaults: generate a `defaultConfig` object (or plist/XML) from the repository template at build time, choosing the value this version and platform would receive; CI fails on drift.

**J14. Major (resolved by decision 20): the Analytics peer dependency puts the Analytics SDK in the binary.** [design, codebase-details, zoom-out, inventive-catalyst]
* Analytics must be installed (research/rnfb-remote-config.txt, Prerequisites). Round 3 treated this as a cost and recommended a spike to run Remote Config without the Analytics native SDK and a CI check that blocks Analytics calls. Decision 20 makes Analytics wanted, so the spike is moot and the CI check becomes "Firebase logging and analytics calls only through the wrapper" (K.2, L10).
* Zoom-out and inventive-catalyst: for 100–300 testers, percentage rollouts give little signal (rollout monitoring needs Crashlytics or Analytics, research/firebase-rc-rollouts.txt, and under decision 24 covers only opted-in installs), rollback is a repository revert, and a startup fetch is enough.

**J15. Major (wording superseded, L9): Firebase data collection is incomplete in the Goals and is not disclosed before it starts.** [codebase-details, comprehension]
* Add Android's platform version, Android app ID, package name and SDK version, the Firebase user agent, and installation ID retention (deleted within 180 days of a delete call; research/firebase-installations.txt; research/firebase-privacy.txt).
* The first Remote Config fetch happens at startup, before any consent screen. Recommendation: one onboarding line before key entry. The round-3 example said "No photos or questions go to Firebase"; after decision 22 a report sends question and answer text, so use the L9 wording. Optionally delete the installation when the user resets the app.

**J16. Major: Apple 2.2 changes the TestFlight assumptions (I.8, decision 15).** [codebase-details, inventive-catalyst]
* "Any app submitted for beta distribution via TestFlight should be intended for public distribution and should comply with the App Review Guidelines" (research/apple-review-guidelines.txt, 2.2). Record the public release as planned later, not abandoned, and do not treat 4.2 as gone.
* "Significant updates … should be submitted to TestFlight App Review": review is not only for the first build, and behavior-changing EAS Updates may count.
* The 90-day expiry, 100-internal-tester cap and team-membership rule are not in the evidence (verify). Fixed in I.8 and item 26.

**J17. Major: Play testing claims are unverified, and prerequisites are missing (I.8, decision 16, Phase 4).** [codebase-details, implementation-readiness]
* No Play testing-track document was fetched. The 100-tester cap, "little review", no build expiry, closed-test requirement and Firebase App Distribution claims are all "verify".
* Prerequisites to list: Apple Developer enrollment; App Store Connect app record; `ios.config.usesNonExemptEncryption: false`; Beta App Review contact and notes with the reviewer key; Play developer account and app record; whether the first AAB is uploaded by hand (verify); Data safety and content rating before internal release (verify); EAS `autoIncrement`; who triggers the 80-day rebuild.
* Gating: Play's in-app reporting rule applies on the internal track, and external TestFlight needs consent and a privacy policy, so reporting and consent must ship in the first tester build, not at Phase 4 "polish".

**J18. Major (obsolete: email path removed by decision 22): reporting exposes more personal data than the brief states (I.7).** [comprehension, inventive-catalyst, zoom-out]
* Under email reporting the developer would have received the tester's name, email address and signature. Two points carry over to K.6: send only the reported question and answer plus the reason and technical metadata, show any earlier turns with a toggle to leave them out, and state a retention period.

**J19. Major (obsolete: no attachment or mail status after decisions 22 and 25): report attachment lifetime and status handling (I.7).** [all lenses]
* The attachment-lifetime and mail-status findings no longer apply. The carry-over is the report-status wording: never say "sent" before the server acknowledges (L5).

**J20. Major: kill-switch and minimum-version behavior is not specified for the user, and some flags fail open (I.3, I.4).** [comprehension, design]
* Specify: a full-screen block for `appDisabled`; the same with an Update button below `minSupportedAppVersion` (`updateUrl` per platform: TestFlight vs Play internal opt-in link); an inline notice with a fallback model for a disabled provider or model, including one that is currently selected. Ship default wording in the bundle, for example "PhotoCritic is paused while a problem is fixed. Your keys and settings are kept."
* If `kill_switch` fails validation the app keeps the last good value and fails open; state that this is intended.
* `report_policy.enabled = false` (round 3: `report_channel.enabled`) would remove the reporting Play requires. Allow it only together with `appDisabled`.

**J21. Major: consent write races and downgrade behavior (I.6).** [implementation-readiness]
* If the consent write rejects, block the send and show an error; read the record back before sending. Revoking during a stream aborts it. The consent check uses the session snapshot (J2). Round 4: these rules apply to `consent.json` (K.3).
* "Higher `schemaVersion` read-only" leaves an older build after an OTA rollback unable to re-consent. Define a downgrade write rule: write at your own schema and keep unknown fields.

**J22. Major: the round-3 design is sized for a public app, not for 100–300 testers.** [zoom-out]
* Candidates to cut: `error_message_overrides`; `privacy_policy` (fold into `consent_texts`); `config_meta.publishedAt`; `keyPrefixHint` (key formats undocumented, section F); `maxQuestionChars`, `connectTimeoutMs`, `retryableClasses[]` (retry policy belongs in code, item 23); `lowDataLongEdgePx` and `lowDataMode` (decide D7 first; an unused field is frozen into the schema); `models.maxBytes` and `maxLongEdgePx`; consent-record fields other than `{decision, consentTextVersion, recordedAt}`; `lastConfigVersionSeen` and `updatedAt`; the model-list filter in I.4 "Model selection". (The round-3 `report_channel.*` and `pc.install.installId` candidates are gone after round 4.)
* Merge the scheduled live CI canary into the offline benchmark harness and run it before each build, which avoids live keys in CI.
* Under test-track distribution, the reviewer-key runbook shrinks to one capped paid key for Beta App Review, and territory restriction becomes invitation control.

#### Nits

* **J23.** `device.country` conditions use the request IP (research/firebase-rc-condition-reference.txt); this answers item 28's open question about the region signal. It is spoofable with a VPN.
* **J24.** Log `lastFetchStatus`, `fetchTimeMillis`, `configVersion`, `ValueSource` per parameter and `onConfigUpdate` error codes in the local diagnostic ring buffer, and include `configVersion` in reports (codebase-details).
* **J25.** Explain the purpose of `lastConfigVersionSeen` and the reason `history[]` is capped, so I.6 can be read without item 32 (comprehension).
* **J26.** I.8 should use `bannerMessage` to warn testers when a TestFlight build is near expiry (comprehension).
* **J27.** `keyPrefixHint` depends on key-prefix detection, which section F marks unverified (comprehension, zoom-out).
* **J28.** Turn the 90-day expiry to use: a scheduled EAS build and submit about every 75 days also refreshes the bundled defaults and limits which old iOS builds the remote schema must support (inventive-catalyst).

#### Factual corrections made in place (round 3)

* I.3 and D13: parameter and template limits are in the evidence (1,000,000 characters across all values, 3,000 parameters, 2,000 conditions); only the per-value limit stays "verify".
* I.2 and I.5: 300 template versions is a lifetime cap; rollout monitoring needs Analytics or Crashlytics.
* I.4: `setDefaults` replaced by `remoteConfig.defaultConfig`; the 3 s timeout marked as the author's choice.
* I.5: App Check cannot protect Remote Config (not a supported service).
* I.1: the Expo guide does not name Remote Config as unsupported in the JS SDK; Android data fields and installation-ID retention added.
* I.7: Android status wording; Gmail-on-iOS and `mailto:` attachment claims marked "verify"; `isHtml` quote.
* I.8: Apple 2.2 wording quoted (the earlier "verify the exact wording" removed); 4.2 risk reduced, not removed; TestFlight and Play limits marked "verify"; Firebase App Distribution marked not chosen.
* Item 15: the `AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY` deprecation claim marked doubtful.
* Section H: round-3 confirmed, corrected and not-covered entries added.

#### Mechanical contradiction fixes made in place (round 3)

1. Header "Mode": "round 2 evidence pass" → round 3 design review. Reviewer Status table: Round 3 column added. Notes: round-3 evidence note added.
2. Overview: rewritten for decisions 10–16 (no free-tier pointer, re-send per turn, secure-store consent records, Firebase Remote Config, email reports, TestFlight and Play internal). The free-tier-pointer risk bullet and "stores nothing apart from settings and keys" were removed. "Store copy must still present it as a complete app" was replaced with the 2.2 TestFlight wording.
3. Item 1: "A public store release still carries the full compliance load" → "Any release, including TestFlight external testing and the Play internal track … still carries most of the compliance load". 4.2 bullet notes that 2.2 applies the guidelines to TestFlight.
4. Item 5: "Blocker (reopened)" → "Blocker (resolved, D10)", with a round-3 line.
5. Item 8: "This conflicts with the no-backend non-goal. See item 27." → addressed by decision 14, Android open (J4).
6. Item 17: retitled "(resolved, D13)"; "signed," removed from the manifest recommendation; resolution line added; "store binary" → "new binary".
7. Item 18: `freeTierEligible` removed from the capability fields; pointer to J12 added.
8. Item 21: note that a pasted unbilled Gemini key still behaves as unbilled after decision 10.
9. Item 22: "bundles in the manifest" → "bundles in Remote Config"; the bullet "The Gemini default must be free-tier eligible, so that users arriving from the pointer do not fail on the first send" replaced with a decision-10-consistent bullet.
10. Item 25: retitled "(partly resolved, D10)"; "Resolved: option (a), decision 10" added; territory bullet notes invitation control on test tracks.
11. Item 26: retitled "(resolved, D15/D16)"; "Play open or closed testing" → "Play testing tracks"; "and much of the reviewer-key runbook" removed (I.8 says the key is still needed); "If a public release is required … Decision needed (D15)" → "If a public release follows …" plus resolution line.
12. Item 27: retitled; "Options: a minimal report endpoint, or a hosted form … (D14)" → superseded by decision 14 / I.7, with the Android conflict pointer.
13. Item 28: "its author receives nothing" → "… unless the user sends a report (I.7)"; "the app keeps nothing" → "nothing beyond keys, settings and consent records"; the "my project has billing enabled" self-declaration marked dropped; region-signal evidence added.
14. Item 32: retitled "(resolved, D12)"; "first public release … staged in Phase 4" → "first tester release … Phase 1"; "keys survive on iOS but consent does not" corrected (Keychain-stored consent also survives).
15. Item 33: retitled "(resolved, D13)"; "`unknown`" aligned with "something went wrong"; implementation pointer added.
16. Item 34: note that the Gemini free-tier column no longer drives the default.
17. Section F: row 2 "deep-link to AI Studio" → "deep-link to each provider's key page"; row 3 "signed remote manifest" → "Firebase Remote Config (signing dropped)"; row 5 notes test-track status; row 6 "the same manifest as the privacy policy" → Remote Config `consent_texts` / `privacy_policy`.
18. Section order: H moved before I, so A–J run in order. I heading "(author decisions 10–13)" → "(10–16)"; I source list completed.
19. I.3 `imageDetail`: Gemini "`media_resolution`" → "`resolution`" (field name depends on D3), matching items 11 and 34.
20. Goals: "Privacy policy in the app and in metadata" qualified for test-track distribution; Firebase data list completed; distribution goal added. Non-Goals: public listing and Play production added; "Firebase Remote Config (read-only)" notes the installation ID; "Anthropic automatic caching stays open under D11" → decided by the Phase 0 cache spike.
21. Alternatives: "Public App Store release" row → "TestFlight and Play internal testing only", "Avoids the 4.2 risk" → "Reduces"; "Signed manifest" row records the publisher-compromise threat (J3); "Local-first" row "if a public release is kept" → "if a public release follows"; Firebase App Distribution row added as not chosen.
22. Decisions 5, 7 and 15: author text kept, superseding notes appended (pointer removed by decision 10; public release superseded by decisions 15–16; Android channel closed by decision 16).
23. Gate table: D10 "still to set with D15" → "still to set (tester invitations, I.8)"; D11 adds the caching on/off detail; D13 open detail narrowed to the per-value limit; D15 row covers both platforms; new open rows D17–D19 from J.
24. Staging: Phase 0 "Settle D3–D15" → "Settle D3–D9 and D17–D19"; Phase 1 renamed; Phase 2 "Start only after D10 is decided" removed; Phase 4 retitled "polish and tester release", "(decision 15)" → "(decisions 15–16)", and reporting/consent moved into the first tester build; Phase 5 "new store binary" → "new binary".

### K. Round 4 decisions: local-only user data, config fetched at every start, opt-in Analytics and Crashlytics, in-app text reports (author decisions 17–26)

This section supersedes earlier text as mapped below. Where they disagree, K wins; where K itself has open defects, section L records them.

| Earlier text | Superseded by | What still applies |
| --- | --- | --- |
| I.1 "Analytics collection must be turned off" | Decisions 20, 24 | Remote Config and Installations data collection; RNFB setup |
| I.3 `providers.baseUrl`, `authStyle` | Decision 23 | `baseUrl` allowed only within the in-code host allowlist; `authStyle` removed |
| I.3 `report_channel` | Decisions 22, 23, 25 | Replaced by `report_policy` (K.6–K.7) |
| I.3 per-value `schema`, `app.version` schema conditions; J7 | Decision 19 | Validation; `minSupportedAppVersion` comparison |
| I.4 load order, fetch interval, "keep the last good value" | K.2, K.4 | Bundled defaults; validation; model selection. Fallback rule open (L8) |
| I.5 "App Check is not used" | Decision 22 | Not applicable to Remote Config |
| I.6 storage layout, reinstall rule "keep the keys", 2 KB cap | Decisions 17, 21; K.2, K.3 | Record format; send gate; revocation; J21 write rules |
| I.6 "consent records never leave the device" | Open (L12) | Holds unless K.5 `consent_recorded` stays |
| I.7 email reporting | Decisions 22, 25 | Redaction rule; no key or headers in reports |
| I.8 "keep the email report" | Decision 22 | In-app report for AI answers; TestFlight feedback for bugs |
| J1, J2, J3, J4, J5, J14 | K.2, K.3, K.4; decisions 20, 22, 23 | New defects: L3, L4, L11 |
| J6 | Partly (K.2) | Whole-set validation still recommended (L8) |
| J15 example wording | L9 | Data-collection list; onboarding notice |
| J18, J19 | Decisions 22, 25 | Minimal report content; no "sent" before acknowledgement |
| J20, J21 | — | Still apply (`report_policy.enabled`; `consent.json`) |
| Decisions 12, 13 (report channel), 14, 18 | Decisions 17, 21, 22, 23, 25 | Notes appended in the decisions list |

#### K.1 Author decisions

* **17. Losing settings and consent on reinstall is acceptable.** Settings, preferences and consent records do not need to survive uninstall.
* **18. Firebase holds application configuration only:** provider settings, the model list, limits, the error map, consent texts, the privacy policy text and version, the kill switch and the report address. No user data, keys, photos, questions or answers are stored in Firebase. (Amended by decision 22: plus the text reports the user chooses to send. No report address after decision 23. I.3 holds the privacy policy URL and version, not its text.)
* **19. The app fetches configuration at every start.** Backward compatibility of the configuration is the author's responsibility; the app does not carry schema-migration logic for remote values.
* **20. The app uses Firebase Analytics and Crashlytics.** This replaces the round-1 non-goal "no analytics by default". (Opt-in, off by default for everyone: decision 24.)
* **21. API keys stay on the device only and need not survive uninstall.**
* Decisions 22–26 are in K.6 and K.7, where they were made.

#### K.2 Problems with these decisions

* **iOS keeps Keychain items after uninstall, so decision 21 needs code, not just a storage choice.** `expo-secure-store` writes to the Keychain (research/expo-securestore.txt). To make keys disappear on reinstall:
  * write an install marker to a plain file in the app's own storage, which iOS and Android delete on uninstall;
  * at launch, if the marker is missing, delete every `pc.apikey.*` item from secure store, then write the marker.
  * This resolves J1: the marker is no longer a Keychain item. The sequence has defects (L3): secure store has no list API, so the wipe deletes a fixed, permanent list of names; a read error is not "missing"; a build without the marker followed by one with it wipes every tester's keys; and backups can restore the plain files.
* **"Fetch at every start" and Firebase's throttling.** The recommended production minimum fetch interval is 12 hours, and Firebase may throttle frequent fetches (research/rnfb-remote-config.txt). For 100–300 testers, set `minimumFetchIntervalMillis` to 0 and fetch at each cold start; the loading guide rates the throttling risk of a fetch per launch as low (research/firebase-rc-loading.txt). Treat a `throttled` or failed fetch as "use the last activated values", never as an error shown to the user. Starting the app is not blocked on the fetch beyond a short timeout (about 3 seconds). L4 separates this first-screen timeout from the native `fetchTimeoutMillis`.
* **"I will take care of backward compatibility" still leaves one rule in the app.** A bad publish (malformed JSON, a missing field) must not crash every installed copy at once. The app keeps two things from I.4: validation of each value against a schema, and the bundled defaults as a fallback. It drops version-negotiation and migration logic. (Whether the fallback is per value or for the whole set is open, L8.)
* **Provider settings in Firebase keep the J3 risk.** If `baseUrl` and the key header style are remote, one wrong publish sends every tester's API key to another host. The cheapest guard: the app keeps a short list of allowed hosts per provider in code (for example `api.openai.com`, `api.anthropic.com`, `generativelanguage.googleapis.com`) and refuses a remote `baseUrl` outside it. Model lists, limits, texts and the error map stay fully remote. Accepted as decision 23 (K.6); matching and redirect rules in L11.
* **Analytics and Crashlytics can leak exactly what the app promises not to keep.**
  * Crashlytics collects "any custom keys, logs, and free-text user IDs that developers attach to crash reports", and breadcrumb logs when used with Analytics (research/firebase-ios-data-collection.txt, `FirebaseCrashlytics`). Never put an API key, a question, an answer, an image path or a provider error body into a log, custom key, event parameter or user ID. Enforce this with one logging wrapper and a lint rule that bans direct calls to the Firebase logging APIs. Automatically captured errors bypass the lint rule (L10).
  * Analytics events go to Google. The privacy policy, the consent flow, the App Privacy answers and the Play Data safety form must list analytics and crash data. The exact fields Analytics collects are not in the fetched docs (verify against Firebase's Analytics data-collection page). The full inventory is in L9.
  * Turn off advertising-ID collection: the RNFB analytics config plugin option `withoutAdIdSupport: true` (research/rnfb-getting-started.txt; iOS only). Android needs its own setting (verify). With no advertising ID and no cross-app tracking, Apple's App Tracking Transparency prompt should not be needed (verify against Apple 5.1.2 and the ATT rules).
  * For testers in the EEA, Switzerland or the UK, analytics usually needs opt-in consent. Add a "Share usage and crash data" switch on first launch and in Settings, and call `setAnalyticsCollectionEnabled` and `setCrashlyticsCollectionEnabled` from it. (Resolved by decision 24: off for every user.)
  * Crashlytics on iOS needs debug symbols (dSYM) uploaded from each EAS build, or crash reports are unreadable. Add this to the build profile (whether the RNFB Crashlytics plugin does this on EAS: verify).
* **Decision 20 resolves J14.** The Analytics SDK is now wanted, so the peer dependency is no longer a cost. Remote Config rollouts can be monitored with Crashlytics, for opted-in installs only (decision 24).

#### K.3 Local storage (replaces the I.6 layout)

| Item | Where | Survives uninstall | Contents |
| --- | --- | --- | --- |
| `pc.apikey.<provider>` | `expo-secure-store`, `WHEN_UNLOCKED_THIS_DEVICE_ONLY` | No (wiped at first launch after reinstall, K.2) | API key only |
| `install.json` | App document directory (plain file) | No, unless restored from a backup (L3) | `installedAt`, `appVersionAtInstall` |
| `settings.json` | App document directory | No, unless restored from a backup (L3) | `schemaVersion`, selected provider, model and tier per provider, low-data mode, analytics opt-in, `onboardingCompletedAt`, `updatedAt` |
| `consent.json` | App document directory | No, unless restored from a backup (L3) | per provider: `current` record and up to 10 earlier records (the I.6 record format, unchanged) |

* Moving settings and consent out of secure store removes the 2 KB limit (resolves J5) and matches decision 17. These records are not secret. On iOS, app files are protected by the device's file encryption (verify the protection class); on Android, app-private storage is not readable by other apps.
* Write files atomically: write to a temporary file, then rename, so a crash mid-write cannot leave a half-written consent record. (Whether expo-file-system's move replaces an existing file atomically is not covered; L5 of the findings gives a two-slot alternative and recovery rules.)
* The I.6 consent rules stay: no send without a `granted` record whose `consentTextVersion` is at least the current one from Remote Config.
* Missing from this table: the sent-report list and daily counter needed by K.6–K.7, and the analytics consent record (L12, L13).

#### K.4 Configuration load order (replaces I.4 steps 1–4; resolves J2; gaps in L4)

1. At cold start, read the values activated in the previous run (or the bundled defaults on first launch) and take a **session snapshot**: an in-memory copy the UI reads for the whole session.
2. Call `fetchAndActivate` with a 3-second timeout. If it succeeds before the first screen is shown, rebuild the snapshot from the new values. If it is slower, the new values are used from the next cold start.
3. Evaluate `kill_switch` from the snapshot. A disabled app still runs step 2 on each start, so lifting the kill switch takes effect at the next start.
4. Subscribe to `onConfigUpdate`. When `kill_switch` changes, activate and apply the kill switch at once (showing the disabled screen ends the session anyway). Other changes wait for the next cold start. `activate()` applies all fetched values together (research/rnfb-remote-config.txt), which is why the UI reads the snapshot, not live values.

#### K.5 What to measure (Analytics events)

Events are sent only while "Share usage and crash data" is on (decision 24). Events carry only the parameters listed. No text, no images, no keys. Event naming and the reserved name `session_start` are open (L12).

| Event | Parameters |
| --- | --- |
| `session_start` | `provider`, `model`, `tier`, `config_version` |
| `image_prepared` | `source` (camera or gallery), `input_format`, `prepare_ms`, `sent_long_edge_px`, `sent_bytes` |
| `question_sent` | `provider`, `model`, `turn_index`, `starter_prompt` (identify, explain, extract, or none) |
| `answer_received` | `provider`, `model`, `time_to_first_token_ms`, `total_ms`, `input_tokens`, `output_tokens`, `cached_tokens`, `stop_reason` |
| `request_failed` | `provider`, `model`, `error_class`, `http_status`, `retry_count` |
| `consent_recorded` | `provider`, `decision`, `consent_text_version` |
| `report_submitted` (round 3 name `report_opened`) | `provider`, `model`, `category`, `result` (sent, queued, failed, limit reached) |
| `config_fetch` | `result` (success, throttled, failed, timeout), `config_version`, `ms` |

These give the demonstrator supporting field measures from testers who opt in: time to first answer per provider and model, error rates per class, and token use per turn. The offline benchmark harness stays the primary measure, including OCR accuracy, which analytics cannot measure without collecting text (L7).

#### K.6 Round 4 follow-up: reports stored in Firestore, host allowlist, analytics opt-in for everyone (author decisions 22–24)

* **22. AI-output reports are stored in Firebase (Cloud Firestore), sent from inside the app.** This replaces the email path in I.7 and resolves D17 and J4: the user reports without leaving the app, which is what Play's AI-content policy asks for (research/play-ai-content.txt).
* **23. D18 accepted as recommended.** Provider `baseUrl` values from Remote Config must match a per-provider host allowlist kept in code (`api.openai.com`, `api.anthropic.com`, `generativelanguage.googleapis.com`); the key header style is set in code per provider, not remotely. A remote value outside the allowlist is ignored and logged as a config validation failure. With reports in Firestore, the report email address is no longer used.
* **24. D20: analytics and crash reporting are opt-in for every user.** The "Share usage and crash data" switch starts off on every device. `setAnalyticsCollectionEnabled(false)` and `setCrashlyticsCollectionEnabled(false)` are the build defaults, turned on only after the user agrees. Crashes before consent are not reported, so early crash data covers only users who opted in. (Note: those two are runtime calls; the build-time defaults are `firebase.json` keys, L9.)

Problems with decision 22:

* **It amends decision 18 ("Firebase holds configuration only, no user data").** Reports contain the user's comment, the reported question and the AI answer (the photo was removed by decision 25). Decision 18 should read "configuration, plus reports the user chooses to send". The privacy policy, the App Privacy answers and the Play Data safety form must then list user content as collected when a report is sent, with the purpose (reviewing AI output) and the retention period.
* **Anyone with the app can write to the database.** There is no sign-in, so the database must be protected by:
  * Security rules that allow only `create` on `reports`, never read, update, list or delete from the app, and that check every field's type and size (research/firestore-rules-conditions.txt shows `request.resource` checks; rules cannot loop, so the `conversation` array must become fixed fields, L2);
  * Firebase App Check enforced on Firestore (App Attest on iOS, research/app-check-firestore.txt; Play Integrity on Android, research/firebase-app-check.txt), which "prevents some, but not all, abuse" (research/firebase-app-check.txt);
  * a client-side limit (for example 10 reports per day per install) so one user cannot flood the free quota by accident. It is not a security control (L6).
* **Where the image goes decided the price plan (resolved by decision 25: no image).** Cloud Storage for Firebase requires the Blaze plan (research/storage-faq-blaze.txt) and Cloud Functions also need Blaze (research/firebase-pricing-plans.txt). Firestore has a free quota of 1 GiB stored and 20,000 writes per day, and a 1 MiB limit per document (research/firestore-quotas.txt). The round-4 options (base64 image in the document on Spark, or Blaze with Cloud Storage and a notifying Cloud Function) are recorded in Alternatives.
* **Without Cloud Functions, nothing tells you a report arrived.** You check the Firestore console. A weekly check is the realistic rhythm for a tester group; say so in the tester instructions.
* **Users cannot delete a report after sending it,** because the app has no read or delete rights. Show the `reportId` after sending, and let the privacy policy say how to request deletion with the report ID. Decision 23 removed the report address, so a support contact must be defined (L13).
* **Retention.** Add an `expireAt` field (for example 90 days after creation) and a Firestore TTL policy on it. TTL deletion typically happens within 24 hours after expiry (research/firestore-ttl.txt). State the 90 days in the privacy policy. (Objection: TTL deletes need billing, so this does not work on Spark, L1. A client-set `expireAt` can also be omitted or set far ahead, L2.)
* **Offline.** If the device is offline, the Firestore SDK queues the write. Tell the user "Report saved; it will be sent when you are back online" rather than "sent". Persistence is on by default on native platforms (research/rnfb-firestore.txt, Offline Capabilities), so queued writes are kept in a local database; survival across an app kill still needs a test. The same cache keeps report text on the device (L5).

Report document (`reports/{reportId}`), final after decisions 25 and 26:

| Field | Type | Notes |
| --- | --- | --- |
| `reportId` | string (UUID) | Also the document ID |
| `createdAt` | server timestamp | Rules require `request.time` (verify syntax) |
| `expireAt` | timestamp | `createdAt` + 90 days; TTL field (L1, L2) |
| `appVersion`, `platform`, `osVersion`, `locale` | strings | |
| `configVersion` | number | `config_meta.configVersion` from the session snapshot |
| `provider`, `requestedModel`, `servedModel`, `providerRequestId` | strings | The request ID is visible only in the user's own provider account under BYOK (L14) |
| `imageSettings` | map | `longEdgePx`, `detail`, `bytes`; describes what the provider received |
| `conversation` | array of `{role, text}` | Capped by `report_policy.maxConversationChars`; rules cannot check array elements (L2) |
| `reportedTurnIndex` | number | Which answer was reported |
| `reason` | string | User's free text, at most 1,000 characters |
| `category` | string | `offensive`, `wrong`, `unsafe`, `other` |
| `errorClass`, `errorDetails` | strings, optional | Redacted, as in "show details" |
| `instanceId` | string, optional | Present only when sharing is on (decision 26) |

Never included: API keys, request headers, any photo (decision 25), EXIF data, device identifiers other than the Analytics app instance ID when the user has opted in (decision 26).

Remote Config `report_channel` becomes `report_policy`: `enabled`, `maxConversationChars`, `dailyLimitPerInstall`, `retentionDays` (used only for the text shown to users; the TTL is set server-side). The round-4 `includeImageDefault` and `maxImageBytes` fields were removed by decision 25.

#### K.7 Round 4 follow-up: text-only reports, Analytics app instance ID as the user ID (author decisions 25–26)

* **25. Reports carry no photo, only text.** The `imageJpegBase64` field, the image re-encoding step, `includeImageDefault` and `maxImageBytes` are removed from K.6. With no image, Cloud Storage is not needed, a report is a few KB, the free Spark plan's 1 GiB holds hundreds of thousands of reports, and D21 is resolved: stay on Spark. The privacy disclosures shrink to "text you write in a report, plus the reported question and answer". (Objection: TTL deletion is not available without billing, L1.)
* **26. At startup, the app reads the Google Analytics app instance ID and uses it as the user ID** for Analytics (`setUserId`), Crashlytics (`setUserId`) and the `instanceId` field of a report. (Amended and accepted in the decisions list: read only after the user opts in, per decision 24.)

Problems with decision 25:

* **"Just the text message" needs one clarification.** The design keeps the reported question and AI answer text (capped by `maxConversationChars`) and drops only the photo; decision 25 as recorded confirms this.
* **Without the photo, an answer about the photo can't be checked.** Ask in the report form "Describe what was in the photo, if it matters", and keep `provider`, `servedModel` and structured image metadata so you can reproduce a similar request. Under BYOK only the user can look up `providerRequestId` (L14).

Problems with decision 26:

* **It identifies an app install, not a person.** The app instance ID changes on reinstall, on "clear app data" on Android, and when `resetAnalyticsData` is called (verify). The same person on an iPhone and an Android phone has two IDs. Use it as "install ID", not "user ID", in the privacy policy and in analysis.
* **It conflicts with decision 24 (opt-in, off by default).** With Analytics collection disabled, the SDK may not provide an app instance ID (verify `getAppInstanceId` behavior; not in the fetched evidence). Therefore:
  * read the ID only after the user turns on "Share usage and crash data", not unconditionally at startup;
  * reports from users who have not opted in carry no `instanceId`;
  * when the user turns sharing off, call `setUserId(null)` and stop attaching the ID.
* **Setting the instance ID as the Analytics user ID adds nothing to Analytics.** Analytics already groups events by app instance. The value is in Crashlytics and in reports: it lets you line up a report with the crash and event history of the same install.
* **Linking reports to the analytics ID makes report text "linked to the user" for Apple's privacy label.** The App Privacy answers then list Identifiers (Device ID or User ID) and User Content as linked, and the Play Data safety form lists the device or other ID as collected. The privacy policy must say that reports from users who share usage data can be matched to that install's usage and crash data.
* **It helps with deletion requests.** Show the ID in Settings as "Support ID" next to each sent report ID, so a user can ask for both their reports and their analytics data to be deleted. Analytics user deletion is done through Google Analytics' user-deletion feature (verify), and report deletion through the Firestore console. Deleting the Firebase installation ID does not delete Analytics data, because Analytics "uses its own form of ID" (research/firebase-installations.txt).

### L. Round 4 review findings

All seven lenses completed. Findings are deduplicated; the lenses that raised each one are in brackets. Decisions 1–26 are not re-opened except where a finding is recorded as an **objection**, with evidence. Four Blockers, sixteen Majors, ten Nits.

#### Blockers

**L1. Blocker (objection to decision 25's "stay on Spark" together with the 90-day TTL in decision 22): Firestore TTL does not delete anything on the free plan.** [design, codebase-details; confirmed by the synthesizer]
* research/firestore-quotas.txt, Free quota: "The following operations and features don't include free usage. You must enable billing to use these features: TTL deletes …". research/firestore-ttl.txt: "TTL delete operations count towards your document delete costs." Whether a TTL policy can even be configured on Spark is not covered (verify).
* The privacy policy, `report_policy.retentionDays` and the report screen would promise 90-day deletion that never happens. Decision 25's "D21 is resolved: stay on Spark" and decision 22's "90-day TTL" cannot both hold.
* Options for the author (D21 reopened):
  * (a) Blaze with a budget alert. TTL deletes at tester volume cost close to nothing; Cloud Storage and Functions stay unused. Watch `ttl_deletion_count` (research/firestore-ttl.txt, Monitor TTL deletions).
  * (b) Stay on Spark and purge by hand: a scheduled Admin SDK script on the developer's machine or CI deletes reports older than N days, which counts against the free 20,000 deletes per day (research/firestore-quotas.txt). Server libraries bypass rules (research/firestore-rules-conditions.txt). The policy then says "deleted within N days" and names who runs the job.
  * (c) Promise only manual deletion on request.
* Whichever is chosen, keep a timestamp field to query or expire on, and record the choice in Alternatives.

**L2. Blocker: the report schema cannot be enforced by security rules as written (K.6 table).** [codebase-details, implementation-readiness, design, inventive-catalyst]
* Rules functions "cannot execute loops", and a request evaluates at most 1,000 expressions (research/firestore-rules-conditions.txt; research/firestore-quotas.txt). `conversation: array of {role, text}` cannot be type- and size-checked per element, so a forged client can store arbitrary nested data up to 1 MiB per document.
* `expireAt` is set by the client, because it cannot compute "server time + 90 days". A client can omit it (an absent or null TTL field disables expiry for that document, research/firestore-ttl.txt) or set it decades ahead.
* Recommendation:
  * Replace `conversation` with fixed string fields (`reportedQuestion`, `reportedAnswer`, optional `earlierTurnsText`), each with a `size()` cap; or cap the array at a small fixed length and check each index.
  * Require `keys().hasOnly([...])` and `hasAll([...])`, `reportId` equal to the document ID and matching a UUID pattern, `category in [...]`, `createdAt == request.time`, and either `expireAt` within request.time + 89 to 91 days, or drop `expireAt` and put the TTL policy on `createdAt` with a 90-day expiration offset (research/firestore-ttl.txt, Expiration offset). Syntax for `request.time`, `duration`, `hasOnly` and `size()` is not in the fetched excerpt (verify).
  * A second write to an existing ID evaluates as an update and is denied under create-only rules; forbid `merge`.
  * Rules cannot read Remote Config, so their caps are constants. CI checks that `report_policy.maxConversationChars` and the reason cap are at or below the rule limits, and generates the client validator and the rules limits from one schema file (structural).
  * Firestore rules emulator tests in CI (`connectFirestoreEmulator`, research/rnfb-firestore.txt; the rules-unit-testing harness is not in the evidence, verify): valid create allowed; second set on same ID, read, list, update, delete, extra or missing key, oversize field, wrong type, bad category, `createdAt` not `request.time`, `expireAt` out of range, ID mismatch all denied.

**L3. Blocker: the reinstall key wipe can delete keys on a normal launch, and backups defeat it (K.2, K.3).** [implementation-readiness, design, codebase-details, inventive-catalyst, comprehension]
* Sequence. (1) Read `install.json`. (2) Only on a definite "file does not exist", call `deleteItemAsync` for each key name. (3) Then write the marker. A read error (I/O error, or iOS file protection before first unlock on a background or prewarmed launch; verify) must skip the wipe for this launch. A corrupt but present marker counts as present. Marker-first ordering is forbidden. A crash between delete and marker write is safe because delete is idempotent. If a delete rejects ("rejects if the value can't be deleted", research/expo-securestore.txt), do not write the marker and treat keys as absent for this session.
* Enumeration. expo-secure-store has no list method (research/expo-securestore.txt documents get, set, delete, isAvailable). The wipe deletes a fixed, permanent list (`pc.apikey.openai`, `pc.apikey.anthropic`, `pc.apikey.gemini`, plus every name ever added), with the same `keychainService` as the writes.
* Upgrade versus reinstall. Any tester who installed a build without the marker loses their keys on the update that adds it. The marker logic must ship in the first build installed on any non-developer device. Defensive rule: if `install.json` is missing but `settings.json` or `consent.json` exists, treat it as an upgrade and do not wipe. The marker name and directory are frozen permanently.
* Write failure loop. If the marker write fails (disk full), every launch wipes again. Write, read back, and show an error instead of looping.
* Gating. Every secure-store read and the onboarding "has key?" check await the wipe.
* Backups. Android Auto Backup "automatically backs up a user's data" for API 23+ apps, and only SecureStore is excluded automatically (research/expo-securestore.txt, Android Auto Backup). `install.json`, `settings.json` (including the analytics opt-in) and `consent.json` are likely restored on reinstall or a new device (restore behavior for files: verify), the keys are not, the wipe does not run, and old consent and opt-in come back without being asked again. iOS backups carry the Documents directory (verify) while `THIS_DEVICE_ONLY` items are "not migrated to a new device when restoring from a backup" (research/expo-securestore.txt).
* Options: (a) exclude the files with custom backup rules; a custom configuration turns off SecureStore's automatic exclusion, so it must also contain `<exclude domain="sharedpref" path="SecureStore"/>` (research/expo-securestore.txt); on iOS set the exclude-from-backup attribute (Expo support: verify). (b) Inventive-catalyst: bind the marker to the Keychain, with a random nonce stored in both `install.json` and a secure-store item `pc.install.nonce`. Matching nonces mean the same install; a Keychain nonce with no file means a reinstall (wipe); a file with no or a different Keychain nonce means restored from backup (reset consent and opt-in, show key entry). (c) Accept restore, correct the K.3 column, and re-ask the analytics opt-in after a restore.
* Tests: upgrade keeps keys; pre-marker upgrade does not wipe; crash between delete and marker write; unreadable marker; marker write failure; iOS reinstall on a physical device; Android backup restore; iOS new-device restore; keys missing while marker present shows key entry without an error.

**L4. Blocker: the K.4 timeout has two incompatible readings, and a late fetch can miss a kill switch for the whole session.** [implementation-readiness, design, codebase-details, inventive-catalyst, zoom-out]
* If "3-second timeout" means `fetchTimeoutMillis: 3000`, the fetch fails at 3 s and nothing is stored, so "the new values are used from the next cold start" is false and slow networks never update (J2 nit). If it means a JavaScript race, the native fetch continues (default `fetchTimeoutMillis` 60 s, research/rnfb-remote-config.txt) and activates mid-session.
* A late `fetchAndActivate` makes the device's cached version current, so the real-time listener gets no invalidation (research/firebase-rc-realtime.txt). A kill switch published before this start but fetched late is then never applied until the next start, which on iOS can be days.
* Recommendation:
  * Step 0: `ensureInitialized()`, then `activate()` any values fetched by the listener in the previous run, then build the snapshot.
  * Set `fetchTimeoutMillis` to about 30 s. Race only the first screen against 3 s, holding the splash screen (`expo-splash-screen`, not yet in the dependency list); say the splash can last up to 3 s on every cold start, offline included.
  * After a late completion and on every `onConfigUpdate`: activate, re-validate `kill_switch` alone, and apply it by field (`appDisabled` or below `minSupportedAppVersion`: abort the stream and show the block; a disabled provider or model in use: abort and show an inline notice with a fallback chosen from models present in the snapshot; `bannerMessage`: wait). Never rebuild the snapshot from these paths.
  * Ban `getValue`/`getAll` outside the snapshot builder with a lint rule.
  * Throttling with interval 0: catch the rejection, check `lastFetchStatus === 'throttled'` (research/rnfb-remote-config.txt), do not retry this launch, log it locally.
  * Name the two lifetimes: "config snapshot" (process) and "conversation" (one photo, item 20). Optionally rebuild the snapshot on foreground when no conversation is open.
* Alternatives raised: zoom-out would drop `onConfigUpdate` entirely, since a kill switch at the next start is enough for testers; inventive-catalyst would keep the listener, whose on-connect version check gives the same freshness with a longer fetch interval. Either is workable once the late-activation rule above is written.

#### Major

**L5. Major: report delivery states and the Firestore on-device cache (K.6 Offline).** [design, codebase-details, implementation-readiness, comprehension]
* Persistence is on by default (research/rnfb-firestore.txt), so report text stays in the Firestore cache on the device, against "the app stores nothing beyond keys, settings and consent records". Choose `persistence: false` (queue kept in memory, lost on kill) or keep it and disclose it; or clear it after `waitForPendingWrites` with `terminate` and `clearPersistence`.
* The write promise resolves only on server acknowledgement. Do not await it before showing a state. Race it against a few seconds; on timeout show "Saved on this phone. It will be sent when you're back online. Report ID: … [Copy]"; on acknowledgement "Report sent"; on `permission-denied` (App Check or rules) "The report could not be sent" with Copy; on the daily limit "You can send more reports tomorrow". A retried create after a lost acknowledgement may be denied as an update (verify); treat `permission-denied` on a known report ID as possibly delivered.
* Atomic file writes (K.3): whether expo-file-system's move replaces an existing file atomically is not covered (verify). If not, use two slots (`consent.a.json`, `consent.b.json`) with a sequence number and checksum, read the newest valid one. Serialize writes through one queue per file. At startup promote or delete stray `.tmp` files. A corrupt file is renamed aside; settings fall back to defaults, consent to "none" (re-prompt), and the analytics opt-in to off.

**L6. Major: abuse of the unauthenticated write path on Spark (K.6).** [design, codebase-details, inventive-catalyst, zoom-out]
* The per-install daily limit is client-only and resets on reinstall. Rules cannot enforce it without an identity. App Check "prevents some, but not all, abuse" (research/firebase-app-check.txt), and tokens are replayable until they expire.
* On Spark, exceeding a no-cost quota can shut the product off "for the remainder of that month" for all apps in the project (research/firebase-pricing-plans.txt), while research/firestore-quotas.txt says quotas reset daily (verify which applies). One scripted device writing 20,000 documents could remove the reporting Play requires. Honest worst case (300 testers × 10 reports) is 3,000 writes per day.
* Recommendation: call the daily limit a UX guard in the brief (Goals fixed in place). Record the accepted risk, or add one of: deterministic document IDs `reports/{installKey}_{yyyymmdd}_{n}` with `n` limited to 0–9 and the date checked against `request.time` (caps accidental floods only); or Firebase Anonymous Auth (a no-cost product, research/firebase-pricing-plans.txt) with App Check enforced on Authentication and a per-uid counter, which adds an identifier to disclose. Set the App Check token TTL toward the 30-minute minimum (research/app-check-firestore.txt). Check the Firestore usage tab weekly; tell testers reporting can be unavailable.

**L7. Major: opt-in analytics gives a thin, biased sample, and the brief treats K.5 as the demonstrator's measure (K.5, Acceptance).** [zoom-out, inventive-catalyst, comprehension, structural]
* With sharing off by default (decision 24), only a self-selected minority of 100–300 testers report. OCR accuracy, error-mapping coverage and local-versus-cloud come from the offline harness anyway; time to first answer is measured better there too. The "crash-free sessions target" cannot be measured without opt-in; TestFlight crash reports cover all iOS testers.
* Fixed in place: K.5 and the acceptance success measures now say "supporting, opted-in testers only". Recommendation: ask testers to opt in in the invitation text; report the opt-in rate alongside any K.5 number.
* Crashes before consent: the riskiest code (first-launch wipe, first fetch, onboarding) runs before anyone can opt in. If Crashlytics keeps unsent reports while collection is off (verify RNFB `checkForUnsentReports` / `sendUnsentReports` / `deleteUnsentReports`), either delete them so nothing pre-consent is ever sent, or ask once on next launch "PhotoCritic closed unexpectedly last time. Send the crash report?" (inventive-catalyst). Decide which; the current text says only "not reported".

**L8. Major: the fallback rule after a bad publish is stated three ways (K.2, I.4, Phase 1, acceptance tests).** [design, codebase-details, implementation-readiness, comprehension, structural, inventive-catalyst, zoom-out]
* K.2 keeps per-value validation with bundled defaults; I.4 kept "the last good value"; the acceptance test expected "the last good set". Per-value fallback mixes bundled `models` with remote `defaults` and brings back J6's dangling references. A `kill_switch` that falls back to the bundle fails open.
* Recommendation (one rule, one test): validate `kill_switch` on its own; validate every other parameter as one set, all or nothing. On failure use either the whole bundled set (zoom-out: Remote Config already persists the last activated values, so no extra file) or a stored `config-lastgood.json` written only after a set passes (design). The author chooses; record it in K.2 and list any file in K.3. CI validates each publish against the exported schema of every live build (decision 19 makes this the author's job).

**L9. Major: Firebase disclosures are incomplete, the first fetch precedes the notice, and the collection defaults are misdescribed (K.2, decision 24, Goals, D6).** [codebase-details, implementation-readiness, comprehension, design]
* Build-time defaults: `setAnalyticsCollectionEnabled(false)` and `setCrashlyticsCollectionEnabled(false)` are runtime calls; native SDKs start before JavaScript. Set `analytics_auto_collection_enabled: false`, `google_analytics_adid_collection_enabled: false` and `crashlytics_auto_collection_enabled: false` in `firebase.json` (key names: verify). Make `settings.json` authoritative and push it to both SDKs at every start before other Firebase calls. Debug builds need `crashlytics_debug_enabled` to test reporting (research/rnfb-getting-started.txt).
* Inventory to add to the privacy policy, App Privacy label, Data safety form and onboarding: Firebase Sessions SDK (app and device metadata, network type, foreground times; whether it honors Crashlytics opt-out: verify); Crashlytics device fields, installation UUID, 90-day retention, Remote Config rollout metadata (research/firebase-privacy.txt; research/firebase-android-data-disclosure.txt); App Check attestation objects; the Firebase user agent from Firestore and App Check; Remote Config collecting Analytics user properties and `first_open` once Analytics is present (research/firebase-ios-data-collection.txt); Google Analytics fields (link only, verify); report text as User Content; the instance ID as a linked identifier when sharing is on; Android advertising-ID setting, AD_ID manifest permission and Play advertising-ID declaration (verify).
* Ordering: K.4 fetches on the first cold start, before the onboarding notice the Goals require "before the first fetch". Either skip the fetch on first launch (no `settings.json`) until the notice is acknowledged, or drop "before the first fetch". Onboarding order: Firebase notice, sharing switch, provider, BYOK explanation, key, AI consent.
* Proposed notice: "PhotoCritic uses Google Firebase to download settings each time it starts. Firebase sees your device's country, language, time zone, OS version and a random install ID. Your API key and photos never go to Firebase. If you send a report, its text goes to the developer's Firebase database." The J15 example "No photos or questions go to Firebase" is now false.
* The report screen names Google/Firebase as the recipient, separately from the AI-provider consent. Choose the Firestore database location deliberately in Phase 0 (EEA testers; the location is likely permanent, verify).

**L10. Major: the logging wrapper does not cover automatically captured errors (K.2).** [design]
* The lint rule catches direct calls only. Crashlytics captures native exceptions and, through RNFB's JS handler (verify), unhandled JavaScript errors. Messages can carry answer fragments (`JSON.parse` errors), zod issue values, provider error bodies and URLs. Breadcrumbs come from Analytics.
* Recommendation: wrap the global handler and sanitize before `recordError` (keep the error class, drop message text and argument values); never pass zod issue values; ban screen names containing user content; keep custom logs under the 64 kB limit (research/firebase-pricing-plans.txt). Test: throw an error containing a marker string and assert the marker is absent from the payload.

**L11. Major: the host allowlist needs exact matching, redirect handling and coverage of other remote URLs (decision 23).** [design, zoom-out]
* Match the parsed host exactly: https only, no userinfo (`https://api.openai.com@evil`), no port, lowercase, no suffix match (`api.openai.com.evil.com`). On rejection use the bundled `baseUrl` and keep the provider enabled.
* Redirects: fetch drops `Authorization` on a cross-origin redirect but not custom headers, so Anthropic's `x-api-key` and Gemini's `x-goog-api-key` would follow a 3xx. Refuse redirects (`redirect: 'error'` in `expo/fetch`: verify) or check the target against the allowlist.
* A malicious publish can still phish through `keyHelpUrl`, `policyUrl`, `privacyFactsUrl`, `updateUrl` and override texts. Allowlist the domains of user-facing URLs too.
* Zoom-out: with one allowed host per provider, a remote `baseUrl` can only equal the built-in value; drop the field.

**L12. Major: the K.5 event list has naming, consent and dead-parameter problems.** [codebase-details, comprehension, zoom-out, implementation-readiness, inventive-catalyst, structural]
* `session_start` is very likely a reserved, automatically collected Google Analytics event; reserved names are rejected (not in the evidence, verify). Prefix all custom events (`pc_`), check names in CI, and verify name, parameter-count and value-length limits.
* `consent_recorded` contradicts I.6 "consent records never leave the device". Drop it, or amend I.6.
* The round-3 `report_opened` parameters `channel` and `image_included` were dead after decisions 22 and 25 (renamed to `report_submitted` in place).
* Zoom-out: merge `question_sent`, `answer_received` and `request_failed` into one `turn_completed` event with an outcome; add `turn_index`, `served_model` or a model-mismatch flag, `image_detail`, and, for `error_class = unknown`, the enumerated raw provider code (never message text). `config_fetch` before opt-in is never sent, so also record the last fetch outcome locally.
* Route every event through the wrapper, which does nothing while sharing is off.
* The analytics opt-in is itself a consent under 5.1.1; store it in `consent.json` in the same record format (`analytics` entry with `privacyPolicyVersion`), not only as a settings flag.

**L13. Major: user-facing copy and local state for keys, sharing, reports and the kill switch are missing (K.3, K.6, K.7, J20).** [comprehension, implementation-readiness, design]
* Keys: on key entry, "Your key is stored only on this device and sent only to <provider>. It is not backed up. If you delete PhotoCritic, you will need to enter it again. App updates keep it."
* Sharing switch: "Share usage and crash data" (off). Subtitle: "Sends Google Firebase usage events (AI service, model, response times, error types, token counts) and crash reports. Never your photos, questions, answers or API key. Linked to a random install ID, shown below as your Support ID. Turning this off doesn't change how the app works." Settings shows "Support ID: <id> [Copy]" when on, "none (usage sharing is off)" when off. Say what happens to data already sent when sharing is turned off.
* Report screen: header "Report this answer"; category; reason box (1,000 characters); "Your photo is not sent. Describe what was in it, if it matters."; a "What is sent" box listing comment, question and answer, AI service and model, request ID, app/OS/language versions, and the Support ID when sharing is on; earlier turns shown with a toggle to leave them out. States as in L5.
* Deletion contact: K.6 says "email the developer with the report ID" but decision 23 removed the report address and nothing else defines a contact. Apple 1.5 and 5.1.1(i) require contact and deletion information. Define a bundled support address or URL, shown on the report confirmation and in Settings > Privacy: "To delete this report, email <support address> with report ID …".
* Kill switch screens (J20 still applies): "PhotoCritic is paused while a problem is fixed. Your keys and settings are kept on this device. [Try again]" calling `fetchAndActivate`; below the minimum version, "[Open TestFlight] / [Open Google Play]"; inline notices for a disabled provider or model; a stream stopped by the switch says so.
* Local state missing from K.3: `reports.json` (or a settings field) holding the day, count and a capped list of sent `{reportId, createdAt}`; lost on reinstall, and the brief should say so. The Support ID is read from the SDK only while sharing is on; keep nothing derived from it on disk.
* Use one user-facing name for the instance ID ("Support ID"), and say it identifies this install only.

**L14. Major: reports are hard to act on without the photo, and `providerRequestId` does not help the developer under BYOK (K.6, K.7).** [inventive-catalyst]
* The request ran on the user's own provider account; only the user can look it up in their console. Fixed in place in the K.6 table note.
* Recommendation: add non-content fields that let the developer recreate a similar case (image source, input format, starter prompt, turn index; `imageSettings` already present). On iOS, the confirmation can point to a TestFlight feedback screenshot carrying the report ID. No equivalent Android channel is confirmed (verify Play internal-testing feedback).

**L15. Major: round-3 machinery that round 4 made dead weight.** [zoom-out]
* `report_policy`: `enabled` must never be false while the app runs (J20); `retentionDays` duplicates the server-side retention and can drift; `dailyLimitPerInstall` and `maxConversationChars` are constants. Zoom-out would drop the parameter and use constants in code.
* `privacy_policy`: decision 18 says the policy text lives in Firebase, but I.3 holds only `url` and `version`; a hosted URL is needed for store metadata anyway (fixed in place in K.1 note).
* Consent `history` (up to 10 records) has no reader: records never leave the device and are lost on reinstall. Keep only `current` = `{decision, consentTextVersion, recordedAt}` (plus `consentTextHash` if J10 is adopted). `install.json.appVersionAtInstall` and `settings.json.updatedAt` have no reader.
* The local diagnostic ring buffer (item 33, J24) has no export path. Drop it, or attach the last request's redacted diagnostics to the report.
* `error_message_overrides`, `config_meta.publishedAt`, `keyPrefixHint`, `connectTimeoutMs`, `retryableClasses[]`, `maxQuestionChars` (J22) remain cut candidates.

**L16. Major: App Check setup and rollout are unspecified (K.6).** [codebase-details, implementation-readiness, design, zoom-out]
* App Check "doesn't accept tokens generated in the App Attest sandbox environment"; the entitlement must be `production` (research/app-check-firestore.txt). Initialize App Check before any other Firebase SDK. Simulators, dev-client builds and CI need the debug provider, with debug tokens registered per developer device in the development project only; CI fails if the debug provider is in the production profile.
* Play Integrity applies to Play-installed builds; sideloaded EAS preview APKs fail, so the preview profile uses the debug provider or a Play-installed build (verify). Link Play Integrity in the Play Console (verify). Standard quota 10,000 calls per day (research/firebase-app-check.txt); Apple recommends gradual App Attest onboarding (research/app-check-firestore.txt).
* Dependencies: `@react-native-firebase/app-check` and `/firestore` (verify package names and config-plugin support for the entitlement).
* Rollout: ship tokens, watch App Check metrics, then enforce before the first external TestFlight build and the first Play internal release (research/app-check-firestore.txt, Monitor metrics and enable enforcement). Show a clear message when attestation fails.

**L17. Major: decision 26 links more data than it needs (K.7).** [inventive-catalyst, design]
* `setUserId` on Analytics adds nothing (K.7 already says so) but makes all events linked. Inventive-catalyst: do not call Analytics `setUserId`; set the Crashlytics user ID only while sharing is on; attach `instanceId` to a report only when the user ticks "Attach my Support ID so crashes can be matched". The label may still need Identifiers because Crashlytics has its own installation UUID (verify).
* Opt-out: `setUserId(null)` on both SDKs (the Crashlytics clear value is not covered, verify), collection off, and optionally `resetAnalyticsData` so a later opt-in does not relink. Reports already sent stay linked; say so.
* Not an objection to decision 26; the author decides.

**L18. Major: no phase owned the round-4 work, and the privacy artefacts changed (Staging).** [structural, implementation-readiness, zoom-out]
* Fixed in place in Proposed staging: Firebase project setup, Firestore location, App Check registration, collection-off `firebase.json` keys and dSYM in Phase 0; marker and wipe, file storage, K.4, logging wrapper, sharing switch in Phase 1; K.5 events with the provider work in Phases 2–3; reports, rules tests, retention mechanism, App Check enforcement and updated privacy label and Data safety form in Phase 4 before the first tester build.

**L19. Major: the round-4 text was written incrementally and contradicted itself.** [structural, comprehension]
* K.6 described an image-in-report design that K.7 removed, its table held `imageJpegBase64` next to "Never included: any photo (decision 25)", decision 26 referred to an `instanceId` field the table lacked, K.2 called D18 "still open" and offered a region-only opt-in already settled by decision 24, and the K heading named decisions 17–21. Fixed in place (list below). A supersession map now opens K. Keep it current in later rounds.

**L20. Major: the client validator and the rules limits are a second copy of the report schema.** [structural]
* They will drift. Generate both from one schema file in the repository and run the rules emulator suite on each change (L2).

#### Nits

* **L21.** K.7 stated as fact that the SDK "does not provide an app instance ID" with collection off, then said "verify". Reworded as conditional; the read-after-opt-in rule holds either way (codebase-details).
* **L22.** "Remote Config rollouts can be monitored with Crashlytics" holds for opted-in installs only (decision 24); research/firebase-rc-rollouts.txt also gives minimum Crashlytics SDK versions. Fixed in place in K.2 and I.2 (all lenses).
* **L23.** K.2 cited "per J-section notes" for `withoutAdIdSupport`; now cites research/rnfb-getting-started.txt and notes it is iOS-only (codebase-details, structural, comprehension).
* **L24.** `configVersion` in the report is `config_meta.configVersion` from the snapshot, not Firebase's template version (J9). Fixed in place in the K.6 table (implementation-readiness).
* **L25.** Firestore allows "exactly one free database per project" (research/firestore-quotas.txt); the development and production projects each get their own (codebase-details).
* **L26.** Remote Config is listed among products with a paid tier of usage (research/firebase-pricing-plans.txt); its Spark quota was not fetched (verify; unlikely to matter at tester scale) (codebase-details).
* **L27.** Hermes JavaScript stack symbolication in Crashlytics and the Android R8 mapping upload are not covered (verify) (implementation-readiness).
* **L28.** Debug and dev-client builds do not send Crashlytics reports unless `crashlytics_debug_enabled` is set (research/rnfb-getting-started.txt); add to the test plan (codebase-details).
* **L29.** Exclude `expireAt` and the long text fields from single-field indexes (research/firestore-ttl.txt, TTL fields and indexes; indexed values over 1,500 bytes are truncated, research/firestore-quotas.txt) (implementation-readiness, design).
* **L30.** Say what the user sees during the up-to-3 s start (the splash screen), so it does not look like a hang (comprehension).

#### Mechanical contradiction fixes made in place (round 4)

1. Header "Mode": round 3 → round 4 (decisions 17–26). Reviewer Status: Round 4 column added. Notes: round-4 evidence note added.
2. Overview: rewritten for decisions 17–26 (keys wiped on reinstall, plain-file settings and consent, config at every start, host allowlist, text-only Firestore reports, opt-in Analytics and Crashlytics, instance ID). Round-3 risk bullets J1–J5 and email reporting removed as resolved; round-4 risks added.
3. Item 7: "Round 3 adds a remote-config path for key leakage (J3)" → closed by decision 23; Crashlytics leak path pointer added.
4. Item 8: "Addressed by decision 14; the Android hand-off … still open" → resolved by decision 22.
5. Item 10: "The same JPEG is the report attachment (I.7)" → reports carry no image (decision 25).
6. Item 15: "a first-launch wipe-or-reconsent rule is still needed" → wipe chosen (decision 21); backup note added.
7. Item 20: "(but see J: report attachments need their own lifetime)" removed; snapshot-versus-conversation note added.
8. Item 21: key-not-backed-up line added.
9. Item 27: retitled "(resolved, D17 by decision 22)"; body points to K.6–K.7.
10. Item 28: "unless the user sends a report (I.7)" → report text in Firebase (K.6–K.7) or opt-in sharing.
11. Item 30: harness-stays-primary note.
12. Item 32: retitled with "storage superseded by decisions 17 and 21"; "Either wipe keys or require re-consent … I.6 stores the flag in the Keychain" → round-4 resolution.
13. Item 33: schema and last-good bullets annotated for decision 19 and L8; "Observability without analytics" → "Local observability (now alongside opt-in Analytics)".
14. Item 6: Firestore on-device cache pointer added.
15. Section F: heading notes round-4 rows; row 3 notes decision 23 and the residual threat; row 6 rewritten for reports and opt-in data; rows 7–12 added.
16. Section H: heading date range; round-4 Confirmed, Corrected and Not-covered entries added; mail-composer facts marked unused; round-3 mail items and the Remote-Config-without-Analytics item marked obsolete; `firebase.json` key list extended.
17. I.1: "or the app no longer meets the non-goal 'no analytics by default'" removed and a decision 20/24 note added; Remote Config instant and secure-store-size bullets annotated.
18. I.2: rollout monitoring limited to opted-in installs.
19. I.3: `authStyle` removed and `baseUrl` annotated (decision 23); `report_channel` row replaced by `report_policy`; schema line and `app.version` condition annotated (decision 19); Analytics-audience reason updated.
20. I.4: heading extended to fetch interval and fallback; load order marked superseded; "12 hours in production" marked superseded by K.2; validation bullet points to L8.
21. I.5: "Firebase App Check is not used … Revisit this if D14 adds a write endpoint" → not used for Remote Config; enforced on Firestore (decision 22).
22. I.6: heading names what is superseded and what still applies; "All persisted state lives in expo-secure-store" and the "keep the keys" reinstall rule marked superseded; table rows point to K.3; "read-only mode" annotated with J21; "never leave the device" annotated (L12).
23. I.7: body condensed to a history note (superseded by decisions 22 and 25); the redaction rule carried to K.6.
24. I.8: "Keep the email report (it carries the AI answer and the sent image)" → keep the in-app report (text only).
25. Section J: status tags on J1–J7, J14, J15, J18, J19; J13 lists the round-4 RNFB packages; J14's spike and CI ban replaced; J15's "No photos or questions go to Firebase" flagged; J18 and J19 bodies reduced to their carry-over points; J20 `report_channel.enabled` → `report_policy.enabled`; J21 points to `consent.json`; J22 drops the gone `report_channel.*` and `installId` candidates; J24 "report emails" → "reports".
26. K heading: "(author decisions 17–21)" → "17–26", topic list adds reports; one-line supersession sentence replaced by a supersession table.
27. K.1: decisions 18 and 20 kept verbatim with amendment notes; pointer to decisions 22–26.
28. K.2: "This is D18, still open" → accepted as decision 23; "default off for those regions or for everyone" → resolved by decision 24; "per J-section notes; verify" → research/rnfb-getting-started.txt (iOS only); throttling wording aligned with research/firebase-rc-loading.txt; rollout monitoring limited to opted-in installs; pointers to L3, L4, L8, L10.
29. K.3: "Survives uninstall" column qualified for backup restore; atomic-write and file-encryption claims marked verify; missing local state noted.
30. K.5: opt-in sentence added; `report_opened` with `channel` (mail, share sheet, mailto) and `image_included` → `report_submitted` with `category` and `result`; "give the demonstrator its measures across testers" → supporting measures from opted-in testers.
31. K.6: decision 24 note on runtime vs build-time defaults; "Reports contain the user's photo" → text only; App Check claim and citations corrected; base64-image and Blaze paragraph condensed and marked resolved by decision 25; deletion and retention bullets point to L13, L1, L2; offline bullet corrected with research/rnfb-firestore.txt; report table: `imageJpegBase64` removed, `instanceId` added, `configVersion` and `providerRequestId` notes corrected; `report_policy` loses `includeImageDefault` and `maxImageBytes`.
32. K.7: decision 25 and 26 kept verbatim with objection and amendment notes; "If you meant the user's comment alone …" replaced by the recorded decision; instance-ID claims marked conditional or verify; Installations citation added; the duplicate "Report document changes" note removed (merged into the K.6 table).
33. Goals: the three round-4 bullets merged into the bullets they replace; mail-composer report text replaced by the Firestore report; "Analytics collection off … last-good fallback" → opt-in and the L8 fallback rule; secure-store settings and consent → plain files (D19 resolved); key wipe added to the BYOK goal; daily limit marked client-side; disclosure goal extended.
34. Non-Goals: first bullet adds Firestore reports and App Check; new non-goals for photos in reports, Cloud Storage and Functions, sign-in for reporting, report notifications, and keeping keys or settings across reinstall.
35. Alternatives: static-JSON row notes decision 20 removed its main advantage; `mailto:` row obsolete; report-endpoint row → Firestore adopted (decision 22); signed-manifest row → decision 23 and residual threat; secure-storage row → superseded by decision 17; new rows for email reports, image in the Firestore document, Blaze with Cloud Storage and Functions, region-only opt-in, keeping keys across reinstall, Anonymous Auth, and retention options on Spark.
36. Decisions 12, 13, 14, 18, 20, 22, 25 and 26: author text kept, round-4 notes appended.
37. Gate table: D6 adds the Firebase disclosures, sharing wording and deletion contact; D7 `pc.settings` → `settings.json`; D8 drops report-attachment lifetime; D9 adds RNFB Crashlytics, Firestore and App Check setup; D12 → K.3, J1 and J5 resolved, J21 open; D13 → J2 resolved, J7 dropped, fallback rule open; D14 → superseded by decision 22; D20 adds the Android advertising-ID check; D21 reopened (L1); new open rows D22–D25.
38. Staging: Phase 0 "Settle D3–D9 and D17–D19" → "Settle D3–D9 and D21–D25"; Remote-Config-without-Analytics spike removed and round-4 spikes added; Phase 1 "per section I.6 with the J1 and J5 fixes" and "Analytics collection off … last-good set" → K.2–K.4; Phase 4 email reporting and mail tests → Firestore reports and their tests.
39. Acceptance: "maximum-size consent item at most 1,536 bytes", "iOS reinstall forces re-consent and keeps keys", "keeps the last good set", the schema-upgrade and OTA consent-schema tests replaced; round-4 tests added; success measures marked opted-in only.

### M. Round 5: UX, screens and visual design

#### M.1 Author's first-run and home-screen flow (decision 28)

1. **First launch: provider setup.** The app opens on a provider setup screen. The user picks a provider, picks a model, and pastes an API key.
2. **Home screen.** Once setup is saved, the home screen offers two ways to get a photo: take a new one with the camera, or choose one from the device.
3. **Conversation sheet.** The home screen has a multi-line text box for the conversation. The user can pull the box up to read longer answers. Its background is 80% transparent, so the photo stays visible behind it.
4. **New photo button.** A button on the home screen starts over with a new photo or image.

#### M.2 Problems with this flow

* **The consent step has no place in it.** Apple 5.1.2(i) needs a consent screen naming the provider before the first photo is sent (section B, item 4; section I.6). Two places it can go:
  * at the end of provider setup, before the key is saved; or
  * the first time the user taps Send for a provider (recommended: the user then sees it in context, and switching provider later triggers it again for the new provider).
* **The analytics opt-in (decision 24) has no place either.** Recommended: one "Share usage and crash data" switch on the provider setup screen, off by default, with a one-line explanation. It is also in Settings.
* **The setup screen must handle a key that does not work.** The key check (item 24) can fail for a typo, a key from the wrong provider, no billing, or no network. The screen stays open, shows the plain-language reason under the key field, and keeps what the user typed. A "Where do I get a key?" link per provider opens that provider's key page.
* **What the text box does before a photo is chosen is undefined.** The app answers questions about one photo, so with no photo:
  * the photo area shows the two large buttons (Camera, Photos);
  * the text box is visible but disabled, with the hint "Choose a photo to start";
  * starter chips (Identify, Explain, Copy text) appear once a photo is loaded.
* **80% transparent means 20% opaque, and text over a photo at that level is often unreadable.** White or black text over a bright, busy photo will fail contrast checks (WCAG 4.5:1 for body text). Recommended instead:
  * a blurred backdrop (`expo-blur` `BlurView`) behind the sheet, plus a tint that adapts to light or dark mode;
  * the 80% transparency applies only when the sheet is collapsed (input bar and the last answer); as the sheet is pulled up, the tint becomes nearly opaque so long answers stay readable;
  * honor iOS "Reduce Transparency" and Android high-contrast text by switching to a solid background.
* **A text input inside a draggable sheet is a known source of keyboard bugs.** The input must stay above the keyboard on both platforms, and dragging the sheet must not fight scrolling of the answers. Use a bottom-sheet library with built-in keyboard handling (for example `@gorhom/bottom-sheet` and its `BottomSheetTextInput`; verify current Expo compatibility) and test on both platforms early.
* **"Select a new photo" throws away the conversation.** Earlier rounds decided to ask for confirmation when the conversation is not empty ("Start over with a new photo? This conversation will be cleared."). No confirmation is needed when nothing has been asked yet.
* **The photo is mostly hidden when the sheet is pulled up.** Allow pinch-to-zoom and pan on the photo when the sheet is collapsed, and a tap on the photo collapses the sheet. This matters for questions about small details and text.

#### M.3 Home screen layout (draft)

```
 Sheet collapsed (photo loaded)            Sheet expanded (long answer)
 ┌──────────────────────────────┐          ┌──────────────────────────────┐
 │ [⚙ Settings]   [＋ New photo] │          │ [⚙]  (photo, blurred) [＋]    │
 │                              │          │ ┌──────────────────────────┐ │
 │                              │          │ │ ── drag handle ──        │ │
 │        PHOTO (full screen,   │          │ │ You: What does the sign  │ │
 │        pinch to zoom)        │          │ │ say?                     │ │
 │                              │          │ │ Claude Sonnet: The sign  │ │
 │                              │          │ │ reads "…"  [Copy] [Share]│ │
 │ ┌──────────────────────────┐ │          │ │ … (scrolls)              │ │
 │ │ ── drag handle ──        │ │          │ │                          │ │
 │ │ Last answer (2–3 lines)… │ │          │ │                          │ │
 │ │ [Identify][Explain][Text]│ │          │ ├──────────────────────────┤ │
 │ │ ┌──────────────────┐ [➤] │ │          │ │ Ask about this photo… [➤]│ │
 │ │ │ Ask about photo… │     │ │          │ └──────────────────────────┘ │
 │ └──────────────────────────┘ │          └──────────────────────────────┘
 └──────────────────────────────┘
 Translucent blurred sheet                 Near-opaque tint for reading
```

Sheet snap points (starting values to tune): collapsed at about 25% of screen height, half at 50%, expanded at about 90%.

No photo yet: the photo area shows two large buttons, "Take photo" and "Choose from Photos". The sheet shows only the disabled input.

#### M.4 Round 5 answers (decisions 29–34)

* **29. Extracted text appears as its own block with Copy and Share buttons,** separate from normal chat replies.
  * Problem: the app has to know which part of an answer is "extracted text". Ask the model, in the system prompt for the Copy text starter and for any question that asks for text, to wrap verbatim text in a marker (for example a fenced block tagged `text`). The app renders each marked block as a text card. If the model does not use the marker, the whole answer is shown as a normal reply with a Copy button, so nothing is lost.
  * The text card keeps line breaks and uses a monospaced or tabular font option, because receipts and tables lose meaning when reflowed.
* **30. No provider or model switching mid-conversation.** Changing provider or model in Settings while a conversation is open ends it, after the same confirmation used for a new photo.
* **Withdrawn by decision 36: no log in v1.** **31. A separate "Conversation log" screen** lists past conversations, stored only on the device. Each entry shows start and end date and time, provider and model, token counts (input, output, cached if reported), and an estimated cost if a price is known. The user can delete single entries or older entries.
  * Problems:
    * It reverses the round-1 non-goal "no persisted conversation history" in part. Keep it metadata-only: no photo, no questions, no answers. Say so on the screen ("This log keeps usage details only, not your photos or messages").
    * "End time" needs a definition: the time of the last answer, set when the user starts a new photo, changes provider, or the app is closed.
    * Cost is an estimate. Prices are per model and change (round 2); keep the per-model price table in Remote Config `models` (`inputPricePerMTok`, `outputPricePerMTok`, `cachedInputPricePerMTok`, `priceAsOf`) and show "Estimated, based on prices as of <date>". Show "Cost unknown" when no price is set.
    * Storage: a small local database (`expo-sqlite`) or one JSON file per month; cap at 1,000 entries and delete the oldest first. It is lost on reinstall, consistent with decision 17.
* **32. Visual identity: an original look with its own design system and icons.** Method in M.5.
* **33. Tablets and landscape are in scope; English only.**
  * Problems:
    * A bottom sheet over a full-screen photo works in portrait phone layouts. In landscape and on tablets it covers most of the photo. Use a two-pane layout when the window is wider than about 700 points: photo on the left, conversation panel on the right (resizable on tablets).
    * iPad apps that support all orientations are expected to support Split View and Slide Over, which means the window can be any width at any time. Layout must follow window size (`useWindowDimensions`), not device type.
    * Camera capture on iPad and Android tablets must handle rotation; the photo pipeline already applies EXIF orientation before sending (section C, item 9).
* **34. Image editing is a planned future feature.** The architecture keeps the original photo, an edit layer and the sent copy separate (section N).

#### M.5 How to build the design system and icons

1. **Write the tokens first, in one TypeScript file** (`src/design/tokens.ts`): colors (with light and dark values), type scale, spacing scale, corner radii, elevation or blur levels, motion durations. Every component reads from it; nothing hard-codes a color or size. This is the same approach Callstack uses for its own products: one file holds the brand values in code (research/callstack/ai__docs__DESIGN.md).
2. **Pick a small number of brand decisions and write them down:** one accent color that passes 4.5:1 contrast on the background, one typeface for UI text (the platform system font is the low-risk choice), and a shape language (corner radius, line weight). Callstack's own design file is an example of this level of detail: a token table with contrast ratios and rules such as "never use the accent for status" (research/callstack/ai__docs__DESIGN.md).
3. **Build the components from tokens:** button, icon button, chip, text field, sheet, card, list row, switch, toast, empty state. Show each in light, dark, and Reduce Transparency modes.
4. **Icons:**
   * UI icons: use one open-source line icon set with an MIT or ISC license, drawn on a 24 px grid (for example Lucide or Phosphor), rendered through `react-native-svg`. Using one set keeps line weight consistent. Add your own icons only where the set lacks one, drawn on the same grid and stroke.
   * App icon: a custom mark, designed as an SVG at 1024 px, exported to the iOS icon set and the Android adaptive icon (foreground and background layers). Expo generates the sizes from `icon` and `android.adaptiveIcon` in the app config.
5. **Where it lives:** the token file and components are the source of truth. A published design-system page (tokens, components, icon grid) is a useful reference for review, and can be generated from the same values.

The clickable mockup (https://claude.ai/artifact/QMFTwpETHLVAHHHyySA9e7, private to the author) proposes a first token set, "Darkroom": near-black surfaces, one amber accent #F5B13D for the primary action, Space Grotesk headings over Inter body text, 12/14/24 px radii, glass only over photos. It also proposes an app-icon direction (aperture with a speech tail) and an 8-icon set on a 24 px grid.

### N. Round 5: architecture and React Native practices (decision 35)

Author decision 35: follow Callstack's published React Native practices for the New Architecture. Sources: Callstack's agent skills (`react-native-best-practices`, `react-native-testing`, `github-actions`) and the React Native AI library, fetched 2026-11-15 into `research/callstack/`.

#### N.1 What the Callstack material changes in this brief

* **D3 (provider layer) has a stronger answer now: use the Vercel AI SDK on the device, behind PhotoCritic's own interface.** Callstack's React Native AI library gives on-device providers (Apple Foundation Models, Android Gemini Nano through ML Kit GenAI, Core AI, and llama.rn for GGUF models) that work with the AI SDK's `generateText` and `streamText` (research/callstack/ai__README.md). The cloud providers (`@ai-sdk/openai`, `@ai-sdk/anthropic`, `@ai-sdk/google`) use the same functions. One interface then covers v1 cloud and the later local phase. Two checks remain:
  * the round-2 finding that the AI SDK's Expo guide assumes a server route (item 31): the D3 spike must confirm direct device-to-provider calls with `expo/fetch`, streaming, abort and readable error bodies;
  * React Native AI versions are tied to AI SDK majors (0.13 and above for AI SDK v7; research/callstack/ai__README.md). Pin both together.
* **Bottom sheet:** use `@gorhom/bottom-sheet` v5. Keep gesture and animation state in Reanimated shared values, not React state, so dragging does not re-render the conversation. Use `BottomSheetTextInput` and `keyboardBehavior="interactive"` for the input. Set `enableDynamicSizing={false}` when using fixed snap points (research/callstack/…js-bottomsheet.md). Check its Reanimated version support before choosing Reanimated 3 or 4; the reference says v5 is built for Reanimated 3 and v4 is not officially guaranteed.
* **Lists:** render the conversation with FlashList v2 (New Architecture) or Legend List, not `ScrollView` (research/callstack/…js-lists-flatlist-flashlist.md). Do not add `estimatedItemSize` on FlashList v2.
* **State:** use Zustand (or Jotai) with selectors for app state, instead of one broad React Context, so a streaming answer updates only the message being written (research/callstack/…js-atomic-state.md).
* **React Compiler:** turn it on (it is configured by Expo SDK 54 and later; research/callstack/…js-react-compiler.md) and do not add `useMemo`/`useCallback` by hand without a measured problem (research/callstack/…SKILL.md, Review Guardrails).
* **Text input:** the conversation input can stay controlled on the New Architecture; switch to an uncontrolled input only if measured lag appears (research/callstack/…js-uncontrolled-components.md).
* **Navigation:** native stack navigation (`react-native-screens`, which Expo Router uses) for platform transitions and headers (research/callstack/…native-sdks-over-polyfills.md).
* **Measure before optimizing:** record cold-start time (TTI) with `react-native-performance` markers, and FPS during sheet drags and streaming, on the minimum supported device. Only optimize what the numbers show (research/callstack/…SKILL.md, Optimization Workflow).
* **Bundle and binary size:** import directly from source files, not barrel `index.ts` files; enable R8 on Android; check third-party native libraries for Android 16 KB page-size alignment, which Google Play requires (research/callstack/…bundle-barrel-exports.md, …bundle-r8-android.md, …native-android-16kb-alignment.md).
* **Tests:** React Native Testing Library with its rules: `screen` queries, `getByRole` with names first, `findBy*` for async elements, no side effects inside `waitFor`, no manual `act()` or `cleanup()` (research/callstack/…react-native-testing__SKILL.md). Callstack's `github-actions` skill covers building iOS and Android in CI.

#### N.2 Code structure

```
src/
  app/                 Expo Router screens: setup, home, settings, log, consent
  design/              tokens.ts, components (Button, Chip, Sheet, TextCard…), icons
  features/
    session/           one-photo conversation: store, prompt assembly, history cap
    providers/         PhotoCritic Provider interface + adapters (AI SDK cloud now, local later)
    image/             pipeline: original → edits (future) → send copy (resize, JPEG, EXIF strip)
    consent/           consent records and gating
    report/            in-app report to Firestore
  platform/
    config/            Remote Config load, validation, session snapshot, host allowlist
    storage/           secure store (keys), files (settings, consent)
    telemetry/         one logging wrapper for Analytics and Crashlytics (redaction)
```

Rules:

* Screens call features; features never import screens. `platform/` has no UI.
* The `Provider` interface (round 1, item 18) is the only place that knows a vendor. Errors leave it as the internal error classes.
* **Image editing (decision 34):** the `image` feature keeps three things: the original photo URI (never changed), an edit description (crop, rotate, markup, filters, as data), and the rendered send copy. Editing later adds a step that renders the edit description before the resize. The conversation holds the send copy it used, so editing a photo starts a new conversation rather than changing the image under existing answers. A GPU drawing library such as `@shopify/react-native-skia` is the likely tool for editing (verify New Architecture and Expo support at that time).

### O. Round-5 findings (UX and architecture review)

Scope: sections M and N and decisions 28–35. Earlier sections were used only to check consistency. Round-4 findings L1–L30 and gate rows D21–D25 are not re-raised (decision 27). All seven lenses completed. "Verify" marks claims the fetched evidence does not cover.

Short paths used below:
* `rnbp/X` = `research/callstack/agent-skills__skills__react-native-best-practices__references__X`
* `rnai/` = `research/callstack/ai__…`
* `Main`, `Tablet`, `System` = the mockup files `Main.dc.html`, `Tablet.dc.html` and `System.dc.html`

#### High

**O1. High. Decision 31 vs Non-Goals. The conversation log contradicts two non-goals that were never updated.** (all lenses)
* **Problem:** Two non-goals still stand:
  * "User accounts and persisted conversation history" (line 1368);
  * "A per-session cost display (zoom-out; prices change within weeks)" (line 1378).

  Decision 31 adds a stored log that shows an estimated cost for each conversation. M.4 mentions the first conflict but not the second. The privacy-policy line in F row 6 (line 306) says the app stores only keys, settings and consent, which is also no longer true.
* **Evidence:** concepts_brief.md lines 76, 306, 1274–1278, 1368, 1378; `Main` lines 318–320 (log rows show "Estimated $0.07 (prices as of Nov 1)").
* **Fix:**
  * Change line 1368 to "no persisted conversation content (photos, questions, answers); a metadata-only usage log is in scope (decision 31)".
  * Either remove line 1378, or keep it and drop cost from decision 31 (gate row D30).
  * Add "usage log on device" to the F row 6 privacy text.

**O2. High. Decision 28, M.2, M.5. Text on the collapsed sheet fails the brief's own 4.5:1 contrast rule, and the mockup hides the problem.** (codebase-details, comprehension, design, inventive)
* **Problem:** The mockup's collapsed sheet is the #17181B surface at 20% opacity with a 22 px blur, and it holds the last answer, the chips and the input.
  * Blur removes detail, not brightness. Over a white or sky photo, the background works out to about #D1D1D1, and #F4F4F2 text on it is about 1.4:1.
  * White text over a white photo needs a tint of at least about 62% to reach 4.5:1.
  * The sample photo happens to be dark under the sheet, so the mockup looks fine.
  * The muted text (66–72%) and the chips (45% background) are the weakest parts. System claims 4.5:1 for muted text only on the solid background.
  * Android `BlurView` does not blur unless you opt in to a blur method (verify for the chosen SDK), so on Android there is often only the tint.
* **Evidence:** `System` line 51; `Main` lines 140, 155–156, 166, 376–378; concepts_brief.md lines 1233–1236.
* **Fix:** Never put text on less than about 65% tint, by either:
  * giving the text and the input their own solid "pill", with glass only on the handle and the empty area; or
  * placing the photo above the panel (`contentFit="contain"`), so the panel can always be opaque (gate row D27).

  Test with a white, high-key photo. Draw the Reduce Transparency and light variants in the mockup. Express the blur as expo-blur `intensity` (0–100), not pixels.

**O3. High. N.1 and decision 35. "One interface covers v1 cloud and the later local phase" is not supported for a photo app.** (design, codebase-details, inventive, zoom-out)
* **Problem: React Native AI is on-device only.** `rnai/README.md` line 5 says "A collection of on-device AI primitives". No Callstack file shows the `@ai-sdk/*` cloud providers running on a device. The only fetched AI SDK guide (`research/aisdk-expo.txt`) runs them in a server route.
* **Problem: on-device image input is narrow.**
  * Apple Foundation Models: no image prompts on iOS 26. On iOS 27 an image is read only on the final user prompt, and usage counts may be undefined.
  * Core AI (Qwen3-VL): raises the app's deployment target to iOS 27 and is a 2.9 GB download.
  * llama: needs a separate projector file, defaults to a 4096-token context, and one image can use a quarter of it.
  * Gemini Nano: needs an AICore phone and may download on first use.

  The D9 floor is iOS 17, so on-device vision reaches only a few devices.
* **Problem: Goals line 1363.** It promises an on-device provider "without UI changes". Local models have availability and download states that the setup screen (M.1: "paste an API key") does not have.
* **Evidence:**
  * `rnai/README.md` lines 5, 109, 120–175;
  * `ai__website__src__content__docs__docs__apple__ios-versions.md` lines 49, 71–76;
  * `…apple__models.md` lines 29–43, 53–64;
  * `…llama__generating.md` lines 160–216;
  * `…llama__model-management.md` lines 14–17, 56, 125–135;
  * `ai__skills__react-native-ai__references__core-ai-provider.md` line 30.
* **Fix:**
  * Restate the claim as "one call surface; capabilities differ by model and device".
  * Extend the item-18 capability object with: `acceptsImages`, `imagePlacement` (none, final turn only, any turn), `maxImageEdge` or `maxImageTokens`, `contextTokens`, `streaming` (tokens or whole), `structuredOutput`, `usageReporting`, and `dataLeavesDevice`. The consent gate keys on `dataLeavesDevice`; Apple Private Cloud Compute counts as leaving the device.
  * Add `getAvailability()`, `prepare(onProgress)` and `dispose()` to the interface now. Cloud adapters make them no-ops.
  * Reserve `features/models/` in N.2 for download, storage and removal. Store model files outside backup.
  * Remove "without UI changes" from line 1363.
  * Make the v1 fake provider (F row 4) deliberately final-turn-only, whole-response and usage-less, to prove the UI copes.

**O4. High. N.2 vs item 18. N.2 moves prompt assembly and the history cap out of the provider.** (design, inventive)
* **Problem:** N.2 puts "prompt assembly, history cap" in `session/` (line 1326). Item 18 says "Each provider owns its preprocessing and message assembly" (line 231). The two need different things:
  * Cloud models want a stable prefix with the image first (F row 1, line 301).
  * Apple needs the image on the final turn.
  * llama needs a cap measured in tokens. `conversation_policy.historyTurnsCap` (line 622) is a single global turn count.
* **Fix:**
  * `session/` keeps a provider-neutral history: turns plus a reference to an image version.
  * Each adapter builds the actual messages and applies a token-based cap from its `contextTokens`.
  * The Remote Config value becomes an upper bound, not the rule.
  * Extend the N.2 rule: no AI SDK types (messages, usage, stream parts, errors) leave the adapter, so the AI SDK stays replaceable.

**O5. High. N.1. No versions are pinned, and the bottom-sheet advice is probably wrong for any current Expo SDK.** (codebase-details, implementation-readiness, design, zoom-out)
* **Problem: no Expo SDK is chosen.** The brief only gives floors: "SDK 54+" (line 130) and "SDK 55+" for ExecuTorch (line 1393). `research/expo-fetch.txt` line 82 already mentions SDK 58 and React Native 0.88.
* **Problem: the bottom-sheet advice dates from Reanimated 3.**
  * `rnbp/js-bottomsheet.md` lines 60–61 say v5 "is built for react-native-reanimated v3", and v4 is "not officially guaranteed". Its install line (line 65) pins `react-native-reanimated@^3`.
  * Recent Expo SDKs ship Reanimated 4, and recent `@gorhom/bottom-sheet` 5.x releases may declare Reanimated 4 support (verify both).
  * The same file offers `@lodev09/react-native-true-sheet` as a native New Architecture sheet with native keyboard and accessibility handling (lines 306–315). N.1 does not mention it.
* **Problem: other version couplings are not written down.**
  * AI SDK v7 goes with React Native AI 0.13+ (`rnai/README.md` lines 16–20). No file says which `@ai-sdk/openai`, `@ai-sdk/anthropic` or `@ai-sdk/google` versions match.
  * FlashList v2 requires the New Architecture.
  * Expo Router, Gesture Handler, RNFB and RNTL (v14 needs an awaited render) are also unpinned.
* **Fix:**
  * Make the Expo SDK and React Native pin a Phase 0 decision (gate row D26).
  * Add a version table to N with exact versions.
  * Run a Phase 0 sheet spike comparing gorhom v5 and true-sheet on that SDK's Reanimated.
  * Add a CI check that installed versions match the SDK's expected versions, and a rule that the AI SDK and React Native AI are upgraded together.

**O6. High. N.1 and D3. N.1's spike checklist drops item-31 checks and misses several others.** (codebase-details, implementation-readiness, design)
* **Problem: three item-31 checks are dropped.** N.1 keeps `expo/fetch`, streaming, abort and error bodies. It drops these item-31 checks (lines 381–386):
  * no string model IDs;
  * no Gateway traffic;
  * no `Origin` header, which triggers Anthropic's CORS 401 (`research/anthropic-errors.txt` line 15).
* **Problem: further checks are missing.** Each should be pass/fail on a release build for all three providers.
  * Polyfills. Callstack says "Every provider needs the AI SDK's polyfills" (`rnai/website__src__content__docs__docs__index.md` line 38). That conflicts with item 31's note that `expo/fetch` makes them unnecessary.
  * One request per send. The AI SDK's `maxRetries` default would clash with the brief's own retry policy.
  * Readable `APICallError` fields: `statusCode`, `responseBody` and response headers, including each provider's request-ID header for "show details".
  * Mid-stream error parts and `finishReason`. Apple returns `content-filter` rather than throwing, so try/catch alone can end an answer silently.
  * Image detail and resolution passed through provider options.
  * Usage reported after an abort.
  * Redirects refused (L11).
  * A connect timeout built from `AbortSignal` (check that Hermes has `AbortSignal.timeout` and `any`).
  * Whether `anthropic-dangerous-direct-browser-access` is needed. React Native does no CORS preflight, so it should not be (verify).
  * Gemini key sent in `x-goog-api-key`, never `?key=`.
  * Which OpenAI API the adapter calls (Responses or Chat Completions), since `error_map` rules depend on it.
* **Problem: decision 35 quietly settles open D3 sub-questions.**
  * Choosing `@ai-sdk/google` also chooses the Gemini API surface.
  * `providers.apiVersion` (I.3) is now set by the adapter.
* **Fix:**
  * Restore the item-31 checks and add the list above.
  * Route all calls through one PhotoCritic `fetch` wrapper passed to each provider factory (verify the option exists in v7). It enforces the host allowlist (decision 23), `redirect: 'error'`, Origin stripping and request-ID capture.
  * Record the Gemini endpoint.
  * Redefine `apiVersion`, or remove it.
  * Give the spike a time limit, keeping line 386's fallback to thin adapters.

**O7. High. M.2 and M.3. The conversation has no defined states for waiting, streaming, stopping, errors or offline.** (comprehension, design, implementation-readiness)
* **Problem: no Stop control.** No screen has one, although the D3 spike requires abort (lines 382, 1307).
* **Problem: nothing defines what the user sees:**
  * between Send and the first word (several seconds with a 2048 px image on a slow network);
  * while the photo is being prepared (HEIC conversion);
  * while the key check runs;
  * when the connection drops mid-answer;
  * where Retry sits, and that Retry re-sends the photo and costs tokens again;
  * when the device is offline (Non-Goals line 1374 promises a "no-connection state");
  * on the kill-switch and update-required screens (line 1123).
* **Problem: the mockup shows less.** It has a placeholder "[The answer streams in here…]" (`Main` line 349). Its report toast always says "sent", although K defines sent, queued and failed (`Main` line 413). `System` line 94 lists "error banner" as still to draw.
* **Fix:** Add a state table to M. States: no photo, preparing, ready, sending, streaming, stopped, error by class (the six user actions, line 1355), offline, disabled. For each, say what the sheet, the input and the chips show.
  * Send becomes Stop while streaming.
  * The question appears at once, followed by a typing indicator.
  * Partial answers stay, marked "Stopped" or "Incomplete", and can be copied (gate row D34).
  * Screen readers hear the finished answer once, not every streamed piece.

**O8. High. Decision 33 and N.2. Rotation or a Split View resize swaps component trees mid-conversation, and nothing says what survives.** (comprehension, design, inventive, implementation-readiness)
* **Problem: the swap.** Crossing about 700 pt switches between the bottom sheet and the two-pane layout. Neither M nor N says whether these survive the switch:
  * the draft question;
  * the scroll position;
  * an answer that is still streaming;
  * the photo zoom;
  * keyboard focus;
  * the last sheet position.

  If abort is tied to a component unmounting, a rotation kills the answer.
* **Problem: no stated owner for non-UI work.** N.2 does not say who owns the AbortController, the turn state machine (item 20), retries, the consent check and log writes. If consent is checked in screens, a chip, a retry, or a send replayed after the consent sheet can skip it.
* **Problem: flapping and resize.** "About 700 pt" has no gap between the switch-up and switch-down widths, so the layout can flip back and forth near the boundary. M.4 says the panel is resizable on tablets, but `Tablet` line 45 has a fixed 420 px panel.
* **Fix:**
  * Add a plain-TypeScript `SessionController`, one per conversation. It owns the turn state machine, the abort signal and the provider handle. It is the only caller of `provider.send`, and it runs the consent check before every call.
  * The Zustand store is a read-only projection for the UI. `log/` and `telemetry/` subscribe to turn events.
  * Write the allowed import directions.
  * Switch to two panes at 720 pt and back below 680 pt.
  * Keep the conversation content component identical in both containers; only the scroll container is pluggable.
  * Add acceptance tests: rotate during streaming, and resize Split View across the breakpoint with the keyboard open.

**O9. High. M.3 and N.1. Fixed snap points cannot hold the collapsed sheet at large text sizes, and the text and mockup disagree on snap points.** (comprehension, design, implementation-readiness)
* **Problem: collapsed height.** At default text size, the collapsed sheet needs the handle (28), 2–3 answer lines, chips (36), the input (44) and padding: more than 170 pt. 25% of a 667 pt iPhone SE is about 167 pt.
* **Problem: the N.1 advice.** N.1 recommends `enableDynamicSizing={false}` with fixed percentages, which works against Dynamic Type (Goals line 1362).
* **Problem: the mockup.** The chip row does not wrap (`Main` line 165). Buttons and chips have fixed heights. The mockup has two heights (200 or 300 px collapsed, 736 px expanded); M.3 has three snap points (25/50/90%) and never says what "half" is for.
* **Evidence:** concepts_brief.md lines 1264, 1309; `Main` lines 165, 378; `rnbp/js-bottomsheet.md` line 68.
* **Fix:**
  * The collapsed snap point is the measured content height, with a minimum.
  * Use min-heights, not fixed heights.
  * Chips wrap or scroll sideways.
  * All text follows the system size, including the monospaced text card.
  * Pick two or three snap points and say what each shows (gate row D28).
  * Test at the largest accessibility text size.

**O10. High. Goals, Non-Goals, gate table, Alternatives, staging and acceptance were not updated for decisions 28–35.** (structural, zoom-out, implementation-readiness)
* **Problem: D3 is still listed as open.**
  * The gate row (line 1460) still offers three options.
  * The Alternatives row (line 1394) says "Open, lenses split".
  * Decision 35 says D3 is resolved, subject to the spike.
* **Problem: D8 is out of date.** D8 (line 1465) still lists the provider switch, which decision 30 settles. M.2 line 1238 says "Earlier rounds decided to ask for confirmation", but that was a reviewer recommendation (item 20, line 253), not a decision.
* **Problem: Goals.** They have nothing on:
  * the text card;
  * the conversation log;
  * tablets and the two-pane layout;
  * the design system;
  * Reduce Transparency and the contrast threshold;
  * the new price fields.
* **Problem: staging.** Phases 0–4 (lines 1486–1519) have no:
  * sheet and keyboard spike;
  * version pinning;
  * tokens and components;
  * setup and home screens;
  * log store and migrations;
  * two-pane layout.
* **Problem: acceptance.** The acceptance gaps (lines 1525–1538) have no tests for M.
* **Problem: the header.** The Mode line, Reviewer Status, reading notes and Overview stop at round 4.
* **Fix:**
  * Strike D3 as "Resolved (decision 35), conditional on the spike; fallback is thin adapters". Keep the Gemini surface and the version pin open in the row.
  * Update D8 and reword M.2 line 1238.
  * Add a Goals bullet for each of decisions 28–34.
  * Add a round-5 block to each staging phase, and an "M acceptance" block (see O27).
  * Update the header lines.

**O11. High. Decision 31. The log has no schema, and its end-time rule cannot be implemented.** (all lenses except structural)
* **Problem: end time.** "Set when … the app is closed" (line 1277) relies on an event that does not exist. iOS gives no reliable callback when a suspended app is killed, and Android kills background processes without warning.
* **Problem: the schema is missing.** M.4 lists what is shown, not what is stored, and does not say when rows are written. These are undefined:
  * aborted turns, whose usage may be partial or missing;
  * the served model, given Gemini's silent re-routing (item 30);
  * cache-write tokens, which Anthropic bills at a different rate;
  * cached tokens, which some APIs count inside input tokens and others separately;
  * whether cost is calculated when the row is written or when it is shown. Calculating at display time re-prices old rows whenever prices change.
* **Problem: storage.** It is left open in M.4 (`expo-sqlite` or JSON per month), but N.2 assumes sqlite. The log is missing from the K.3 storage table.
* **Fix:**
  * Write the row after the first completed turn and update it after each turn, so `endedAt` is the time of the last turn. Process death then loses at most the turn that was streaming.
  * Fields: `id`, `startedAt`, `endedAt`, `endReason`, `provider`, `requestedModelId`, `servedModelId`, `turnCount`, `inputTokens`, `outputTokens`, `cachedReadTokens`, `cacheWriteTokens`, `reasoningTokens`, `usageComplete`, `costMicroUsd` (nullable integer), `priceAsOf`, `configVersion`, `appVersion`, `schemaVersion`.
  * The adapter returns usage in one normalized shape.
  * Calculate cost once, when the row is written, from the session config snapshot. Local models show "On device".
  * Add a `log` row to K.3. If sqlite is chosen, track the schema with `PRAGMA user_version`, use add-only migrations, and rename a corrupt file and start empty (gate row D31).

**O12. High. Decisions 31–34 vs decision 7. v1 scope has grown in ways that push back the local phase, and Phase 5 has no entry criterion.** (zoom-out, design)
* **Problem:** v1 now includes:
  * tablets, Split View and a resizable pane;
  * a custom token system with two bundled fonts;
  * ten components, each shown in three modes, plus a published design-system page;
  * a cost log fed by a Remote Config price table;
  * image-editing prep;
  * FlashList;
  * R8;
  * TTI markers;
  * CI device builds.

  None of this helps show cloud against on-device, which decision 7 makes the headline. J22 (line 823) already found the design oversized for the tester group. Phase 5 still says "Needs an entry criterion or date" (line 1521).
* **Fix:**
  * Label each of decisions 31–34 either "v1" or "after Phase 5".
  * Set a Phase 5 entry criterion now (gate row D40).
  * Minimum v1 that still makes the point: phone portrait; setup; one home screen; consent; report; settings; system font; one icon set; a custom app icon.

#### Medium

**O13. Medium. M.4 price table vs the I.3 `models` schema.** (implementation-readiness, design, zoom-out)
* **Problem:**
  * The four price fields are not in the I.3 table (line 619).
  * If `models` is parsed strictly, adding a price field makes older builds reject the whole value and fall back.
  * A routine price edit touches the allowlist.
  * The value grows, and its per-value size limit is still unverified.
  * Prices change on 2026-12-01 and 2027-01-01 (line 45), so the author must edit prices for three providers at least twice.
* **Fix:**
  * If cost is kept, use a separate `prices` parameter keyed by served model ID, with optional fields.
  * Parse `models` non-strictly.
  * A bad price makes only that entry "Cost unknown".
  * Add `cacheWritePricePerMTok`, and state that tiered pricing is ignored.
  * Add a CI check that every active model has all its prices or none.

**O14. Medium. Decision 30 vs the kill-switch fallback, and key replacement.** (comprehension, design)
* **Problem: the kill-switch fallback.** Line 1066 and J20 (line 815) switch a disabled model in use to a fallback model mid-conversation. Decision 30 forbids that.
* **Problem: key replacement.** M does not say whether replacing the key for the same provider ends the conversation.
* **Problem: the mockup.** The Settings rows for provider and model cannot be tapped, there is no confirmation dialog, and "Replace" has no flow (`Main` lines 249–253). The model pill in the top bar looks tappable but does nothing (`Main` line 115).
* **Fix:**
  * A kill switch on the current model ends the conversation, with "Start over with <fallback>" (gate row D35).
  * Replacing the key for the same provider keeps the conversation.
  * Draw the picker and the confirmation dialog.

**O15. Medium. M.2 consent and the N.2 route list.** (comprehension, implementation-readiness, structural)
* **Problem: consent placement.** M.2 recommends consent at the first Send but does not record it. The mockup builds it, but with one global flag (`Main` line 315), so changing provider would not ask again.
* **Problem: consent text.** The mockup's consent text says "PhotoCritic itself keeps no photos or messages" (`Main` line 190). Reports send the question and answer to Firestore, and the log keeps usage details.
* **Problem: the route list.** N.2 lists "consent" as a route, but a route cannot easily return "granted" to a pending send. The list lacks kill-switch, update-required and licences screens. N.2 does not say that every file under `src/app/` becomes a route.
* **Fix:**
  * Consent is an in-screen sheet owned by `features/consent`, which `SessionController` awaits. Store one record per provider and consent-text version (gate row D33).
  * Reword the text to: "PhotoCritic does not keep your photos. Questions and answers leave the device only if you send a report."
  * Replace the route list with a file list: `_layout.tsx` (gate logic), `index.tsx`, `setup.tsx`, `settings/…`, `settings/log.tsx`, `settings/licenses.tsx`, and the disabled and update-required screens.
  * Give the analytics switch `accessibilityRole="switch"`.

**O16. Medium. Decision 29. The extracted-text marker, streaming and Markdown.** (design, implementation-readiness, comprehension, inventive)
* **Problem: the prompt.** Adding the marker instruction only "for the Copy text starter and for any question that asks for text" changes the system prompt per turn. That breaks the stable prefix and caching (line 301) and needs intent detection.
* **Problem: undefined rendering rules.**
  * how a half-received fence looks while streaming;
  * what a stream that ends inside an open fence shows;
  * whether several cards per answer are allowed (the mockup shows one);
  * whether other Markdown is rendered;
  * what Copy copies;
  * what happens when the photo has no text (the fallback would offer Copy on "I can't see any text").
* **Problem: the chip name.** "Copy text" reads like a clipboard action, but it sends a question and costs a turn.
* **Fix:**
  * Put the marker rule in the one fixed base instruction.
  * Define the fence exactly (a fenced block tagged `text`).
  * Show the card in a receiving state until the fence closes, or as "Incomplete" if the stream ends first.
  * Write the parser as a pure function, tested on chunk sequences.
  * Add a "no text found" signal, with no Copy button.
  * Rename the chip "Read text".
  * Test structured output for the starter in the D3 spike (gate row D36).

**O17. Medium. M.1 and decision 28 vs item 22 and Goals line 1352. Choosing a model at first launch.** (design, comprehension)
* **Problem: the first-launch choice.** M.1 asks for a model at first launch. Item 22 (line 274) and Goals line 1352 say the provider is the only first-launch choice, with tiers and model IDs under Advanced.
* **Problem: setup copy.**
  * The mockup's text "Usage is billed to your account with that provider" (`Main` line 31) repeats a claim item 21 marked wrong for Gemini.
  * The BYOK explanation and the 18+ line (line 1351) are missing.
* **Problem: setup behaviour in the mockup.**
  * The key check fails only when the field is empty (`Main` lines 59, 390), with no checking state and no message per reason.
  * The "Where do I get a key?" link is generic.
  * A key pasted for one provider is kept when the user switches provider (`Main` line 363).
* **Fix:**
  * Offer Fast and Best as the choice, with model names in small print (gate row D39).
  * Fix the billing copy and add the BYOK and 18+ lines.
  * Draw the idle, checking and per-reason failure states.
  * Name the provider in the link.
  * Keep one draft key per provider.

**O18. Medium. M.5. The design-system method misreads its evidence and conflicts with its own advice.** (codebase-details, design, inventive, zoom-out, structural)
* **Problem: the evidence.** M.5 step 1 calls Callstack's token file "the same approach Callstack uses for its own products". `rnai/docs__DESIGN.md` says `brand.ts` governs only the website, and that the React Native example app "follows iOS system design (`PlatformColor`, SF Symbols)" (lines 11–14, 38).
* **Problem: fonts and theme.**
  * M.5 step 2 calls the system font the low-risk choice, but the mockup uses Space Grotesk and Inter.
  * Bundled fonts add start-up loading, Dynamic Type checks and licence notices.
  * The mockup loads the fonts from `fonts.googleapis.com` (`Main` line 12). The app must bundle them, or it makes an undisclosed transfer to Google.
  * The light theme is "values to define" (`System` line 51), while M.2 and M.5 assume light and dark.
* **Problem: resemblance.** Darkroom's near-black surfaces with one bright accent resemble Callstack's own brand family (DESIGN.md line 3). That matters if decision 32's "original" is meant strictly.
* **Fix:**
  * Cite DESIGN.md only as an example of a written token table.
  * Settle fonts and theme scope (gate row D32).
  * Bundle any font through `expo-font`.

**O19. Medium. M.5 "Darkroom" summary and the mockup tokens disagree in places, and the accent fails on light backgrounds.** (codebase-details, design, comprehension)
* **Problem: contrast.**
  * Amber #F5B13D on #0F1012 is about 10:1, which passes.
  * White on amber, and amber on white, are each about 1.9:1. In light mode that fails as text (4.5:1) and as a boundary (3:1).
  * The mockup's switch is a white thumb on an amber track (`System` line 85), about 1.9:1, below WCAG 1.4.11.
  * Text-card buttons are 32 px and "Copy answer" and "Report" about 26 px, below the mockup's own "Touch targets ≥ 44" (`System` line 71).
* **Problem: the accent rule.** The accent is labelled "Primary action only" (`System` line 47), but the mockup also uses it for log costs, user bubbles, tier tags, the switch and links.
* **Problem: radii.** M.5 gives 12/14/24, but the mockup uses 12, 14–16, 24 and 999. Its primary button uses 14, which contradicts "controls 12", and its card buttons use 8, which is not in the scale.
* **Problem: icons.** M.5 says to use Lucide or Phosphor, but the mockup's icons are hand-drawn. Phosphor is drawn on a 256 grid, not 24 px. Lucide's default stroke is 2 (verify). The set lacks share, delete, close or back, warning, and key.
* **Problem: fonts and icon formats.**
  * `ui-monospace` is not valid in React Native.
  * Expo's `icon` and `adaptiveIcon` take PNGs, not SVG. Keep the mark inside the 66 dp safe zone, and add `monochromeImage` (verify for the chosen SDK).
* **Fix:**
  * Copy the full token list into M.5.
  * Add an `accentOnLight` token, and a rule "never white on amber".
  * Use a dark switch thumb.
  * Make touch targets at least 44 pt.
  * Settle the radius scale.
  * State whether the icons are custom or Lucide; if Lucide, copy about 12 icons in with the licence kept.
  * Use Menlo on iOS and `monospace` on Android, or `tabular-nums`.

**O20. Medium. N.1. Several adopted practices go beyond, or against, the cited Callstack text.** (codebase-details, design, zoom-out)
* **Problem: React Compiler.** "Configured by Expo SDK 54 and later" overstates it. You still install `babel-plugin-react-compiler` and set `experiments.reactCompiler: true`, and lint support arrives only in SDK 55 (`rnbp/js-react-compiler.md` lines 60–76, 117).
* **Problem: measure first.** The cited guardrail says "Do not recommend memoization, atomic state, or compiler changes without a measured render or FPS problem" (`…react-native-best-practices__SKILL.md` line 49; also `rnbp/js-atomic-state.md` line 174).
* **Problem: open choices.** "Zustand (or Jotai)" and "FlashList v2 or Legend List" are left open.
* **Problem: CI.** The `github-actions` skill covers only simulator and emulator builds (its SKILL.md lines 3, 11). It does not cover signed store builds.
* **Fix:**
  * Correct the compiler wording.
  * Label the compiler and Zustand as the author's default architecture choice, not Callstack advice.
  * Pick Zustand.
  * Use EAS for signed builds.

**O21. Medium. N.1 lists inside the sheet.** (codebase-details, inventive, design, zoom-out)
* **Problem:**
  * `BottomSheetFlashList` is deprecated. FlashList v2 inside gorhom needs `useBottomSheetScrollableCreator()` passed as `renderScrollComponent` (`rnbp/js-bottomsheet.md` lines 200–214).
  * Plain React Native scrollables inside the sheet do not coordinate gestures (line 329).
  * Callstack's own decision matrix says "Small static content: ScrollView OK" (`rnbp/js-lists-flatlist-flashlist.md` lines 205–209). The conversation is short because of the history cap.
  * How FlashList v2 anchors a chat list at the bottom is unverified.
* **Fix:**
  * Use the sheet's scroll view for the conversation.
  * Use FlashList for the log, up to 1,000 rows.
  * If FlashList is used in the sheet, name the scrollable-creator pattern.

**O22. Medium. M.2 and M.3 accessibility beyond contrast.** (comprehension, design, codebase-details)
* **Problem: reading order.** The mockup reverses the message list and uses `column-reverse` (`Main` lines 132–133, 375), so VoiceOver reads the newest answer first, before its question. An inverted list in React Native has the same problem.
* **Problem: focus and controls.**
  * The photo behind the expanded sheet can still be focused.
  * The sheet needs expand and collapse actions.
  * Dialogs need to trap focus.
  * The photo's label cannot describe its content.
  * Tap-to-collapse and pinch-zoom on the photo conflict with the sheet's drag gesture.
* **Problem: Reduce Motion** is not mentioned.
* **Problem: platform support.**
  * `isReduceTransparencyEnabled` is iOS-only.
  * Android has no "reduce transparency" setting.
  * Whether React Native exposes Android high-contrast text depends on the version (verify).
* **Fix:**
  * Keep reading order oldest to newest.
  * Hide the photo from screen readers when the sheet is expanded.
  * Add sheet accessibility actions that announce the position.
  * Trap focus in dialogs.
  * Label the photo "Your photo".
  * Honour Reduce Motion (`useReducedMotion`).
  * Add an in-app "Solid backgrounds" setting, on by default when iOS Reduce Transparency is on.

**O23. Medium. M.2 new-photo flow, starter chips and labels.** (comprehension)
* **Problem: new photo.** In the mockup, confirming clears the conversation before the picker opens (`Main` lines 205–209, 397–399). If the user cancels the picker, the conversation is already gone.
* **Problem: labels.** M.2 says "Camera, Photos" and M.3 says "Take photo" and "Choose from Photos". "Photos" is an iOS name.
* **Problem: chips.** M does not say:
  * whether a chip sends at once or fills the input;
  * whether chips stay visible after the first question (they are hidden when expanded on phone but always shown on tablet);
  * whether the sheet auto-expands.
* **Problem: auto-scroll.** M does not say whether streaming pulls the view down while the user is reading higher up.
* **Fix:**
  * Clear the conversation only after a new photo is picked.
  * Use platform-neutral labels.
  * A chip sends at once.
  * Follow the stream only when the user is at the bottom; otherwise show "Jump to latest".

**O24. Medium. N.2 and decision 34. Image-editing preparation.** (zoom-out, design)
* **Problem: speculative structure.** An edit-description model (crop, rotate, markup, filters), designed before any editing UI exists, will be guessed wrong.
* **Problem: missing data-model points.**
  * The data model does not say which image version each turn used.
  * The pipeline order is unstated.
  * Picker and camera URIs often sit in a cache directory the OS can purge, so they cannot hold an original that must "never change".
  * N.2 does not say where the send copy lives or how it is cleaned up.
* **Fix:**
  * Drop the edit-description layer and the Skia note from v1.
  * Keep "original and send copy are separate; one function produces the send copy".
  * Each turn records an image version ID.
  * Pipeline order: original, then EXIF orientation, then edits (later), then resize, then strip metadata.
  * Copy the original into app-owned storage for the life of the conversation, and delete the send copy when the conversation ends.

**O25. Medium. Decision 30 vs the demonstration goal.** (inventive)
* **Problem:** The main demonstration is comparing models on the same photo. Decision 30 makes every comparison destructive (confirm, then clear), and N.2 does the same for every edit.
* **Fix:** Model `PhotoSession { original, variants[], conversations[] }`, where each `Conversation` is pinned to one variant, one provider and one model.
  * Decision 30 still holds: one conversation, one model.
  * A model change can later create a sibling conversation instead of clearing.
  * Ship the destructive behaviour in v1 if preferred (gate row D38).

**O26. Medium. N.1 telemetry gaps.** (codebase-details)
* **Problem:**
  * R8 obfuscates Android stack traces, but only iOS dSYM upload is planned (line 1358).
  * AI SDK `streamText` reports mid-stream failures as error parts, not throws (see O6).
  * The AI SDK Profiler's OpenTelemetry spans (`rnai/README.md` lines 26–28) may carry prompt and answer text.
* **Fix:**
  * Upload the R8 mapping file on every release build, or defer R8 (O32).
  * Keep AI SDK telemetry out of release builds and out of Crashlytics and Analytics.

**O27. Medium. N.1. M has no acceptance criteria or test plan.** (implementation-readiness)
* **Problem:** N.1 gives RNTL rules, but RNTL cannot test gestures, the keyboard or rotation. No end-to-end tool is chosen, and there are no frame-rate or start-up targets.
* **Fix:**
  * **Jest and RNTL:**
    * text-card parser on chunk sequences;
    * log cap, migrations, cost maths, missing price, partial usage;
    * confirmation rules;
    * disabled input with no photo;
    * setup errors keep what the user typed;
    * consent before send;
    * Reduce Transparency gives a solid background;
    * the two-pane switch from a mocked width.
  * **Device end-to-end tests** (Maestro or Detox):
    * keyboard over the sheet on both platforms;
    * dragging the sheet while the list scrolls;
    * rotation mid-stream;
    * Split View resize across the breakpoint;
    * camera capture in landscape;
    * process kill mid-stream, then check that the log row exists.
  * Contrast checks with white and busy fixture photos.
  * Frame-rate targets for sheet drags on the minimum device.

**O28. Medium. Decision 33 platform settings.** (codebase-details, zoom-out, implementation-readiness)
* **Problem: app config.** Expo templates default to `orientation: "portrait"`. Decision 33 needs `"default"`, `ios.requireFullScreen: false` and a resizable Android activity (verify the keys).
* **Problem: camera.** The camera may be unavailable in iPad multitasking (verify), so a `launchCameraAsync` failure needs a message.
* **Problem: Android large screens.** On large screens, Android may ignore a portrait lock for apps targeting API 36 (verify). So even a "phone only" v1 must stretch acceptably on Android tablets.
* **Problem: test devices.** M does not name the minimum tablet or foldable used for testing.
* **Fix:** Add these to M.4 and D9.

#### Low

**O29. Low. N.1 citations.** (structural, codebase-details)
* **Problem:**
  * "research/callstack/…js-bottomsheet.md" and similar are shortened paths, not real ones.
  * "…SKILL.md" could match four files.
  * Section H has no round-5 entries.
* **Fix:** Use full filenames, or define `rnbp/` once as section O does. Add the Callstack files to H.

**O30. Low. Numbering collisions in M.4.** (structural)
* **Problem:**
  * M.4's bullets "29."–"34." share numbers with section G items 29–34, and "item 31" is cited in N.1 and D3.
  * The M.4 heading says decisions 29–34, but decision 34 is detailed in N.2 and decision 28 is in M.1.
  * Decision 28 is recorded without M.2's objection.
* **Fix:**
  * Label the bullets "Decision 29." and so on.
  * Adopt the rule that "item N" means a finding in A–G.
  * Add an M.0 list of decisions 28–35, the way K.1 does for round 4.
  * Append a reviewer note to decision 28 pointing to O2.

**O31. Low. M.5 mockup link.** (structural)
* **Problem:** The link is a private claude.ai artifact. The local copies sit in a temporary scratchpad under `/private/tmp/…/scratchpad/pcmock/project/`, which will be deleted.
* **Fix:** Copy the files into the project (for example `design/mockup-darkroom/`) and cite that path.

**O32. Low. N.1 size and performance work.** (zoom-out, codebase-details)
* **Problem:**
  * R8 brings keep rules and release-only crashes.
  * Barrel-import rules and per-icon imports matter only for size.
  * TTI markers measure something the demonstrator does not report.
* **Fix:**
  * Defer R8.
  * Make the 16 KB alignment check (`zipalign -c -P 16`) a one-off whenever a native dependency is added.
  * Make the barrel rule a lint setting.
  * Measure time to first answer instead of cold-start TTI.

**O33. Low. Terms.** (comprehension, structural)
* **Problem:**
  * "Multi-line text box for the conversation" (M.1, decision 28) mixes up the input field and the panel.
  * "Session" means two lifetimes: `features/session/` and the "session snapshot" in `platform/config/`.
  * "Half" snap point, "text card", "tabular font option" and "Copy text starter" are undefined.
* **Fix:**
  * Define "conversation sheet" once.
  * Rename to `conversation/` and "launch config snapshot".
  * Define the remaining terms.

**O34. Low. Conversation log UI.** (comprehension)
* **Problem:** The mockup shows the model but not the provider, and adds a turn count. It has a fixed "older than 30 days" delete, with no undo or confirmation. M does not say whether the current conversation appears in the log, or where the log is opened from.
* **Fix:**
  * Add the provider.
  * Single delete with undo, plus "Delete all" with a confirmation.
  * State the entry point (Settings).
  * State whether the current conversation is shown.

**O35. Low. Conversation lost when the app is killed in the background.** (comprehension)
* **Problem:** M does not say that a background kill silently loses the conversation.
* **Fix:** State the loss in the privacy and help text, or keep the conversation until the next cold start.

#### New gate rows (decisions only the author can make)

| # | Decision | Options | Recommendation | Blocks |
| --- | --- | --- | --- | --- |
| D26 | **Resolved (decision 36): (a), then a Phase 0 sheet spike.** Expo SDK and React Native version; sheet library | (a) Latest stable SDK at Phase 0; (b) SDK 55 (ExecuTorch floor). Sheet: gorhom v5 on that SDK's Reanimated, or `react-native-true-sheet` | (a). A Phase 0 sheet spike on one low-end Android phone and one iPhone chooses the sheet (O5) | Every library pin, Phase 0 |
| D27 | **Resolved (decision 36): (b).** Photo and conversation surface (amends decision 28) | (a) 20% glass as written; (b) glass on chrome, solid pill or tint of at least 65% under all text; (c) photo above an opaque panel, no overlay | (b). (c) if the "photo shows through" look is not essential (O2) | Home screen, tokens |
| D28 | **Resolved (decision 36): (b).** Snap points | (a) Three (25/50/90%); (b) two, with the collapsed height sized to content | (b) (O9) | Sheet build, Dynamic Type |
| D29 | **Resolved (decision 36): (b).** Tablets and landscape (decision 33) | (a) Phone portrait only in v1, stretch-safe on Android; (b) fixed two-pane split with hysteresis, no divider; (c) resizable two-pane, as written | (b). Lenses split: zoom-out prefers (a). Under either, keep all conversation state outside layout components (O8, O12) | Layout, test matrix |
| D30 | **Resolved (decision 36): (c), no log in v1; the author will add it later.** Conversation log content (decision 31) | (a) As written, cost from a Remote Config price table; (b) tokens plus latency (time to first token, total), with a link to the provider's billing console; (c) drop the log | (b). If cost is kept, calculate it when the row is written and store the price date. Rewrite non-goals 1368 and 1378 under any option (O1, O11, O13) | Non-goals, Remote Config schema |
| D31 | **Resolved (decision 36): not needed, no log in v1.** Log storage | (a) `expo-sqlite` with `user_version` migrations; (b) one JSON file written atomically, consistent with K.3 | (a), which N.2 already assumes. Lenses split: codebase-details prefers (b) | Phase 1 storage |
| D32 | **Resolved (decision 36): (b).** Visual identity scope (decision 32) | (a) Full Darkroom: two bundled fonts, light and dark, custom icons, published page; (b) system font, dark only in v1, one accent plus `accentOnLight`, copied Lucide icons, custom app icon; (c) platform-native semantics plus accent and icon | (b) (O18, O19) | Tokens, components |
| D33 | **Resolved (decision 36): (b).** Consent placement and storage | (a) End of setup; (b) in-screen sheet at the first Send for each provider, stored per provider and text version | (b), as the mockup shows (O15) | Onboarding, `features/consent` |
| D34 | **Resolved (decision 36): (c), without the log.** Stop and partial answers | (a) No Stop; (b) Stop and discard; (c) Stop, keep the answer labelled "Stopped", copyable, excluded from history | (c) (O7) | Session state machine, log |
| D35 | **Resolved (decision 36): (a), without the log `endReason`.** Kill switch on the model in use (line 1066 vs decision 30) | (a) End the conversation with "Start over with <fallback>"; (b) fall back mid-conversation | (a) (O14) | Config handling, log `endReason` |
| D36 | **Resolved (decision 36): (a); test (b) in the D3 spike.** Extracted-text mechanism (decision 29) | (a) Fenced marker in the fixed base prompt; (b) structured output for the starter plus a tool for free-form questions, with the marker as fallback; (c) (b) plus platform OCR | (a) in v1, with (b) tested in the D3 spike; platform OCR in the local phase (O16) | Prompt, parser |
| D37 | **Resolved (decision 36): (a).** Local-phase provider scope | (a) Vision-capable on-device models only; (b) add an on-device OCR step for text-only models; (c) both | (a) for the first local build, with the device coverage written down. Add the capability and lifecycle methods to the interface now (O3) | Provider interface, Phase 5 |
| D38 | **Resolved (decision 36): (a), data model ready for (b).** Meaning of decision 30 | (a) Destructive restart, as written; (b) sibling conversations within one photo session | Ship (a) in v1. Build the data model (session, image version, conversation IDs) so (b) can be added (O25) | Session data model |
| D39 | **Resolved (decision 36): (a), the user picks the model.** First-launch choice | (a) Pick a model, as in M.1; (b) pick Fast or Best, with models under Advanced | (b), consistent with item 22 and Goals line 1352 (O17) | Setup screen |
| D40 | **Resolved (decision 36): (b) or (c) as recommended.** Phase 5 entry criterion | (a) A date; (b) first external tester build shipped; (c) harness baseline recorded for three cloud providers | (b) or (c), chosen now (O12) | Scope of v1 |
| D41 | **Resolved (decision 36): (a).** Markdown in normal replies | (a) Plain text, with text cards only; (b) a small rendered subset; (c) full Markdown | (a) for v1. Lenses split: comprehension prefers (b). Copy always gives plain text (O16) | Answer rendering |

## Goals vs. Non-Goals

### In-Scope Goals (v1)

* Camera capture and gallery pick through the system photo picker. No broad media permission; storage and audio permissions blocked and asserted in CI.
* Conversation about a single image. Starter prompts for identify, explain and extract text. Selectable answer text and the system share sheet.
* On-device conversion of every input (HEIC, AVIF and animated GIF included) to JPEG. EXIF orientation applied to the pixels, then all metadata stripped. Decode-time downsampling to one send target (about a 2048 px long edge), plus an explicit per-model `detail` / `resolution` setting. The image is re-sent every turn (decision 11).
* BYOK, with keys in `expo-secure-store` (`WHEN_UNLOCKED_THIS_DEVICE_ONLY` passed on every write; Android backup excluded), wiped on the first launch after a reinstall through the plain-file `install.json` marker (decision 21, K.2; sequence in L3). Key validated on entry, with an inference probe for Gemini. A plain explanation of BYOK before key entry (item 21), including that the key is not backed up.
* Provider and model choice at first launch and in Settings, from the curated model list in Remote Config (decision 36); "Fast"/"Best" are labels only. No free-tier pointer.
* Per-provider third-party AI consent screen with a versioned consent text, stating the per-provider facts in item 28 before the first send. Privacy policy in the app, and in the TestFlight / Play Console fields that require it (which ones: verify); App Privacy label and Play Data safety form before any public listing, and wherever the test tracks require them. 18+ age rating. A support contact for deletion requests (L13).
* In-app "Report" action on each answer, written to Cloud Firestore as text only (the user's comment plus the reported question and answer; no photo), with create-only rules that check every field, App Check enforced, a client-side daily limit per install (a UX guard, not a security control), a retention period with a working deletion mechanism (L1), a shown report ID, honest sent/queued/failed states, and the Analytics app instance ID only when sharing is on (decisions 22, 25, 26; K.6–K.7).
* Normalized error model: provider-specific parsing into internal classes, grouped into six user actions, with a corrected retry policy. Redacted "show details" with the provider request ID.
* Firebase Remote Config (React Native Firebase) holding the kill switch, provider settings (base URLs limited to the in-code host allowlist; header style in code), curated vision-model allowlist with tiers, image and conversation limits, error map, consent texts and privacy policy version, fetched at every cold start with a short first-screen timeout and a per-launch config snapshot, with bundled defaults, schema validation, one stated fallback rule (L8) and CI-published templates (decisions 13, 19, 23; I.3, I.5, K.2, K.4).
* Settings and per-provider consent records in plain files in the app document directory, written atomically, not kept across reinstall, each consent record carrying its consent-text version and UTC timestamp; sends blocked without current consent (decision 17; K.3; record format and rules in I.6).
* Firebase Analytics and Crashlytics behind a "Share usage and crash data" switch that is off by default for every user, with collection disabled at build time, the event list in K.5, one logging wrapper that strips text, keys and images (including automatically captured errors, L10), advertising ID off, and dSYM upload in each iOS build (decisions 20, 24).
* Firebase data collection (Remote Config: country, language, time zone, OS version, app and bundle or package ID, SDK and platform version; Firebase user agent; installation ID; for opted-in users Analytics events, Crashlytics and Firebase Sessions data and the linked instance ID; App Check attestation data; report text) declared in the App Privacy label, Play Data safety form and privacy policy, and stated in onboarding before the first fetch or with the first-fetch order recorded (L9).
* Distribution: TestFlight (internal testers on every build; external testers after Beta App Review) and the Google Play internal testing track (decisions 15–16).
* History cap after a stable prefix, and a notice that each follow-up re-sends the photo.
* Basic accessibility (screen-reader labels, Dynamic Type).
* A capability-based provider interface that can accept an on-device provider later without UI changes.
* An offline benchmark harness, outside the shipped app, for the demonstrator's measures: OCR accuracy per resolution setting, time to first answer, and error-fixture coverage, recording the served model and settings per run. This is the primary measure; K.5 field events are supporting data from opted-in testers.

### Out-of-Scope Non-Goals (v1)

* User accounts and persisted conversation history. "No backend" holds except for Firebase: Remote Config (read-only configuration), opt-in Analytics and Crashlytics (decisions 20, 24), and create-only Firestore reports protected by App Check (decision 22). No keys go to Firebase; user content goes there only inside a report the user chooses to send.
* Photos in reports (decision 25); Cloud Storage and Cloud Functions.
* User sign-in for reporting (unless the author adopts Anonymous Auth, L6).
* Notification to the developer when a report arrives (the console is checked by hand, K.6).
* Keeping keys, settings or consent across reinstall (decisions 17, 21).
* A public App Store listing and a Play production release (decisions 15–16). A later public release is intended (Apple 2.2).
* Offline use. A no-connection state is shown instead; the local-model phase would reopen this.
* On-device vision model inference.
* DeepSeek or any provider whose hosted API does not accept images.
* Canned demo content for reviewers.
* A per-session cost display (zoom-out; prices change within weeks).
* i18n beyond keeping strings in a translatable form.
* Explicit provider caching APIs and the Files APIs. Whether Anthropic automatic caching is on is decided by the Phase 0 cache spike (D11 open detail).
* Support for OS versions below the stated minimums.

## Alternatives Weighed

| Alternative | Status | Reason and reviewer notes |
| --- | --- | --- |
| DeepSeek as a provider | Rejected by author | Hosted API text-only, vision models open weights only, per mid-2026 knowledge. No DeepSeek evidence was fetched (verify, or restate as a scope decision). |
| Canned demo mode for app review | Rejected by author | Author's reasons: 4.2 risk, demo code shipped to all users, maintenance. Corrected note: 2.1(a) allows a built-in demo mode only "in lieu of a demo account", with Apple's prior approval. PhotoCritic has no login, so it hardly applies. 4.2 is about utility, not demos. The rejection stands on the code and maintenance reasons. |
| Local-first on-device model for v1 | Rejected by author (deferred) | Under the demonstrator rationale this is the headline comparison. Zoom-out suggests bringing on-device OCR into v1 if a public release follows (item 26). |
| Bare React Native workflow | Rejected by author | A dev build gives native-module access. llama.rn and ExecuTorch both support dev builds; both need the New Architecture. |
| Downscale to the minimum resolution | Rejected by author | Damages OCR on small text. A fixed send target is now required anyway by OpenAI's patch limit and Anthropic's high-res cost. Low-data mode needs a measured OCR floor or should be cut. |
| Downscale the displayed image | Rejected by author | `expo-image` downscales at render by default (`allowDownscaling: true`). Set its cache policy to avoid disk persistence (item 6). |
| Flutter, Kotlin Multiplatform, fully native | Not chosen (team skills) | Unchallenged. Pin the SDK upgrade policy; ExecuTorch needs Expo SDK 55+. |
| Own provider abstraction vs Vercel AI SDK (D3) | Open, lenses split | The documented AI SDK Expo path uses a server route and AI Gateway (item 31). Its error types are transport-level only. Thin adapters (design) gain support. The AI SDK behind your own interface (inventive-catalyst, codebase-details) remains possible only if the D3 spike passes the checks in item 31. |
| Fixed vs fetched vs user-typed model list (D5) | Near consensus; largely specified in I.3–I.4 | Curated, remotely updated allowlist, keyed by model, intersected with what the key can access, typed ID as advanced fallback. Silent re-routing means the allowlist must carry re-route dates. |
| Gemini only in v1 | Rejected by author | Decision 8. The phased order (Gemini first) still applies. |
| Image re-sent every turn vs image digest vs caching (D11) | Resolved: re-send every turn (decision 11) | The evidence favors a stable prefix plus Anthropic automatic caching over the digest (item 29). Gemini implicit caching is listed but its prefix rules are not covered. OpenAI's are not covered either. Don't promise savings; measure with cache usage fields. |
| Keep Gemini free-tier pointer (D10) | Rejected by author (round 3) | Google's terms say not to send personal information to unpaid services, and only paid services may be offered to users in the EEA, Switzerland and the UK (research/gemini-terms.txt). Removed in decision 10. |
| Static JSON on Firebase Hosting for remote config | Rejected by author (round 3) | Avoids the Firebase SDK and its data collection, but conditions by app version, rollback and staged rollouts would have to be written by hand. Remote Config chosen (section I.2). Round 4: decision 20 makes the Analytics SDK wanted, which removes this option's main advantage. |
| Report by email from the user's mail app (decision 14) | Replaced (round 4, decision 22) | The Android mail intent leaves the app, against Play's in-app reporting rule (J4); the developer's inbox would collect photos and email addresses. |
| `mailto:` link for reports | Obsolete (round 4) | No email path after decision 22. |
| Report endpoint (Firebase Function, Firestore or a web form) | Rejected round 3; Firestore adopted round 4 (decision 22) | Firestore with create-only rules and App Check, written directly from the app. Cloud Functions and a web form stay unused (Blaze needed for Functions, research/firebase-pricing-plans.txt). |
| Image as base64 inside the Firestore report document (Spark) | Rejected by author (decision 25) | Would fit the 1 MiB document limit at about 600 KB JPEG and hold about 1,000 reports in 1 GiB (research/firestore-quotas.txt). Removed with the photo. |
| Blaze plan with the image in Cloud Storage and a Cloud Function that emails each report | Rejected by author (decision 25) | Cloud Storage and Functions need Blaze (research/storage-faq-blaze.txt; research/firebase-pricing-plans.txt). More parts; would notify the developer. |
| Report retention on Spark: TTL policy vs Blaze vs manual purge | Open (D21 reopened, L1) | TTL deletes need billing (research/firestore-quotas.txt). Blaze with a budget alert costs close to nothing at tester volume; a scheduled Admin SDK purge stays on Spark but needs an owner. |
| Firebase Anonymous Auth for report writes | Raised round 4 (L6), not decided | A no-cost product (research/firebase-pricing-plans.txt) that gives rules a uid for per-user caps. Adds an SDK and an identifier to disclose; in tension with "no accounts". |
| Analytics opt-in only for EEA/UK/CH | Rejected by author (decision 24) | Opt-in for everyone instead. |
| Keep keys across reinstall (round-3 I.6 choice) | Rejected by author (decision 21) | Keys are wiped on the first launch after a reinstall. |
| Signed manifest (round 2, item 33) | Dropped (round 3) | Remote Config values arrive over Google-authenticated HTTPS and are not secret, so signing adds work against forged reads. It does not address a malicious publish (J3). Round 4: `authStyle` moved to code and `baseUrl` allowlisted (decision 23); the report address is gone (decision 22). A malicious publish can still change models, limits, error map, consent texts and user-facing URLs (L11). |
| Consent and settings in AsyncStorage or SQLite | Rejected round 3; superseded round 4 | Round 3 chose secure storage. Round 4 moved settings and consent to plain files in the document directory (decision 17, K.3); secure store holds keys only. |
| Public App Store release for the first version (D15) | Deferred by author (round 3) | TestFlight and Play internal testing only for the first release. Reduces the 4.2 risk of a public listing (2.2 still applies the guidelines) and keeps the audience small; costs 90-day build expiry on iOS and no store discovery. Revisit after the local-model phase. |
| Firebase App Distribution or a Play closed test for Android (D16) | Not chosen (round 3) | Play internal testing track chosen (decision 16). App Distribution would skip Play review (verify). |

## Key Decisions & Staging for Next Steps

### Decisions recorded (author)

1. Cloud first; on-device model is a later evaluation phase. Under decision 7 the local phase is the main thing to demonstrate.
2. Expo development build rather than bare React Native.
3. User selects provider and model. Reviewers recommend Fast/Best tiers with an Advanced override. (Round 5: tier recommendation rejected, decision 36; the user always chooses provider and model.)
4. Camera and gallery both supported. Reviewers recommend the system photo picker with no media permission.
5. Store review through a spend-capped reviewer key, plus the Gemini free-tier pointer. Reviewers now recommend a capped paid key as primary (item 19), and reopen the pointer (item 25). (Round 3 note: pointer part replaced by decision 10; the reviewer key is still needed for Beta App Review.)
6. Original shown at full quality; only a send-time copy is downscaled.
7. (Round 2) Product rationale: technology demonstrator, free, public release. Resolves D1. (Round 3 note: release channel superseded by decisions 15 and 16; a public release remains intended later.)
8. (Round 2) Three providers in v1 (OpenAI, Anthropic, Gemini). Resolves D2. Phased build order (Gemini first) is still recommended.
9. (Round 2, replaced by decision 10) Keep the Gemini free-tier pointer. Write an app privacy policy that covers, per provider: what is sent, how long the provider keeps it, whether the provider may train on it or have humans review it, and region limits. Show the same facts in the in-app consent screen. Feeds D6. Reviewer note: necessary but not sufficient; see D10.
10. (Round 3) Remove the Gemini free-tier pointer. Resolves D10. Onboarding links to each provider's key page without suggesting a free option. The 18+ age rating and developer-tool framing still apply (section I.1).
11. (Round 3) Send the downscaled image on every turn. Resolves D11. Digest and explicit caching stay out of v1. The fixed send target, per-model detail setting and history cap are required (section I.1).
12. (Round 3) Persist settings and consent records in secure local storage, with a version and timestamp on every consent record. Resolves D12. Design in section I.6. (Round 4 note: storage moved to plain files, secure store holds keys only; decisions 17 and 21, K.3.)
13. (Round 3) Host remote configuration on Firebase Remote Config: kill switch, provider settings, model allowlist, image and conversation limits, error map, consent texts, privacy policy version and report channel. Resolves D13. Design in sections I.2–I.5. (Round 4 note: report channel replaced by `report_policy`, decisions 22–23; fetched at every start, decision 19; provider host allowlist and header style in code, decision 23.)
14. (Round 3) AI-output reports open the user's mail app with the report details, the AI answer and the image attached, addressed to `yogeshkolte@gmail.com`; the address is set in Remote Config `report_channel`. Resolves D14. Design in section I.7. (Round 4 note: replaced by decisions 22 and 25.)
15. (Round 3) Initial release only through Apple TestFlight. Resolves D15 for iOS. The public App Store listing is deferred. Section I.8 lists what still applies. The Android channel is open (D16). (Round 3 note: closed by decision 16.)
16. (Round 3) Android initial release through the Google Play internal testing track. Resolves D16. Section I.8 lists what still applies.
17. (Round 4) Losing settings, preferences and consent records on reinstall is acceptable. Resolves D19 with K.3.
18. (Round 4) Firebase holds application configuration only (provider settings, model list, limits, error map, consent and privacy policy texts, kill switch, report address); no user data. Section K.1. (Round 4 note: amended by decision 22 to "configuration, plus reports the user chooses to send"; no report address after decision 23.)
19. (Round 4) The app fetches configuration at every start; the author owns backward compatibility of remote values. Section K.4.
20. (Round 4) Firebase Analytics and Crashlytics are in scope. Replaces the "no analytics by default" non-goal. Sections K.2 and K.5. (Opt-in per decision 24.)
21. (Round 4) API keys are stored only on the device, in secure store, and are wiped after a reinstall. Section K.2.
22. (Round 4) AI-output reports are written from inside the app to Cloud Firestore (create-only rules, App Check, image as base64 in the document, 90-day TTL), replacing email. Resolves D17. Amends decision 18 to "configuration, plus reports the user chooses to send". Section K.6. (Round 4 note: image removed by decision 25; the TTL needs billing, L1.)
23. (Round 4) Host allowlist in code for provider `baseUrl`; key header style in code. Resolves D18. Section K.6.
24. (Round 4) Analytics and crash reporting are opt-in for every user, off by default. Resolves D20. Section K.6.
25. (Round 4) Reports carry text only: the user's comment and the reported question and answer, no photo. Resolves D21 (free Spark plan, no Cloud Storage). Section K.7. (Objection, L1: TTL deletes are not free on Spark; D21 reopened for the retention mechanism only.)
26. (Round 4) The Google Analytics app instance ID is used as the user ID for Analytics, Crashlytics and reports, read only after the user opts in to sharing (decision 24). Section K.7. (Note: it identifies an install, not a person; L17 suggests narrower linking.)
27. (Round 5) Round-4 findings L1–L30 and gate rows D21–D25 are deferred by the author to development. They stay listed as open, not resolved. The brief now focuses on architecture, UX and visual design.
28. (Round 5) First launch opens a provider setup screen (provider, model, API key). The home screen offers camera and photo-library options, a multi-line conversation box the user can pull up for long answers, with an 80%-transparent background so the photo shows through, and a button to start over with a new photo. Section M.1; open points in M.2.
29. (Round 5) Extracted text shows as its own block with Copy and Share. Section M.4.
30. (Round 5) No provider or model switching mid-conversation; changing either ends the conversation after confirmation. Section M.4.
31. **Withdrawn (decision 36): no conversation log in v1; the author will add it later.** (Round 5) A local "Conversation log" screen keeps metadata only (start/end time, provider, model, tokens, estimated cost) and lets the user delete entries. Partly reverses the "no persisted history" non-goal. Section M.4.
32. (Round 5) An original visual identity with its own design system and icons. Section M.5.
33. (Round 5) Tablets and landscape in scope, with a two-pane layout on wide windows; English only. Section M.4.
34. (Round 5) Image editing is a planned future feature; the image pipeline keeps original, edits and send copy separate. Section N.2.
35. (Round 5) Follow Callstack's React Native practices for the New Architecture; provider layer is the Vercel AI SDK behind PhotoCritic's own interface, which also fits Callstack's on-device providers for the local phase. Resolves D3 subject to the device-call spike. Section N.
36. (Round 5, gate rows D26–D41) Author resolutions:
    * **No conversation log in v1 (D30, D31).** Decision 31 is withdrawn. Nothing about past conversations is stored on the device. The non-goals "persisted conversation history" and "per-session cost display" stand as written, and the privacy text in F row 6 stays accurate. This closes O1, O11, O13 and O34. Drop the log screen, the `log/` feature folder, the cost estimate and any Remote Config price table. The author will add a log later.
    * **The user always chooses provider and model (D39), at first launch and in Settings. This is final.** Fast/Best tiers are rejected as the choice; they may appear as labels in the model list. Item 22 and the Goals line are updated to match, which closes O17.
    * **All other rows take the reviewers' recommendation:** D26 pin the latest stable Expo SDK at Phase 0, then choose the sheet library by spike; D27 glass on controls, solid or at least 65% tint under all text; D28 two snap points, collapsed height sized to content; D29 fixed two-pane split with hysteresis on wide windows; D32 system font, dark only in v1, one accent plus `accentOnLight`, copied Lucide icons, custom app icon; D33 consent sheet at the first Send for each provider, stored per provider and text version; D34 Stop keeps the partial answer labelled "Stopped", copyable, excluded from history; D35 a kill switch on the model in use ends the conversation with "Start over with <fallback>"; D36 fenced marker in the base prompt for v1, structured output tested in the D3 spike; D37 vision-capable on-device models only in the first local build; D38 decision 30 stays destructive, with session, image-version and conversation IDs in the data model; D40 Phase 5 starts after the first external tester build ships or a harness baseline is recorded; D41 plain text in v1, copy always gives plain text.
37. (Round 5) No model name on the home screen, so the photo uses the full screen. The top bar has only Settings and New photo, and answers carry no model label. The model in use is shown only in Settings. Reports still record the model name (K.6).

### Decisions needed before any code (Phase 0 gate)

| # | Decision | Blocks |
| --- | --- | --- |
| D1 | ~~One-sentence product rationale.~~ Resolved: technology demonstrator. | — |
| D2 | ~~Number of providers in v1.~~ Resolved: three (OpenAI, Anthropic, Gemini). | — |
| D3 | Provider layer: thin adapters, AI SDK, or AI SDK behind your own interface. Also: which Gemini API surface and revision (Interactions with `Api-Revision`, or `generateContent`), plus pinned `anthropic-version`. Settled by the item 31 spike. | Transport, streaming, error mapping, Gemini `resolution` field name |
| D4 | Streaming in v1. Evidence supports yes (item 14). | Timeout/truncation definitions, classification points |
| D5 | Model-list strategy: curated allowlist keyed by model, tier bundles, re-route dates. Largely specified in I.3–I.4; request-shaping fields open (J12). | First-launch flow, retired-model handling |
| D6 | Consent screen design and versioned text (item 28), privacy policy, App Privacy label, Data safety form, 18+ age rating, Firebase disclosure in onboarding and its order relative to the first fetch (L9), sharing-switch wording, report disclosures and the deletion contact (L13). | Onboarding, submission |
| D7 | Send target and per-model `detail` / `resolution`; whether low-data mode stays, with a measured OCR floor. | Image pipeline, `settings.json` schema |
| D8 | Session lifecycle: backgrounding, process death, Android camera recovery, provider switch, discard confirmation, cache cleanup. | Chat screen, privacy text |
| D9 | Minimum OS (recommended iOS 17, Android minSdk 26); New Architecture on from v1; OTA `fingerprint` policy; RNFB dynamic iOS frameworks and Xcode 26.2 (J13); RNFB Crashlytics, Firestore and App Check packages and plugins, dSYM upload (L16, L9). | Build pipeline |
| D10 | ~~Gemini free-tier pointer.~~ Resolved: removed (decision 10). Tester regions still to set (tester invitations, I.8). | — |
| D11 | ~~Image re-send strategy.~~ Resolved: re-send every turn, with history cap (decision 11). Cap value and Anthropic automatic caching on/off still to set. | — |
| D12 | ~~Persisted-state schema and consent ledger.~~ Resolved: I.6 record format; storage replaced by K.3 (decisions 17, 21). J1 and J5 resolved; J21 still applies; L3 and L5 open. | — |
| D13 | ~~Remote manifest contract.~~ Resolved: Firebase Remote Config, sections I.2–I.5 (decision 13), fetched at every start (decision 19). Open details: the per-value size limit; the fallback rule (L8). J2 resolved by K.4 (gaps in L4); J7 dropped by decision 19. | — |
| D14 | ~~AI-output report delivery path.~~ Resolved, then superseded: in-app Firestore text report (decisions 22, 25; K.6–K.7). | — |
| D15 | ~~Public store release vs testing tracks.~~ Resolved: TestFlight (iOS, decision 15) and Play internal testing (Android, decision 16); section I.8. | — |
| D16 | ~~Android distribution.~~ Resolved: Play internal testing track (decision 16, section I.8). | — |
| D17 | ~~Android reporting.~~ Resolved: in-app report stored in Firestore (decision 22, section K.6). | — |
| D18 | ~~Which fields may be remote.~~ Resolved: host allowlist in code, header style in code (decision 23). Matching and redirect rules in L11. | — |
| D19 | ~~Storage location for the install marker, settings and consent.~~ Resolved: plain files in app storage, keys in secure store wiped on reinstall (decisions 17 and 21, section K.3). | — |
| D20 | ~~Analytics consent.~~ Resolved: opt-in for every user (decision 24). Still verify that no ATT prompt is needed with advertising ID off, and set the Android advertising-ID option and Play declaration. | — |
| D21 | **Deferred to development (decision 27).** Firebase plan for reports. Decision 25 chose Spark and text-only reports; reopened for retention only, because TTL deletes need billing (L1): Blaze with a budget alert, a scheduled manual purge on Spark, or manual deletion on request. | Privacy policy retention text, `report_policy`, Phase 4 |
| D22 | **Deferred to development (decision 27).** Report write protection: accept the open-write risk with App Check and rules, or add deterministic per-install document IDs or Anonymous Auth (L6). | Rules, privacy label |
| D23 | **Deferred to development (decision 27).** Reinstall and backup handling: exclude plain files from backup, Keychain-bound nonce, or accept restore (L3). | Phase 1 storage, K.3 |
| D24 | **Deferred to development (decision 27).** Config fallback rule (whole bundled set vs stored last-good set) and whether `onConfigUpdate` stays (L4, L8). | Phase 1 config |
| D25 | **Deferred to development (decision 27).** Crashes before opt-in: delete unsent reports, or ask on next launch (L7). | Crashlytics setup |

### Proposed staging

* **Phase 0 (setup and decisions):**
  * Scaffold the dev client with the New Architecture.
  * Set up the EAS profiles (development, preview, production), bundle IDs, credentials, and CI (typecheck, lint, Jest, merged-manifest permission check).
  * Create separate Firebase projects for development and production; add RNFB with `expo-build-properties` `ios.useFrameworks: "dynamic"`, the config plugins, and the Google services files (J8, J13). Packages: app, remote-config, analytics, crashlytics, firestore, app-check, pinned to one RNFB version.
  * In both projects: create the Firestore database (choose its location deliberately, L9), keep the rules file in the repository with an emulator test job in CI, register App Check (App Attest with the production entitlement, Play Integrity, debug provider tokens in development only, L16), and set collection-off defaults in `firebase.json` (key names: verify). Verify dSYM upload on one preview build.
  * Settle D3–D9 and D21–D25; close the open details in the D10, D11, D13 and D20 rows.
  * Run the spikes:
    * D3: direct-from-device calls to all three providers with streaming and abort, no `Origin` header, and reachable error bodies;
    * D7: OCR accuracy per send target and setting;
    * D11: cache usage fields per provider;
    * round 4: `getAppInstanceId` with collection off and on; Crashlytics unsent-report behavior; a Firestore write queued offline and the app killed; App Check on simulator, dev build, TestFlight and Play internal builds; expo-file-system move over an existing file; Android backup restore of document-directory files; Google Analytics reserved event names.
  * Write the compliance artifacts, the persisted-state contract and the reviewer-key runbook (one capped paid key for Beta App Review).
* **Phase 1 (image pipeline, local storage, remote config, telemetry plumbing):**
  * Capture/pick; HEIC/AVIF/GIF to JPEG; orientation; metadata strip; decode-time downsampling; `expo-image` cache policy; temp-file cleanup.
  * Install marker and key wipe per K.2 and L3; `settings.json`, `consent.json` and `reports.json` per K.3 with atomic writes and recovery (L5); analytics consent record (L12).
  * React Native Firebase Remote Config fetched at every cold start (`minimumFetchIntervalMillis` 0, explicit `fetchTimeoutMillis`, 3 s first-screen race), config snapshot and kill-switch handling per K.4 and L4, the fallback rule (L8), host allowlist and in-code header style (decision 23, L11), bundled defaults generated from the repository template, template JSON in the repository with CI validation and publishing (I.3, I.5).
  * Logging wrapper, sanitized global error handler and lint ban on direct Firebase logging and analytics calls (L10), before any feature code logs anything; the "Share usage and crash data" switch with its consent record and the opt-in-gated ID handling (decisions 24, 26).
  * Fixture tests: HEIC with GPS, AVIF, DNG, animated GIF (Android), corrupt JPEG, 48 MP image, extreme panorama.
* **Phase 2 (one provider end to end, Gemini):**
  * Provider interface, consent, key entry and the inference-probe validation.
  * Error mapping table with recorded-fixture contract tests.
  * Copy/share.
  * Remote Config `models`, `error_map` and `consent_texts` filled for Gemini.
  * K.5 events on the Gemini path, through the wrapper.
  * Error-table rows needing fixtures:
    * Gemini: 403 variants, 402, spend-rate 429 vs daily 429, safety 400 codes.
    * Anthropic: spend-limit 400, 402, 429 with and without retry-after, 413, 504/500 timeout, CORS 401.
    * OpenAI: each billing 429 code, `slow_down`, 503 overload, region 403.
* **Phase 3 (additional providers):** Anthropic, then OpenAI, against the same contract tests and the same K.5 events. Add the scheduled CI canary (live smoke call per provider and tier), or fold it into the offline harness (J22).
* **Phase 4 (polish and tester release):**
  * Settings and key management, accessibility pass.
  * AI-output reporting to Firestore (K.6–K.7) and the consent screens must be in the first tester build, because Play's reporting rule applies on the internal track and external TestFlight needs consent and a privacy policy (J17). Includes the retention mechanism chosen under D21, App Check enforcement after watching metrics, and the report UI states (L5, L13). Tests: rules emulator suite (L2); write without App Check rejected; daily limit; offline then online; app killed with a queued write; permission-denied path; report ID shown and copyable; no image field; `instanceId` present only when sharing is on; key redaction.
  * App Privacy label, Data safety form and privacy policy updated for Analytics, Crashlytics, Firebase Sessions, App Check, the linked instance ID, report text, retention and the deletion contact (L9), before the first external TestFlight build and the first Play internal release.
  * TestFlight: internal testers on every build, external testers after Beta App Review; resubmit significant updates. A build at least every 80 days. Android on the Play internal testing track (decisions 15–16), up to 100 testers (verify). Play Console declarations as required (verify). Public store releases deferred. Ask testers in the invitation to turn on sharing.
* **Phase 5 (on-device evaluation):**
  * Needs an entry criterion or date (item 1).
  * Feasibility: model size and download, RAM floor, memory entitlement, ExecuTorch telemetry disclosure, compatibility with dynamic iOS frameworks. Requires a new binary, not an OTA update.
  * Possible first step: on-device text extraction with no key, then the local-versus-cloud benchmark.

### Acceptance and measurement gaps to close in the spec

* Measurable thresholds:
  * request timeouts (connect and total);
  * retry/backoff policy and the maximum wait;
  * history cap;
  * time to first response and image-processing latency on the minimum device;
  * crash-free sessions target (measurable only for opted-in installs and TestFlight crash reports).
* Success measures: the offline benchmark harness (OCR accuracy per setting, latency, error rate per class against fixtures), recording the served model and settings; plus the K.5 Analytics events from opted-in testers only, reported with the opt-in rate.
* A provider × raw signal → error class → message ID table with one fixture per row. A CI check fails on unmapped codes. Fixtures carry a recording date and are re-recorded quarterly.
* A manual smoke test with live keys: invalid key, revoked key, unrestricted old Gemini key, free-tier-unavailable Gemini project, spend-capped key on each provider, re-routed Gemini model ID, oversize image on OpenAI.
* Remote Config tests: first launch offline on bundled defaults; corrupt JSON or an invalid value triggers the single fallback rule (L8) without a crash; `defaults` pointing at a missing model rejects the whole set; a remote `baseUrl` outside the allowlist is ignored and logged; kill switch delivered in real time during a stream aborts it; a kill switch arriving in a late fetch after the 3 s race is applied; disabled then lifted recovers through Try again without a restart; throttled fetch; version comparison 1.10.0 vs 1.9.0 for `minSupportedAppVersion`.
* Storage tests: consent write failure blocks the send; a kill during a `consent.json` write leaves the previous record intact; stray `.tmp` promoted or deleted; corrupt `consent.json` re-prompts and corrupt `settings.json` falls back to defaults; concurrent consent writes serialized; upgrade keeps keys; pre-marker upgrade does not wipe; crash between key delete and marker write; unreadable marker does not wipe; iOS reinstall on a physical device wipes keys and shows onboarding and consent again; keys missing while the marker exists shows key entry without an error; Android backup restore and iOS new-device restore behave as decided in D23; OTA rollback with a higher `consent.json` schema still lets the user re-consent.
* Telemetry tests: with sharing off, no Analytics or Crashlytics network traffic (proxy check); a crash before opt-in is handled as decided in D25; opting out clears the user ID on both SDKs; the event wrapper drops all events while sharing is off; an error containing a marker string never reaches Crashlytics; the App Check debug provider is absent from the production build; Crashlytics test reports use `crashlytics_debug_enabled` in debug builds.
