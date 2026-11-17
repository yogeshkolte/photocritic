# Phase 0 results

Status of each Phase 0 item (design: Implementation order, file 67).
**Pass/fail** is filled in with evidence (device, build, date). Spike code
lives in `src/spikes/`, `src/app/spike.tsx` and `spikes/`, and is deleted when
Phase 0 ends.

## Done on 2026-11-15

| Item | Result |
| --- | --- |
| Scaffold (Expo SDK 58, Expo Router, TypeScript strict, `src/app/`) | Done |
| Exact version pin, `docs/VERSIONS.md` | Done; `expo install --check` and `expo-doctor` (20/20) pass |
| Platform minimums (Q2): iOS 17, Android API 24 | Set in `app.config.ts`; confirmed allowed by the installed React Native, Expo and Firebase |
| Firebase collection-off keys (file 7) | Key names verified against installed sources; set in `firebase.json` |
| Tuning constants (§8a) | `src/platform/config/tuning.ts`; seven values PROVISIONAL |
| Lint layering rules, Jest, CI workflow | `npm run typecheck`, `npm run lint`, `npm test` pass locally |

## Needs the author

| Item | What is needed |
| --- | --- |
| Git repository and GitHub remote | `git init`, first commit, push; CI then runs `.github/workflows/ci.yml` |
| Firebase projects `photocritic-dev`, `photocritic-prod` | Done 2026-11-16: both registered as `com.yk.photocritic`; service files in `firebase/dev/` and `firebase/prod/`, checked against `app.config.ts` |
| Expo account and EAS | Done 2026-11-16: project `@yogeshkolte/photocritic`, ID in `app.config.ts` (`extra.eas.projectId`) |
| Apple Developer account | Needed for device builds and TestFlight |
| One API key per provider, with a spend cap | For the AI SDK checks below |
| A physical iPhone and Android phone (API 24–27 device if possible) | Device checks below |

## AI SDK setup checks (Q1)

Run on a release build: `eas build --profile spike`, open the app, tap "Open
AI SDK setup checks". A first pass can run under Node:
`SPIKE_PROVIDER=gemini SPIKE_MODEL=<id> SPIKE_API_KEY=<key> npm run spike:ai`.
A failure is worked around inside the AI SDK setup (AR1).

| Check | Gemini | Anthropic | OpenAI |
| --- | --- | --- | --- |
| Custom fetch used, allowlisted host, key not in URL | | | |
| No Origin header reaches the provider | | | |
| Streaming with an image | | | |
| Request ID header readable (record the header name) | | | |
| Image detail or resolution reaches the request body | | n/a | |
| Stop (abort) ends the stream | | | |
| Invalid key gives a readable error, one request only | | | |
| `redirect: 'error'` honored by `expo/fetch` | | (same) | (same) |
| Mid-stream error event readable (manual: cut the network mid-answer) | | | |
| Gemini API surface and version used | | | |
| OpenAI API used (Responses is the v7 default) | | | |

## Other Phase 0 checks

| Check | Result |
| --- | --- |
| Q3 sheet library: both candidates on the pinned SDK, drag, keyboard, FlashList inside, Reanimated 4 | |
| Q5 image-size accuracy (text extraction at several long-edge sizes) | |
| Q5 history cap value (sets `HISTORY_TURNS_CAP`) | |
| Anthropic automatic caching fields | |
| `getAppInstanceId` with collection off and on | |
| Crashlytics unsent reports before opt-in (Q10) | |
| Firestore persistence off leaves no report text on disk | |
| Atomic file move (write temp, rename) on both platforms | |
| HEIC and AVIF on Android API 24–27 show the generic error | |
| Analytics reserved event names | |
| Timeouts: `CONNECT_TIMEOUT_MS`, `REQUEST_TIMEOUT_MS` from measured first-token and total times | |
