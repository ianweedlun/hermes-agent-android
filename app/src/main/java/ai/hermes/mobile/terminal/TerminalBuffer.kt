package ai.hermes.mobile.terminal

import java.nio.charset.StandardCharsets

/**
 * In-memory circular buffer for terminal output supporting line capping and ANSI filtering.
 */
class TerminalBuffer(
    val maxLines: Int = DEFAULT_MAX_LINES,
) {
    private val lines = ArrayDeque<String>(maxLines)
    private var pendingPartialLine = StringBuilder()

    @Synchronized
    fun appendBytes(
        bytes: ByteArray,
        offset: Int = 0,
        length: Int = bytes.size,
    ) {
        val text = String(bytes, offset, length, StandardCharsets.UTF_8)
        appendString(text)
    }

    @Synchronized
    fun appendString(text: String) {
        val cleanText = stripAnsiCodes(text)
        val parts = cleanText.split("\n")

        for (i in 0 until parts.size - 1) {
            pendingPartialLine.append(parts[i])
            pushLine(pendingPartialLine.toString())
            pendingPartialLine = StringBuilder()
        }

        // Last part is either complete (if text ended with \n) or partial
        pendingPartialLine.append(parts.last())
    }

    @Synchronized
    fun pushLine(line: String) {
        if (lines.size >= maxLines) {
            lines.removeFirst()
        }
        lines.addLast(line)
    }

    @Synchronized
    fun getLines(): List<String> = lines.toList()

    @Synchronized
    fun getLatestLine(): String = lines.lastOrNull() ?: pendingPartialLine.toString()

    @Synchronized
    fun renderFullText(): String {
        val builder = StringBuilder()
        for (line in lines) {
            builder.append(line).append("\n")
        }
        if (pendingPartialLine.isNotEmpty()) {
            builder.append(pendingPartialLine)
        }
        return builder.toString()
    }

    @Synchronized
    fun clear() {
        lines.clear()
        pendingPartialLine.setLength(0)
    }

    @Synchronized
    fun lineCount(): Int = lines.size

    companion object {
        const val DEFAULT_MAX_LINES = 1000
        private val ANSI_REGEX = Regex("\u001B\\[[;?0-9]*[a-zA-Z]")

        fun stripAnsiCodes(input: String): String = ANSI_REGEX.replace(input, "")
    }
}
