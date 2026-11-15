package ai.hermes.mobile.bridge

import android.os.BatteryManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryBridgeTest {
    @Test
    fun batteryBridgeMapsIntentReadingsAndStreamsLiveUpdates() =
        runBlocking {
            val dataSource =
                FakeBatteryDataSource(
                    BatteryRawReading(
                        level = 42,
                        scale = 100,
                        status = BatteryManager.BATTERY_STATUS_CHARGING,
                        plugged = BatteryManager.BATTERY_PLUGGED_AC,
                        temperatureTenthsC = 345,
                        health = BatteryManager.BATTERY_HEALTH_GOOD,
                    ),
                )
            val bridge = BatteryBridge(dataSource)
            val initial = bridge.getBatteryStatus()
            assertEquals(42, initial.percentage)
            assertTrue(initial.isCharging)
            assertEquals(BatteryPlugType.AC, initial.plugType)
            assertEquals(34.5f, initial.temperatureC, 0.01f)
            assertEquals(BatteryHealth.GOOD, initial.health)

            bridge.start()
            assertTrue(dataSource.isRegistered)

            dataSource.emit(
                BatteryRawReading(
                    level = 18,
                    scale = 100,
                    status = BatteryManager.BATTERY_STATUS_DISCHARGING,
                    plugged = 0,
                    temperatureTenthsC = 430,
                    health = BatteryManager.BATTERY_HEALTH_OVERHEAT,
                ),
            )
            val updated = bridge.observeBatteryStatus().first()
            assertEquals(18, updated.percentage)
            assertFalse(updated.isCharging)
            assertEquals(BatteryPlugType.UNKNOWN, updated.plugType)
            assertEquals(43.0f, updated.temperatureC, 0.01f)
            assertEquals(BatteryHealth.OVERHEAT, updated.health)

            bridge.stop()
            assertFalse(dataSource.isRegistered)
        }

    @Test
    fun batteryBridgeHandlesWirelessUsbFallbackCapacityAndHealthStates() {
        val wirelessCold =
            BatteryBridge.mapReading(
                BatteryRawReading(
                    level = -1,
                    scale = 0,
                    status = BatteryManager.BATTERY_STATUS_UNKNOWN,
                    plugged = BatteryManager.BATTERY_PLUGGED_WIRELESS,
                    temperatureTenthsC = -25,
                    health = BatteryManager.BATTERY_HEALTH_COLD,
                    fallbackCharging = true,
                    fallbackCapacity = 76,
                ),
            )
        assertEquals(76, wirelessCold.percentage)
        assertTrue(wirelessCold.isCharging)
        assertEquals(BatteryPlugType.WIRELESS, wirelessCold.plugType)
        assertEquals(-2.5f, wirelessCold.temperatureC, 0.01f)
        assertEquals(BatteryHealth.COLD, wirelessCold.health)

        val usbDead =
            BatteryBridge.mapReading(
                BatteryRawReading(
                    level = 1,
                    scale = 100,
                    status = BatteryManager.BATTERY_STATUS_FULL,
                    plugged = BatteryManager.BATTERY_PLUGGED_USB,
                    temperatureTenthsC = AndroidBatteryDataSource.MISSING_TEMPERATURE,
                    health = BatteryManager.BATTERY_HEALTH_DEAD,
                ),
            )
        assertTrue(usbDead.isCharging)
        assertEquals(BatteryPlugType.USB, usbDead.plugType)
        assertEquals(0.0f, usbDead.temperatureC, 0.01f)
        assertEquals(BatteryHealth.DEAD, usbDead.health)
    }
}
