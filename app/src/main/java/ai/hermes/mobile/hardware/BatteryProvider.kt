package ai.hermes.mobile.hardware

import ai.hermes.mobile.hardware.model.BatteryStatus
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/**
 * Abstraction for battery status queries per HARDWARE-BRIDGE-CONTRACT.md.
 */
interface BatteryProvider {
    fun getBatteryStatus(): BatteryStatus
}

/**
 * Android platform implementation backed by BatteryManager and sticky ACTION_BATTERY_CHANGED.
 */
class AndroidBatteryProvider(
    private val context: Context,
) : BatteryProvider {
    override fun getBatteryStatus(): BatteryStatus {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryIntent = context.registerReceiver(null, filter) ?: return BatteryStatus.unknown()

        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percentage =
            if (level >= 0 && scale > 0) {
                (level * PERCENT_MULTIPLIER) / scale
            } else {
                -1
            }

        val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging =
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val plugType =
            when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> BatteryStatus.PLUG_AC
                BatteryManager.BATTERY_PLUGGED_USB -> BatteryStatus.PLUG_USB
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> BatteryStatus.PLUG_WIRELESS
                else -> BatteryStatus.PLUG_NONE
            }

        val rawTemperature = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        val temperatureC = rawTemperature / TEMPERATURE_SCALE_DIVISOR

        val healthCode =
            batteryIntent.getIntExtra(
                BatteryManager.EXTRA_HEALTH,
                BatteryManager.BATTERY_HEALTH_UNKNOWN,
            )
        val health =
            when (healthCode) {
                BatteryManager.BATTERY_HEALTH_GOOD -> BatteryStatus.HEALTH_GOOD
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> BatteryStatus.HEALTH_OVERHEAT
                else -> BatteryStatus.HEALTH_UNKNOWN
            }

        return BatteryStatus(
            percentage = percentage,
            isCharging = isCharging,
            plugType = plugType,
            temperatureC = temperatureC,
            health = health,
        )
    }

    companion object {
        private const val PERCENT_MULTIPLIER = 100
        private const val TEMPERATURE_SCALE_DIVISOR = 10.0f
    }
}
