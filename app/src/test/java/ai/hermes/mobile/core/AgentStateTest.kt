package ai.hermes.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentStateTest {
    @Test
    fun wireNameMappingResolvesAllIpcStatesAndFallsBackToIdle() {
        assertEquals(AgentState.IDLE, AgentState.fromWireName("idle"))
        assertEquals(AgentState.THINKING, AgentState.fromWireName(" THINKING "))
        assertEquals(AgentState.EXECUTING_TOOL, AgentState.fromWireName("executing_tool"))
        assertEquals(AgentState.ERROR, AgentState.fromWireName("error"))
        assertEquals(AgentState.IDLE, AgentState.fromWireName(null))
        assertEquals(AgentState.IDLE, AgentState.fromWireName("unknown_state"))
    }

    @Test
    fun wakeLockPolicyEnabledOnlyDuringActiveExecutionWhenNotThrottled() {
        assertFalse(DaemonStatus(state = AgentState.IDLE).shouldHoldWakeLock)
        assertFalse(DaemonStatus(state = AgentState.ERROR).shouldHoldWakeLock)
        assertTrue(DaemonStatus(state = AgentState.THINKING).shouldHoldWakeLock)
        assertTrue(DaemonStatus(state = AgentState.EXECUTING_TOOL).shouldHoldWakeLock)

        val throttledStatus =
            DaemonStatus(
                state = AgentState.EXECUTING_TOOL,
                thermalLevel = ThermalLevel.SEVERE,
            )
        assertTrue(throttledStatus.isThrottled)
        assertFalse(throttledStatus.shouldHoldWakeLock)
    }

    @Test
    fun thermalLevelMappingMatchesAndroidThrottlingThreshold() {
        assertFalse(ThermalLevel.fromPlatformStatus(0).isThrottled)
        assertFalse(ThermalLevel.fromPlatformStatus(1).isThrottled)
        assertFalse(ThermalLevel.fromPlatformStatus(2).isThrottled)
        assertTrue(ThermalLevel.fromPlatformStatus(3).isThrottled)
        assertTrue(ThermalLevel.fromPlatformStatus(4).isThrottled)
        assertTrue(ThermalLevel.fromPlatformStatus(5).isThrottled)
        assertTrue(ThermalLevel.fromPlatformStatus(6).isThrottled)
        assertEquals(ThermalLevel.SHUTDOWN, ThermalLevel.fromPlatformStatus(99))
        assertEquals(ThermalLevel.NONE, ThermalLevel.fromPlatformStatus(-1))
    }

    @Test
    fun notificationPriorityAndActionIntentWireMappingsResolveSafely() {
        assertEquals(NotificationPriority.HIGH, NotificationPriority.fromWireName("high"))
        assertEquals(NotificationPriority.DEFAULT, NotificationPriority.fromWireName("other"))
        assertEquals(
            NotificationActionIntent.OPEN_TERMINAL,
            NotificationActionIntent.fromWireName("open_terminal"),
        )
        assertEquals(
            NotificationActionIntent.OPEN_CHAT,
            NotificationActionIntent.fromWireName(null),
        )
    }

    @Test
    fun daemonTelemetryAcceptsValidNonNegativeMetrics() {
        val telemetry =
            DaemonTelemetry(
                uptimeSeconds = 3600L,
                rssBytes = 157286400L,
                cpuPercent = 3.5,
                activeModel = "gemini-3.8-flash",
            )
        assertEquals(3600L, telemetry.uptimeSeconds)
        assertEquals("gemini-3.8-flash", telemetry.activeModel)
    }
}
