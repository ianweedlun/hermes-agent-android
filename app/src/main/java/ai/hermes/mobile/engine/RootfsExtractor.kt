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

    /**
     * Unpacks a tar.gz archive into [destinationDir] using the specified [tarExecutable]
     * or provided [runner]. On success, records [targetVersion] into the sentinel.
     */
    fun extractFromTarGz(
        tarGzFile: File,
        targetVersion: String,
        tarExecutable: String = "tar",
        runner: (List<String>) -> Boolean = { cmd ->
            try {
                val proc = ProcessBuilder(cmd).redirectErrorStream(true).start()
                proc.waitFor() == 0
            } catch (_: Exception) {
                false
            }
        },
    ): Boolean {
        if (!tarGzFile.exists() || !tarGzFile.isFile) {
            return false
        }
        if (!destinationDir.exists()) {
            destinationDir.mkdirs()
        }

        // First attempt standard shell extraction
        val command =
            listOf(
                tarExecutable,
                "-xzf",
                tarGzFile.absolutePath,
                "-C",
                destinationDir.absolutePath,
            )
        var success = runner(command)

        // SELinux untrusted_app blocks /system/bin/tar -> /vendor/bin/toybox_vendor exec.
        // Fallback to pure JVM Apache Commons-style tar/gzip parsing.
        if (!success) {
            success = extractTarGzJvm(tarGzFile)
        }

        if (success) {
            markExtracted(targetVersion)
        }
        return success
    }

    /**
     * Pure Java implementation of tar.gz extraction that does not rely on system tar or toybox,
     * fully bypassing Android SELinux domain restrictions.
     */
    fun extractTarGzJvm(tarGzFile: File): Boolean {
        try {
            java.io.FileInputStream(tarGzFile).use { fis ->
                val stream: java.io.InputStream =
                    if (tarGzFile.name.endsWith(".gz")) {
                        java.util.zip.GZIPInputStream(fis)
                    } else {
                        fis
                    }
                val buffer = ByteArray(512)
                val dataBuffer = ByteArray(8192)
                while (true) {
                    var bytesRead = 0
                    while (bytesRead < 512) {
                        val r = stream.read(buffer, bytesRead, 512 - bytesRead)
                        if (r < 0) break
                        bytesRead += r
                    }
                    if (bytesRead < 512) break

                    // Check for end-of-archive (two consecutive 512-byte zero blocks)
                    if (buffer.all { it == 0.toByte() }) {
                        continue
                    }

                    // Header: name (offset 0, size 100)
                    var rawName = String(buffer, 0, 100, Charsets.UTF_8).trimEnd(0.toChar())
                    // Typeflag (offset 156, 1 byte)
                    val typeFlag = buffer[156].toInt().toChar()
                    // Prefix (offset 345, size 155)
                    val prefix = String(buffer, 345, 155, Charsets.UTF_8).trimEnd(0.toChar())
                    if (prefix.isNotEmpty()) {
                        rawName = "$prefix/$rawName"
                    }
                    if (rawName.isEmpty()) continue

                    // File size in octal (offset 124, size 12)
                    val sizeStr = String(buffer, 124, 12, Charsets.UTF_8).trim().trimEnd(0.toChar())
                    val fileSize = if (sizeStr.isNotEmpty()) sizeStr.toLongOrNull(8) ?: 0L else 0L

                    // Normalize entry path
                    val cleanName = rawName.removePrefix("./").removePrefix("/")
                    if (cleanName.isEmpty() || cleanName == ".") {
                        continue
                    }

                    val targetFile = File(destinationDir, cleanName)
                    val canonicalDest = destinationDir.canonicalPath
                    val canonicalTarget = targetFile.canonicalPath
                    if (!canonicalTarget.startsWith(canonicalDest + File.separator) && canonicalTarget != canonicalDest) {
                        return false
                    }

                    // Link target (offset 157, size 100) for symlinks (typeflag '2')
                    val linkTarget = String(buffer, 157, 100, Charsets.UTF_8).trimEnd(0.toChar())

                    if (typeFlag == '5' || rawName.endsWith("/")) {
                        targetFile.mkdirs()
                    } else if (typeFlag == '2') {
                        targetFile.parentFile?.mkdirs()
                        targetFile.delete()
                        try {
                            java.nio.file.Files.createSymbolicLink(
                                targetFile.toPath(),
                                java.nio.file.Paths
                                    .get(linkTarget),
                            )
                        } catch (_: Exception) {
                            // If createSymbolicLink fails on Android filesystem, copy source file
                            val srcFile =
                                if (linkTarget.startsWith("/")) {
                                    File(destinationDir, linkTarget.removePrefix("/"))
                                } else {
                                    File(targetFile.parentFile, linkTarget)
                                }
                            if (srcFile.exists()) {
                                srcFile.copyTo(targetFile, overwrite = true)
                                if (isExecutableEntry(cleanName)) {
                                    targetFile.setExecutable(true, false)
                                }
                            }
                        }
                    } else if (typeFlag == '0' || typeFlag == 0.toChar()) {
                        targetFile.parentFile?.mkdirs()
                        targetFile.outputStream().use { out ->
                            var remaining = fileSize
                            while (remaining > 0) {
                                val toRead = minOf(remaining, dataBuffer.size.toLong()).toInt()
                                val r = stream.read(dataBuffer, 0, toRead)
                                if (r < 0) break
                                out.write(dataBuffer, 0, r)
                                remaining -= r
                            }
                        }
                        val padding = (512 - (fileSize % 512)) % 512
                        var skipped = 0L
                        while (skipped < padding) {
                            val s = stream.skip(padding - skipped)
                            if (s <= 0) break
                            skipped += s
                        }
                        // Mode in octal (offset 100, size 8)
                        val modeStr = String(buffer, 100, 8, Charsets.UTF_8).trim().trimEnd(0.toChar())
                        val mode = modeStr.toIntOrNull(8) ?: 0
                        if ((mode and 0b001001001) != 0 || isExecutableEntry(cleanName) || isExecutableEntry(rawName)) {
                            targetFile.setExecutable(true, false)
                        }
                    } else {
                        // Skip content blocks for unsupported entry types
                        val totalToSkip = fileSize + ((512 - (fileSize % 512)) % 512)
                        var skipped = 0L
                        while (skipped < totalToSkip) {
                            val s = stream.skip(totalToSkip - skipped)
                            if (s <= 0) break
                            skipped += s
                        }
                    }
                }
            }
            return true
        } catch (e: Exception) {
            android.util.Log.e("RootfsExtractor", "JVM tar extraction failed: ${e.message}", e)
            return false
        }
    }

    fun markExtracted(version: String) {
        if (!destinationDir.exists()) {
            destinationDir.mkdirs()
        }
        sentinelFile.writeText(version)
    }

    private fun isExecutableEntry(name: String): Boolean =
        name.contains("/bin/") ||
            name.startsWith("bin/") ||
            name.contains("/sbin/") ||
            name.startsWith("sbin/") ||
            name.contains("/lib/") ||
            name.startsWith("lib/") ||
            name.contains(".so") ||
            name.endsWith(".sh")

    companion object {
        const val SENTINEL_FILE_NAME = ".rootfs_extracted"
    }
}
