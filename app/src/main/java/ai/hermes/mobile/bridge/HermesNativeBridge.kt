package ai.hermes.mobile.bridge

import android.webkit.JavascriptInterface
import org.json.JSONObject

fun interface JsEvaluator {
    fun evaluateJavascript(script: String)
}

interface NativeBridgeListener {
    fun onStatusReceived(event: JsonRpcMessage.StatusEvent)

    fun onTelemetryReceived(event: JsonRpcMessage.TelemetryEvent)

    fun onNotificationRequested(event: JsonRpcMessage.NotifyEvent)
}

/**
 * Bidirectional Native Kotlin <-> WebView PWA JavaScript bridge per IPC-CONTRACT.md.
 * Registered as `window.HermesNativeBridge`.
 */
class HermesNativeBridge(
    private val jsEvaluator: JsEvaluator? = null,
    private val listener: NativeBridgeListener? = null,
) {
    @JavascriptInterface
    fun postMessage(rawJson: String): Boolean {
        val message = JsonRpcMessage.parse(rawJson) ?: return false
        when (message) {
            is JsonRpcMessage.StatusEvent -> listener?.onStatusReceived(message)
            is JsonRpcMessage.TelemetryEvent -> listener?.onTelemetryReceived(message)
            is JsonRpcMessage.NotifyEvent -> listener?.onNotificationRequested(message)
            is JsonRpcMessage.GenericRequest -> {
                // Handled or ignored for generic extensions
            }
        }
        return true
    }

    /**
     * Dispatches an event from Native Android to the WebView frontend.
     */
    fun sendEvent(
        method: String,
        params: JSONObject,
    ) {
        val payload =
            JSONObject().apply {
                put("jsonrpc", JsonRpcMessage.VERSION)
                put("method", method)
                put("params", params)
            }
        val script = "window.dispatchEvent(new CustomEvent('hermes-ipc', { detail: $payload }));"
        jsEvaluator?.evaluateJavascript(script)
    }

    companion object {
        const val JS_INTERFACE_NAME = "HermesNativeBridge"
    }
}
