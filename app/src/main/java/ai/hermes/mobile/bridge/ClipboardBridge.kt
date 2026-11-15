package ai.hermes.mobile.bridge

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal interface ClipboardGateway {
    fun readClip(): ClipboardPayload?

    fun writeClip(
        text: String,
        isSensitive: Boolean,
        timestamp: Long,
    )

    fun clearClip(expectedText: String?)
}

/**
 * Manages system clipboard access and enforces coroutine-based 60-second auto-clear
 * for sensitive clipboard writes per `HARDWARE-BRIDGE-CONTRACT.md` and `SECURITY-SANDBOX.md`.
 */
class ClipboardBridge internal constructor(
    private val gateway: ClipboardGateway,
    private val scope: CoroutineScope,
    private val autoClearDelayMs: Long = DEFAULT_SENSITIVE_CLEAR_DELAY_MS,
    private val clock: () -> Long = System::currentTimeMillis,
    private val delayFn: suspend (Long) -> Unit = { durationMs -> delay(durationMs) },
    private val ownsScope: Boolean = false,
) {
    constructor(
        context: Context,
        scope: CoroutineScope? = null,
        autoClearDelayMs: Long = DEFAULT_SENSITIVE_CLEAR_DELAY_MS,
    ) : this(
        gateway = AndroidClipboardGateway(context, System::currentTimeMillis),
        scope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default),
        autoClearDelayMs = autoClearDelayMs,
        clock = System::currentTimeMillis,
        delayFn = { durationMs -> delay(durationMs) },
        ownsScope = scope == null,
    )

    private val lock = Any()
    private var autoClearJob: Job? = null
    private var activeGeneration: Long = 0L
    private var activeSensitiveText: String? = null

    fun getClipboardText(): ClipboardPayload {
        val raw = gateway.readClip() ?: return ClipboardPayload(text = "", isSensitive = false, timestamp = clock())
        val trackedSensitive =
            synchronized(lock) {
                activeSensitiveText != null && activeSensitiveText == raw.text
            }
        return if (raw.isSensitive || !trackedSensitive) {
            raw
        } else {
            raw.copy(isSensitive = true)
        }
    }

    fun setClipboardText(
        text: String,
        isSensitive: Boolean,
    ): ClipboardPayload {
        require(autoClearDelayMs > 0L) { "autoClearDelayMs must be positive" }
        val timestamp = clock()
        synchronized(lock) {
            autoClearJob?.cancel()
            autoClearJob = null
            activeGeneration += 1L
            val generation = activeGeneration
            gateway.writeClip(text = text, isSensitive = isSensitive, timestamp = timestamp)
            if (isSensitive) {
                activeSensitiveText = text
                autoClearJob =
                    scope.launch {
                        delayFn(autoClearDelayMs)
                        clearIfUnchanged(expectedGeneration = generation, expectedText = text)
                    }
            } else {
                activeSensitiveText = null
            }
        }
        return ClipboardPayload(text = text, isSensitive = isSensitive, timestamp = timestamp)
    }

    fun clearClipboard() {
        synchronized(lock) {
            autoClearJob?.cancel()
            autoClearJob = null
            activeGeneration += 1L
            activeSensitiveText = null
            gateway.clearClip(expectedText = null)
        }
    }

    fun dispose() {
        synchronized(lock) {
            autoClearJob?.cancel()
            autoClearJob = null
            activeGeneration += 1L
            activeSensitiveText = null
        }
        if (ownsScope) {
            scope.cancel()
        }
    }

    private fun clearIfUnchanged(
        expectedGeneration: Long,
        expectedText: String,
    ) {
        synchronized(lock) {
            if (activeGeneration != expectedGeneration) {
                return
            }
            activeSensitiveText = null
            autoClearJob = null
            gateway.clearClip(expectedText = expectedText)
        }
    }

    companion object {
        const val DEFAULT_SENSITIVE_CLEAR_DELAY_MS: Long = 60_000L
    }
}
