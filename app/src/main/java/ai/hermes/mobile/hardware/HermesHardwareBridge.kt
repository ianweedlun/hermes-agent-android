package ai.hermes.mobile.hardware

import android.content.Context
import android.webkit.JavascriptInterface

/**
 * Native hardware and telemetry bridge interface exposed to WebView JavaScript as
 * `window.HermesHardware` per HARDWARE-BRIDGE-CONTRACT.md.
 */
class HermesHardwareBridge(
    private val batteryProvider: BatteryProvider,
    private val clipboardProvider: ClipboardProvider,
    private val hapticsProvider: HapticsProvider,
    private val networkProvider: NetworkProvider,
) {
    constructor(context: Context) : this(
        batteryProvider = AndroidBatteryProvider(context),
        clipboardProvider = AndroidClipboardProvider(context),
        hapticsProvider = AndroidHapticsProvider(context),
        networkProvider = AndroidNetworkProvider(context),
    )

    @JavascriptInterface
    fun getBatteryStatus(): String = batteryProvider.getBatteryStatus().toJsonString()

    @JavascriptInterface
    fun getClipboardText(): String = clipboardProvider.getClipboardText()

    @JavascriptInterface
    fun setClipboardText(
        text: String,
        isSensitive: Boolean,
    ) {
        clipboardProvider.setClipboardText(text, isSensitive)
    }

    @JavascriptInterface
    fun vibrate(effect: String) {
        hapticsProvider.vibrate(effect)
    }

    @JavascriptInterface
    fun getNetworkStatus(): String = networkProvider.getNetworkStatus().toJsonString()

    companion object {
        const val JS_INTERFACE_NAME = "HermesHardware"
    }
}
