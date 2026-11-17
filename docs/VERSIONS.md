# Version table

Pinned at Phase 0 on 2026-11-15 (design D26). `package.json` holds exact
versions for every dependency; `npx expo install --check` must pass in CI.

| Package | Version | Why this version |
| --- | --- | --- |
| `expo` | 58.0.25 | Latest stable SDK at Phase 0 |
| `react-native` | 0.88.1 | Version Expo SDK 58 selects; New Architecture only (no opt-out key exists in SDK 58) |
| `react` | 19.3.0 | Version Expo SDK 58 selects |
| `react-native-reanimated` | 4.7.1 | Expo SDK 58 selection; needs `react-native-worklets` |
| `react-native-worklets` | 0.13.0 | Required peer of Reanimated 4 |
| `react-native-gesture-handler` | 3.3.0 | Expo SDK 58 selection |
| `react-native-screens` | 4.30.1 | Expo SDK 58 selection |
| `expo-router` | 58.0.31 | Expo SDK 58 selection |
| `@shopify/flash-list` | 2.3.2 | FlashList v2, Expo SDK 58 selection |
| `ai` | 7.0.173 | AI SDK v7 (final provider layer, Q1) |
| `@ai-sdk/openai` | 4.0.118 | Matches `ai` 7 |
| `@ai-sdk/anthropic` | 4.0.105 | Matches `ai` 7 |
| `@ai-sdk/google` | 4.0.121 | Matches `ai` 7 |
| `@react-native-firebase/*` | 27.1.1 | All modules at one version; Firebase Apple SDK 13 (Swift Package Manager only), needs `useFrameworks: "dynamic"` |
| `zustand` | 5.1.0 | State store |
| `zod` | 4.6.7 | Config schemas |
| `typescript` | 6.0.3 | Expo SDK 58 template |
| `jest-expo` | 58.0.14 | Matches the SDK |
| Sheet library | not chosen yet | Q3: `@gorhom/bottom-sheet` 5.2.14 or `@lodev09/react-native-true-sheet` 4.1.0; both declare Reanimated 4 support |

## Platform minimums

| Platform | Minimum | Source |
| --- | --- | --- |
| iOS | 17.0 | Design Q2. Expo SDK 58 allows 16.4, React Native 0.88 15.1, Firebase 15.0 |
| Android | API 24 (Android 7.0) | Author decision 2026-11-15. React Native 0.88 and React Native Firebase 27 both require 24 |

## Upgrade rules

- Upgrade Expo with `npx expo install expo@<sdk> --fix`, then re-pin exact
  versions and update this table.
- All `@react-native-firebase/*` packages move together.
- `ai` and the `@ai-sdk/*` providers move together; in Phase 5 they also move
  with React Native AI.

## Notes found while pinning

- AI SDK v7 has `streamRetries` in addition to `maxRetries`. Both are set to
  `0` (design G12: no automatic retries).
- AI SDK v7's Google factory is `createGoogle` (not `createGoogleGenerativeAI`).
- OpenAI per-image detail is passed on the image part as
  `providerOptions.openai.imageDetail`; Gemini resolution is a call option,
  `providerOptions.google.mediaResolution`.
- `expo/fetch` types declare a `redirect` option; whether the native side
  honors `redirect: 'error'` is a device check (docs/spikes/phase0.md).
- React Native Firebase `firebase.json` key names were checked against the
  installed native sources (design file 7).
- Android activities are resizable by default at this target SDK; the app
  config has no separate key for it (design file 2, "verify key names").
