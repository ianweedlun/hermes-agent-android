package ai.hermes.mobile.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StorageTopologyTest {
    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun topologyCreatesAllDirectories() {
        val filesDir = tempFolder.newFolder("files")
        val cacheDir = tempFolder.newFolder("cache")

        val topology = StorageTopology(filesDir, cacheDir)
        topology.initialize()

        assertTrue(topology.rootfsDir.exists())
        assertTrue(topology.workspaceDir.exists())
        assertTrue(topology.stateDir.exists())
        assertTrue(topology.binDir.exists())
        assertTrue(topology.scratchDir.exists())
        assertTrue(topology.logsDir.exists())
        assertTrue(topology.webviewCacheDir.exists())
    }

    @Test
    fun pruneScratchRemovesStaleFilesOnly() {
        val filesDir = tempFolder.newFolder("files")
        val cacheDir = tempFolder.newFolder("cache")

        val topology = StorageTopology(filesDir, cacheDir)
        topology.initialize()

        val oldFile = File(topology.scratchDir, "old_session.tmp")
        oldFile.writeText("stale")
        // Set last modified to 48 hours ago
        val now = System.currentTimeMillis()
        val twoDaysAgo = now - (48 * 3600 * 1000L)
        oldFile.setLastModified(twoDaysAgo)

        val freshFile = File(topology.scratchDir, "fresh_session.tmp")
        freshFile.writeText("fresh")
        freshFile.setLastModified(now)

        val pruned = topology.pruneScratch(maxAgeMs = 24 * 3600 * 1000L, currentTimeMs = now)

        assertEquals(1, pruned)
        assertFalse(oldFile.exists())
        assertTrue(freshFile.exists())
    }
}
