package ai.hermes.mobile.bridge

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HermesNativeBridgeTest {
    private class TestListener : NativeBridgeListener {
        var statusEvent: JsonRpcMessage.StatusEvent? = null
        var telemetryEvent: JsonRpcMessage.TelemetryEvent? = null
        var notifyEvent: JsonRpcMessage.NotifyEvent? = null

        override fun onStatusReceived(event: JsonRpcMessage.StatusEvent) {
            statusEvent = event
        }

        override fun onTelemetryReceived(event: JsonRpcMessage.TelemetryEvent) {
            telemetryEvent = event
        }

        override fun onNotificationRequested(event: JsonRpcMessage.NotifyEvent) {
            notifyEvent = event
        }
    }

    @Test
    fun postMessageDispatchesToListener() {
        val listener = TestListener()
        val bridge = HermesNativeBridge(listener = listener)

        val statusPayload =
            """
            {
              "jsonrpc": "2.0",
              "method": "daemon/status",
              "params": {
                "state": "thinking",
                "step_index": 2,
                "session_id": "sess-1"
              }
            }
            """.trimIndent()

        val handled = bridge.postMessage(statusPayload)
        assertTrue(handled)
        assertNotNull(listener.statusEvent)
        assertEquals("thinking", listener.statusEvent?.state)
        assertEquals(2, listener.statusEvent?.stepIndex)
    }

    @Test
    fun sendEventFormatsValidScript() {
        var evaluatedScript = ""
        val bridge = HermesNativeBridge(jsEvaluator = { script -> evaluatedScript = script })

        val params =
            JSONObject().apply {
                put("battery", 80)
            }
        bridge.sendEvent("device/battery", params)

        assertTrue(evaluatedScript.startsWith("window.dispatchEvent(new CustomEvent('hermes-ipc'"))
        assertTrue(evaluatedScript.contains("device/battery"))
        assertTrue(evaluatedScript.contains(""""battery":80"""))
    }
}
