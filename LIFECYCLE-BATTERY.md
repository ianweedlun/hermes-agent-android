# Hermes Mobile — Lifecycle & Battery Contract

## Purpose
Guarantees reliable background agent execution without causing thermal runaway or draining battery on mobile hardware.

---

## 1. Foreground Service & Notification Transparency
- Background execution is anchored to an ongoing Android Foreground Service (`HermesDaemonService`) with type `specialUse` (Android 14+).
- The service notification displays real-time agent status (e.g. "Hermes Idle", "Running task: Building project...") and interactive actions ("Stop Daemon", "Open Terminal").

---

## 2. Power & Thermal Guardrails
- **Dynamic Throttling:**
  - When battery is below 20% and device is not charging, heavy background processes (local LLM inference, indexing) are suspended or throttled.
  - Thermal status is monitored via Android `PowerManager.OnThermalStatusChangedListener`. At `THERMAL_STATUS_SEVERE`, background jobs yield execution cycles.
- **WakeLock Policy:**
  - Partial wake locks (`PARTIAL_WAKE_LOCK`) must acquire with explicit timeout parameters to prevent permanent battery locks.
  - Wake locks are released immediately when the agent task queue is idle.

---

## 3. Process Supervision & Crash Recovery
- If the embedded PRoot daemon crashes or terminates unexpectedly, the Foreground Service executes an exponential backoff restart strategy (max 3 retries in 5 minutes).
- Unrecoverable crashes trigger a descriptive Android notification alerting the user, with option to inspect diagnostic logs.
