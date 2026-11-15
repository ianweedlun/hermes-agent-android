package ai.hermes.mobile.terminal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream

class TerminalPtyBridgeTest {
    private class PipePtyConnection : PtyConnection {
        val remoteToTerminal = PipedOutputStream()
        override val inputStream: InputStream = PipedInputStream(remoteToTerminal)

        val terminalToRemote = PipedInputStream()
        override val outputStream: OutputStream = PipedOutputStream(terminalToRemote)

        val recordedResizes = mutableListOf<Pair<Int, Int>>()
        var closed = false

        override fun sendResize(
            cols: Int,
            rows: Int,
        ): Boolean {
            recordedResizes.add(cols to rows)
            return true
        }

        override fun close() {
            closed = true
            remoteToTerminal.close()
            outputStream.close()
        }
    }

    @Test
    fun bridgeStreamsOutputAndForwardsCommandsAndResizes() =
        runBlocking {
            val connection = PipePtyConnection()
            val session = TerminalSession()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val bridge =
                TerminalPtyBridge(
                    session = session,
                    connector = PtyConnector { _, _ -> connection },
                    scope = scope,
                )

            bridge.start()
            withTimeout(3000L) {
                session.sessionState.first { it == TerminalSessionState.ACTIVE }
            }

            connection.remoteToTerminal.write("hello from pty\n".toByteArray(Charsets.UTF_8))
            connection.remoteToTerminal.flush()

            withTimeout(3000L) {
                session.outputBuffer.first { it.plainText.contains("hello from pty") }
            }

            assertTrue(session.writeCommand("whoami"))
            val written = ByteArray(7)
            var total = 0
            while (total < written.size) {
                val read = connection.terminalToRemote.read(written, total, written.size - total)
                if (read == -1) break
                total += read
            }
            assertEquals("whoami\n", written.toString(Charsets.UTF_8))

            assertTrue(session.resize(cols = 100, rows = 30))
            withTimeout(3000L) {
                while (connection.recordedResizes.isEmpty()) {
                    kotlinx.coroutines.delay(10L)
                }
            }
            assertEquals(listOf(100 to 30), connection.recordedResizes)

            bridge.stop()
            scope.cancel()
            assertTrue(connection.closed)
            assertEquals(TerminalSessionState.DISCONNECTED, session.sessionState.value)
        }
}
