package ai.hermes.mobile.terminal

/**
 * Bounded terminal screen and scrollback buffer enforcing the 64KB IPC-CONTRACT backpressure cap.
 */
internal class TerminalScreenBuffer(
    private val maxBufferBytes: Int,
    private val maxLines: Int,
) {
    private val lines = mutableListOf(MutableTerminalLine())
    private var cursorRow: Int = 0
    private var cursorCol: Int = 0
    private var cursorVisible: Boolean = true
    private var droppedBytes: Long = 0L
    private var overflowDetected: Boolean = false

    fun cursorState(): CursorState =
        CursorState(
            row = cursorRow,
            col = cursorCol,
            visible = cursorVisible,
        )

    fun clear() {
        lines.clear()
        lines.add(MutableTerminalLine())
        cursorRow = 0
        cursorCol = 0
        droppedBytes = 0L
        overflowDetected = false
    }

    fun applyTokens(
        tokens: List<AnsiToken>,
        maxCols: Int,
        maxRows: Int,
    ) {
        for (token in tokens) {
            applyToken(token, maxCols, maxRows)
        }
        enforceCapacity()
    }

    fun snapshot(version: Long): TerminalBufferState {
        val immutableLines = lines.map { it.toTerminalLine() }
        val totalBytes = immutableLines.sumOf { it.byteLength }
        return TerminalBufferState(
            lines = immutableLines,
            totalBytes = totalBytes,
            droppedBytes = droppedBytes,
            overflowDetected = overflowDetected,
            version = version,
        )
    }

    private fun applyToken(
        token: AnsiToken,
        maxCols: Int,
        maxRows: Int,
    ) {
        when (token) {
            is AnsiToken.Text -> writeText(token.value, token.style)
            AnsiToken.NewLine -> {
                cursorRow += 1
                cursorCol = 0
                ensureLine(cursorRow)
            }
            AnsiToken.CarriageReturn -> cursorCol = 0
            AnsiToken.Backspace -> cursorCol = (cursorCol - 1).coerceAtLeast(0)
            AnsiToken.Tab -> {
                val spaces = TAB_STOP_WIDTH - (cursorCol % TAB_STOP_WIDTH)
                writeText(" ".repeat(spaces), AnsiTextStyle())
            }
            AnsiToken.EraseScreen -> {
                lines.clear()
                lines.add(MutableTerminalLine())
                cursorRow = 0
                cursorCol = 0
            }
            AnsiToken.EraseLine -> {
                ensureLine(cursorRow).clear()
                cursorCol = 0
            }
            AnsiToken.EraseToEndOfLine -> ensureLine(cursorRow).truncateFrom(cursorCol)
            AnsiToken.CursorHome -> {
                cursorRow = 0
                cursorCol = 0
            }
            is AnsiToken.CursorPosition -> {
                cursorRow = token.row.coerceIn(0, (maxRows - 1).coerceAtLeast(0))
                cursorCol = token.col.coerceIn(0, (maxCols - 1).coerceAtLeast(0))
                ensureLine(cursorRow)
            }
            is AnsiToken.CursorMove -> {
                cursorRow = (cursorRow + token.rowDelta).coerceAtLeast(0)
                cursorCol = (cursorCol + token.colDelta).coerceAtLeast(0)
                ensureLine(cursorRow)
            }
            is AnsiToken.CursorVisibility -> cursorVisible = token.visible
        }
    }

    private fun writeText(
        text: String,
        style: AnsiTextStyle,
    ) {
        val line = ensureLine(cursorRow)
        for (ch in text) {
            line.writeChar(cursorCol, ch, style)
            cursorCol += 1
        }
    }

    private fun ensureLine(row: Int): MutableTerminalLine {
        while (lines.size <= row) {
            lines.add(MutableTerminalLine())
        }
        return lines[row]
    }

    private fun enforceCapacity() {
        var totalBytes = lines.sumOf { it.byteCount() }
        while (lines.size > 1 && (lines.size > maxLines || totalBytes > maxBufferBytes)) {
            val removedBytes = lines.removeAt(0).byteCount()
            totalBytes -= removedBytes
            cursorRow = (cursorRow - 1).coerceAtLeast(0)
            if (totalBytes + removedBytes > maxBufferBytes) {
                droppedBytes += removedBytes
                overflowDetected = true
            }
        }
        if (lines.size == 1 && totalBytes > maxBufferBytes) {
            val dropped = lines[0].trimHeadBytes(maxBufferBytes)
            if (dropped > 0L) {
                droppedBytes += dropped
                overflowDetected = true
                cursorCol = lines[0].length().coerceAtMost(cursorCol)
            }
        }
    }

    companion object {
        private const val TAB_STOP_WIDTH = 8
    }
}
