package com.tanaw.app.feature.workspace.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tanaw.app.MainActivity
import com.tanaw.app.R
import com.tanaw.app.feature.workspace.rules.DeviceAlertPayload
import com.tanaw.app.feature.workspace.rules.DeviceNotificationRules
import kotlinx.serialization.json.JsonObject

/**
 * Manages the Android system notification channel (`tanaw_workspace_alerts`),
 * persists per-user seen notification IDs so alerts are never duplicated across polls,
 * and posts heads-up / system-tray notifications to the user's device.
 */
class WorkspaceDeviceNotifier(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "tanaw_workspace_alerts"
        const val CHANNEL_NAME = "Shipment & Dispatch Alerts"
        const val CHANNEL_DESCRIPTION = "Real-time notifications for quotations, dispatch, delivery milestones, and alerts"
        const val EXTRA_OPEN_ALERTS = "com.tanaw.app.EXTRA_OPEN_ALERTS"
        const val EXTRA_SHIPMENT_ID = "com.tanaw.app.EXTRA_SHIPMENT_ID"
        const val EXTRA_NOTIFICATION_ID = "com.tanaw.app.EXTRA_NOTIFICATION_ID"
        const val EXTRA_INTERNAL_TOKEN = "com.tanaw.app.EXTRA_INTERNAL_TOKEN"
        private const val PREFS_NAME = "tanaw_device_notifications"
        private val PROCESS_TOKEN: String = run {
            val bytes = ByteArray(16)
            java.security.SecureRandom().nextBytes(bytes)
            bytes.joinToString("") { "%02x".format(it) }
        }

        fun isTrustedNotificationIntent(intent: Intent?): Boolean {
            val token = intent?.getStringExtra(EXTRA_INTERNAL_TOKEN) ?: return false
            return java.security.MessageDigest.isEqual(
                token.toByteArray(Charsets.UTF_8),
                PROCESS_TOKEN.toByteArray(Charsets.UTF_8),
            )
        }
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = CHANNEL_DESCRIPTION
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun canPostNotifications(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    /**
     * Evaluates the workspace [notificationRows] for [userId] and posts any newly arrived
     * unread alerts to the Android device notification tray.
     */
    fun onWorkspaceNotificationsLoaded(
        userId: String,
        notificationRows: List<JsonObject>,
        isAlertsScreenOpen: Boolean,
    ) {
        if (userId.isBlank()) return
        val seenKey = "seen_ids_$userId"
        val seededKey = "seeded_$userId"
        val isSeeded = prefs.getBoolean(seededKey, false)
        val previousIds = prefs.getStringSet(seenKey, emptySet())?.toSet().orEmpty()

        val evaluation = DeviceNotificationRules.evaluateIncomingNotifications(
            previousSeenIds = previousIds,
            rows = notificationRows,
            isInitialSeed = !isSeeded,
            isAlertsScreenOpen = isAlertsScreenOpen,
            postLatestOnInitialSeed = false,
        )

        prefs.edit()
            .putBoolean(seededKey, true)
            .putStringSet(seenKey, evaluation.updatedSeenIds)
            .apply()

        if (evaluation.alertsToPost.isEmpty() || !canPostNotifications()) return
        ensureChannel()
        evaluation.alertsToPost.forEach(::postAlert)
    }

    fun postAlert(alert: DeviceAlertPayload) {
        if (!canPostNotifications()) return
        ensureChannel()
        val androidId = DeviceNotificationRules.androidNotificationId(alert.id)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_INTERNAL_TOKEN, PROCESS_TOKEN)
            putExtra(EXTRA_OPEN_ALERTS, true)
            putExtra(EXTRA_NOTIFICATION_ID, alert.id)
            if (!alert.shipmentId.isNullOrBlank()) {
                putExtra(EXTRA_SHIPMENT_ID, alert.shipmentId)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            androidId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_location_pin)
            .setContentTitle(alert.title)
            .setContentText(alert.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(androidId, notification)
        } catch (_: SecurityException) {
            // Permission revoked concurrently
        }
    }

    fun dismissNotification(notificationId: String) {
        if (notificationId.isBlank()) return
        val androidId = DeviceNotificationRules.androidNotificationId(notificationId)
        runCatching {
            NotificationManagerCompat.from(context).cancel(androidId)
        }
    }

    fun clearUserSession(userId: String) {
        if (userId.isBlank()) return
        prefs.edit()
            .remove("seen_ids_$userId")
            .remove("seeded_$userId")
            .apply()
    }
}
