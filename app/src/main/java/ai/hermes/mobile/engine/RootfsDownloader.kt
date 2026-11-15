package ai.hermes.mobile.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val isIndeterminate: Boolean = totalBytes <= 0L,
) {
    val percent: Int
        get() = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt() else 0
}

interface RootfsDownloader {
    suspend fun download(
        url: String,
        destinationFile: File,
        expectedSha256: String? = null,
        onProgress: ((DownloadProgress) -> Unit)? = null,
    ): Result<File>

    fun verifyChecksum(
        file: File,
        expectedSha256: String,
    ): Boolean
}

class HttpRootfsDownloader(
    private val connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
    private val readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS,
    private val streamOpener: (URL) -> InputStream = { url ->
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = connectTimeoutMs
        conn.readTimeout = readTimeoutMs
        conn.instanceFollowRedirects = true
        val responseCode = conn.responseCode
        if (responseCode !in 200..299) {
            throw IOException("HTTP request failed with response code $responseCode")
        }
        conn.inputStream
    },
) : RootfsDownloader {

    override suspend fun download(
        url: String,
        destinationFile: File,
        expectedSha256: String?,
        onProgress: ((DownloadProgress) -> Unit)?,
    ): Result<File> = withContext(Dispatchers.IO) {
        val parent = destinationFile.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }

        val tempFile = File(destinationFile.parentFile, "${destinationFile.name}.downloading")
        try {
            val stream = streamOpener(URL(url))
            val digest = MessageDigest.getInstance("SHA-256")
            var bytesWritten = 0L

            stream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var read = input.read(buffer)
                    while (read != -1) {
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        bytesWritten += read
                        onProgress?.invoke(DownloadProgress(bytesWritten, -1L, true))
                        read = input.read(buffer)
                    }
                    output.flush()
                }
            }

            if (expectedSha256 != null) {
                val calculatedHex = digest.digest().joinToString("") { "%02x".format(it) }
                if (!calculatedHex.equals(expectedSha256.trim(), ignoreCase = true)) {
                    tempFile.delete()
                    return@withContext Result.failure(
                        SecurityException(
                            "SHA-256 checksum mismatch: expected $expectedSha256 but got $calculatedHex",
                        ),
                    )
                }
            }

            if (destinationFile.exists()) {
                destinationFile.delete()
            }
            if (!tempFile.renameTo(destinationFile)) {
                tempFile.copyTo(destinationFile, overwrite = true)
                tempFile.delete()
            }

            Result.success(destinationFile)
        } catch (e: IOException) {
            tempFile.delete()
            Result.failure(e)
        } catch (e: SecurityException) {
            tempFile.delete()
            Result.failure(e)
        } catch (e: NoSuchAlgorithmException) {
            tempFile.delete()
            Result.failure(e)
        }
    }

    override fun verifyChecksum(
        file: File,
        expectedSha256: String,
    ): Boolean {
        if (!file.exists() || !file.isFile) return false
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                var read = input.read(buffer)
                while (read != -1) {
                    digest.update(buffer, 0, read)
                    read = input.read(buffer)
                }
            }
            val calculated = digest.digest().joinToString("") { "%02x".format(it) }
            calculated.equals(expectedSha256.trim(), ignoreCase = true)
        } catch (e: IOException) {
            false
        } catch (e: NoSuchAlgorithmException) {
            false
        }
    }

    companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MS = 15000
        const val DEFAULT_READ_TIMEOUT_MS = 30000
        private const val BUFFER_SIZE = 8192
    }
}
