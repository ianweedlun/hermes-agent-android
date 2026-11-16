package ai.hermes.mobile.terminal

import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.util.Log
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Active bidirectional stream connection to a daemon pseudo-terminal or shell process.
 */
interface PtyConnection : Closeable {
    val inputStream: InputStream
    val outputStream: OutputStream

    fun sendResize(
        cols: Int,
        rows: Int,
    ): Boolean
}

/**
 * Factory that establishes a [PtyConnection] for the given initial terminal dimensions.
 */
fun interface PtyConnector {
    @Throws(IOException::class)
    fun connect(
        cols: Int,
        rows: Int,
    ): PtyConnection
}

/**
 * Connects to the Hermes daemon PTY Unix domain socket per IPC-CONTRACT.md (`ipc.sock`).
 */
class UnixSocketPtyConnector(
    private val socketFile: File,
    private val onResize: ((Int, Int) -> Boolean)? = null,
    private val maxRetries: Int = DEFAULT_MAX_RETRIES,
    private val retryDelayMs: Long = DEFAULT_RETRY_DELAY_MS,
) : PtyConnector {
    override fun connect(
        cols: Int,
        rows: Int,
    ): PtyConnection {
        var lastException: IOException? = null
        for (attempt in 0..maxRetries) {
            val socket = LocalSocket(LocalSocket.SOCKET_STREAM)
            try {
                val address =
                    LocalSocketAddress(
                        socketFile.absolutePath,
                        LocalSocketAddress.Namespace.FILESYSTEM,
                    )
                socket.connect(address)
                onResize?.invoke(cols, rows)
                return UnixSocketPtyConnection(socket, onResize)
            } catch (e: IOException) {
                closeQuietly(socket)
                lastException = e
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(retryDelayMs)
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw IOException("Interrupted while waiting for PTY socket readiness", ie)
                    }
                }
            }
        }
        throw lastException ?: IOException("Failed to connect to PTY Unix domain socket at ${socketFile.absolutePath}")
    }

    private fun closeQuietly(socket: LocalSocket) {
        try {
            socket.close()
        } catch (e: IOException) {
            Log.d(TAG, "Failed to close socket during failed connect", e)
        }
    }

    private class UnixSocketPtyConnection(
        private val socket: LocalSocket,
        private val onResize: ((Int, Int) -> Boolean)?,
    ) : PtyConnection {
        override val inputStream: InputStream = socket.inputStream
        override val outputStream: OutputStream = socket.outputStream

        override fun sendResize(
            cols: Int,
            rows: Int,
        ): Boolean = onResize?.invoke(cols, rows) ?: false

        override fun close() {
            try {
                socket.close()
            } catch (e: IOException) {
                Log.d(TAG, "Error closing PTY Unix socket", e)
            }
        }
    }

    companion object {
        private const val TAG = "UnixSocketPtyConnector"
        const val DEFAULT_MAX_RETRIES = 10
        const val DEFAULT_RETRY_DELAY_MS = 500L
    }
}

/**
 * Launches an interactive shell subprocess as a PTY transport when daemon socket IPC is unavailable.
 */
class ShellProcessPtyConnector(
    private val command: List<String> = DEFAULT_COMMAND,
    private val environment: Map<String, String> = DEFAULT_ENVIRONMENT,
    private val workingDir: File? = null,
) : PtyConnector {
    override fun connect(
        cols: Int,
        rows: Int,
    ): PtyConnection {
        val builder = ProcessBuilder(command).redirectErrorStream(true)
        if (workingDir != null && workingDir.exists() && workingDir.isDirectory) {
            builder.directory(workingDir)
        }
        builder.environment().putAll(environment)
        builder.environment()["COLUMNS"] = cols.toString()
        builder.environment()["LINES"] = rows.toString()
        val process = builder.start()
        return ProcessPtyConnection(process)
    }

    private class ProcessPtyConnection(
        private val process: Process,
    ) : PtyConnection {
        override val inputStream: InputStream = process.inputStream
        override val outputStream: OutputStream = process.outputStream

        override fun sendResize(
            cols: Int,
            rows: Int,
        ): Boolean = false

        override fun close() {
            closeStreamQuietly(outputStream)
            closeStreamQuietly(inputStream)
            process.destroy()
        }

        private fun closeStreamQuietly(stream: Closeable) {
            try {
                stream.close()
            } catch (e: IOException) {
                Log.d(TAG, "Error closing shell process stream", e)
            }
        }
    }

    companion object {
        private const val TAG = "ShellProcessPtyConnector"
        val DEFAULT_COMMAND = listOf("/system/bin/sh", "-i")
        val DEFAULT_ENVIRONMENT =
            mapOf(
                "TERM" to "xterm-256color",
                "LANG" to "en_US.UTF-8",
            )
    }
}

/**
 * Attempts a primary [PtyConnector] (e.g. daemon Unix domain socket) and falls back
 * cleanly when the socket is not yet listening.
 */
class FallbackPtyConnector(
    private val primary: PtyConnector,
    private val fallback: PtyConnector,
) : PtyConnector {
    override fun connect(
        cols: Int,
        rows: Int,
    ): PtyConnection =
        try {
            primary.connect(cols, rows)
        } catch (e: IOException) {
            Log.i(TAG, "Primary PTY connector unavailable, falling back to interactive shell", e)
            fallback.connect(cols, rows)
        }

    companion object {
        private const val TAG = "FallbackPtyConnector"
    }
}
