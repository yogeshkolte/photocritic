# Implementation Readiness Report (Stage 9)

Status: concluded by the author after round 1 (2026-11-17). Round 1 fixes were applied (see goldfish_review_report.md); a round 2 was not run, so the review did not converge under the two-clean-rounds rule.

* **Target:** `docs/designs/photocritic.md`, lines 1334–2087 (Detailed
  Implementation Plan to end of file), native-apps revision of 2026-11-17
* **Date:** 2026-11-17
* **Round:** 1
* **Goldfish context:** `concepts_brief.md`, `mockup/`, `research/README.md`,
  `research/callstack/`, `research/openai-your-data.txt`, `docs/VERSIONS.md`,
  `docs/plans/native-migration.md`. The mockup URL was left out by the author.

## Reviewers

| Reviewer | Standard | Status | Raw findings |
| --- | --- | --- | --- |
| `egm-implementation-readiness` | implementation-readiness-standard | completed | 1 Blocker, 14 Major, 12 Nit |
| `egm-codebase-details` | implementation-readiness-standard | completed | 1 Blocker, 21 Major, 11 Nit |
| `egm-style` | (persona only) | completed | 0 Blocker, 5 Major, 12 Nit |

Both content reviewers give the verdict **FAIL** against the Zero-Decision
Rule. The plan names nearly every file with an exact path and states the
security settings, but it leaves schema, test-infrastructure, build and
phasing choices to the implementer.

## Consolidated findings

Raw IDs: `IR` = implementation readiness, `CD` = codebase details,
`ST` = style. Line numbers refer to the target as reviewed.

### 🔴 Blockers

| ID | Finding | Lines | Raw findings |
| --- | --- | --- | --- |
| B1 | The report document schema is not defined: field list, types, size limits, `category` values, error fields after `errorClass` was removed, `expireAt` bound, where the server timestamp is set, how the conversation is cut, resend and daily-limit behavior. The rules' item limit can reject long conversations. | 1485–1510, 1747–1750, 1802–1808 | CD-B1, IR-M4, CD-N5 |
| B2 | Session lifecycle and screen states are left open (Q4, Q13, part of Q12): exit from `image-error`, backgrounding, rotation and process death mid-stream, failed consent write, exact transition list. | 1394–1406, 1728–1739, 1810–1844 | IR-B1, CD-M13 |

### 🟡 Majors

