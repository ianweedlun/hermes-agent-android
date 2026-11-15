package ai.hermes.mobile.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RootfsExtractorTest {
    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun extractFromZipExtractsEntriesAndCreatesSentinel() {
        val destDir = tempFolder.newFolder("rootfs")
        val extractor = RootfsExtractor(destDir)

        assertFalse(extractor.isExtracted())

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("bin/sh"))
            zos.write("#!/bin/sh\necho hello".toByteArray())
            zos.closeEntry()
        }

        extractor.extractFromZip(ByteArrayInputStream(baos.toByteArray()), "1.0.0")

        assertTrue(extractor.isExtracted())
        val extractedSh = File(destDir, "bin/sh")
        assertTrue(extractedSh.exists())
    }

    @Test(expected = SecurityException::class)
    fun extractFromZipRejectsPathTraversal() {
        val destDir = tempFolder.newFolder("rootfs_secure")
        val extractor = RootfsExtractor(destDir)

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("../malicious.txt"))
            zos.write("exploit".toByteArray())
            zos.closeEntry()
        }

        extractor.extractFromZip(ByteArrayInputStream(baos.toByteArray()), "1.0.0")
    }
}
