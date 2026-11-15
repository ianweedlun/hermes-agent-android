package ai.hermes.mobile.terminal

/**
 * Semantic terminal color representation parsed from ANSI SGR escape sequences.
 */
sealed class TerminalColor {
    data object Default : TerminalColor()

    data class Standard(val index: Int) : TerminalColor()

    data class Bright(val index: Int) : TerminalColor()

    data class Indexed256(val index: Int) : TerminalColor()

    data class Rgb(val red: Int, val green: Int, val blue: Int) : TerminalColor()
}

/**
 * Immutable text style attributes active for a terminal span.
 */
data class AnsiTextStyle(
    val foreground: TerminalColor = TerminalColor.Default,
    val background: TerminalColor = TerminalColor.Default,
    val bold: Boolean = false,
    val dim: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val inverse: Boolean = false,
)

/**
 * A styled run of characters within a terminal output line.
 */
data class TerminalSpan(
    val text: String,
    val style: AnsiTextStyle = AnsiTextStyle(),
)

/**
 * A single logical line in the terminal output buffer.
 */
data class TerminalLine(
    val spans: List<TerminalSpan> = emptyList(),
) {
    val plainText: String
        get() = spans.joinToString(separator = "") { it.text }

    val byteLength: Int
        get() = spans.sumOf { it.text.toByteArray(Charsets.UTF_8).size }

    companion object {
        val EMPTY = TerminalLine(emptyList())
    }
}

/**
 * Parsed token emitted by [AnsiParser] from raw PTY byte/text streams.
 */
sealed class AnsiToken {
    data class Text(val value: String, val style: AnsiTextStyle) : AnsiToken()

    data object NewLine : AnsiToken()

    data object CarriageReturn : AnsiToken()

    data object Backspace : AnsiToken()

    data object Tab : AnsiToken()

    data object EraseScreen : AnsiToken()

    data object EraseLine : AnsiToken()

    data object EraseToEndOfLine : AnsiToken()

    data object CursorHome : AnsiToken()

    data class CursorPosition(val row: Int, val col: Int) : AnsiToken()

    data class CursorMove(val rowDelta: Int, val colDelta: Int) : AnsiToken()

    data class CursorVisibility(val visible: Boolean) : AnsiToken()
}

/**
 * Stateful streaming ANSI/VT escape sequence parser supporting SGR colors/styles,
 * cursor movement, screen/line erasure, and OSC filtering across chunk boundaries.
 */
class AnsiParser {
    private enum class ParseState {
        NORMAL,
        ESCAPE,
        CSI,
        OSC,
        OSC_ESCAPE,
    }

    private var state: ParseState = ParseState.NORMAL
    private var currentStyle: AnsiTextStyle = AnsiTextStyle()
    private val escapeBuffer = StringBuilder()

    fun activeStyle(): AnsiTextStyle = currentStyle

    fun reset() {
        state = ParseState.NORMAL
        currentStyle = AnsiTextStyle()
        escapeBuffer.clear()
    }

    fun feed(input: String): List<AnsiToken> {
        if (input.isEmpty()) {
            return emptyList()
        }
        val tokens = mutableListOf<AnsiToken>()
        val textBuffer = StringBuilder()
        for (ch in input) {
            when (state) {
                ParseState.NORMAL -> processNormalChar(ch, tokens, textBuffer)
                ParseState.ESCAPE -> processEscapeChar(ch)
                ParseState.CSI -> processCsiChar(ch, tokens)
                ParseState.OSC -> processOscChar(ch)
                ParseState.OSC_ESCAPE -> {
                    state = if (ch == '\\') ParseState.NORMAL else ParseState.OSC
                    if (state == ParseState.NORMAL) escapeBuffer.clear()
                }
            }
        }
        if (textBuffer.isNotEmpty()) {
            tokens.add(AnsiToken.Text(textBuffer.toString(), currentStyle))
        }
        return tokens
    }

    private fun processNormalChar(
        ch: Char,
        tokens: MutableList<AnsiToken>,
        textBuffer: StringBuilder,
    ) {
        val controlToken =
            when (ch) {
                '\n' -> AnsiToken.NewLine
                '\r' -> AnsiToken.CarriageReturn
                '\b' -> AnsiToken.Backspace
                '\t' -> AnsiToken.Tab
                else -> null
            }
        if (ch == ESC || controlToken != null) {
            if (textBuffer.isNotEmpty()) {
                tokens.add(AnsiToken.Text(textBuffer.toString(), currentStyle))
                textBuffer.clear()
            }
            if (ch == ESC) {
                state = ParseState.ESCAPE
            } else if (controlToken != null) {
                tokens.add(controlToken)
            }
        } else if (ch >= ' ' || ch.code >= MIN_NON_CONTROL_CODE) {
            textBuffer.append(ch)
        }
    }

