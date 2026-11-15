package ai.hermes.mobile.bridge

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

internal sealed interface HapticPattern {
    data class Predefined(
        val effectId: Int,
    ) : HapticPattern

    data class Waveform(
        val timingsMs: LongArray,
        val amplitudes: IntArray,
        val repeatIndex: Int = -1,
    ) : HapticPattern {
        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other !is Waveform) {
                return false
            }
            return timingsMs.contentEquals(other.timingsMs) &&
                amplitudes.contentEquals(other.amplitudes) &&
                repeatIndex == other.repeatIndex
        }

        override fun hashCode(): Int {
            var result = timingsMs.contentHashCode()
            result = 31 * result + amplitudes.contentHashCode()
            result = 31 * result + repeatIndex
            return result
        }
    }
}

internal interface HapticPlayer {
    fun hasVibrator(): Boolean

    fun play(pattern: HapticPattern): Boolean
}

internal class AndroidHapticPlayer(
    context: Context,
) : HapticPlayer {
    private val vibrator: Vibrator? by lazy { resolveVibrator(context.applicationContext) }

    override fun hasVibrator(): Boolean = vibrator?.hasVibrator() == true

    override fun play(pattern: HapticPattern): Boolean {
        val target = vibrator
        if (target == null || !target.hasVibrator()) {
            return false
        }
        val effect =
            when (pattern) {
                is HapticPattern.Predefined -> VibrationEffect.createPredefined(pattern.effectId)
                is HapticPattern.Waveform ->
                    VibrationEffect.createWaveform(pattern.timingsMs, pattern.amplitudes, pattern.repeatIndex)
            }
        return try {
            target.vibrate(effect)
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Missing VIBRATE permission when triggering haptic feedback", e)
            false
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Invalid haptic vibration effect", e)
            false
        }
    }

    private fun resolveVibrator(appContext: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Vibrator::class.java)
        }

    companion object {
        private const val TAG = "AndroidHapticPlayer"
    }
}
