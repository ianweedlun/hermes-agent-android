package ai.hermes.mobile.hardware

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Abstraction for system clipboard interactions per HARDWARE-BRIDGE-CONTRACT.md.
 */
interface ClipboardProvider {
    fun getClipboardText(): String

    fun setClipboardText(
        text: String,
        isSensitive: Boolean,
    )
}

/**
 * Android platform implementation backed by ClipboardManager with automatic 60s
 * sensitive clip expiration.
 */
class AndroidClipboardProvider(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ClipboardProvider {
    private val clipboardManager: ClipboardManager? by lazy {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    }

    private var pendingClearJob: Job? = null

    override fun getClipboardText(): String {
        val manager = clipboardManager ?: return ""
        if (!manager.hasPrimaryClip()) {
            return ""
        }
        val clipData = manager.primaryClip ?: return ""
        if (clipData.itemCount <= 0) {
            return ""
        }
        return clipData.getItemAt(0)?.text?.toString() ?: ""
    }

    override fun setClipboardText(
        text: String,
        isSensitive: Boolean,
    ) {
        val manager = clipboardManager ?: return
        pendingClearJob?.cancel()
        pendingClearJob = null

        val clipData = ClipData.newPlainText(CLIP_LABEL, text)
        if (isSensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val extras =
                PersistableBundle().apply {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                }
            clipData.description.extras = extras
        }

        manager.setPrimaryClip(clipData)

        if (isSensitive) {
            pendingClearJob =
                scope.launch(mainDispatcher) {
                    delay(SENSITIVE_CLEAR_DELAY_MS)
                    clearIfMatching(text)
                }
        }
    }

    private fun clearIfMatching(expectedText: String) {
        val manager = clipboardManager ?: return
        if (getClipboardText() == expectedText) {
            manager.clearPrimaryClip()
        }
    }

    companion object {
        const val CLIP_LABEL = "Hermes"
        const val SENSITIVE_CLEAR_DELAY_MS = 60_000L
    }
}
