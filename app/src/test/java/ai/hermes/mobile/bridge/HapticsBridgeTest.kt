package ai.hermes.mobile.bridge

import android.os.VibrationEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticsBridgeTest {
    @Test
    fun hapticsBridgeMapsPredefinedEffectsAndErrorWaveform() {
        val player = FakeHapticPlayer(supported = true)
        val bridge = HapticsBridge(player)

        assertTrue(bridge.isSupported())
        assertTrue(bridge.vibrate("click"))
        assertTrue(bridge.vibrate("EFFECT_TICK"))
        assertTrue(bridge.vibrate("heavy_click"))
        assertTrue(bridge.vibrate(HapticEffect.ERROR))
        assertFalse(bridge.vibrate("invalid_effect"))

        assertEquals(
            listOf(
                HapticPattern.Predefined(VibrationEffect.EFFECT_CLICK),
                HapticPattern.Predefined(VibrationEffect.EFFECT_TICK),
                HapticPattern.Predefined(VibrationEffect.EFFECT_HEAVY_CLICK),
                HapticPattern.Waveform(
                    timingsMs = HapticsBridge.ERROR_TIMINGS_MS,
                    amplitudes = HapticsBridge.ERROR_AMPLITUDES,
                    repeatIndex = -1,
                ),
            ),
            player.played,
        )
    }

    @Test
    fun hapticsBridgeNoOpsGracefullyWithoutVibrator() {
        val player = FakeHapticPlayer(supported = false)
        val bridge = HapticsBridge(player)

        assertFalse(bridge.isSupported())
        assertFalse(bridge.vibrate(HapticEffect.CLICK))
        assertTrue(player.played.isEmpty())
    }
}
