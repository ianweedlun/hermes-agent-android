package ai.hermes.mobile.terminal

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalSessionTest {
    @Test
    fun outputBufferTracksLinesCursorAndClear() {
        val session = TerminalSession(sessionId = "sess-1")

        session.appendOutput("hermes@proot:~$ ls\nREADME.md\n")

        val buffer = session.outputBuffer.value
        assertEquals("hermes@proot:~$ ls\nREADME.md\n", buffer.plainText)
        assertEquals(3, buffer.lines.size)
        assertEquals(CursorState(row = 2, col = 0, visible = true), session.cursorState.value)
        assertFalse(buffer.overflowDetected)

        session.clearBuffer()
        val cleared = session.outputBuffer.value
        assertEquals("", cleared.plainText)
        assertEquals(0, cleared.totalBytes)
        assertEquals(CursorState(row = 0, col = 0, visible = true), session.cursorState.value)
    }

    @Test
    fun outputBufferHandlesCarriageReturnBackspaceAndErase() {
        val session = TerminalSession()

        session.appendOutput("progress 10%\rprogress 99%")
        assertEquals("progress 99%", session.outputBuffer.value.plainText)

        session.appendOutput("\b\b00%")
        assertEquals("progress 100%", session.outputBuffer.value.plainText)

        session.appendOutput("\u001B[2Jfresh")
        assertEquals("fresh", session.outputBuffer.value.plainText)
    }

    @Test
    fun outputBufferEnforcesBackpressureCapAndSetsOverflow() {
        val session = TerminalSession(maxBufferBytes = 32, maxLines = 100)

        session.appendOutput("line-0123456789\n")
        session.appendOutput("line-abcdefghij\n")
        session.appendOutput("line-klmnopqrst\n")

        val buffer = session.outputBuffer.value
        assertTrue(buffer.overflowDetected)
        assertTrue(buffer.droppedBytes > 0L)
        assertTrue(buffer.totalBytes <= 32)
        assertTrue(buffer.plainText.contains("line-klmnopqrst"))

        session.clearBuffer()
        assertFalse(session.outputBuffer.value.overflowDetected)
        assertEquals(0L, session.outputBuffer.value.droppedBytes)
    }

    @Test
    fun resizeEmitsEventsAndClampsToValidBounds() =
        runBlocking {
            val session = TerminalSession(initialDimensions = TerminalDimensions(cols = 80, rows = 24))

            val eventDeferred = async { session.resizeEvents.first() }
            val changed = session.resize(cols = 120, rows = 40)
            assertTrue(changed)
            assertEquals(TerminalDimensions(cols = 120, rows = 40), eventDeferred.await())
            assertEquals(TerminalDimensions(cols = 120, rows = 40), session.dimensions.value)

            // Identical dimensions should not emit a duplicate resize
            assertFalse(session.resize(cols = 120, rows = 40))

            // Out-of-bounds dimensions clamp cleanly
            assertTrue(session.resize(cols = 2, rows = 999))
            assertEquals(
                TerminalDimensions(cols = TerminalDimensions.MIN_COLS, rows = TerminalDimensions.MAX_ROWS),
                session.dimensions.value,
            )
        }

    @Test
    fun commandWritingForwardsBytesAndRespectsLifecycle() =
        runBlocking {
            val writtenPayloads = mutableListOf<String>()
            val session =
                TerminalSession(
                    inputSink = { bytes ->
                        writtenPayloads.add(bytes.toString(Charsets.UTF_8))
                        true
                    },
                )

            val inputDeferred = async { session.inputEvents.first() }
            assertTrue(session.writeCommand("uname -a"))
            assertEquals("uname -a\n", inputDeferred.await().toString(Charsets.UTF_8))
            assertEquals(listOf("uname -a\n"), writtenPayloads)

            // Already newline-terminated command does not double-append newline
            assertTrue(session.writeCommand("pwd\n"))
            assertEquals("pwd\n", writtenPayloads.last())

            // Empty input rejected
            assertFalse(session.writeInput(""))

            // Disconnected session rejects further writes
            session.close()
            assertEquals(TerminalSessionState.DISCONNECTED, session.sessionState.value)
            assertFalse(session.writeCommand("echo fail"))
        }
}