    private fun processEscapeChar(ch: Char) {
        escapeBuffer.clear()
        state =
            when (ch) {
                '[' -> ParseState.CSI
                ']' -> ParseState.OSC
                else -> ParseState.NORMAL
            }
    }

    private fun processCsiChar(
        ch: Char,
        tokens: MutableList<AnsiToken>,
    ) {
        if (ch.code in CSI_FINAL_MIN..CSI_FINAL_MAX) {
            dispatchCsi(escapeBuffer.toString(), ch, tokens)
            escapeBuffer.clear()
            state = ParseState.NORMAL
        } else if (escapeBuffer.length < MAX_ESCAPE_SEQUENCE_LEN) {
            escapeBuffer.append(ch)
        } else {
            escapeBuffer.clear()
            state = ParseState.NORMAL
        }
    }

    private fun processOscChar(ch: Char) {
        when (ch) {
            BEL -> {
                escapeBuffer.clear()
                state = ParseState.NORMAL
            }
            ESC -> state = ParseState.OSC_ESCAPE
            else -> {
                if (escapeBuffer.length >= MAX_ESCAPE_SEQUENCE_LEN) {
                    escapeBuffer.clear()
                    state = ParseState.NORMAL
                } else {
                    escapeBuffer.append(ch)
                }
            }
        }
    }

    private fun dispatchCsi(
        rawParams: String,
        finalChar: Char,
        tokens: MutableList<AnsiToken>,
    ) {
        val codes = parseCsiNumbers(rawParams)
        when (finalChar) {
            'm' -> {
                val sgrCodes = if (rawParams.isEmpty()) listOf(0) else codes
                currentStyle = AnsiSgrDecoder.applySgrSequence(currentStyle, sgrCodes)
            }
            'J' -> if ((codes.firstOrNull() ?: 0) in 2..3) tokens.add(AnsiToken.EraseScreen)
            'K' -> {
                when (codes.firstOrNull() ?: 0) {
                    0 -> tokens.add(AnsiToken.EraseToEndOfLine)
                    2 -> tokens.add(AnsiToken.EraseLine)
                }
            }
            'H', 'f' -> tokens.add(buildCursorPositionToken(rawParams, codes))
            'A', 'B', 'C', 'D' -> tokens.add(buildCursorMoveToken(finalChar, codes))
            'h', 'l' -> if (rawParams == "?25") tokens.add(AnsiToken.CursorVisibility(finalChar == 'h'))
            else -> Unit
        }
    }

    private fun buildCursorPositionToken(
        rawParams: String,
        codes: List<Int>,
    ): AnsiToken =
        if (rawParams.isEmpty()) {
            AnsiToken.CursorHome
        } else {
            val row = (codes.getOrNull(0) ?: 1).coerceAtLeast(1) - 1
            val col = (codes.getOrNull(1) ?: 1).coerceAtLeast(1) - 1
            AnsiToken.CursorPosition(row = row, col = col)
        }

    private fun buildCursorMoveToken(
        direction: Char,
        codes: List<Int>,
    ): AnsiToken.CursorMove {
        val count = (codes.firstOrNull() ?: 1).coerceAtLeast(1)
        return when (direction) {
            'A' -> AnsiToken.CursorMove(rowDelta = -count, colDelta = 0)
            'B' -> AnsiToken.CursorMove(rowDelta = count, colDelta = 0)
            'C' -> AnsiToken.CursorMove(rowDelta = 0, colDelta = count)
            else -> AnsiToken.CursorMove(rowDelta = 0, colDelta = -count)
        }
    }

    private fun parseCsiNumbers(rawParams: String): List<Int> =
        if (rawParams.isEmpty()) {
            emptyList()
        } else {
            rawParams.split(';').map { it.toIntOrNull() ?: 0 }
        }

    companion object {
        private const val ESC = '\u001B'
        private const val BEL = '\u0007'
        private const val MIN_NON_CONTROL_CODE = 128
        private const val CSI_FINAL_MIN = 0x40
        private const val CSI_FINAL_MAX = 0x7E
        private const val MAX_ESCAPE_SEQUENCE_LEN = 256
    }
}
