package ai.hermes.mobile.core

import ai.hermes.mobile.R
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.util.Log
import android.view.ContextThemeWrapper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors

/**
 * Pure-Kotlin specification for a Hermes notification channel, decoupled from framework stubs
 * so channel configuration can be verified deterministically in JVM unit tests.
 */
data class NotificationChannelSpec(
    val id: String,
    val name: String,
    val description: String,
    val importance: Int,
    val showBadge: Boolean,
    val enableVibration: Boolean,
) {
    fun toNotificationChannel(): NotificationChannel =
        NotificationChannel(id, name, importance).apply {
            description = this@NotificationChannelSpec.description
            setShowBadge(showBadge)
            enableVibration(enableVibration)
        }
}

/**
 * Pure-Kotlin presentation model for daemon foreground notification copy.
 */
data class NotificationContentSpec(
    val title: String,
    val text: String,
    val subText: String?,
    val isOngoing: Boolean,
    val category: String,
)

/**
 * Localized copy templates used to render [DaemonStatus] into notification strings.
 */
data class NotificationCopy(
    val title: String = "Hermes Agent",
    val idleText: String = "Idle — Ready for instructions",
    val thinkingText: String = "Thinking…",
    val executingToolWithName: (String) -> String = { tool -> "Executing Tool: $tool" },
    val executingToolGeneric: String = "Executing Tool…",
    val errorText: String = "Error — Check daemon logs",
    val thermalSubtext: (String) -> String = { level -> "Thermal throttling ($level)" },
    val stepSubtext: (Int) -> String = { step -> "Step $step" },
) {
    companion object {
        fun fromContext(context: Context): NotificationCopy =
            NotificationCopy(
                title = context.getString(R.string.notification_title_daemon),
                idleText = context.getString(R.string.notification_status_idle),
                thinkingText = context.getString(R.string.notification_status_thinking),
                executingToolWithName = { tool ->
                    context.getString(R.string.notification_status_executing_tool, tool)
                },
                executingToolGeneric = context.getString(R.string.notification_status_executing_tool_generic),
                errorText = context.getString(R.string.notification_status_error),
                thermalSubtext = { level ->
                    context.getString(R.string.notification_subtext_thermal, level)
                },
                stepSubtext = { step ->
                    context.getString(R.string.notification_subtext_step, step)
                },
            )
    }
}

/**
 * Manages Hermes notification channels (`LOW` ongoing daemon status and `HIGH` alerts),
 * dynamic state rendering, and Material 3 branded notification construction.
 */
