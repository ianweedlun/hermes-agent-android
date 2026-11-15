package ai.hermes.mobile.hardware.model

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkStatusTest {
    @Test
    fun serializationMatchesContract() {
        val status =
            NetworkStatus(
                isConnected = true,
                type = NetworkStatus.TYPE_WIFI,
                isMetered = false,
                isOffline = false,
            )

        val json = JSONObject(status.toJsonString())

        assertTrue(json.getBoolean("isConnected"))
        assertEquals("wifi", json.getString("type"))
        assertFalse(json.getBoolean("isMetered"))
        assertFalse(json.getBoolean("isOffline"))
    }

    @Test
    fun offlineStatusDefaults() {
        val status = NetworkStatus.offline()
        val json = JSONObject(status.toJsonString())

        assertFalse(json.getBoolean("isConnected"))
        assertEquals(NetworkStatus.TYPE_NONE, json.getString("type"))
        assertFalse(json.getBoolean("isMetered"))
        assertTrue(json.getBoolean("isOffline"))
    }
}
