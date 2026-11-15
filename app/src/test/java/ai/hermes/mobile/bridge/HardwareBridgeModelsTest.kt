package ai.hermes.mobile.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareBridgeModelsTest {
    @Test
    fun batteryStatusRoundTripsAllSchemaFieldsAndEnumValues() {
        val status =
            BatteryStatus(
                percentage = 84,
                isCharging = true,
                plugType = BatteryPlugType.USB,
                temperatureC = 31.5f,
                health = BatteryHealth.GOOD,
            )

        val json = status.toJson()
        assertTrue(json.contains("\"percentage\":84"))
        assertTrue(json.contains("\"isCharging\":true"))
        assertTrue(json.contains("\"plugType\":\"usb\""))
        assertTrue(json.contains("\"temperatureC\":31.5"))
        assertTrue(json.contains("\"health\":\"good\""))

        val decoded = BatteryStatus.fromJson(json)
        assertEquals(status, decoded)
    }

    @Test
    fun batteryPlugTypeAndHealthWireMappingsResolveSafely() {
        assertEquals(BatteryPlugType.AC, BatteryPlugType.fromWireName("ac"))
        assertEquals(BatteryPlugType.USB, BatteryPlugType.fromWireName(" USB "))
        assertEquals(BatteryPlugType.WIRELESS, BatteryPlugType.fromWireName("wireless"))
        assertEquals(BatteryPlugType.UNKNOWN, BatteryPlugType.fromWireName("other"))
        assertEquals(BatteryPlugType.UNKNOWN, BatteryPlugType.fromWireName(null))

        assertEquals(BatteryHealth.GOOD, BatteryHealth.fromWireName("good"))
        assertEquals(BatteryHealth.OVERHEAT, BatteryHealth.fromWireName("OVERHEAT"))
        assertEquals(BatteryHealth.COLD, BatteryHealth.fromWireName("cold"))
        assertEquals(BatteryHealth.DEAD, BatteryHealth.fromWireName("dead"))
        assertEquals(BatteryHealth.UNKNOWN, BatteryHealth.fromWireName(null))
    }

    @Test
    fun batteryStatusSanitizesNonFiniteTemperatureAndOutOfRangePercentage() {
        val nonFinite =
            BatteryStatus(
                percentage = 45,
                isCharging = false,
                plugType = BatteryPlugType.UNKNOWN,
                temperatureC = Float.NaN,
                health = BatteryHealth.GOOD,
            )
        val decoded = BatteryStatus.fromJson(nonFinite.toJson())
        assertEquals(0.0f, decoded.temperatureC, 0.001f)

        val raw =
            "{\"percentage\":150,\"isCharging\":false,\"plugType\":\"ac\"," +
                "\"temperatureC\":22.5,\"health\":\"cold\"}"
        val clamped = BatteryStatus.fromJson(raw)
        assertEquals(100, clamped.percentage)
        assertEquals(BatteryPlugType.AC, clamped.plugType)
        assertEquals(BatteryHealth.COLD, clamped.health)
    }

    @Test
    fun networkStatusRoundTripsConnectedAndOfflineSchemas() {
        val wifi =
            NetworkStatus(
                isConnected = true,
                type = NetworkType.WIFI,
                isMetered = false,
                isOffline = false,
            )
        val decodedWifi = NetworkStatus.fromJson(wifi.toJson())
        assertEquals(wifi, decodedWifi)

        val cellular =
            NetworkStatus(
                isConnected = true,
                type = NetworkType.CELLULAR,
                isMetered = true,
                isOffline = false,
            )
        assertEquals(cellular, NetworkStatus.fromJson(cellular.toJson()))

        val ethernet =
            NetworkStatus(
                isConnected = true,
                type = NetworkType.ETHERNET,
                isMetered = false,
                isOffline = false,
            )
        assertEquals(ethernet, NetworkStatus.fromJson(ethernet.toJson()))

        val offlineJson = NetworkStatus.OFFLINE.toJson()
        assertTrue(offlineJson.contains("\"isConnected\":false"))
        assertTrue(offlineJson.contains("\"type\":\"none\""))
        assertTrue(offlineJson.contains("\"isOffline\":true"))
        assertEquals(NetworkStatus.OFFLINE, NetworkStatus.fromJson(offlineJson))
    }

    @Test
    fun clipboardPayloadEscapesJsonAndRedactsSensitiveToString() {
        val secret = "token=\"abc\n123\\xyz\"\t\u0007"
        val sensitivePayload =
            ClipboardPayload(
                text = secret,
                isSensitive = true,
                timestamp = 1731634600123L,
            )
        assertFalse(sensitivePayload.toString().contains("abc"))
        assertTrue(sensitivePayload.toString().contains("REDACTED"))

        val decoded = ClipboardPayload.fromJson(sensitivePayload.toJson())
        assertEquals(sensitivePayload, decoded)

        val publicPayload = ClipboardPayload(text = "hello", isSensitive = false, timestamp = 42L)
        assertTrue(publicPayload.toString().contains("hello"))
        assertEquals(publicPayload, ClipboardPayload.fromJson(publicPayload.toJson()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun batteryStatusFromJsonRejectsMalformedPayload() {
        BatteryStatus.fromJson("{\"percentage\":50}")
    }
}