| ID | Finding | Lines | Raw findings |
| --- | --- | --- | --- |
| M1 | Tuning values are deleted with `src/` before `shared/tuning.json` exists; its format and which values the author sets versus Phase 0 measures are not stated. | 1368–1373, 1425–1436, 1523–1528 | IR-M1, CD-M1 |
| M2 | Fixture JSON format, request-fixture inputs, the test image, Remote Config sample shape and the body comparison rule are undefined. | 1516–1564, 1849–1854 | IR-M2, CD-M11 (part) |
| M3 | Telemetry points to brief K.5, which keeps error classes, retry counts, a `queued` result and the reserved `session_start` name; triggers are not given; the reserved-names check was dropped from file 50. | 1776–1777, 2001–2009 | IR-M3, CD-M12 |
| M4 | Logic written twice has no shared vectors or constants: HistoryTrim factors and image-token formulas, ExtractedText cases, app-version comparison, ReportBuilder expected document. | 1684–1691, 1514–1571 | IR-M5, CD-M11 |
| M5 | Redaction patterns and the replacement text are not defined. | 1566–1571, 1692–1694 | IR-M6 |
| M6 | Remote Config display rules are missing: `status` values, `replacementId`, model `minAppVersion`, `bannerMessage`, `updateUrl`, `providers.enabled` versus `disabledProviders`, consent sheet fields, the two consent-version fields. | 1697–1705, 1823–1826 | IR-M7, CD-M14 |
| M7 | No dependency-injection approach; fake provider selection and fakes for Remote Config, reports and key storage are not specified; FakeProvider's `dataLeavesDevice` is unset. | 1720–1726, 1810–1815, 1864–1867, 1975–1976 | IR-M8, CD-M9 (part) |
| M8 | UI tests have no runner in CI (Robolectric or instrumented not stated); the `xcodebuild` command has no scheme or destination; XcodeGen schemes are missing. | 1451–1458, 1864–1867, 2073–2074 | IR-M9, CD-M9 (part), CD-M18 (part) |
| M9 | Swift Package Manager cannot use `shared/prompts/` as package resources; the way Swift tests read `shared/` is unstated. | 1536–1537, 1941–1942, 1970–1972 | IR-M10, CD-M5 |
| M10 | `checkPermissions` is ambiguous (own versus merged manifest, allowlist versus denylist, variant); Firebase merges permissions not listed, including ad-services ones; `AD_ID` needs its full name. | 1606–1621 | IR-M11, CD-M20, CD-N3 |
| M11 | Release signing is missing (Android keystore and properties, iOS team); "signed build at each phase end" cannot be met before the Apple account exists; the Swift live run has no runner. | 1350–1352, 1468–1469, 1599–1605, 1887–1896, 2051–2053 | IR-M12, CD-M18 |
| M12 | Harness: dataset and result locations, ground-truth and CSV formats, sizes, image encoding on the JVM (the pipeline is Android-only), canary model list, photo, secrets and schedule; `.gitignore` pattern misses `android/harness/results/`; "one harness is enough" overclaims. | 1445, 1465–1469, 1869–1882 | IR-M13, CD-M16, ST-M3, CD-N11 |
| M13 | Privacy-policy host and the support contact are not chosen. | 2013–2021, 1843 | IR-M14, CD-M21 (part) |
| M14 | Phases do not map to files; CI's `shared` job needs `tools/` and `shared/remote-config/` that no Phase 0 step creates; the Phase 0 Gemini client needs files 24–25; Gradle working directory unstated. | 1366–1392, 1448–1460 | CD-M2, IR-N1 |
| M15 | `assets/` deletion in the one-commit teardown depends on files created later. | 1369–1370, 1431 | CD-M3, IR-N2 |
| M16 | `.gitignore` rewrite could keep `/ios` and `/android` (ignoring the new apps) and drop secret-file rules. | 1441–1446 | CD-M4 |
| M17 | `Package.swift` platforms and tools version are missing, so `swift test` on macOS may not compile. | 1454–1455, 1928–1934 | CD-M6 |
| M18 | Lint rules as written cannot be built or contradict §2: `println` via ForbiddenImport, `okhttp3` allowed in providers, SwiftLint paths wrong, Analytics instance ID needed outside telemetry. | 1641–1645, 1923–1926 | CD-M7 |
| M19 | Build and test tools are not named: ktlint plugin, coverage tool, JUnit 4/5 mix, Node dependencies and versions, npm script names, emulator setup in CI, pinned XcodeGen and SwiftLint. | 1358–1360, 1453, 1505, 1575–1597, 1988–1996, 2060–2062 | CD-M8, IR-N7, IR-N8 |
| M20 | Unit tests missing for privacy-critical code: ImagePipeline, Cleanup, KillSwitch, Registry, ReportLimit, SecureKeys, RemoteConfigLoader (throttled path is in the wrong test), Allowlist, `checkPermissions`. | 1848–1863, 1970–1974 | CD-M10 |
| M21 | Some file entries are directories or globs; string resources, `proguard-rules.pro`, schemes and harness files are missing from the list. | 1540, 1655, 1846, 1869, 1928, 1945, 1967 | CD-M17 |
| M22 | OpenAI refusal events (`response.refusal.delta`) are not mapped, so a refusal would end as a normal empty answer. | 1560, 1710–1716 | CD-M19 |
| M23 | The consent gate is worded backwards: read literally, an on-device provider can never send. | 1744–1747 | CD-M15, IR-N5 |
| M24 | The lower re-encode quality and the outcome when still over `maxBytes` are not given; animated GIF handling is not mentioned. | 1797–1799 | CD-M21 (part), IR-N4 |
| M25 | File 3's "Why" claims every guarantee has an automatic check on both platforms; several are manual. | 1459–1460 | ST-M1 |
| M26 | File 23's "Why" says the §4 rules live in one file; three files are listed. | 1670–1671 | ST-M2 |
| M27 | File 38 is one 25-line paragraph covering 18 files. | 1820–1844 | ST-M4 |
| M28 | The 680–720 dp band is undefined (hysteresis not stated; initial mode unknown; iOS thresholds not restated). | 1831–1832, 1963 | ST-M5, IR-N6, CD-N4 |

