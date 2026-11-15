package ai.hermes.mobile.core

import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HermesNotificationManagerTest {
    @Test
    fun defaultChannelSpecsConfigureLowDaemonAndHighAlertsChannels() {
        val specs = HermesNotificationManager.createDefaultChannelSpecs()
        assertEquals(2, specs.size)

        val lowChannel = specs.first { it.id == HermesNotificationManager.CHANNEL_ID_DAEMON_LOW }
        assertEquals(NotificationManager.IMPORTANCE_LOW, lowChannel.importance)
        assertFalse(lowChannel.showBadge)
        assertFalse(lowChannel.enableVibration)
        assertTrue(lowChannel.name.isNotBlank())
        assertTrue(lowChannel.description.isNotBlank())

        val highChannel = specs.first { it.id == HermesNotificationManager.CHANNEL_ID_ALERTS_HIGH }
        assertEquals(NotificationManager.IMPORTANCE_HIGH, highChannel.importance)
        assertTrue(highChannel.showBadge)
        assertTrue(highChannel.enableVibration)
        assertTrue(highChannel.name.isNotBlank())
        assertTrue(highChannel.description.isNotBlank())
    }

    @Test
    fun idleStateFormatsOngoingNotificationWithoutStepSubtext() {
        val status = DaemonStatus(state = AgentState.IDLE, stepIndex = 4)
        val content = HermesNotificationManager.formatDaemonNotificationContent(status)

        assertEquals("Hermes Agent", content.title)
        assertTrue(content.text.contains("Idle"))
        assertNull(content.subText)
        assertTrue(content.isOngoing)
        assertEquals(NotificationCompat.CATEGORY_SERVICE, content.category)
    }

    @Test
    fun thinkingStateFormatsOngoingNotificationWithStepSubtext() {
        val status = DaemonStatus(state = AgentState.THINKING, stepIndex = 7)
        val content = HermesNotificationManager.formatDaemonNotificationContent(status)

        assertTrue(content.text.contains("Thinking"))
        assertEquals("Step 7", content.subText)
        assertTrue(content.isOngoing)
    }

    @Test
    fun executingToolStateIncludesActiveToolNameWhenPresent() {
        val status =
            DaemonStatus(
                state = AgentState.EXECUTING_TOOL,
                activeTool = "terminal",
                stepIndex = 3,
            )
        val content = HermesNotificationManager.formatDaemonNotificationContent(status)

        assertEquals("Executing Tool: terminal", content.text)
        assertEquals("Step 3", content.subText)
    }

    @Test
    fun executingToolStateFallsBackWhenActiveToolIsBlank() {
        val status = DaemonStatus(state = AgentState.EXECUTING_TOOL, activeTool = "   ")
        val content = HermesNotificationManager.formatDaemonNotificationContent(status)

        assertTrue(content.text.startsWith("Executing Tool"))
        assertFalse(content.text.contains(":"))
    }

    @Test
    fun errorStateFormatsDiagnosticNotificationText() {
        val status = DaemonStatus(state = AgentState.ERROR)
        val content = HermesNotificationManager.formatDaemonNotificationContent(status)

        assertTrue(content.text.contains("Error"))
        assertTrue(content.isOngoing)
    }

    @Test
    fun thermalThrottlingOverridesSubtextWithThermalWarning() {
        val status =
            DaemonStatus(
                state = AgentState.THINKING,
                stepIndex = 12,
                thermalLevel = ThermalLevel.SEVERE,
            )
        val content = HermesNotificationManager.formatDaemonNotificationContent(status)

        assertEquals("Thermal throttling (Severe)", content.subText)
    }
}
