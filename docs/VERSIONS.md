# Version table

Pinned at Phase 0 on 2026-11-17 for the native apps (design file 49). Each is
the latest stable release on that date unless the reason says otherwise.
Android versions live in `android/gradle/libs.versions.toml`; iOS versions in
`ios/project.yml`; Node tool versions in `tools/package.json`.

## Android

| Package | Version | Why this version |
| --- | --- | --- |
| Gradle (wrapper) | 9.9.1 | Latest stable |
| Android Gradle Plugin | 9.5.1 | Latest stable (9.6 is alpha) |
| Kotlin | 2.4.21 | Latest stable (2.5 is RC) |
| compileSdk / targetSdk / minSdk | 37 / 36 / 24 | Compile against the newest platform; target the level Play requires; minimum per Q2 |
| JDK | 17 (Zulu) | Build JDK; also CI. `android/gradle/gradle-daemon-jvm.properties` makes Gradle pick a JDK 17 even when the default Java is newer |
| Compose BOM | 2026.09.00 | Latest stable BOM |
| AndroidX core-ktx | 1.19.1 | |
| AndroidX activity-compose | 1.13.0 | Photo Picker contracts |
| AndroidX lifecycle | 2.11.0 | ViewModel, runtime-compose |
| Navigation Compose | 2.10.2 | |
| AndroidX ExifInterface | 1.4.2 | Orientation on the `BitmapFactory` path (§6) |
| kotlinx.coroutines | 1.11.0 | |
| kotlinx.serialization | 1.13.0 | |
| OkHttp (and mockwebserver3) | 5.5.1 | The one HTTP client (§4) |
| Firebase Android BoM | 35.2.0 | Remote Config, Analytics, Crashlytics, Firestore |
| Google services plugin | 4.6.0 | |
| Crashlytics Gradle plugin | 3.0.8 | Mapping upload on release |
| JUnit Jupiter | 6.1.3 | `:core` tests |
| JUnit 4 | 4.13.2 | `:app` (Robolectric, Compose UI tests) |
| Robolectric | 4.18 | Runs SDK 35 in tests (see notes) |
| Turbine | 1.2.1 | Flow tests |
| Kover | 0.9.11 | 80% line coverage on `:core` |
| detekt | 1.23.8 | Latest stable (2.0 is alpha); runs on Kotlin 2.0.21 internally |
| ktlint Gradle plugin | 14.2.0 | |

## iOS

| Package | Version | Why this version |
| --- | --- | --- |
| Xcode / iOS SDK | 27.2 | Installed on the build Mac |
| Deployment target | iOS 17.0 | Q2 |
| Swift | 6 language mode, tools 6.0 | |
| Firebase Apple SDK | 13.1.1 | Latest release older than four weeks (13.2.0 came out on 2026-11-13). Products: `FirebaseAnalytics` (without the `FirebaseAnalyticsIdentitySupport` add-on, so no advertising ID), `FirebaseCrashlytics`, `FirebaseRemoteConfig`, `FirebaseFirestore` |
| XcodeGen | 2.47.0 | Generates `PhotoCritic.xcodeproj` from `ios/project.yml` |
| SwiftLint | 0.65.1 | |

## Tools and tests (Node)

| Package | Version | Why this version |
| --- | --- | --- |
| Node | 22 | CI and local |
| ajv / ajv-formats | 8.20.0 / 3.0.1 | Remote Config schema validation |
| tsx | 4.23.15 | Runs the TypeScript tools |
| typescript | 6.0.3 | Type check only; 7.0 is the new native compiler and is not adopted yet |
| @types/node | 26.6.6 | |
| firebase-tools, @firebase/rules-unit-testing | 15.36.0, 5.0.2 | Firestore rules tests (Phase 4, file 8) |

## Platform minimums

| Platform | Minimum | Source |
| --- | --- | --- |
| iOS | 17.0 | Design Q2 |
| Android | API 24 (Android 7.0) | Author decision 2026-11-15. Jetpack Compose allows 21; 24 is kept |

## Upgrade rules

- Update the version catalog, `ios/project.yml` or `tools/package.json`
  and this table in the same change.
- All Firebase Android libraries follow the BoM; the Firebase Apple products
  move together.
- A provider API version (Anthropic `anthropic-version`, Gemini `v1beta`)
  changes in both apps in the same pull request.

## Notes found while pinning

- Robolectric 4.18 needs Java 21 to emulate SDK 36; the build uses JDK 17,
  so the JVM UI tests run on SDK 35 (`app/src/test/resources/robolectric.properties`).
  Move to SDK 36 in tests when the build JDK moves to 21.
- detekt 1.23 allows one `ForbiddenImport` list per project, so the
  "OkHttp only in `core/net`" rule is the root Gradle task `checkLayering`.
- The merged `prodRelease` manifest has exactly these permissions:
  `INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`,
  `BIND_GET_INSTALL_REFERRER_SERVICE`,
  `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (checked 2026-11-17).
- Firebase Apple SDK 13 makes advertising-ID support an opt-in product
  (`FirebaseAnalyticsIdentitySupport`); the app does not add it.

## Earlier (Expo) findings that still apply

- Gemini returns no request-ID header; its body `responseId` is the only ID,
  and failed calls carry none (2026-11-17).
- OpenAI's Responses API keeps response data for 30 days unless the request
  sends `store: false`.
