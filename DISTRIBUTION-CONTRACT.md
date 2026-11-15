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
