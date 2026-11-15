package ai.hermes.mobile.terminal

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Models an active pseudo-terminal session including dimensions, cursor state,
 * ANSI-parsed bounded output buffer flow, resize events, and input writing.
 */
class TerminalSession(
    val sessionId: String = UUID.randomUUID().toString(),
    initialDimensions: TerminalDimensions = TerminalDimensions(),
    private val maxBufferBytes: Int = DEFAULT_MAX_BUFFER_BYTES,
    private val maxLines: Int = DEFAULT_MAX_LINES,
    inputSink: PtyInputSink? = null,
) {
    private val lock = Any()
    private val ansiParser = AnsiParser()
    private val screenBuffer = TerminalScreenBuffer(maxBufferBytes = maxBufferBytes, maxLines = maxLines)
    private var versionCounter: Long = 0L

    @Volatile
    private var activeSink: PtyInputSink? = inputSink

    private val _dimensions = MutableStateFlow(clampDimensions(initialDimensions))
    val dimensions: StateFlow<TerminalDimensions> = _dimensions.asStateFlow()

    private val _cursorState = MutableStateFlow(CursorState())
    val cursorState: StateFlow<CursorState> = _cursorState.asStateFlow()

    private val _sessionState = MutableStateFlow(TerminalSessionState.IDLE)
    val sessionState: StateFlow<TerminalSessionState> = _sessionState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _outputBuffer = MutableStateFlow(TerminalBufferState())
    val outputBuffer: StateFlow<TerminalBufferState> = _outputBuffer.asStateFlow()

    private val _resizeEvents =
        MutableSharedFlow<TerminalDimensions>(
            replay = 1,
            extraBufferCapacity = EVENT_BUFFER_CAPACITY,
        )
    val resizeEvents: SharedFlow<TerminalDimensions> = _resizeEvents.asSharedFlow()

    private val _inputEvents =
        MutableSharedFlow<ByteArray>(
            replay = 1,
            extraBufferCapacity = EVENT_BUFFER_CAPACITY,
        )
    val inputEvents: SharedFlow<ByteArray> = _inputEvents.asSharedFlow()

    fun attachInputSink(sink: PtyInputSink?) {
        activeSink = sink
    }

    fun updateLifecycleState(
        newState: TerminalSessionState,
        errorReason: String? = null,
    ) {
        _sessionState.value = newState
        _errorMessage.value = if (newState == TerminalSessionState.ERROR) errorReason else null
    }

    fun appendOutput(rawText: String) {
        if (rawText.isEmpty()) {
            return
        }
        synchronized(lock) {
            val tokens = ansiParser.feed(rawText)
            val dims = _dimensions.value
            screenBuffer.applyTokens(tokens, maxCols = dims.cols, maxRows = dims.rows)
            versionCounter += 1L
            _cursorState.value = screenBuffer.cursorState()
            _outputBuffer.value = screenBuffer.snapshot(versionCounter)
        }
    }

    fun clearBuffer() {
        synchronized(lock) {
            ansiParser.reset()
            screenBuffer.clear()
            versionCounter += 1L
            _cursorState.value = screenBuffer.cursorState()
            _outputBuffer.value = screenBuffer.snapshot(versionCounter)
        }
    }

    fun resize(
        cols: Int,
        rows: Int,
    ): Boolean {
        val clamped = clampDimensions(TerminalDimensions(cols = cols, rows = rows))
        val changed =
            synchronized(lock) {
                if (clamped == _dimensions.value) {
                    false
                } else {
                    _dimensions.value = clamped
                    true
                }
            }
        if (changed) {
            _resizeEvents.tryEmit(clamped)
        }
        return changed
    }

    fun writeCommand(command: String): Boolean {
        val line = if (command.endsWith("\n")) command else "$command\n"
        return writeInput(line)
    }

    fun writeInput(text: String): Boolean {
        if (text.isEmpty()) {
            return false
        }
        return writeBytes(text.toByteArray(Charsets.UTF_8))
    }

    fun writeBytes(bytes: ByteArray): Boolean {
        val state = _sessionState.value
        val sink = activeSink
        if (bytes.isEmpty() || state == TerminalSessionState.DISCONNECTED || state == TerminalSessionState.ERROR) {
            return false
        }
        val accepted = sink?.write(bytes.copyOf()) ?: false
        if (accepted) {
            _inputEvents.tryEmit(bytes.copyOf())
        }
        return accepted
    }

    fun close() {
        activeSink = null
        updateLifecycleState(TerminalSessionState.DISCONNECTED)
    }

    private fun clampDimensions(raw: TerminalDimensions): TerminalDimensions =
        TerminalDimensions(
            cols = raw.cols.coerceIn(TerminalDimensions.MIN_COLS, TerminalDimensions.MAX_COLS),
            rows = raw.rows.coerceIn(TerminalDimensions.MIN_ROWS, TerminalDimensions.MAX_ROWS),
        )

    companion object {
        const val DEFAULT_MAX_BUFFER_BYTES = 64 * 1024
        const val DEFAULT_MAX_LINES = 2000
        private const val EVENT_BUFFER_CAPACITY = 64
    }
}
