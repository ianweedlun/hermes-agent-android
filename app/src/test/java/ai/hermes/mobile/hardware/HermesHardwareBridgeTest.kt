package ai.hermes.mobile.hardware

import ai.hermes.mobile.hardware.model.BatteryStatus
import ai.hermes.mobile.hardware.model.NetworkStatus
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HermesHardwareBridgeTest {
    private class FakeBatteryProvider(
        var status: BatteryStatus = BatteryStatus.unknown(),
    ) : BatteryProvider {
        override fun getBatteryStatus(): BatteryStatus = status
    }

    private class FakeClipboardProvider : ClipboardProvider {
        var text: String = ""
        var sensitive: Boolean = false

        override fun getClipboardText(): String = text

        override fun setClipboardText(
            text: String,
            isSensitive: Boolean,
        ) {
            this.text = text
            this.sensitive = isSensitive
        }
    }

    private class FakeHapticsProvider : HapticsProvider {
        val invokedEffects = mutableListOf<String>()

        override fun vibrate(effect: String) {
            invokedEffects.add(effect)
        }
    }

    private class FakeNetworkProvider(
        var status: NetworkStatus = NetworkStatus.offline(),
    ) : NetworkProvider {
        override fun getNetworkStatus(): NetworkStatus = status
    }

    @Test
    fun getBatteryStatusReturnsValidJson() {
        val fakeBattery =
            FakeBatteryProvider(
                status =
                    BatteryStatus(
                        percentage = 92,
                        isCharging = false,
                        plugType = BatteryStatus.PLUG_NONE,
                        temperatureC = 24.0f,
                        health = BatteryStatus.HEALTH_GOOD,
                    ),
            )
        val bridge =
            HermesHardwareBridge(
                batteryProvider = fakeBattery,
                clipboardProvider = FakeClipboardProvider(),
                hapticsProvider = FakeHapticsProvider(),
                networkProvider = FakeNetworkProvider(),
            )

        val resultJson = JSONObject(bridge.getBatteryStatus())
        assertEquals(92, resultJson.getInt("percentage"))
        assertEquals("none", resultJson.getString("plugType"))
    }

    @Test
    fun clipboardOperationsDelegateCorrectly() {
        val fakeClipboard = FakeClipboardProvider()
        val bridge =
            HermesHardwareBridge(
                batteryProvider = FakeBatteryProvider(),
                clipboardProvider = fakeClipboard,
                hapticsProvider = FakeHapticsProvider(),
                networkProvider = FakeNetworkProvider(),
            )

        bridge.setClipboardText("Hermes API Token", isSensitive = true)
        assertEquals("Hermes API Token", bridge.getClipboardText())
        assertTrue(fakeClipboard.sensitive)
    }

    @Test
    fun hapticsDelegationRecordsEffect() {
        val fakeHaptics = FakeHapticsProvider()
        val bridge =
            HermesHardwareBridge(
                batteryProvider = FakeBatteryProvider(),
                clipboardProvider = FakeClipboardProvider(),
                hapticsProvider = fakeHaptics,
                networkProvider = FakeNetworkProvider(),
            )

        bridge.vibrate("heavy_click")
        assertEquals(listOf("heavy_click"), fakeHaptics.invokedEffects)
    }

    @Test
    fun getNetworkStatusReturnsValidJson() {
        val fakeNetwork =
            FakeNetworkProvider(
                status =
                    NetworkStatus(
                        isConnected = true,
                        type = NetworkStatus.TYPE_CELLULAR,
                        isMetered = true,
                        isOffline = false,
                    ),
            )
        val bridge =
            HermesHardwareBridge(
                batteryProvider = FakeBatteryProvider(),
                clipboardProvider = FakeClipboardProvider(),
                hapticsProvider = FakeHapticsProvider(),
                networkProvider = fakeNetwork,
            )

        val resultJson = JSONObject(bridge.getNetworkStatus())
        assertTrue(resultJson.getBoolean("isConnected"))
        assertEquals("cellular", resultJson.getString("type"))
        assertTrue(resultJson.getBoolean("isMetered"))
    }
}
