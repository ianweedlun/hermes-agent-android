package ai.hermes.mobile.bridge

import android.os.BatteryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HermesHardwareBridgeTest {
    @Test
    fun hermesHardwareBridgeExposesValidJsonForAllInterfaceMethods() {
        val batterySource =
            FakeBatteryDataSource(
                BatteryRawReading(
                    level = 91,
                    scale = 100,
                    status = BatteryManager.BATTERY_STATUS_CHARGING,
                    plugged = BatteryManager.BATTERY_PLUGGED_USB,
                    temperatureTenthsC = 294,
                    health = BatteryManager.BATTERY_HEALTH_GOOD,
                ),
            )
        val networkSource =
            FakeNetworkDataSource(
                NetworkSnapshot(
                    hasInternet = true,
                    isValidated = true,
                    isWifi = true,
                    isCellular = false,
                    isEthernet = false,
                    isMetered = false,
                ),
            )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val gateway = FakeClipboardGateway()
        val hapticPlayer = FakeHapticPlayer(supported = true)
        val jsBridge =
            HermesHardwareBridge(
                batteryBridge = BatteryBridge(batterySource),
                networkBridge = NetworkBridge(networkSource),
                clipboardBridge = ClipboardBridge(gateway = gateway, scope = scope, clock = { 555L }),
                hapticsBridge = HapticsBridge(hapticPlayer),
            )

        assertEquals("HermesHardware", HermesHardwareBridge.JS_INTERFACE_NAME)
        val battery = BatteryStatus.fromJson(jsBridge.getBatteryStatus())
        assertEquals(91, battery.percentage)
        assertEquals(BatteryPlugType.USB, battery.plugType)

        val network = NetworkStatus.fromJson(jsBridge.getNetworkStatus())
        assertTrue(network.isConnected)
        assertEquals(NetworkType.WIFI, network.type)

        val setOk = JsonCodec.parseObject(jsBridge.setClipboardText("hello", isSensitive = false))
        assertEquals(true, setOk["ok"])
        val clip = ClipboardPayload.fromJson(jsBridge.getClipboardText())
        assertEquals("hello", clip.text)
        assertFalse(clip.isSensitive)

        val setNull = JsonCodec.parseObject(jsBridge.setClipboardText(null, isSensitive = true))
        assertEquals(false, setNull["ok"])

        val vibrateOk = JsonCodec.parseObject(jsBridge.vibrate("click"))
        assertEquals(true, vibrateOk["ok"])
        assertEquals(true, vibrateOk["executed"])

        val vibrateBad = JsonCodec.parseObject(jsBridge.vibrate("unknown"))
        assertEquals(false, vibrateBad["ok"])
        scope.cancel()
    }

    @Test
    fun hermesHardwareBridgeLifecycleRegistersAndUnregistersDataSources() {
        val batterySource =
            FakeBatteryDataSource(
                BatteryRawReading(
                    level = 50,
                    scale = 100,
                    status = BatteryManager.BATTERY_STATUS_DISCHARGING,
                    plugged = 0,
                    temperatureTenthsC = 250,
                    health = BatteryManager.BATTERY_HEALTH_GOOD,
                ),
            )
        val networkSource = FakeNetworkDataSource(NetworkSnapshot.OFFLINE)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val jsBridge =
            HermesHardwareBridge(
                batteryBridge = BatteryBridge(batterySource),
                networkBridge = NetworkBridge(networkSource),
                clipboardBridge = ClipboardBridge(gateway = FakeClipboardGateway(), scope = scope),
                hapticsBridge = HapticsBridge(FakeHapticPlayer(supported = false)),
            )

        jsBridge.start()
        assertTrue(batterySource.isRegistered)
        assertTrue(networkSource.isRegistered)

        jsBridge.stop()
        assertFalse(batterySource.isRegistered)
        assertFalse(networkSource.isRegistered)
        scope.cancel()
    }
}
