# Hermes Mobile — Security & Sandbox Boundary Contract

## Purpose
Establishes defense-in-depth isolation for running an embedded Linux runtime and LLM agent on Android.

---

## 1. Network Boundary
- **Loopback Strictly:** Daemon and local servers listen solely on `127.0.0.1`.
- **Network Security Config:** `res/xml/network_security_config.xml` permits cleartext HTTP only to `127.0.0.1` and `localhost`. All external traffic must use HTTPS/TLS.
- **Web Content Isolation:** WebView has `setAllowFileAccess(false)`, `setAllowContentAccess(false)`, and CSP headers configured to prevent arbitrary local file traversal via web content.

---

## 2. Credential & Secret Hygiene
- **Zero Credentials in Version Control:** Keystores, GitHub tokens, and API keys are blocked by `.gitignore`.
- **Logcat Redaction:** Native log output filters tokens, API keys, and sensitive prompts before logging or diagnostic bundle generation.
- **Android Keystore Integration:** Sensitive API tokens are stored encrypted using Android Jetpack `EncryptedSharedPreferences` backed by the hardware-backed Android Keystore.

---

## 3. Rootfs & Execution Security
- PRoot runs unprivileged (`proot -0`) within the application's Linux UID sandbox without requiring root or modifying system partitions.
- SUID binaries and kernel module loading are explicitly prohibited inside the container.
