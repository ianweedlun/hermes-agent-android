package ai.hermes.mobile.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Hardened WebViewClient enforcing external URL redirection to the system browser
 * per SECURITY-SANDBOX.md.
 */
class HermesWebViewClient(
    private val context: Context,
    private val externalUrlLauncher: ((Uri) -> Boolean)? = null,
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest?,
    ): Boolean {
        val uri = request?.url ?: return false
        return handleUri(uri)
    }

    @Deprecated("Deprecated in Java")
    override fun shouldOverrideUrlLoading(
        view: WebView?,
        url: String?,
    ): Boolean {
        val uri = if (url != null) Uri.parse(url) else return false
        return handleUri(uri)
    }

    fun isInternalUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.startsWith("http://127.0.0.1") ||
            lower.startsWith("http://localhost") ||
            lower.startsWith("file:///android_asset/") ||
            lower.startsWith("data:") ||
            lower.startsWith("about:blank")
    }

    fun isInternalUrl(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()

        if (scheme == "file" || scheme == "about" || scheme == "data") {
            return true
        }

        return host == "127.0.0.1" || host == "localhost"
    }

    private fun handleUri(uri: Uri): Boolean {
        if (isInternalUrl(uri.toString()) || isInternalUrl(uri)) {
            // Allow internal loopback and asset URLs to load inside the WebView
            return false
        }

        // Intercept external URLs and redirect to the system browser
        if (externalUrlLauncher != null) {
            return externalUrlLauncher.invoke(uri)
        }

        return try {
            val intent =
                Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No browser available to handle external URL: $uri", e)
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "Security exception launching URL intent: $uri", e)
            false
        }
    }

    companion object {
        private const val TAG = "HermesWebViewClient"
    }
}
