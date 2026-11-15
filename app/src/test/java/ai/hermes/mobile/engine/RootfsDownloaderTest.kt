package ai.hermes.mobile.engine

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest

class RootfsDownloaderTest {

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun downloadSuccessfulWithoutChecksum() = runBlocking {
        val destFile = File(tempFolder.newFolder("downloads"), "hermes-rootfs-aarch64.tar.gz")
        val content = "fake-rootfs-binary-content".toByteArray()

        val downloader = HttpRootfsDownloader(
            streamOpener = { ByteArrayInputStream(content) },
        )

        val result = downloader.download(
            url = "https://example.com/assets/rootfs.tar.gz",
            destinationFile = destFile,
        )

        assertTrue(result.isSuccess)
        val file = result.getOrThrow()
        assertTrue(file.exists())
        assertEquals("fake-rootfs-binary-content", file.readText())
    }

    @Test
    fun downloadSuccessfulWithValidChecksum() = runBlocking {
        val destFile = File(tempFolder.newFolder("downloads"), "hermes-rootfs-aarch64.tar.gz")
        val content = "test-arm64-alpine-payload".toByteArray()
        val expectedSha = sha256Hex(content)

        var progressCalls = 0
        val downloader = HttpRootfsDownloader(
            streamOpener = { ByteArrayInputStream(content) },
        )

        val result = downloader.download(
            url = "https://example.com/assets/rootfs.tar.gz",
            destinationFile = destFile,
            expectedSha256 = expectedSha,
            onProgress = { progress ->
                progressCalls++
                assertTrue(progress.bytesDownloaded > 0)
            },
        )

        assertTrue(result.isSuccess)
        assertTrue(destFile.exists())
        assertTrue(progressCalls > 0)
        assertTrue(downloader.verifyChecksum(destFile, expectedSha))
    }

    @Test
    fun downloadFailsAndDeletesTempWhenChecksumMismatches() = runBlocking {
        val destFile = File(tempFolder.newFolder("downloads"), "hermes-rootfs-aarch64.tar.gz")
        val content = "tampered-content".toByteArray()

        val downloader = HttpRootfsDownloader(
            streamOpener = { ByteArrayInputStream(content) },
        )

        val result = downloader.download(
            url = "https://example.com/assets/rootfs.tar.gz",
            destinationFile = destFile,
            expectedSha256 = "0000000000000000000000000000000000000000000000000000000000000000",
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
        assertFalse(destFile.exists())
        assertFalse(File(destFile.parentFile, "${destFile.name}.downloading").exists())
    }

    @Test
    fun downloadFailsGracefullyOnStreamException() = runBlocking {
        val destFile = File(tempFolder.newFolder("downloads"), "hermes-rootfs-aarch64.tar.gz")

        val downloader = HttpRootfsDownloader(
            streamOpener = { throw IOException("Connection reset by peer") },
        )

        val result = downloader.download(
            url = "https://example.com/assets/rootfs.tar.gz",
            destinationFile = destFile,
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
        assertFalse(destFile.exists())
    }

    @Test
    fun verifyChecksumReturnsFalseForNonexistentOrMismatchedFile() {
        val folder = tempFolder.newFolder("verify_test")
        val nonExistent = File(folder, "missing.tar.gz")
        val downloader = HttpRootfsDownloader()

        assertFalse(downloader.verifyChecksum(nonExistent, "abc123"))

        val realFile = File(folder, "sample.tar.gz")
        realFile.writeText("hello world")
        val expectedSha = sha256Hex("hello world".toByteArray())

        assertTrue(downloader.verifyChecksum(realFile, expectedSha))
        assertFalse(downloader.verifyChecksum(realFile, "deadbeef"))
    }

    private fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data).joinToString("") { "%02x".format(it) }
    }
}
