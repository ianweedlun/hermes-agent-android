package ai.hermes.mobile.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HermesWebViewClientTest {
    @Test
    fun internalLoopbackAndAssetUrlsIdentifiedCorrectly() {
        val client =
            HermesWebViewClient(
                context = android.app.Application(),
                externalUrlLauncher = { true },
            )

        assertTrue(client.isInternalUrl("http://127.0.0.1:8080/"))
        assertTrue(client.isInternalUrl("http://localhost:3000/chat"))
        assertTrue(client.isInternalUrl("file:///android_asset/index.html"))
        assertTrue(client.isInternalUrl("data:text/html,hello"))
        assertTrue(client.isInternalUrl("about:blank"))

        // External URLs must NOT be classified as internal
        assertFalse(client.isInternalUrl("https://github.com/ianweedlun/hermes-agent-android"))
        assertFalse(client.isInternalUrl("https://example.com/api"))
        assertFalse(client.isInternalUrl("http://192.168.1.100/test"))
    }
}
