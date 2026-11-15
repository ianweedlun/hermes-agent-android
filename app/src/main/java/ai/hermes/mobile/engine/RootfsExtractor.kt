package ai.hermes.mobile.engine

import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Handles safe unpacking of bundled rootfs and binaries into app-private storage.
 * Enforces path traversal prevention per SECURITY-SANDBOX.md.
 */
class RootfsExtractor(
    private val destinationDir: File,
) {
    private val sentinelFile: File = File(destinationDir, SENTINEL_FILE_NAME)

    fun isExtracted(): Boolean = sentinelFile.exists()

    /**
     * Unpacks a gzip-compressed or raw stream into [destinationDir].
     * Rejects any path escaping [destinationDir] to prevent zip slip / path traversal attacks.
     */
    fun extractFromZip(
        zipStream: InputStream,
        targetVersion: String,
    ) {
        if (!destinationDir.exists()) {
            destinationDir.mkdirs()
        }
        val canonicalDestPath = destinationDir.canonicalPath

        ZipInputStream(zipStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outputFile = File(destinationDir, entry.name)
                val canonicalOutputFile = outputFile.canonicalPath

                // Strict path traversal defense
                if (!canonicalOutputFile.startsWith(canonicalDestPath + File.separator) &&
                    canonicalOutputFile != canonicalDestPath
                ) {
                    throw SecurityException("Path traversal attempt detected in rootfs: ${entry.name}")
                }

                if (entry.isDirectory) {
                    outputFile.mkdirs()
                } else {
                    outputFile.parentFile?.mkdirs()
                    outputFile.outputStream().use { fos ->
                        zis.copyTo(fos)
                    }
                    if (isExecutableEntry(entry.name)) {
                        outputFile.setExecutable(true, false)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        sentinelFile.writeText(targetVersion)
    }

    fun markExtracted(version: String) {
        if (!destinationDir.exists()) {
            destinationDir.mkdirs()
        }
        sentinelFile.writeText(version)
    }

    private fun isExecutableEntry(name: String): Boolean = name.contains("/bin/") || name.contains("/sbin/") || name.endsWith(".sh")

    companion object {
        const val SENTINEL_FILE_NAME = ".rootfs_extracted"
    }
}
