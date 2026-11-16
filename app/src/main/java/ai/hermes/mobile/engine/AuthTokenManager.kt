package ai.hermes.mobile.engine

import java.io.File
import java.security.SecureRandom

/**
 * Manages the high-entropy bearer token stored in /state/hermes-auth.token
 * adhering strictly to IPC-CONTRACT.md.
 */
object AuthTokenManager {
    const val TOKEN_FILE_NAME = "hermes-auth.token"
    const val DEFAULT_PORT = 8787
    private const val TOKEN_BYTE_LENGTH = 32

    /**
     * Retrieves existing bearer token from stateDir or generates and persists a new one.
     * Restricts file readability to the owning application UID.
     */
    fun resolveOrCreateToken(stateDir: File): String {
        stateDir.mkdirs()
        val tokenFile = File(stateDir, TOKEN_FILE_NAME)

        if (tokenFile.exists() && tokenFile.length() > 0) {
            val existingToken = tokenFile.readText().trim()
            if (existingToken.isNotEmpty()) {
                return existingToken
            }
        }

        val randomBytes = ByteArray(TOKEN_BYTE_LENGTH)
        SecureRandom().nextBytes(randomBytes)
        val newToken = randomBytes.joinToString("") { "%02x".format(it) }

        tokenFile.writeText(newToken)
        tokenFile.setReadable(true, true)
        tokenFile.setWritable(true, true)
        return newToken
    }

    /**
     * Constructs the local authenticated WebUI URL for the Android WebView.
     */
    fun buildWebUiUrl(
        stateDir: File,
        port: Int = DEFAULT_PORT,
    ): String {
        val token = resolveOrCreateToken(stateDir)
        return "http://127.0.0.1:$port/?token=$token"
    }
}
