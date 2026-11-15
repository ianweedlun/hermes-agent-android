package ai.hermes.mobile.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonRpcMessageTest {
    @Test
    fun parseDaemonStatusEvent() {
        val json =
            """
            {
              "jsonrpc": "2.0",
              "method": "daemon/status",
              "params": {
                "state": "executing_tool",
                "active_tool": "terminal",
                "step_index": 5,
                "session_id": "test-uuid"
              }
            }
            """.trimIndent()

        val parsed = JsonRpcMessage.parse(json)
        assertNotNull(parsed)
        assertTrue(parsed is JsonRpcMessage.StatusEvent)
        val status = parsed as JsonRpcMessage.StatusEvent
        assertEquals("executing_tool", status.state)
        assertEquals("terminal", status.activeTool)
        assertEquals(5, status.stepIndex)
        assertEquals("test-uuid", status.sessionId)
    }

    @Test
    fun parseDaemonTelemetryEvent() {
        val json =
            """
            {
              "jsonrpc": "2.0",
              "method": "daemon/telemetry",
              "params": {
                "uptime_seconds": 3600,
                "rss_bytes": 1048576,
                "cpu_percent": 4.2,
                "active_model": "gemini-3.8-flash"
              }
            }
            """.trimIndent()

        val parsed = JsonRpcMessage.parse(json)
        assertNotNull(parsed)
        assertTrue(parsed is JsonRpcMessage.TelemetryEvent)
        val telemetry = parsed as JsonRpcMessage.TelemetryEvent
        assertEquals(3600L, telemetry.uptimeSeconds)
        assertEquals(1048576L, telemetry.rssBytes)
        assertEquals(4.2, telemetry.cpuPercent, 0.01)
        assertEquals("gemini-3.8-flash", telemetry.activeModel)
    }

    @Test
    fun parseNotificationTriggerEvent() {
        val json =
            """
            {
              "jsonrpc": "2.0",
              "method": "native/notify",
              "params": {
                "title": "Build Finished",
                "body": "APK assembled successfully",
                "priority": "high",
                "action_intent": "open_terminal"
              }
            }
            """.trimIndent()

        val parsed = JsonRpcMessage.parse(json)
        assertNotNull(parsed)
        assertTrue(parsed is JsonRpcMessage.NotifyEvent)
        val notify = parsed as JsonRpcMessage.NotifyEvent
        assertEquals("Build Finished", notify.title)
        assertEquals("APK assembled successfully", notify.body)
        assertEquals("high", notify.priority)
        assertEquals("open_terminal", notify.actionIntent)
    }

    @Test
    fun invalidVersionOrJsonReturnsNull() {
        assertNull(JsonRpcMessage.parse("not json"))
        assertNull(JsonRpcMessage.parse("""{"jsonrpc": "1.0", "method": "daemon/status"}"""))
    }
}
