# Hermes Mobile — Lifecycle & Battery Budget Contract

## Purpose
Prevents OS process kills, thermal runaway, and unexpected battery drain under Android 14+ process freezing policies.

---

## 1. Foreground Service Architecture
- **Service Class:** `HermesDaemonService` bound as `android:foregroundServiceType="specialUse"`.
- **Notification Channels:**
  - `channel_daemon_running`: Ongoing notification showing Hermes logo and current activity (e.g. "Hermes: Ready", "Hermes: Running task #3").
  - `channel_alerts`: High-priority channel for task completion and human input requests.
- **WakeLock Policy:** Acquire `PARTIAL_WAKE_LOCK` **only** during active tool execution and release immediately upon transitioning to `idle`.

---

## 2. Battery & Thermal Throttling Contract
- **Low Battery Threshold (< 15% without AC):**
  - Heavy subagent tasks and local LLM inference are deferred.
  - Heartbeat intervals scale from 30s to 120s.
- **Thermal Overheat (`BATTERY_HEALTH_OVERHEAT`):**
  - Immediate pause of CPU-intensive workloads; native toast warning posted.

---

## 3. Crash Recovery & Resilience
- **Watchdog Supervisor:** Kotlin coroutine monitors the PRoot daemon PID.
- **Restart Policy:** If the daemon terminates unexpectedly:
  - Restart with exponential backoff (1s, 2s, 4s, up to 3 retries).
  - Restore active context via `ai-memory` handoff snapshot without losing uncommitted user instructions.
