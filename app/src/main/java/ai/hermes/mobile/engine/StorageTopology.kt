package ai.hermes.mobile.engine

import android.content.Context
import java.io.File

/**
 * Enforces the directory structure and scoped sandbox storage per STORAGE-CONTRACT.md.
 */
class StorageTopology(
    val filesDir: File,
    val cacheDir: File,
) {
    constructor(context: Context) : this(
        filesDir = context.filesDir,
        cacheDir = context.cacheDir,
    )

    val rootfsDir: File = File(filesDir, "rootfs")
    val workspaceDir: File = File(filesDir, "workspace")
    val stateDir: File = File(filesDir, "state")
    val binDir: File = File(filesDir, "bin")

    val scratchDir: File = File(cacheDir, "scratch")
    val logsDir: File = File(cacheDir, "logs")
    val webviewCacheDir: File = File(cacheDir, "webview_cache")

    /**
     * Initializes all required storage and cache directories.
     */
    fun initialize() {
        listOf(
            rootfsDir,
            workspaceDir,
            stateDir,
            binDir,
            scratchDir,
            logsDir,
            webviewCacheDir,
        ).forEach { dir ->
            if (!dir.exists()) {
                dir.mkdirs()
            }
        }
    }

    /**
     * Prunes ephemeral scratch files older than [maxAgeMs] (default 24h) per STORAGE-CONTRACT.md.
     */
    fun pruneScratch(
        maxAgeMs: Long = DEFAULT_SCRATCH_MAX_AGE_MS,
        currentTimeMs: Long = System.currentTimeMillis(),
    ): Int {
        if (!scratchDir.exists() || !scratchDir.isDirectory) {
            return 0
        }
        val cutoff = currentTimeMs - maxAgeMs
        var prunedCount = 0
        scratchDir.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff) {
                if (file.isDirectory) {
                    file.deleteRecursively()
                } else {
                    file.delete()
                }
                prunedCount++
            }
        }
        return prunedCount
    }

    companion object {
        const val DEFAULT_SCRATCH_MAX_AGE_MS: Long = 24 * 60 * 60 * 1000L
    }
}
