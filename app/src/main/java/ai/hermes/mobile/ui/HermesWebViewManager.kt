package ai.hermes.mobile.ui

import ai.hermes.mobile.bridge.HermesNativeBridge
import ai.hermes.mobile.hardware.HermesHardwareBridge
import android.annotation.SuppressLint
import android.webkit.WebSettings
import android.webkit.WebView

/**
 * Applies security hardening and bridge attachments to a WebView per SECURITY-SANDBOX.md.
 */
object HermesWebViewManager {
    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("DEPRECATION")
    fun configure(
        webView: WebView,
        nativeBridge: HermesNativeBridge? = null,
        hardwareBridge: HermesHardwareBridge? = null,
    ) {
        val settings = webView.settings

        // Enable JavaScript for the PWA client
        settings.javaScriptEnabled = true

        // Offline storage
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        // Strict security boundaries per SECURITY-SANDBOX.md
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.allowFileAccessFromFileURLs = false
        settings.allowUniversalAccessFromFileURLs = false
        settings.setGeolocationEnabled(false)

        // Register bridges
        val context = webView.context
        val hw = hardwareBridge ?: HermesHardwareBridge(context)
        val nb =
            nativeBridge ?: HermesNativeBridge(
                jsEvaluator = { script -> webView.evaluateJavascript(script, null) },
            )

        webView.addJavascriptInterface(hw, HermesHardwareBridge.JS_INTERFACE_NAME)
        webView.addJavascriptInterface(nb, HermesNativeBridge.JS_INTERFACE_NAME)
        webView.webViewClient =
            object : HermesWebViewClient(context) {
                override fun onReceivedError(
                    view: WebView?,
                    request: android.webkit.WebResourceRequest?,
                    error: android.webkit.WebResourceError?,
                ) {
                    super.onReceivedError(view, request, error)
                    val failingUrl = request?.url?.toString()
                    if (request?.isForMainFrame == true &&
                        (failingUrl?.startsWith("http://127.0.0.1:8787") == true ||
                            failingUrl?.startsWith("http://127.0.0.1:8888") == true)
                    ) {
                        // Retry loading once the background server is up
                        view?.postDelayed({ view.loadUrl(failingUrl) }, 1000)
                    }
                }
            }
    }
}
