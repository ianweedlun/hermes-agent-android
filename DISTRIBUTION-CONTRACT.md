# Hermes Mobile — Release & Distribution Contract

## Purpose
Defines versioning, automated signing, GitHub Releases packaging, and on-device update mechanics.

---

## 1. Semantic Versioning & Tagging
- Versions follow `vMAJOR.MINOR.PATCH` (e.g. `v0.1.0`).
- Tagging `v*.*.*` automatically triggers the GitHub Actions Release workflow (`.github/workflows/release.yml`).

---

## 2. Automated Signing
- Production release APKs are signed in GitHub Actions using a release keystore passed via repository secrets (`ANDROID_KEYSTORE_BASE64`, `KEY_ALIAS`, `KEY_PASSWORD`).
- Pull request builds continue using standard debug signing keys.

---

## 3. Artifact Packaging
- Universal or `arm64-v8a` APK published as release asset (`hermes-agent-vX.Y.Z-arm64-v8a.apk`).
- SHA-256 checksums automatically generated and appended to the release description.
- In-app update checker queries GitHub Releases API for new version tags.

---

## 4. Verification Gate (Air Lab Requirement)
- **Strict Pre-Install Gate:** No build may be designated for user installation or placed in `~/storage/downloads` without prior validation in the MacBook Air test lab (AVD emulator harness `scripts/run-device-tests.sh`).
- **Zero-Ian-Touch Sprint Protocol:**
  - Automated fix loops run autonomously against lab failures.
  - User notification occurs ONLY upon a fully verified green APK.
- Every build candidate must produce an explicit lab test report verifying:
  1. APK installation & package integrity
  2. Foreground service initialization (`HermesDaemonService` running, `isForeground=true`)
  3. PRoot binary presence and execution
  4. Local daemon port binding (`127.0.0.1:8787`) and readiness probe success
  5. WebView UI loading without `ERR_CONNECTION_REFUSED`
- User installation is blocked until the Air lab test report is presented with passing assertions.
