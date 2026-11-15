package ai.hermes.mobile.core

import ai.hermes.mobile.MainActivity
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Android Foreground Service (`specialUse`) hosting the Hermes Agent daemon lifecycle.
 * Manages ongoing notifications, bounded partial wake locks, thermal throttling,
 * and per-session high-entropy IPC bearer token generation.
 */
class HermesDaemonService : Service() {
    private val serviceScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val binder: IBinder = DaemonBinder()

    private val _status: MutableStateFlow<DaemonStatus> = MutableStateFlow(DaemonStatus())
    val status: StateFlow<DaemonStatus> = _status.asStateFlow()

    val ipcToken: BearerToken by lazy { IpcTokenGenerator.generate() }

    private lateinit var notificationManager: HermesNotificationManager
    private lateinit var powerController: DaemonPowerController

    private var isForegroundActive: Boolean = false

    /**
     * Local binder exposing daemon status flow and status update hooks to in-process components.
     */
    inner class DaemonBinder : Binder() {
        fun getService(): HermesDaemonService = this@HermesDaemonService
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = HermesNotificationManager(this)
        powerController = DaemonPowerController(this, ::onThermalLevelChanged)
        powerController.registerThermalListener()
        Log.i(TAG, "HermesDaemonService created; IPC token initialized (redacted)")
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int =
        when (intent?.action) {
            ACTION_STOP -> {
                shutdownDaemon("Stop action received")
                START_NOT_STICKY
            }
            ACTION_UPDATE_STATUS -> {
                promoteToForegroundIfNeeded()
                val state = AgentState.fromWireName(intent.getStringExtra(EXTRA_AGENT_STATE))
                val activeTool = intent.getStringExtra(EXTRA_ACTIVE_TOOL)
                val stepIndex = intent.getIntExtra(EXTRA_STEP_INDEX, _status.value.stepIndex)
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: _status.value.sessionId
                updateAgentStatus(
                    state = state,
                    activeTool = activeTool,
                    stepIndex = stepIndex,
                    sessionId = sessionId,
                )
                START_STICKY
            }
            else -> {
                promoteToForegroundIfNeeded()
                START_STICKY
            }
        }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onTimeout(
        startId: Int,
        fgsType: Int,
    ) {
        Log.w(TAG, "Foreground service timeout signaled (startId=$startId, fgsType=$fgsType)")
        shutdownDaemon("Foreground service timeout")
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            Log.i(TAG, "Memory trim requested (level=$level): clearing cached notification assets")
            notificationManager.clearCachedAssets()
        }
    }

    override fun onDestroy() {
        powerController.unregisterThermalListener()
        powerController.releaseWakeLock()
        notificationManager.clearCachedAssets()
        serviceScope.cancel()
        isForegroundActive = false
        Log.i(TAG, "HermesDaemonService destroyed")
        super.onDestroy()
    }

    /**
     * Updates the active [DaemonStatus], synchronizes the partial wake lock policy,
     * and refreshes the ongoing notification.
     */
    fun updateAgentStatus(
        state: AgentState,
        activeTool: String? = null,
        stepIndex: Int = _status.value.stepIndex,
        sessionId: String? = _status.value.sessionId,
    ) {
        val updated =
            _status.value.copy(
                state = state,
                activeTool = activeTool,
                stepIndex = stepIndex,
                sessionId = sessionId,
            )
        _status.value = updated
        powerController.syncWakeLock(updated)
        refreshForegroundNotification(updated)
    }

    private fun promoteToForegroundIfNeeded() {
        if (isForegroundActive) {
            return
        }
        notificationManager.ensureChannelsCreated()
        val notification =
            notificationManager.buildDaemonNotification(
                status = _status.value,
                openAppPendingIntent = createOpenAppPendingIntent(),
                stopServicePendingIntent = createStopServicePendingIntent(),
            )
        ServiceCompat.startForeground(
            this,
            HermesNotificationManager.NOTIFICATION_ID_DAEMON,
            notification,
            foregroundServiceTypeFlag(),
        )
        isForegroundActive = true
        powerController.syncWakeLock(_status.value)
    }

    private fun shutdownDaemon(reason: String) {
        Log.i(TAG, "Shutting down HermesDaemonService: $reason")
        _status.update { it.copy(state = AgentState.IDLE, activeTool = null) }
        powerController.releaseWakeLock()
        if (isForegroundActive) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            isForegroundActive = false
        }
        stopSelf()
    }

    private fun onThermalLevelChanged(thermalLevel: ThermalLevel) {
        _status.update { it.copy(thermalLevel = thermalLevel) }
        val current = _status.value
        powerController.syncWakeLock(current)
        if (thermalLevel.isThrottled) {
            Log.w(
                TAG,
                "Thermal status ${thermalLevel.label} (${thermalLevel.statusCode}): throttling background work",
            )
        }
        refreshForegroundNotification(current)
    }

    private fun refreshForegroundNotification(current: DaemonStatus) {
        if (!isForegroundActive) {
            return
        }
        notificationManager.updateDaemonNotification(
            status = current,
            openAppPendingIntent = createOpenAppPendingIntent(),
            stopServicePendingIntent = createStopServicePendingIntent(),
        )
    }

    private fun createOpenAppPendingIntent(): PendingIntent {
        val intent =
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        return PendingIntent.getActivity(
            this,
            REQUEST_CODE_OPEN_APP,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createStopServicePendingIntent(): PendingIntent {
        val intent =
            Intent(this, HermesDaemonService::class.java).apply {
                action = ACTION_STOP
            }
        return PendingIntent.getService(
            this,
            REQUEST_CODE_STOP_SERVICE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val TAG = "HermesDaemonService"
        private const val REQUEST_CODE_OPEN_APP = 101
        private const val REQUEST_CODE_STOP_SERVICE = 102

        const val DEFAULT_WAKELOCK_TIMEOUT_MS: Long = DaemonPowerController.DEFAULT_WAKELOCK_TIMEOUT_MS

        const val ACTION_START: String = "ai.hermes.mobile.action.START_DAEMON"
        const val ACTION_STOP: String = "ai.hermes.mobile.action.STOP_DAEMON"
        const val ACTION_UPDATE_STATUS: String = "ai.hermes.mobile.action.UPDATE_STATUS"

        const val EXTRA_AGENT_STATE: String = "extra_agent_state"
        const val EXTRA_ACTIVE_TOOL: String = "extra_active_tool"
        const val EXTRA_STEP_INDEX: String = "extra_step_index"
        const val EXTRA_SESSION_ID: String = "extra_session_id"

        internal fun foregroundServiceTypeFlag(): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }

        fun startIntent(context: Context): Intent =
            Intent(context, HermesDaemonService::class.java).apply {
                action = ACTION_START
            }

        fun stopIntent(context: Context): Intent =
            Intent(context, HermesDaemonService::class.java).apply {
                action = ACTION_STOP
            }
    }
}
