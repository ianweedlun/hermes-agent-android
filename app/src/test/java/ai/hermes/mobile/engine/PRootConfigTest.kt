package ai.hermes.mobile.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PRootConfigTest {
    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun commandLineContainsRequiredPRootMounts() {
        val root = tempFolder.newFolder("mock_app")
        val filesDir = File(root, "files").apply { mkdirs() }
        val cacheDir = File(root, "cache").apply { mkdirs() }

        val topology = StorageTopology(filesDir, cacheDir)
        topology.initialize()

        val config =
            PRootConfig.fromTopology(
                topology = topology,
                ipcToken = "hermes-secret-token",
                command = listOf("hermes", "daemon", "--port", "8080"),
            )

        val cmd = config.buildCommandLine()

        // Verifies binary
        assertEquals(topology.binDir.resolve("proot").absolutePath, cmd[0])

        // Verifies rootfs and fake root
        assertTrue(cmd.contains("-r"))
        assertTrue(cmd.contains(topology.rootfsDir.absolutePath))
        assertTrue(cmd.contains("-0"))

        // Verifies workspace bind mount
        val workspaceMount = "${topology.workspaceDir.absolutePath}:/workspace"
        assertTrue(cmd.contains(workspaceMount))

        // Verifies command passed
        assertTrue(cmd.contains("hermes"))
        assertTrue(cmd.contains("daemon"))

        // Verifies environment
        val env = config.buildEnvironment()
        assertEquals("hermes-secret-token", env["HERMES_IPC_TOKEN"])
        assertEquals("/home/hermes", env["HOME"])
    }
}
