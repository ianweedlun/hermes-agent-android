# Hermes Mobile — Security & Sandbox Contract

## Purpose
Defines the isolation boundaries, credential protection rules, and permission constraints governing the Hermes Android client.

---

## 1. Zero Secret Ingress in Version Control
- Never store API keys, tokens, keystores, passwords, or connection strings in code or version control.
- Secrets in testing and CI are injected strictly via GitHub Actions Secrets (`${{ secrets.* }}`).
- Local credentials on device are saved to Android Keystore-backed `EncryptedSharedPreferences` or restricted app-private internal storage (`/data/user/0/ai.hermes.mobile/files/`).

---

## 2. Android Sandbox & Cleartext Traffic
- **Network Security Config:** `res/xml/network_security_config.xml` must prohibit cleartext HTTP except for explicit `127.0.0.1` loopback traffic for the local daemon.
- **Storage Isolation:** The PRoot rootfs and Hermes workspace reside exclusively in app-private storage (`context.filesDir`). No world-readable permissions (`0777`) are permitted.
- **Component Exporting:** All activities, services, and broadcast receivers must be marked `android:exported="false"` unless explicitly required (such as `MainActivity` with the launcher intent).

---

## 3. WebView Hardening
- `allowFileAccessFromFileURLs` and `allowUniversalAccessFromFileURLs` must remain `false`.
- `setGeolocationEnabled(false)` unless explicitly authorized by user intent.
- Deep links and external URLs must be intercepted via `shouldOverrideUrlLoading` and redirected to the system browser.
