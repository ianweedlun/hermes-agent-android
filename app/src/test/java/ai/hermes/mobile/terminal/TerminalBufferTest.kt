package ai.hermes.mobile.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalBufferTest {
    @Test
    fun appendsLinesAndEvictsWhenExceedingCapacity() {
        val buffer = TerminalBuffer(maxLines = 3)
        buffer.appendString("Line 1\nLine 2\nLine 3\nLine 4\n")

        val lines = buffer.getLines()
        assertEquals(3, lines.size)
        assertEquals("Line 2", lines[0])
        assertEquals("Line 3", lines[1])
        assertEquals("Line 4", lines[2])
    }

    @Test
    fun stripsAnsiEscapeCodes() {
        val coloredOutput = "\u001B[32malpine:proot: listening\u001B[0m\n"
        val buffer = TerminalBuffer()
        buffer.appendString(coloredOutput)

        val latest = buffer.getLatestLine()
        assertEquals("alpine:proot: listening", latest)
    }

    @Test
    fun preservesPartialStreamingLines() {
        val buffer = TerminalBuffer()
        buffer.appendString("proot initializing...")
        assertEquals("proot initializing...", buffer.getLatestLine())

        buffer.appendString(" done!\n")
        assertEquals("proot initializing... done!", buffer.getLatestLine())
        assertEquals(1, buffer.lineCount())
    }

    @Test
    fun clearEmptiesBuffer() {
        val buffer = TerminalBuffer()
        buffer.appendString("some logs\n")
        buffer.clear()
        assertEquals(0, buffer.lineCount())
        assertTrue(buffer.renderFullText().isEmpty())
    }
}
