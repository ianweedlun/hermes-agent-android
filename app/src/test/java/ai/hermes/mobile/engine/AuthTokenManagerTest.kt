package ai.hermes.mobile.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AuthTokenManagerTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `generates 64-char hex token and persists to state directory`() {
        val stateDir = tempFolder.newFolder("state")
        val token = AuthTokenManager.resolveOrCreateToken(stateDir)

        assertNotNull(token)
        assertEquals(64, token.length)
        assertTrue(token.matches(Regex("^[0-9a-f]{64}$")))

        val tokenFile = File(stateDir, AuthTokenManager.TOKEN_FILE_NAME)
        assertTrue(tokenFile.exists())
        assertEquals(token, tokenFile.readText().trim())
    }

    @Test
    fun `reuses existing token when already present`() {
        val stateDir = tempFolder.newFolder("state")
        val existing = "deadbeef1234567890abcdef1234567890abcdef1234567890abcdef1234567890"
        File(stateDir, AuthTokenManager.TOKEN_FILE_NAME).writeText(existing)

        val token = AuthTokenManager.resolveOrCreateToken(stateDir)
        assertEquals(existing, token)
    }

    @Test
    fun `constructs loopback WebUI URL with token parameter`() {
        val stateDir = tempFolder.newFolder("state")
        val url = AuthTokenManager.buildWebUiUrl(stateDir, port = 8787)

        assertTrue(url.startsWith("http://127.0.0.1:8787/?token="))
        val tokenPart = url.substringAfter("?token=")
        assertEquals(64, tokenPart.length)
    }
}
