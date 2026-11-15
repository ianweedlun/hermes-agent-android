package ai.hermes.mobile.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

class PtySocketClientTest {
    private class FakePtyConnection(
        var inputData: ByteArray = ByteArray(0),
    ) : PtyConnection {
        private var open = false
        val outputStream = ByteArrayOutputStream()

        override val isConnected: Boolean
            get() = open

        override fun open(): Boolean {
            open = true
            return true
        }

        override fun getInputStream(): InputStream = ByteArrayInputStream(inputData)

        override fun getOutputStream(): OutputStream = outputStream

        override fun close() {
            open = false
        }
    }

    @Test
    fun readsBytesFromConnection() {
        val testData = "alpine# ls -la\n".toByteArray()
        val fakeConn = FakePtyConnection(testData)
        val received = mutableListOf<Byte>()

        val client =
            PtySocketClient(
                connection = fakeConn,
                onBytesReceived = { bytes, len ->
                    for (i in 0 until len) {
                        received.add(bytes[i])
                    }
                },
            )

        client.start()
        Thread.sleep(100) // Allow reader thread to consume stream
        client.stop()

        assertEquals("alpine# ls -la\n", String(received.toByteArray()))
        assertFalse(client.isConnected)
    }

    @Test
    fun sendInputWritesToConnection() {
        val fakeConn = FakePtyConnection()
        val client = PtySocketClient(fakeConn, onBytesReceived = { _, _ -> })
        fakeConn.open()

        val sent = client.sendInput("help\n".toByteArray())
        assertTrue(sent)
        assertEquals("help\n", fakeConn.outputStream.toString())
    }
}
