# Spec: Hermes Mobile — V1

## Objective
A unified, native Android application for Hermes Agent that integrates an embedded lightweight Linux runtime (via PRoot), a full-screen modern WebView hosting the Hermes WebUI PWA, a native sliding terminal bottom sheet, an ongoing Android Foreground Service with custom Hermes status branding and notifications, and an automated GitHub Actions CI/CD pipeline.

Target GitHub Account: `damnithermes`
Target Repository: `damnithermes/hermes-mobile`

---

## Capability Map (Phase 0)

| Module ID | Responsibility | Depends On |
|---|---|---|
| `core-service` | Android Foreground Service, lifecycle management, wake locks, and Hermes-branded NotificationManager bridge | — |
| `runtime-engine` | Bundled PRoot binary + Alpine rootfs extraction, bootstrap setup, and process supervisor for the Hermes daemon | `core-service` |
| `ui-shell` | Full-screen Hardware-accelerated WebView (PWA shell) with offline caching and JavaScript IPC bridge | `core-service` |
| `terminal-sheet` | Native Android sliding bottom-sheet hosting an interactive PTY/terminal view connected to the Hermes daemon | `runtime-engine` |
| `ci-automation` | GitHub Actions workflow compiling, testing, and signing Android APK releases on commit/tag | All |

Build Order: `core-service` → `runtime-engine` → `ui-shell` → `terminal-sheet` → `ci-automation`

---

## Tech Stack
- **Language:** Kotlin 2.0+ (Android)
- **Build System:** Gradle (Kotlin DSL: `build.gradle.kts`) with Version Catalogs (`libs.versions.toml`)
- **Target SDK:** Android 15 / 16 (API 35/36), Min SDK: Android 10 (API 29)
- **Architecture:** Android Jetpack (Lifecycle, ViewBinding / Compose, Coroutines/Flow)
- **Terminal View:** `com.termux.terminal:terminal-view` or lightweight native PTY emulator
- **Runtime Engine:** Bundled `proot` aarch64 binary + compressed Alpine Linux ARM64 rootfs
- **CI/CD:** GitHub Actions runner (`ubuntu-latest`) with Temurin JDK 17 & Android SDK setup

---

## Commands
- **Lint & Code Style:** `./gradlew ktlintCheck` or `./gradlew lint`
- **Unit Tests:** `./gradlew testDebugUnitTest`
- **Assemble Debug APK:** `./gradlew assembleDebug`
- **Assemble Release APK:** `./gradlew assembleRelease`
- **CI Verification:** `.github/workflows/build.yml` triggered on push

---

## Project Structure
```
hermes-mobile/
├── .github/
│   └── workflows/
│       └── build.yml               # CI build & release workflow
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/org/nousresearch/hermes/
│   │   │   │   ├── core/           # Foreground service & notification manager
│   │   │   │   ├── engine/         # PRoot extractor & daemon supervisor
│   │   │   │   ├── ui/             # WebView PWA host & sliding terminal sheet
│   │   │   │   └── bridge/         # JS <-> Android native interface
│   │   │   ├── res/                # Drawables, layouts, Hermes vector icons
│   │   │   └── AndroidManifest.xml
│   │   └── test/                   # Unit tests
│   └── build.gradle.kts
├── gradle/
│   └── libs.versions.toml          # Centralized dependency catalog
├── build.gradle.kts
├── settings.gradle.kts
├── SPEC-hermes-mobile.md           # This specification document
└── README.md
```

---

## Code Style & Conventions
- **Idiomatic Kotlin:** Explicit return types on public APIs, immutable state where possible, null-safety strictly enforced.
- **Logging:** Structured via Android `Log` or Timber with strict sanitization (no tokens or credentials logged).
- **Android Security:** Cleartext HTTP traffic restricted to `127.0.0.1` (network security config); strict sandbox permissions.

---
## Boundaries
- **Always Do:**
  - Centralize dependencies in `gradle/libs.versions.toml`.
  - Keep secrets (keystores, GitHub tokens) strictly out of version control (.gitignore enforced).
  - Verify clean lint and unit tests before any commit.
- **Ask First:**
  - Changing package identity or min SDK level.
  - Adding third-party native libraries (.so / JNI) or heavy dependencies.
- **Never Do:**
  - Hardcode authentication tokens or API keys.
  - Request unnecessary Android permissions (camera, location, contacts) outside core runtime requirements.

---

## Success Criteria
1. GitHub repository `damnithermes/hermes-mobile` initialized with Git, remote tracking, and clean commit history.
2. Standard Gradle build structure with wrapper and version catalog in place.
3. GitHub Actions CI workflow compiles clean debug APK artifact on push.
4. Notification system posts alerts with the authentic Hermes emblem and title in Android SystemUI.

---

## Out of Scope (Deferred to V2)
- Google Play Store billing and closed-track distribution (V1 focus is direct APK / GitHub Releases).
- Multi-architecture APKs for x86_64 / armv7 (V1 target is strictly `arm64-v8a` for modern Android devices).

---

## Open Questions
1. What is the preferred email address for git commit attribution under `damnithermes`?
2. Do you want the repository to be **public** or **private** on GitHub?
