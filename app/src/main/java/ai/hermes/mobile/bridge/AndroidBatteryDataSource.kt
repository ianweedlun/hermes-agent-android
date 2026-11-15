package ai.hermes.mobile.bridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import androidx.core.content.ContextCompat

internal data class BatteryRawReading(
    val level: Int,
    val scale: Int,
    val status: Int,
    val plugged: Int,
    val temperatureTenthsC: Int,
    val health: Int,
    val fallbackCharging: Boolean = false,
    val fallbackCapacity: Int = -1,
)

internal interface BatteryDataSource {
    fun readCurrent(): BatteryRawReading

    fun registerUpdates(onReading: (BatteryRawReading) -> Unit)

    fun unregisterUpdates()
}

internal class AndroidBatteryDataSource(
    context: Context,
) : BatteryDataSource {
    private val appContext: Context = context.applicationContext
    private val batteryManager: BatteryManager? by lazy {
        appContext.getSystemService(BatteryManager::class.java)
    }
    private var receiver: BroadcastReceiver? = null

    override fun readCurrent(): BatteryRawReading {
        val stickyIntent =
            try {
                appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            } catch (e: SecurityException) {
                Log.w(TAG, "Unable to read sticky battery intent", e)
                null
            }
        return extractReading(stickyIntent, batteryManager)
    }

    override fun registerUpdates(onReading: (BatteryRawReading) -> Unit) {
        if (receiver != null) {
            return
        }
        val created =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context?,
                    intent: Intent?,
                ) {
                    onReading(extractReading(intent, batteryManager))
                }
            }
        try {
            ContextCompat.registerReceiver(
                appContext,
                created,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            receiver = created
        } catch (e: SecurityException) {
            Log.w(TAG, "Unable to register battery broadcast receiver", e)
        }
    }

    override fun unregisterUpdates() {
        val active = receiver ?: return
        try {
            appContext.unregisterReceiver(active)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Battery broadcast receiver already unregistered", e)
        } finally {
            receiver = null
        }
    }

    private fun extractReading(
        intent: Intent?,
        manager: BatteryManager?,
    ): BatteryRawReading {
        val fallbackCharging = manager?.isCharging ?: false
        val fallbackCapacity =
            manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        return BatteryRawReading(
            level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1,
            scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, DEFAULT_SCALE) ?: DEFAULT_SCALE,
            status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
                ?: BatteryManager.BATTERY_STATUS_UNKNOWN,
            plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0,
            temperatureTenthsC = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, MISSING_TEMPERATURE)
                ?: MISSING_TEMPERATURE,
            health = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
                ?: BatteryManager.BATTERY_HEALTH_UNKNOWN,
            fallbackCharging = fallbackCharging,
            fallbackCapacity = fallbackCapacity,
        )
    }

    companion object {
        private const val TAG = "AndroidBatteryDataSource"
        private const val DEFAULT_SCALE = 100
        internal const val MISSING_TEMPERATURE = Int.MIN_VALUE
    }
}
