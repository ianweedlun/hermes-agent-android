package ai.hermes.mobile.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BootstrapExtractionTest {
    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun extractFromTarGzInvokesRunnerWithExpectedFlagsAndCreatesSentinel() {
        val rootfsDir = tempFolder.newFolder("target_rootfs")
        val archiveFile = File(tempFolder.newFolder("archives"), "hermes-rootfs-aarch64.tar.gz")
        archiveFile.writeText("archive-stub")

        var capturedCommand: List<String>? = null
        val extractor = RootfsExtractor(rootfsDir)

        assertFalse(extractor.isExtracted())

        val success =
            extractor.extractFromTarGz(
                tarGzFile = archiveFile,
                targetVersion = "0.2.0-alpine",
                tarExecutable = "/system/bin/tar",
                runner = { cmd ->
                    capturedCommand = cmd
                    true
                },
            )

        assertTrue(success)
        assertTrue(extractor.isExtracted())

        val sentinel = File(rootfsDir, RootfsExtractor.SENTINEL_FILE_NAME)
        assertTrue(sentinel.exists())
        assertEquals("0.2.0-alpine", sentinel.readText())

        assertEquals(
            listOf(
                "/system/bin/tar",
                "-xzf",
                archiveFile.absolutePath,
                "-C",
                rootfsDir.absolutePath,
            ),
            capturedCommand,
        )
    }

    @Test
    fun extractFromTarGzFailsGracefullyWhenRunnerReturnsFalse() {
        val rootfsDir = tempFolder.newFolder("target_rootfs_fail")
        val archiveFile = File(tempFolder.newFolder("archives_fail"), "corrupt.tar.gz")
        archiveFile.writeText("corrupt")

        val extractor = RootfsExtractor(rootfsDir)

        val success =
            extractor.extractFromTarGz(
                tarGzFile = archiveFile,
                targetVersion = "0.2.0-alpine",
                runner = { false },
            )

        assertFalse(success)
        assertFalse(extractor.isExtracted())
        val sentinel = File(rootfsDir, RootfsExtractor.SENTINEL_FILE_NAME)
        assertFalse(sentinel.exists())
    }

    @Test
    fun extractFromTarGzReturnsFalseWhenArchiveFileMissing() {
        val rootfsDir = tempFolder.newFolder("target_rootfs_missing")
        val nonExistentArchive = File(rootfsDir, "does-not-exist.tar.gz")

        val extractor = RootfsExtractor(rootfsDir)

        val success =
            extractor.extractFromTarGz(
                tarGzFile = nonExistentArchive,
                targetVersion = "0.2.0-alpine",
                runner = { true },
            )

        assertFalse(success)
        assertFalse(extractor.isExtracted())
    }
}
