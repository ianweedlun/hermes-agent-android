package ai.hermes.mobile.hardware.model

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryStatusTest {
    @Test
    fun serializationMatchesContract() {
        val status =
            BatteryStatus(
                percentage = 85,
                isCharging = true,
                plugType = BatteryStatus.PLUG_AC,
                temperatureC = 28.5f,
                health = BatteryStatus.HEALTH_GOOD,
            )

        val json = JSONObject(status.toJsonString())

        assertEquals(85, json.getInt("percentage"))
        assertTrue(json.getBoolean("isCharging"))
        assertEquals("ac", json.getString("plugType"))
        assertEquals(28.5, json.getDouble("temperatureC"), 0.01)
        assertEquals("good", json.getString("health"))
    }

    @Test
    fun unknownStatusHasSensibleDefaults() {
        val status = BatteryStatus.unknown()
        val json = JSONObject(status.toJsonString())

        assertEquals(-1, json.getInt("percentage"))
        assertFalse(json.getBoolean("isCharging"))
        assertEquals(BatteryStatus.PLUG_NONE, json.getString("plugType"))
        assertEquals(0.0, json.getDouble("temperatureC"), 0.01)
        assertEquals(BatteryStatus.HEALTH_UNKNOWN, json.getString("health"))
    }
}
