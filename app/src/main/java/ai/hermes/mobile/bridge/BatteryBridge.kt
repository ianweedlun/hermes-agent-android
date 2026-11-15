package ai.hermes.mobile.bridge

import android.content.Context
import android.os.BatteryManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Provides battery telemetry backed by Android `BatteryManager` and `Intent.ACTION_BATTERY_CHANGED`.
 */
class BatteryBridge internal constructor(
    private val dataSource: BatteryDataSource,
) {
    constructor(context: Context) : this(AndroidBatteryDataSource(context))

    private val lock = Any()
    private val _status: MutableStateFlow<BatteryStatus> =
        MutableStateFlow(mapReading(dataSource.readCurrent()))
    val status: StateFlow<BatteryStatus> = _status.asStateFlow()

    fun observeBatteryStatus(): Flow<BatteryStatus> = status

    fun getBatteryStatus(): BatteryStatus {
        val snapshot = mapReading(dataSource.readCurrent())
        _status.value = snapshot
        return snapshot
    }

    fun start() {
        synchronized(lock) {
            dataSource.registerUpdates { reading ->
                _status.value = mapReading(reading)
            }
            _status.value = mapReading(dataSource.readCurrent())
        }
    }

    fun stop() {
        synchronized(lock) {
            dataSource.unregisterUpdates()
        }
    }

    companion object {
        private const val TENTHS_PER_DEGREE = 10.0f
        private const val MAX_PERCENTAGE = 100

        internal fun mapReading(reading: BatteryRawReading): BatteryStatus {
            val percentage = computePercentage(reading.level, reading.scale, reading.fallbackCapacity)
            val isCharging =
                reading.status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    (reading.status == BatteryManager.BATTERY_STATUS_FULL && reading.plugged != 0) ||
                    (reading.status == BatteryManager.BATTERY_STATUS_UNKNOWN && reading.fallbackCharging)
            return BatteryStatus(
                percentage = percentage,
                isCharging = isCharging,
                plugType = mapPlugType(reading.plugged),
                temperatureC = computeTemperatureC(reading.temperatureTenthsC),
                health = mapHealth(reading.health),
            )
        }

        internal fun mapPlugType(plugged: Int): BatteryPlugType =
            when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> BatteryPlugType.AC
                BatteryManager.BATTERY_PLUGGED_USB -> BatteryPlugType.USB
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> BatteryPlugType.WIRELESS
                else -> BatteryPlugType.UNKNOWN
            }

        internal fun mapHealth(health: Int): BatteryHealth =
            when (health) {
                BatteryManager.BATTERY_HEALTH_GOOD -> BatteryHealth.GOOD
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> BatteryHealth.OVERHEAT
                BatteryManager.BATTERY_HEALTH_COLD -> BatteryHealth.COLD
                BatteryManager.BATTERY_HEALTH_DEAD -> BatteryHealth.DEAD
                else -> BatteryHealth.UNKNOWN
            }

        internal fun computePercentage(
            level: Int,
            scale: Int,
            fallbackCapacity: Int,
        ): Int =
            if (level >= 0 && scale > 0) {
                ((level.toFloat() / scale.toFloat()) * MAX_PERCENTAGE).toInt().coerceIn(0, MAX_PERCENTAGE)
            } else if (fallbackCapacity in 0..MAX_PERCENTAGE) {
                fallbackCapacity
            } else {
                0
            }

        internal fun computeTemperatureC(temperatureTenthsC: Int): Float =
            if (temperatureTenthsC == AndroidBatteryDataSource.MISSING_TEMPERATURE) {
                0.0f
            } else {
                temperatureTenthsC / TENTHS_PER_DEGREE
            }
    }
}
