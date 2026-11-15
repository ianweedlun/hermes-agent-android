package ai.hermes.mobile.core

/**
 * Execution lifecycle states reported by the Hermes daemon over IPC (`daemon/status`).
 */
enum class AgentState(
    val wireName: String,
    val displayName: String,
    val requiresWakeLock: Boolean,
) {
    IDLE(wireName = "idle", displayName = "Idle", requiresWakeLock = false),
    THINKING(wireName = "thinking", displayName = "Thinking", requiresWakeLock = true),
    EXECUTING_TOOL(wireName = "executing_tool", displayName = "Executing Tool", requiresWakeLock = true),
    ERROR(wireName = "error", displayName = "Error", requiresWakeLock = false),
    ;

    companion object {
        /**
         * Maps a wire-protocol state identifier from `IPC-CONTRACT.md` to [AgentState].
         * Defaults to [IDLE] for null or unrecognized values.
         */
        fun fromWireName(value: String?): AgentState {
            val normalized = value?.trim()?.lowercase()
            return entries.firstOrNull { it.wireName == normalized } ?: IDLE
        }
    }
}

/**
 * Platform-independent representation of Android `PowerManager` thermal status levels.
 */
enum class ThermalLevel(
    val statusCode: Int,
    val isThrottled: Boolean,
    val label: String,
) {
    NONE(statusCode = 0, isThrottled = false, label = "Normal"),
    LIGHT(statusCode = 1, isThrottled = false, label = "Light"),
    MODERATE(statusCode = 2, isThrottled = false, label = "Moderate"),
    SEVERE(statusCode = 3, isThrottled = true, label = "Severe"),
    CRITICAL(statusCode = 4, isThrottled = true, label = "Critical"),
    EMERGENCY(statusCode = 5, isThrottled = true, label = "Emergency"),
    SHUTDOWN(statusCode = 6, isThrottled = true, label = "Shutdown"),
    ;

    companion object {
        /**
         * Converts an Android `PowerManager.THERMAL_STATUS_*` code into [ThermalLevel].
         */
        fun fromPlatformStatus(statusCode: Int): ThermalLevel =
            entries.firstOrNull { it.statusCode == statusCode }
                ?: if (statusCode > SHUTDOWN.statusCode) SHUTDOWN else NONE
    }
}

/**
 * Immutable snapshot of the Hermes daemon status matching `daemon/status` in `IPC-CONTRACT.md`.
 */
data class DaemonStatus(
    val state: AgentState = AgentState.IDLE,
    val activeTool: String? = null,
    val stepIndex: Int = 0,
    val sessionId: String? = null,
    val thermalLevel: ThermalLevel = ThermalLevel.NONE,
) {
    val isThrottled: Boolean
        get() = thermalLevel.isThrottled

    val shouldHoldWakeLock: Boolean
        get() = state.requiresWakeLock && !thermalLevel.isThrottled
}

/**
 * Immutable snapshot of daemon resource telemetry matching `daemon/telemetry` in `IPC-CONTRACT.md`.
 */
data class DaemonTelemetry(
    val uptimeSeconds: Long,
    val rssBytes: Long,
    val cpuPercent: Double,
    val activeModel: String,
) {
    init {
        require(uptimeSeconds >= 0L) { "uptimeSeconds must be non-negative" }
        require(rssBytes >= 0L) { "rssBytes must be non-negative" }
        require(cpuPercent >= 0.0) { "cpuPercent must be non-negative" }
    }
}

/**
 * Priority levels defined by `native/notify` in `IPC-CONTRACT.md`.
 */
enum class NotificationPriority(
    val wireName: String,
) {
    DEFAULT("default"),
    HIGH("high"),
    ;

    companion object {
        fun fromWireName(value: String?): NotificationPriority {
            val normalized = value?.trim()?.lowercase()
            return entries.firstOrNull { it.wireName == normalized } ?: DEFAULT
        }
    }
}

/**
 * Target navigation action for `native/notify` in `IPC-CONTRACT.md`.
 */
enum class NotificationActionIntent(
    val wireName: String,
) {
    OPEN_CHAT("open_chat"),
    OPEN_TERMINAL("open_terminal"),
    ;

    companion object {
        fun fromWireName(value: String?): NotificationActionIntent {
            val normalized = value?.trim()?.lowercase()
            return entries.firstOrNull { it.wireName == normalized } ?: OPEN_CHAT
        }
    }
}

/**
 * Notification request payload matching `native/notify` in `IPC-CONTRACT.md`.
 */
data class NativeNotificationPayload(
    val title: String,
    val body: String,
    val priority: NotificationPriority = NotificationPriority.DEFAULT,
    val actionIntent: NotificationActionIntent = NotificationActionIntent.OPEN_CHAT,
)
