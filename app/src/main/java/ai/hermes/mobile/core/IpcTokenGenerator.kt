package ai.hermes.mobile.core

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * High-entropy bearer token injected into the PRoot daemon environment (`HERMES_IPC_TOKEN`)
 * per `IPC-CONTRACT.md`. Redacts its secret material in [toString] to prevent log leakage.
 */
class BearerToken private constructor(
    private val value: String,
) {
    fun asHeaderValue(): String = "Bearer $value"

    fun rawToken(): String = value

    /**
     * Performs a constant-time comparison against a presented raw bearer token.
     */
    fun matchesConstantTime(candidate: String?): Boolean {
        if (candidate.isNullOrEmpty()) {
            return false
        }
        return MessageDigest.isEqual(
            value.toByteArray(StandardCharsets.UTF_8),
            candidate.toByteArray(StandardCharsets.UTF_8),
        )
    }

    override fun toString(): String = "BearerToken(***REDACTED***)"

    override fun equals(other: Any?): Boolean = this === other || (other is BearerToken && value == other.value)

    override fun hashCode(): Int = value.hashCode()

    companion object {
        internal fun fromEncoded(encoded: String): BearerToken {
            require(encoded.isNotBlank()) { "Bearer token must not be blank" }
            return BearerToken(encoded)
        }
    }
}

/**
 * Generates cryptographically strong bearer tokens for local loopback HTTP IPC authentication.
 */
object IpcTokenGenerator {
    const val ENV_VAR_NAME: String = "HERMES_IPC_TOKEN"
    const val MIN_BYTE_LENGTH: Int = 16
    const val DEFAULT_BYTE_LENGTH: Int = 32

    /**
     * Generates a URL-safe, unpadded Base64 bearer token backed by [SecureRandom].
     */
    fun generate(
        byteLength: Int = DEFAULT_BYTE_LENGTH,
        random: SecureRandom = SecureRandom(),
    ): BearerToken {
        require(byteLength >= MIN_BYTE_LENGTH) {
            "IPC bearer token must be at least $MIN_BYTE_LENGTH bytes (128 bits)."
        }
        val rawBytes = ByteArray(byteLength)
        random.nextBytes(rawBytes)
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(rawBytes)
        return BearerToken.fromEncoded(encoded)
    }
}
