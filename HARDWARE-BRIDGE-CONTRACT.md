# Hermes Mobile — Native Hardware & Telemetry Bridge Contract

## Purpose
Replaces external Termux:API dependencies with direct Android platform APIs exposed via native Kotlin interfaces.

---

## 1. Native Bridge Interfaces
All bridges expose structured JSON responses via the `@JavascriptInterface` bridge (`window.HermesHardware`) and local HTTP IPC endpoints:

### A. Battery & Power (`getBatteryStatus`)
- Backed by Android `BatteryManager` and `Intent.ACTION_BATTERY_CHANGED`.
- Returns: `{ percentage: Int, isCharging: Boolean, plugType: "ac"|"usb"|"wireless", temperatureC: Float, health: "good"|"overheat" }`.

### B. System Clipboard (`getClipboardText`, `setClipboardText`)
- Backed by Android `ClipboardManager`.
- `setClipboardText(text: String, isSensitive: Boolean)` clears after 60s if marked sensitive.

### C. Haptics & Vibration (`vibrate`)
- Backed by Android `VibratorManager` using predefined `VibrationEffect` compositions (`EFFECT_CLICK`, `EFFECT_TICK`, `EFFECT_HEAVY_CLICK`).

### D. Connectivity Telemetry (`getNetworkStatus`)
- Backed by Android `ConnectivityManager.NetworkCallback`.
- Returns: `{ isConnected: Boolean, type: "wifi"|"cellular"|"none", isMetered: Boolean, isOffline: Boolean }`.

---

## 2. Permissions Contract
- Zero dangerous permissions requested upfront.
- Hardware bridges gracefully degrade if optional permissions (e.g. precise location) are not granted.
