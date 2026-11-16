package ai.hermes.mobile.engine

import java.io.File

/**
 * Encapsulates PRoot execution configuration and filesystem mount bindings
 * adhering to STORAGE-CONTRACT.md and IPC-CONTRACT.md.
 */
data class PRootConfig(
    val prootBinary: File,
    val rootfsDir: File,
    val workspaceDir: File,
    val stateDir: File,
    val scratchDir: File,
    val sharedStorageDir: File? = null,
    val ipcToken: String? = null,
    val customCommand: List<String> = DEFAULT_COMMAND,
) {
    fun buildCommandLine(): List<String> {
        val args = mutableListOf<String>()
        args.add(prootBinary.absolutePath)

        // Rootfs mount with fake root
        args.add("-r")
        args.add(rootfsDir.absolutePath)
        args.add("-0")

        // Workspace bind mount
        args.add("-b")
        args.add("${workspaceDir.absolutePath}:/workspace")

        // State bind mount to daemon home config
        args.add("-b")
        args.add("${stateDir.absolutePath}:/state")

        // Scratch temp bind mount
        args.add("-b")
        args.add("${scratchDir.absolutePath}:/tmp")

        // Android external storage (SAF) if granted
        sharedStorageDir?.let { shared ->
            args.add("-b")
            args.add("${shared.absolutePath}:/storage/shared")
        }

        // Standard pseudofilesystem bindings
        args.add("-b")
        args.add("/dev")
        args.add("-b")
        args.add("/proc")

        // Default working directory
        args.add("-w")
        args.add("/workspace")

        // Command to execute inside PRoot
        args.addAll(customCommand)

        return args
    }

    fun buildEnvironment(): Map<String, String> {
        val env =
            mutableMapOf(
                "HOME" to "/home/hermes",
                "TMPDIR" to "/tmp",
                "PROOT_TMP_DIR" to scratchDir.absolutePath,
                "PATH" to DEFAULT_PATH,
                "TERM" to "xterm-256color",
                "LANG" to "en_US.UTF-8",
            )
        ipcToken?.let { token ->
            env["HERMES_IPC_TOKEN"] = token
        }
        return env
    }

    companion object {
        val DEFAULT_COMMAND = listOf("/opt/hermes-venv/bin/hermes", "serve", "--host", "127.0.0.1", "--port", "8787")
        const val DEFAULT_PATH = "/usr/local/bin:/usr/bin:/bin:/usr/local/sbin:/usr/sbin:/sbin"

        fun fromTopology(
            topology: StorageTopology,
            ipcToken: String? = null,
            sharedStorageDir: File? = null,
            command: List<String> = DEFAULT_COMMAND,
            nativeLibDir: File? = null,
        ): PRootConfig {
            val prootInBin = File(topology.binDir, "proot")
            val prootInRootfs = File(topology.rootfsDir, "bin/proot")
            val prootInUsrBin = File(topology.rootfsDir, "usr/bin/proot")
            val prootInNativeLib = nativeLibDir?.let { File(it, "libproot.so") }
            val prootBinary =
                when {
                    prootInBin.exists() -> prootInBin
                    prootInNativeLib?.exists() == true -> prootInNativeLib
                    prootInRootfs.exists() -> prootInRootfs
                    prootInUsrBin.exists() -> prootInUsrBin
                    else -> prootInBin
                }
            return PRootConfig(
                prootBinary = prootBinary,
                rootfsDir = topology.rootfsDir,
                workspaceDir = topology.workspaceDir,
                stateDir = topology.stateDir,
                scratchDir = topology.scratchDir,
                sharedStorageDir = sharedStorageDir,
                ipcToken = ipcToken,
                customCommand = command,
            )
        }
    }
}
