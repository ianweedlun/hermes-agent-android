package ai.hermes.mobile.terminal

/**
 * Dimensions of a pseudo-terminal viewport in character cells.
 */
data class TerminalDimensions(
    val cols: Int = DEFAULT_COLS,
    val rows: Int = DEFAULT_ROWS,
) {
    companion object {
        const val DEFAULT_COLS = 80
        const val DEFAULT_ROWS = 24
        const val MIN_COLS = 20
        const val MAX_COLS = 500
        const val MIN_ROWS = 5
        const val MAX_ROWS = 200
    }
}

/**
 * Current terminal cursor coordinates (0-indexed) and visibility.
 */
data class CursorState(
    val row: Int = 0,
    val col: Int = 0,
    val visible: Boolean = true,
)

/**
 * Lifecycle state for a [TerminalSession].
 */
enum class TerminalSessionState {
    IDLE,
    CONNECTING,
    ACTIVE,
    DISCONNECTED,
    ERROR,
}

/**
 * Immutable snapshot of the terminal scrollback and screen buffer.
 */
data class TerminalBufferState(
    val lines: List<TerminalLine> = listOf(TerminalLine.EMPTY),
    val totalBytes: Int = 0,
    val droppedBytes: Long = 0L,
    val overflowDetected: Boolean = false,
    val version: Long = 0L,
) {
    val plainText: String
        get() = lines.joinToString(separator = "\n") { it.plainText }
}

/**
 * Sink receiving encoded bytes written by the user or application to the PTY.
 */
fun interface PtyInputSink {
    fun write(bytes: ByteArray): Boolean
}
