package ai.hermes.mobile.terminal

import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.util.Log
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

interface PtyConnection {
    val isConnected: Boolean

    fun open(): Boolean

    fun getInputStream(): InputStream?

    fun getOutputStream(): OutputStream?

    fun close()
}

/**
 * Android platform Unix domain socket connection using LocalSocket.
 */
class LocalSocketConnection(
    private val socketFile: File,
) : PtyConnection {
    private var localSocket: LocalSocket? = null
    private val connected = AtomicBoolean(false)

    override val isConnected: Boolean
        get() = connected.get()

    override fun open(): Boolean =
        try {
            val socket = LocalSocket()
            socket.connect(LocalSocketAddress(socketFile.absolutePath, LocalSocketAddress.Namespace.FILESYSTEM))
            localSocket = socket
            connected.set(true)
            true
        } catch (e: IOException) {
            Log.w(TAG, "Failed to connect to Unix domain socket: ${socketFile.absolutePath}", e)
            connected.set(false)
            false
        }

    override fun getInputStream(): InputStream? = localSocket?.inputStream

    override fun getOutputStream(): OutputStream? = localSocket?.outputStream

    override fun close() {
        try {
            localSocket?.close()
        } catch (e: IOException) {
            Log.d(TAG, "Error closing LocalSocket", e)
        } finally {
            localSocket = null
            connected.set(false)
        }
    }

    companion object {
        private const val TAG = "LocalSocketConnection"
    }
}

/**
 * Manages the PTY byte stream with 64KB backpressure protection per IPC-CONTRACT.md.
 */
class PtySocketClient(
    private val connection: PtyConnection,
    private val onBytesReceived: (ByteArray, Int) -> Unit,
    private val onOverflow: ((Int) -> Unit)? = null,
) {
    private val running = AtomicBoolean(false)
    private var readThread: Thread? = null

    val isConnected: Boolean
        get() = connection.isConnected

    @Synchronized
    fun start(): Boolean {
        if (running.get()) {
            return true
        }

        if (!connection.open()) {
            return false
        }

        running.set(true)
        val thread =
            Thread({
                val buffer = ByteArray(READ_BUFFER_SIZE)
                val inputStream = connection.getInputStream() ?: return@Thread

                try {
                    while (running.get()) {
                        val bytesRead = inputStream.read(buffer)
                        if (bytesRead < 0) {
                            break
                        }
                        if (bytesRead > 0) {
                            if (bytesRead > MAX_BACKPRESSURE_BYTES) {
                                val dropped = bytesRead - MAX_BACKPRESSURE_BYTES
                                onOverflow?.invoke(dropped)
                                onBytesReceived(buffer, MAX_BACKPRESSURE_BYTES)
                            } else {
                                onBytesReceived(buffer, bytesRead)
                            }
                        }
                    }
                } catch (e: IOException) {
                    Log.d(TAG, "PTY socket read stream closed or interrupted", e)
                } finally {
                    stop()
                }
            }, "PtyReadThread")

        readThread = thread
        thread.start()
        return true
    }

    fun sendInput(bytes: ByteArray): Boolean {
        val outputStream = connection.getOutputStream() ?: return false
        return try {
            outputStream.write(bytes)
            outputStream.flush()
            true
        } catch (e: IOException) {
            Log.w(TAG, "Failed to write input to PTY socket", e)
            false
        }
    }

    @Synchronized
    fun stop() {
        if (!running.getAndSet(false)) {
            return
        }
        connection.close()
        readThread?.interrupt()
        readThread = null
    }

    companion object {
        private const val TAG = "PtySocketClient"
        const val READ_BUFFER_SIZE = 8192
        const val MAX_BACKPRESSURE_BYTES = 64 * 1024 // 64KB per IPC-CONTRACT.md
    }
}
