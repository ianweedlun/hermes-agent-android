package ai.hermes.mobile.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DaemonSupervisorTest {
    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private class FakeManagedProcess : ManagedProcess {
        var alive = true
        var destroyed = false
        var forceDestroyed = false

        override val isAlive: Boolean
            get() = alive

        override fun exitValue(): Int = if (alive) throw IllegalThreadStateException() else 0

        override fun destroy() {
            destroyed = true
            alive = false
        }

        override fun destroyForcibly() {
            forceDestroyed = true
            alive = false
        }

        override fun waitFor(timeoutMs: Long): Boolean = !alive
    }

    private class FakeProcessLauncher(
        val process: FakeManagedProcess = FakeManagedProcess(),
    ) : ProcessLauncher {
        override fun start(
            command: List<String>,
            environment: Map<String, String>,
            workingDir: File,
        ): ManagedProcess = process
    }

    @Test
    fun startAndStopTransitionsWorkProperly() {
        val root = tempFolder.newFolder("supervisor_test")
        val filesDir = File(root, "files").apply { mkdirs() }
        val cacheDir = File(root, "cache").apply { mkdirs() }
        val topology = StorageTopology(filesDir, cacheDir)

        val config = PRootConfig.fromTopology(topology)
        val fakeProcess = FakeManagedProcess()
        val launcher = FakeProcessLauncher(fakeProcess)
        val supervisor = DaemonSupervisor(config, launcher)

        assertEquals(DaemonProcessState.STOPPED, supervisor.currentState)

        val started = supervisor.start()
        assertTrue(started)
        assertEquals(DaemonProcessState.RUNNING, supervisor.currentState)

        // Starting again while running returns false
        assertFalse(supervisor.start())

        val stopped = supervisor.stop()
        assertTrue(stopped)
        assertTrue(fakeProcess.destroyed)
        assertEquals(DaemonProcessState.STOPPED, supervisor.currentState)
    }
}
