package ai.hermes.mobile.hardware

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Abstraction for haptic feedback per HARDWARE-BRIDGE-CONTRACT.md.
 */
interface HapticsProvider {
    fun vibrate(effect: String)
}

/**
 * Android platform implementation using VibratorManager (API 31+) and Vibrator.
 */
class AndroidHapticsProvider(
    private val context: Context,
) : HapticsProvider {
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    override fun vibrate(effect: String) {
        val targetVibrator = vibrator ?: return
        if (!targetVibrator.hasVibrator()) {
            return
        }

        val vibrationEffect = resolveEffect(effect)
        try {
            targetVibrator.vibrate(vibrationEffect)
        } catch (e: SecurityException) {
            Log.w(TAG, "Vibrate permission not granted or restricted", e)
        }
    }

    private fun resolveEffect(effect: String): VibrationEffect {
        val effectId =
            when (effect.lowercase()) {
                EFFECT_CLICK -> VibrationEffect.EFFECT_CLICK
                EFFECT_TICK -> VibrationEffect.EFFECT_TICK
                EFFECT_HEAVY_CLICK -> VibrationEffect.EFFECT_HEAVY_CLICK
                else -> null
            }

        return if (effectId != null) {
            VibrationEffect.createPredefined(effectId)
        } else {
            VibrationEffect.createOneShot(DEFAULT_FALLBACK_DURATION_MS, VibrationEffect.DEFAULT_AMPLITUDE)
        }
    }

    companion object {
        private const val TAG = "AndroidHapticsProvider"
        const val EFFECT_CLICK = "click"
        const val EFFECT_TICK = "tick"
        const val EFFECT_HEAVY_CLICK = "heavy_click"
        private const val DEFAULT_FALLBACK_DURATION_MS = 50L
    }
}
