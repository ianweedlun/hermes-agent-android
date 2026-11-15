package ai.hermes.mobile.bridge

import android.util.Log
import org.json.JSONException
import org.json.JSONObject

/**
 * JSON-RPC 2.0 message and event models per IPC-CONTRACT.md.
 */
sealed class JsonRpcMessage {
    data class StatusEvent(
        val state: String,
        val activeTool: String?,
        val stepIndex: Int,
        val sessionId: String,
    ) : JsonRpcMessage()

    data class TelemetryEvent(
        val uptimeSeconds: Long,
        val rssBytes: Long,
        val cpuPercent: Double,
        val activeModel: String,
    ) : JsonRpcMessage()

    data class NotifyEvent(
        val title: String,
        val body: String,
        val priority: String,
        val actionIntent: String,
    ) : JsonRpcMessage()

    data class GenericRequest(
        val method: String,
        val params: JSONObject,
        val id: String?,
    ) : JsonRpcMessage()

    companion object {
        private const val TAG = "JsonRpcMessage"
        const val VERSION = "2.0"
        const val METHOD_STATUS = "daemon/status"
        const val METHOD_TELEMETRY = "daemon/telemetry"
        const val METHOD_NOTIFY = "native/notify"

        fun parse(rawJson: String): JsonRpcMessage? {
            return try {
                val json = JSONObject(rawJson)
                if (json.optString("jsonrpc") != VERSION) {
                    return null
                }
                val method = json.optString("method")
                val params = json.optJSONObject("params") ?: JSONObject()
                val id = if (json.has("id")) json.optString("id") else null

                when (method) {
                    METHOD_STATUS -> parseStatus(params)
                    METHOD_TELEMETRY -> parseTelemetry(params)
                    METHOD_NOTIFY -> parseNotify(params)
                    else -> GenericRequest(method, params, id)
                }
            } catch (e: JSONException) {
                Log.d(TAG, "Unparseable or non-JSON-RPC payload received", e)
                null
            }
        }

        private fun parseStatus(params: JSONObject): StatusEvent =
            StatusEvent(
                state = params.optString("state", "idle"),
                activeTool =
                    if (!params.has("active_tool") || params.isNull("active_tool")) {
                        null
                    } else {
                        params.getString("active_tool")
                    },
                stepIndex = params.optInt("step_index", 0),
                sessionId = params.optString("session_id", ""),
            )

        private fun parseTelemetry(params: JSONObject): TelemetryEvent =
            TelemetryEvent(
                uptimeSeconds = params.optLong("uptime_seconds", 0L),
                rssBytes = params.optLong("rss_bytes", 0L),
                cpuPercent = params.optDouble("cpu_percent", 0.0),
                activeModel = params.optString("active_model", ""),
            )

        private fun parseNotify(params: JSONObject): NotifyEvent =
            NotifyEvent(
                title = params.optString("title", "Hermes Agent"),
                body = params.optString("body", ""),
                priority = params.optString("priority", "default"),
                actionIntent = params.optString("action_intent", "open_chat"),
            )
    }
}
