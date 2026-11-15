package ai.hermes.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IpcTokenGeneratorTest {
    @Test
    fun generatedTokenHasHighEntropyAndUrlSafeCharacters() {
        val token = IpcTokenGenerator.generate()
        val raw = token.rawToken()

        assertTrue(raw.length >= 43)
        assertTrue(raw.matches(Regex("^[A-Za-z0-9_-]+$")))
        assertEquals("Bearer $raw", token.asHeaderValue())
        assertTrue(token.matchesConstantTime(raw))
        assertFalse(token.matchesConstantTime("wrong-token"))
        assertFalse(token.matchesConstantTime(null))
    }

    @Test
    fun consecutiveTokensAreUnique() {
        val first = IpcTokenGenerator.generate()
        val second = IpcTokenGenerator.generate()

        assertNotEquals(first, second)
        assertNotEquals(first.rawToken(), second.rawToken())
    }

    @Test
    fun toStringRedactsRawTokenSecret() {
        val token = IpcTokenGenerator.generate()
        val description = token.toString()

        assertFalse(description.contains(token.rawToken()))
        assertTrue(description.contains("REDACTED"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInsufficientTokenEntropyBelowMinimumByteLength() {
        IpcTokenGenerator.generate(byteLength = 8)
    }
}
