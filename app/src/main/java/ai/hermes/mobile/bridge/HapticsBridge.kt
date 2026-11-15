package ai.hermes.mobile.bridge

import android.content.Context
import android.os.VibrationEffect

/**
 * Supported haptic feedback effects exposed by [HapticsBridge].
 */
enum class HapticEffect(
    val wireName: String,
) {
    CLICK("click"),
    TICK("tick"),
    HEAVY_CLICK("heavy_click"),
    ERROR("error"),
    ;

    companion object {
        fun fromWireName(value: String?): HapticEffect? {
            val normalized = value?.trim()?.lowercase()
            return when (normalized) {
                "click", "effect_click", "confirm" -> CLICK
                "tick", "effect_tick", "gesture_start" -> TICK
                "heavy_click", "heavy", "effect_heavy_click" -> HEAVY_CLICK
                "error", "reject" -> ERROR
                else -> null
            }
        }
    }
}

/**
 * Drives platform haptic feedback via `VibratorManager` (API 31+) with legacy `Vibrator` fallback.
 */
class HapticsBridge internal constructor(
    private val player: HapticPlayer,
) {
    constructor(context: Context) : this(AndroidHapticPlayer(context))

    fun isSupported(): Boolean = player.hasVibrator()

    fun vibrate(effect: HapticEffect): Boolean =
        if (!player.hasVibrator()) {
            false
        } else {
            player.play(mapEffect(effect))
        }

    fun vibrate(effectType: String?): Boolean {
        val effect = HapticEffect.fromWireName(effectType) ?: return false
        return vibrate(effect)
    }

    companion object {
        internal val ERROR_TIMINGS_MS: LongArray = longArrayOf(0L, 45L, 55L, 45L, 55L, 80L)
        internal val ERROR_AMPLITUDES: IntArray = intArrayOf(0, 180, 0, 180, 0, 255)

        internal fun mapEffect(effect: HapticEffect): HapticPattern =
            when (effect) {
                HapticEffect.CLICK -> HapticPattern.Predefined(VibrationEffect.EFFECT_CLICK)
                HapticEffect.TICK -> HapticPattern.Predefined(VibrationEffect.EFFECT_TICK)
                HapticEffect.HEAVY_CLICK -> HapticPattern.Predefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                HapticEffect.ERROR ->
                    HapticPattern.Waveform(
                        timingsMs = ERROR_TIMINGS_MS,
                        amplitudes = ERROR_AMPLITUDES,
                        repeatIndex = -1,
                    )
            }
    }
}
