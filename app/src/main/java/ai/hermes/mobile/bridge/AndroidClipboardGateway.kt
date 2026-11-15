package ai.hermes.mobile.bridge

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal class AndroidClipboardGateway(
    context: Context,
    private val clock: () -> Long,
) : ClipboardGateway {
    private val appContext: Context = context.applicationContext
    private val mainHandler: Handler = Handler(Looper.getMainLooper())
    private var cachedManager: ClipboardManager? = null

    override fun readClip(): ClipboardPayload? = runOnMainThread(defaultValue = null) { readClipOnMain() }

    override fun writeClip(
        text: String,
        isSensitive: Boolean,
        timestamp: Long,
    ) {
        runOnMainThread(defaultValue = Unit) { writeClipOnMain(text, isSensitive) }
    }

    override fun clearClip(expectedText: String?) {
        runOnMainThread(defaultValue = Unit) { clearClipOnMain(expectedText) }
    }

    private fun readClipOnMain(): ClipboardPayload? {
        val manager = resolveManager() ?: return null
        val hasClip =
            try {
                manager.hasPrimaryClip()
            } catch (e: SecurityException) {
                Log.w(TAG, "Clipboard read denied by system policy", e)
                false
            }
        if (!hasClip) {
            return null
        }
        val clip = manager.primaryClip ?: return null
        if (clip.itemCount <= 0) {
            return null
        }
        val text = clip.getItemAt(0)?.coerceToText(appContext)?.toString().orEmpty()
        val description = clip.description
        val sensitive = description?.extras?.getBoolean(sensitiveExtraKey(), false) ?: false
        val timestamp = description?.timestamp?.takeIf { it > 0L } ?: clock()
        return ClipboardPayload(text = text, isSensitive = sensitive, timestamp = timestamp)
    }

    private fun writeClipOnMain(
        text: String,
        isSensitive: Boolean,
    ) {
        val manager = resolveManager() ?: return
        val clip = ClipData.newPlainText(CLIP_LABEL, text)
        if (isSensitive) {
            val extras =
                PersistableBundle().apply {
                    putBoolean(sensitiveExtraKey(), true)
                }
            clip.description.extras = extras
        }
        try {
            manager.setPrimaryClip(clip)
        } catch (e: SecurityException) {
            Log.w(TAG, "Unable to write primary clipboard", e)
        }
    }

    private fun clearClipOnMain(expectedText: String?) {
        val manager = resolveManager() ?: return
        if (expectedText != null && readClipOnMain()?.text != expectedText) {
            return
        }
        try {
            manager.clearPrimaryClip()
        } catch (e: SecurityException) {
            Log.w(TAG, "Unable to clear primary clipboard", e)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Clipboard service unavailable while clearing", e)
        }
    }

    private fun resolveManager(): ClipboardManager? =
        cachedManager ?: appContext.getSystemService(ClipboardManager::class.java).also { cachedManager = it }

    private fun <T> runOnMainThread(
        defaultValue: T,
        block: () -> T,
    ): T {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return block()
        }
        val result = AtomicReference(defaultValue)
        val latch = CountDownLatch(1)
        mainHandler.post {
            try {
                result.set(block())
            } finally {
                latch.countDown()
            }
        }
        try {
            if (!latch.await(MAIN_DISPATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                Log.w(TAG, "Timed out waiting for main-thread clipboard operation")
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            Log.w(TAG, "Interrupted waiting for main-thread clipboard operation", e)
        }
        return result.get()
    }

    private fun sensitiveExtraKey(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ClipDescription.EXTRA_IS_SENSITIVE
        } else {
            LEGACY_EXTRA_IS_SENSITIVE
        }

    companion object {
        private const val TAG = "AndroidClipboardGateway"
        private const val CLIP_LABEL = "Hermes"
        private const val LEGACY_EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
        private const val MAIN_DISPATCH_TIMEOUT_MS = 2_000L
    }
}