class HermesNotificationManager(
    private val context: Context,
) {
    private val platformManager: NotificationManager by lazy {
        context.getSystemService(NotificationManager::class.java)
    }

    private var cachedLogoBitmap: Bitmap? = null

    /**
     * Ensures both `LOW` (`channel_daemon_running`) and `HIGH` (`channel_alerts`) channels exist.
     */
    fun ensureChannelsCreated() {
        val specs =
            createDefaultChannelSpecs(
                daemonChannelName = context.getString(R.string.notification_channel_daemon_name),
                daemonChannelDescription = context.getString(R.string.notification_channel_daemon_description),
                alertsChannelName = context.getString(R.string.notification_channel_alerts_name),
                alertsChannelDescription = context.getString(R.string.notification_channel_alerts_description),
            )
        platformManager.createNotificationChannels(specs.map { it.toNotificationChannel() })
    }

    /**
     * Builds the ongoing foreground service notification reflecting the current [DaemonStatus].
     */
    fun buildDaemonNotification(
        status: DaemonStatus,
        openAppPendingIntent: PendingIntent,
        stopServicePendingIntent: PendingIntent,
    ): Notification {
        val content =
            formatDaemonNotificationContent(
                status = status,
                copy = NotificationCopy.fromContext(context),
            )
        return NotificationCompat
            .Builder(context, CHANNEL_ID_DAEMON_LOW)
            .setSmallIcon(R.drawable.ic_hermes_status)
            .setLargeIcon(getOrCreateLogoBitmap())
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setSubText(content.subText)
            .setColor(resolveAccentColor())
            .setOngoing(content.isOngoing)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(content.category)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                R.drawable.ic_hermes_open,
                context.getString(R.string.notification_action_open_app),
                openAppPendingIntent,
            ).addAction(
                R.drawable.ic_hermes_stop,
                context.getString(R.string.notification_action_stop),
                stopServicePendingIntent,
            ).build()
    }

    /**
     * Updates the ongoing daemon status notification if notification permission is granted.
     */
    fun updateDaemonNotification(
        status: DaemonStatus,
        openAppPendingIntent: PendingIntent,
        stopServicePendingIntent: PendingIntent,
    ) {
        if (!canPostNotifications()) {
            Log.w(TAG, "Skipping daemon notification update: POST_NOTIFICATIONS not granted")
            return
        }
        val notification =
            buildDaemonNotification(
                status = status,
                openAppPendingIntent = openAppPendingIntent,
                stopServicePendingIntent = stopServicePendingIntent,
            )
        platformManager.notify(NOTIFICATION_ID_DAEMON, notification)
    }

    /**
     * Posts an alert notification requested via `native/notify` on the appropriate priority channel.
     */
    fun postAlertNotification(
        payload: NativeNotificationPayload,
        contentIntent: PendingIntent,
        notificationId: Int = NOTIFICATION_ID_ALERT_BASE,
    ) {
        if (!canPostNotifications()) {
            Log.w(TAG, "Skipping alert notification: POST_NOTIFICATIONS not granted")
            return
        }
        ensureChannelsCreated()
        val channelId =
            if (payload.priority == NotificationPriority.HIGH) {
                CHANNEL_ID_ALERTS_HIGH
            } else {
                CHANNEL_ID_DAEMON_LOW
            }
        val priority =
            if (payload.priority == NotificationPriority.HIGH) {
                NotificationCompat.PRIORITY_HIGH
            } else {
                NotificationCompat.PRIORITY_LOW
            }
        val notification =
            NotificationCompat
                .Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_hermes_status)
                .setLargeIcon(getOrCreateLogoBitmap())
                .setContentTitle(payload.title)
                .setContentText(payload.body)
                .setColor(resolveAccentColor())
                .setPriority(priority)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setContentIntent(contentIntent)
                .build()
        platformManager.notify(notificationId, notification)
    }

    /**
     * Drops cached bitmaps under memory pressure.
     */
    fun clearCachedAssets() {
        cachedLogoBitmap = null
    }

    private fun canPostNotifications(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

    private fun resolveAccentColor(): Int {
        val fallback = ContextCompat.getColor(context, R.color.hermes_brand_primary)
        val themedContext =
            DynamicColors.wrapContextIfAvailable(
                ContextThemeWrapper(
                    context,
                    com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar,
                ),
            )
        return MaterialColors.getColor(
            themedContext,
            androidx.appcompat.R.attr.colorPrimary,
            fallback,
        )
    }

    private fun getOrCreateLogoBitmap(): Bitmap? {
        val existing = cachedLogoBitmap
        if (existing != null && !existing.isRecycled) {
            return existing
        }
        return renderVectorBitmap(R.drawable.ic_hermes_logo).also { cachedLogoBitmap = it }
    }

    private fun renderVectorBitmap(drawableResId: Int): Bitmap? {
        val drawable = ContextCompat.getDrawable(context, drawableResId) ?: return null
        val width = drawable.intrinsicWidth.coerceAtLeast(DEFAULT_ICON_SIZE_PX)
        val height = drawable.intrinsicHeight.coerceAtLeast(DEFAULT_ICON_SIZE_PX)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    companion object {
        private const val TAG = "HermesNotificationMgr"
        private const val DEFAULT_ICON_SIZE_PX = 96

        const val CHANNEL_ID_DAEMON_LOW: String = "channel_daemon_running"
        const val CHANNEL_ID_ALERTS_HIGH: String = "channel_alerts"
        const val ACTION_OPEN_TERMINAL: String = "ai.hermes.mobile.action.OPEN_TERMINAL"

        const val IMPORTANCE_LOW: Int = NotificationManager.IMPORTANCE_LOW
        const val IMPORTANCE_HIGH: Int = NotificationManager.IMPORTANCE_HIGH

        const val NOTIFICATION_ID_DAEMON: Int = 1001
        const val NOTIFICATION_ID_ALERT_BASE: Int = 2001

        /**
         * Creates the canonical channel specs (`LOW` ongoing daemon channel and `HIGH` alerts channel).
         */
        fun createDefaultChannelSpecs(
            daemonChannelName: String = "Hermes Daemon",
            daemonChannelDescription: String = "Ongoing status of the background Hermes Agent runtime",
            alertsChannelName: String = "Hermes Alerts",
            alertsChannelDescription: String = "High-priority task completions, errors, and input prompts",
        ): List<NotificationChannelSpec> =
            listOf(
                NotificationChannelSpec(
                    id = CHANNEL_ID_DAEMON_LOW,
                    name = daemonChannelName,
                    description = daemonChannelDescription,
                    importance = IMPORTANCE_LOW,
                    showBadge = false,
                    enableVibration = false,
                ),
                NotificationChannelSpec(
                    id = CHANNEL_ID_ALERTS_HIGH,
                    name = alertsChannelName,
                    description = alertsChannelDescription,
                    importance = IMPORTANCE_HIGH,
                    showBadge = true,
                    enableVibration = true,
                ),
            )

        /**
         * Maps [DaemonStatus] into deterministic notification content displaying agent state
         * (`Idle`, `Thinking`, `Executing Tool`, `Error`) and thermal throttling state.
         */
        fun formatDaemonNotificationContent(
            status: DaemonStatus,
            copy: NotificationCopy = NotificationCopy(),
        ): NotificationContentSpec {
            val stateText =
                when (status.state) {
                    AgentState.IDLE -> copy.idleText
                    AgentState.THINKING -> copy.thinkingText
                    AgentState.EXECUTING_TOOL -> {
                        val tool = status.activeTool?.trim().orEmpty()
                        if (tool.isNotEmpty()) {
                            copy.executingToolWithName(tool)
                        } else {
                            copy.executingToolGeneric
                        }
                    }
                    AgentState.ERROR -> copy.errorText
                }

            val subText =
                if (status.isThrottled) {
                    copy.thermalSubtext(status.thermalLevel.label)
                } else if (status.stepIndex > 0 && status.state != AgentState.IDLE) {
                    copy.stepSubtext(status.stepIndex)
                } else {
                    null
                }

            return NotificationContentSpec(
                title = copy.title,
                text = stateText,
                subText = subText,
                isOngoing = true,
                category = NotificationCompat.CATEGORY_SERVICE,
            )
        }
    }
}