### 🟢 Nits

| ID | Finding | Raw |
| --- | --- | --- |
| N1 | AVIF check range should be API 24–30, not 24–27. | IR-N3 |
| N2 | Manifest should reference `backup_rules.xml` via `fullBackupContent`. | IR-N9 |
| N3 | Privacy manifest: "user defaults if used" hedge; `ITSAppUsesNonExemptEncryption`, `NSPrivacyTracking`, collected data types. | IR-N10, CD-N8 |
| N4 | Proxy check does not say which Firebase hosts count as Analytics or Crashlytics traffic. | IR-N11 |
| N5 | "Show details" labels, `phase` display strings and the Gemini "uses a few tokens" note are missing from the screen files. | IR-N12 |
| N6 | `expo-env.d.ts` does not exist (generated, ignored). | CD-N1 |
| N7 | `firestore.rules` comment will go stale; §8a says rules use `MAX_REPORT_CONVERSATION_CHARS`, file 6 says they cannot check it. | CD-N2 |
| N8 | The `...` path shorthand is used for more packages than it defines. | CD-N6 |
| N9 | Name the exact Firebase Apple product for Analytics without the advertising ID. | CD-N7 |
| N10 | File 50 should land in the same commit as file 1. | CD-N9 |
| N11 | `ProviderHttp` cannot know the per-provider request-ID header; pass it in. | CD-N10 |
| N12 | Decision-list attribution: Q7 and Q9 were decided after review, not during drafting; Q12 is only partly resolved. | ST-N6 |
| N13 | Decision run-on sentences repeat Open Questions and Accepted Risks. | ST-N7 |
| N14 | "so one platform's lessons reach the other" is vague; point to AR4. | ST-N8 |
| N15 | File 8 parenthetical repeats AR3. | ST-N9 |
| N16 | File 15 "Why" repeats AR2. | ST-N10 |
| N17 | "up to 10 seconds" should use `CONFIG_FETCH_TIMEOUT_MS`. | ST-N11 |
| N18 | File 28 "to prove the screens cope" overstates. | ST-N12 |
| N19 | File 37 "Cleared state ends the conversation" is ambiguous. | ST-N13 |
| N20 | "CUJs 1–9" lists ten items, one not a CUJ. | ST-N14 |
| N21 | "the right detail or resolution setting" is not testable. | ST-N15 |
| N22 | "time to first word" versus "time to first token"; reference devices not named. | ST-N16 |
| N23 | Privacy policy drops OpenAI's "unless longer retention is required by law" qualifier. | ST-N17 |

## Traceability

Every raw finding maps to one consolidated ID above. Raw findings not listed
by ID were merged as noted: IR-N1 into M14, IR-N2 into M15, IR-N4 into M24,
IR-N5 into M23, IR-N6 into M28, IR-N7 and IR-N8 into M19, CD-N3 into M10,
CD-N4 into M28, CD-N5 into B1, CD-N11 into M12, ST-M3 into M12.

Resolution: see [goldfish_review_report.md](goldfish_review_report.md).
