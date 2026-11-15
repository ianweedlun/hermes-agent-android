package ai.hermes.mobile.terminal

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bridges [TerminalSession] with a [PtyConnection], streaming UTF-8 output chunks into the
 * session buffer and forwarding user input and resize events back to the PTY.
 */
class TerminalPtyBridge(
    val session: TerminalSession,
    private val connector: PtyConnector,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val running = AtomicBoolean(false)
    private var bridgeJob: Job? = null

    @Volatile
    private var inputChannel = Channel<ByteArray>(Channel.BUFFERED)

    @Volatile
    private var activeConnection: PtyConnection? = null

    fun isRunning(): Boolean = running.get()

    fun start() {
        if (!running.compareAndSet(false, true)) {
            return
        }
        inputChannel = Channel(Channel.BUFFERED)
        session.updateLifecycleState(TerminalSessionState.CONNECTING)
        bridgeJob =
            scope.launch(ioDispatcher) {
                runBridgeLoop()
            }
    }

    fun stop() {
        running.set(false)
        session.attachInputSink(null)
        inputChannel.close()
        closeConnection(activeConnection)
        activeConnection = null
        bridgeJob?.cancel()
        bridgeJob = null
        session.close()
    }

    private suspend fun runBridgeLoop() {
        var connection: PtyConnection? = null
        try {
            val dims = session.dimensions.value
            val opened = connector.connect(dims.cols, dims.rows)
            connection = opened
            activeConnection = opened
            session.attachInputSink { bytes -> inputChannel.trySend(bytes).isSuccess }
            session.updateLifecycleState(TerminalSessionState.ACTIVE)

            coroutineScope {
                val writerJob = launch { pumpInputLoop(opened.outputStream) }
                val resizeJob = launch { pumpResizeLoop(opened) }
                readStreamLoop(opened.inputStream)
                writerJob.cancel()
                resizeJob.cancel()
            }
            if (running.get()) {
                session.updateLifecycleState(TerminalSessionState.DISCONNECTED)
            }
        } catch (e: IOException) {
            handleStreamFailure("PTY I/O error", e)
        } catch (e: SecurityException) {
            handleStreamFailure("PTY permission denied", e)
        } finally {
            session.attachInputSink(null)
            activeConnection = null
            closeConnection(connection)
            running.set(false)
        }
    }

    private suspend fun pumpInputLoop(outputStream: OutputStream) {
        for (bytes in inputChannel) {
            if (!running.get()) {
                break
            }
            outputStream.write(bytes)
            outputStream.flush()
        }
    }

    private suspend fun pumpResizeLoop(connection: PtyConnection) {
        session.resizeEvents.collect { dims ->
            connection.sendResize(dims.cols, dims.rows)
        }
    }

    private fun readStreamLoop(inputStream: InputStream) {
        val decoder =
            Charsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
        val rawBuffer = ByteArray(READ_BUFFER_BYTES)
        val carryBuffer = ByteBuffer.allocate(READ_BUFFER_BYTES + UTF8_MAX_BYTES)
        val charBuffer = CharBuffer.allocate(READ_BUFFER_BYTES + UTF8_MAX_BYTES)

        while (running.get()) {
            val bytesRead = inputStream.read(rawBuffer)
            if (bytesRead == -1) {
                break
            }
            if (bytesRead > 0) {
                carryBuffer.put(rawBuffer, 0, bytesRead)
                carryBuffer.flip()
                charBuffer.clear()
                decoder.decode(carryBuffer, charBuffer, false)
                charBuffer.flip()
                if (charBuffer.hasRemaining()) {
                    session.appendOutput(charBuffer.toString())
                }
                carryBuffer.compact()
            }
        }
    }

    private fun handleStreamFailure(
        prefix: String,
        error: Exception,
    ) {
        if (running.get()) {
            Log.w(TAG, prefix, error)
            session.updateLifecycleState(
                TerminalSessionState.ERROR,
                error.message ?: prefix,
            )
        }
    }

    private fun closeConnection(connection: PtyConnection?) {
        try {
            connection?.close()
        } catch (e: IOException) {
            Log.d(TAG, "Error closing PTY connection", e)
        }
    }

    companion object {
        private const val TAG = "TerminalPtyBridge"
        private const val READ_BUFFER_BYTES = 4096
        private const val UTF8_MAX_BYTES = 4
        const val IPC_SOCKET_NAME = "ipc.sock"

        fun createDefault(
            filesDir: File,
            workspaceDir: File,
            session: TerminalSession = TerminalSession(),
            scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        ): TerminalPtyBridge {
            val socketFile = File(filesDir, IPC_SOCKET_NAME)
            val connector =
                FallbackPtyConnector(
                    primary = UnixSocketPtyConnector(socketFile),
                    fallback = ShellProcessPtyConnector(workingDir = workspaceDir),
                )
            return TerminalPtyBridge(
                session = session,
                connector = connector,
                scope = scope,
            )
        }
    }
}
