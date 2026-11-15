package ai.hermes.mobile.engine

import android.util.Log
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

enum class DaemonProcessState {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    FAILED,
}

interface ManagedProcess {
    val isAlive: Boolean

    fun exitValue(): Int

    fun destroy()

    fun destroyForcibly()

    fun waitFor(timeoutMs: Long): Boolean
}

interface ProcessLauncher {
    fun start(
        command: List<String>,
        environment: Map<String, String>,
        workingDir: File,
    ): ManagedProcess
}

class SystemManagedProcess(
    private val process: Process,
) : ManagedProcess {
    override val isAlive: Boolean
        get() = process.isAlive

    override fun exitValue(): Int = process.exitValue()

    override fun destroy() {
        process.destroy()
    }

    override fun destroyForcibly() {
        process.destroyForcibly()
    }

    override fun waitFor(timeoutMs: Long): Boolean = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
}

class SystemProcessLauncher : ProcessLauncher {
    override fun start(
        command: List<String>,
        environment: Map<String, String>,
        workingDir: File,
    ): ManagedProcess {
        val builder = ProcessBuilder(command)
        builder.directory(workingDir)
        builder.environment().putAll(environment)
        val process = builder.start()
        return SystemManagedProcess(process)
    }
}

/**
 * Supervises the lifecycle of the PRoot-contained Hermes daemon process.
 */
class DaemonSupervisor(
    private val config: PRootConfig,
    private val launcher: ProcessLauncher = SystemProcessLauncher(),
) {
    private val state = AtomicReference(DaemonProcessState.STOPPED)
    private var activeProcess: ManagedProcess? = null
    private var lastExitCode: Int? = null

    val currentState: DaemonProcessState
        get() = state.get()

    val exitCode: Int?
        get() = lastExitCode

    @Synchronized
    fun start(): Boolean {
        if (state.get() == DaemonProcessState.RUNNING || state.get() == DaemonProcessState.STARTING) {
            return false
        }
        state.set(DaemonProcessState.STARTING)
        return try {
            val command = config.buildCommandLine()
            val env = config.buildEnvironment()
            val proc = launcher.start(command, env, config.workspaceDir)
            activeProcess = proc
            state.set(DaemonProcessState.RUNNING)
            true
        } catch (e: java.io.IOException) {
            Log.e(TAG, "Failed to launch PRoot daemon", e)
            state.set(DaemonProcessState.FAILED)
            false
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception launching PRoot daemon", e)
            state.set(DaemonProcessState.FAILED)
            false
        }
    }

    @Synchronized
    fun stop(gracefulTimeoutMs: Long = DEFAULT_STOP_TIMEOUT_MS): Boolean {
        val proc =
            activeProcess ?: run {
                state.set(DaemonProcessState.STOPPED)
                return true
            }

        state.set(DaemonProcessState.STOPPING)
        proc.destroy()

        val exited = proc.waitFor(gracefulTimeoutMs)
        if (!exited) {
            proc.destroyForcibly()
            proc.waitFor(FORCE_KILL_TIMEOUT_MS)
        }

        lastExitCode =
            try {
                proc.exitValue()
            } catch (e: IllegalThreadStateException) {
                Log.w(TAG, "Process not yet exited when reading exit code", e)
                null
            }

        activeProcess = null
        state.set(DaemonProcessState.STOPPED)
        return true
    }

    companion object {
        private const val TAG = "DaemonSupervisor"
        const val DEFAULT_STOP_TIMEOUT_MS = 3000L
        const val FORCE_KILL_TIMEOUT_MS = 1000L
    }
}
