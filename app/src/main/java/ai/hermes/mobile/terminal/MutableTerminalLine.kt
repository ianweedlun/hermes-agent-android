package ai.hermes.mobile.terminal

/**
 * Mutable character-and-style line backing [TerminalScreenBuffer].
 */
internal class MutableTerminalLine {
    private val chars = StringBuilder()
    private val styles = mutableListOf<AnsiTextStyle>()

    fun length(): Int = chars.length

    fun writeChar(
        col: Int,
        ch: Char,
        style: AnsiTextStyle,
    ) {
        while (chars.length < col) {
            chars.append(' ')
            styles.add(AnsiTextStyle())
        }
        if (col == chars.length) {
            chars.append(ch)
            styles.add(style)
        } else {
            chars.setCharAt(col, ch)
            styles[col] = style
        }
    }

    fun truncateFrom(col: Int) {
        if (col < chars.length) {
            chars.setLength(col)
            while (styles.size > col) {
                styles.removeAt(styles.lastIndex)
            }
        }
    }

    fun clear() {
        chars.clear()
        styles.clear()
    }

    fun byteCount(): Int = chars.toString().toByteArray(Charsets.UTF_8).size

    fun trimHeadBytes(targetMaxBytes: Int): Long {
        val initialBytes = byteCount()
        while (chars.isNotEmpty() && byteCount() > targetMaxBytes) {
            chars.deleteCharAt(0)
            styles.removeAt(0)
        }
        return (initialBytes - byteCount()).toLong().coerceAtLeast(0L)
    }

    fun toTerminalLine(): TerminalLine {
        if (chars.isEmpty()) {
            return TerminalLine.EMPTY
        }
        val spans = mutableListOf<TerminalSpan>()
        var activeStyle = styles[0]
        val segment = StringBuilder().append(chars[0])
        for (i in 1 until chars.length) {
            val nextStyle = styles[i]
            if (nextStyle == activeStyle) {
                segment.append(chars[i])
            } else {
                spans.add(TerminalSpan(segment.toString(), activeStyle))
                segment.clear().append(chars[i])
                activeStyle = nextStyle
            }
        }
        spans.add(TerminalSpan(segment.toString(), activeStyle))
        return TerminalLine(spans)
    }
}
