package ai.hermes.mobile.core

import android.content.Context
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Abstraction over Android [PowerManager.WakeLock] enabling deterministic unit testing
 * of bounded wake-lock acquisition and release transitions.
 */
internal interface WakeLockHandle {
    val isHeld: Boolean

    fun acquire(timeoutMs: Long)

    fun release()
}

/**
 * Abstraction over Android thermal status monitoring.
 */
internal interface ThermalMonitor {
    fun currentThermalLevel(): ThermalLevel

    fun register(onThermalChanged: (ThermalLevel) -> Unit)

    fun unregister()
}

internal class AndroidWakeLockHandle(
    private val wakeLock: PowerManager.WakeLock
) : WakeLockHandle {
    override val isHeld: Boolean
        get() = wakeLock.isHeld

    override fun acquire(timeoutMs: Long) {
        wakeLock.acquire(timeoutMs)
    }

    override fun release() {
        wakeLock.release()
    }
}

internal class AndroidThermalMonitor(
    context: Context,
    private val powerManager: PowerManager
) : ThermalMonitor {
    private val appContext: Context = context.applicationContext
    private var listener: PowerManager.OnThermalStatusChangedListener? = null

    override fun currentThermalLevel(): ThermalLevel =
        ThermalLevel.fromPlatformStatus(powerManager.currentThermalStatus)

    override fun register(onThermalChanged: (ThermalLevel) -> Unit) {
        if (listener != null) {
            return
        }
        val createdListener =
            PowerManager.OnThermalStatusChangedListener { platformStatus ->
                onThermalChanged(ThermalLevel.fromPlatformStatus(platformStatus))
            }
        try {
            powerManager.addThermalStatusListener(
                ContextCompat.getMainExecutor(appContext),
                createdListener
            )
            listener = createdListener
            onThermalChanged(currentThermalLevel())
        } catch (e: SecurityException) {
            Log.w(TAG, "Unable to register thermal status listener", e)
        }
    }

    override fun unregister() {
        val activeListener = listener ?: return
        try {
            powerManager.removeThermalStatusListener(activeListener)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Thermal status listener already unregistered", e)
        } finally {
            listener = null
        }
    }

    companion object {
        private const val TAG = "AndroidThermalMonitor"
    }
}

/**
 * Coordinates the daemon's bounded `PARTIAL_WAKE_LOCK` and `PowerManager.OnThermalStatusChangedListener`
 * in accordance with `LIFECYCLE-BATTERY.md` and `LIFECYCLE-CONTRACT.md`.
 */
internal class DaemonPowerController(
    private val wakeLock: WakeLockHandle,
    private val thermalMonitor: ThermalMonitor,
    private val onThermalChanged: (ThermalLevel) -> Unit
) {
    constructor(
        context: Context,
        onThermalChanged: (ThermalLevel) -> Unit
    ) : this(
        wakeLock = createPlatformWakeLock(context),
        thermalMonitor = createPlatformThermalMonitor(context),
        onThermalChanged = onThermalChanged
    )

    fun registerThermalListener() {
        thermalMonitor.register(onThermalChanged)
    }

    fun unregisterThermalListener() {
        thermalMonitor.unregister()
    }

    fun currentThermalLevel(): ThermalLevel = thermalMonitor.currentThermalLevel()

    /**
     * Acquires a bounded partial wake lock when [status] requires active execution and is not
     * thermally throttled, or releases it immediately otherwise.
     */
    fun syncWakeLock(
        status: DaemonStatus,
        timeoutMs: Long = DEFAULT_WAKELOCK_TIMEOUT_MS
    ) {
        if (status.shouldHoldWakeLock) {
            acquireWithTimeout(timeoutMs)
        } else {
            releaseWakeLock()
        }
    }

    /**
     * Releases the partial wake lock if currently held.
     */
    fun releaseWakeLock() {
        try {
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        } catch (e: IllegalStateException) {
            Log.w(TAG, "WakeLock already released", e)
        }
    }

    private fun acquireWithTimeout(timeoutMs: Long) {
        require(timeoutMs > 0L) { "WakeLock timeout must be positive" }
        val boundedTimeout = timeoutMs.coerceAtMost(MAX_WAKELOCK_TIMEOUT_MS)
        try {
            wakeLock.acquire(boundedTimeout)
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing WAKE_LOCK permission when acquiring partial wake lock", e)
        }
    }

    companion object {
        private const val TAG = "DaemonPowerController"
        private const val WAKELOCK_TAG = "ai.hermes.mobile:DaemonExecutionWakeLock"
        const val DEFAULT_WAKELOCK_TIMEOUT_MS: Long = 10L * 60L * 1000L
        const val MAX_WAKELOCK_TIMEOUT_MS: Long = 10L * 60L * 1000L

        private fun createPlatformWakeLock(context: Context): WakeLockHandle {
            val appContext = context.applicationContext
            val powerManager = appContext.getSystemService(PowerManager::class.java)
            val platformLock =
                powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
                    setReferenceCounted(false)
                }
            return AndroidWakeLockHandle(platformLock)
        }

        private fun createPlatformThermalMonitor(context: Context): ThermalMonitor {
            val appContext = context.applicationContext
            val powerManager = appContext.getSystemService(PowerManager::class.java)
            return AndroidThermalMonitor(appContext, powerManager)
        }
    }
}
