package ai.hermes.mobile.bridge

import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import kotlinx.coroutines.CoroutineScope

/**
 * WebView `@JavascriptInterface` bridge exposed as `window.HermesHardware` per `HARDWARE-BRIDGE-CONTRACT.md`.
 * All public `@JavascriptInterface` methods validate inputs and return structured JSON strings safely.
 */
class HermesHardwareBridge(
    private val batteryBridge: BatteryBridge,
    private val networkBridge: NetworkBridge,
    private val clipboardBridge: ClipboardBridge,
    private val hapticsBridge: HapticsBridge,
) {
    constructor(
        context: Context,
        clipboardScope: CoroutineScope? = null,
    ) : this(
        batteryBridge = BatteryBridge(context),
        networkBridge = NetworkBridge(context),
        clipboardBridge = ClipboardBridge(context = context, scope = clipboardScope),
        hapticsBridge = HapticsBridge(context),
    )

    fun start() {
        batteryBridge.start()
        networkBridge.start()
    }

    fun stop() {
        batteryBridge.stop()
        networkBridge.stop()
        clipboardBridge.dispose()
    }

    @JavascriptInterface
    fun getBatteryStatus(): String =
        try {
            batteryBridge.getBatteryStatus().toJson()
        } catch (e: SecurityException) {
            Log.w(TAG, "Permission error reading battery status", e)
            JsonCodec.encodeActionResult(ok = false, error = "Battery telemetry unavailable")
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Illegal state reading battery status", e)
            JsonCodec.encodeActionResult(ok = false, error = "Battery telemetry unavailable")
        }

    @JavascriptInterface
    fun getNetworkStatus(): String =
        try {
            networkBridge.getNetworkStatus().toJson()
        } catch (e: SecurityException) {
            Log.w(TAG, "Permission error reading network status", e)
            NetworkStatus.OFFLINE.toJson()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Illegal state reading network status", e)
            NetworkStatus.OFFLINE.toJson()
        }

    @JavascriptInterface
    fun getClipboardText(): String =
        try {
            clipboardBridge.getClipboardText().toJson()
        } catch (e: SecurityException) {
            Log.w(TAG, "Permission error reading clipboard", e)
            JsonCodec.encodeActionResult(ok = false, error = "Clipboard unavailable")
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Illegal state reading clipboard", e)
            JsonCodec.encodeActionResult(ok = false, error = "Clipboard unavailable")
        }

    @JavascriptInterface
    fun setClipboardText(
        text: String?,
        isSensitive: Boolean,
    ): String {
        if (text == null) {
            return JsonCodec.encodeActionResult(ok = false, error = "Clipboard text must not be null")
        }
        return try {
            clipboardBridge.setClipboardText(text = text, isSensitive = isSensitive)
            JsonCodec.encodeActionResult(ok = true, detailKey = "isSensitive", detailValue = isSensitive)
        } catch (e: SecurityException) {
            Log.w(TAG, "Permission error writing clipboard", e)
            JsonCodec.encodeActionResult(ok = false, error = "Clipboard write denied")
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Illegal state writing clipboard", e)
            JsonCodec.encodeActionResult(ok = false, error = "Clipboard unavailable")
        }
    }

    @JavascriptInterface
    fun vibrate(effectType: String?): String {
        val effect =
            HapticEffect.fromWireName(effectType)
                ?: return JsonCodec.encodeActionResult(ok = false, error = "Unsupported haptic effect")
        return try {
            val executed = hapticsBridge.vibrate(effect)
            JsonCodec.encodeActionResult(ok = true, detailKey = "executed", detailValue = executed)
        } catch (e: SecurityException) {
            Log.w(TAG, "Permission error triggering haptic feedback", e)
            JsonCodec.encodeActionResult(ok = false, error = "Haptics unavailable")
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Illegal state triggering haptic feedback", e)
            JsonCodec.encodeActionResult(ok = false, error = "Haptics unavailable")
        }
    }

    companion object {
        private const val TAG = "HermesHardwareBridge"
        const val JS_INTERFACE_NAME: String = "HermesHardware"
    }
}
