package com.tanaw.app.feature.workspace.rules

import com.tanaw.app.feature.workspace.viewmodel.value
import kotlinx.serialization.json.JsonObject

data class DeviceAlertPayload(
    val id: String,
    val title: String,
    val body: String,
    val shipmentId: String?,
    val createdAt: String,
)

data class DeviceAlertEvaluation(
    val alertsToPost: List<DeviceAlertPayload>,
    val updatedSeenIds: Set<String>,
)

/**
 * Pure helper rules for determining which incoming `public.notifications` rows should be
 * dispatched to the user's Android device notification tray.
 */
object DeviceNotificationRules {
    const val MAX_TRACKED_IDS = 150

    /**
     * Evaluates incoming [rows] from `public.notifications` against [previousSeenIds].
     *
     * - On initial baseline seed ([isInitialSeed] == true when no prior IDs have been recorded
     *   for this user session), all current notification IDs are recorded into `updatedSeenIds`
     *   and only the newest unread notification (if any) is surfaced if [postLatestOnInitialSeed]
     *   is true; by default (`false`), cold start seeds existing IDs silently so historical unread
     *   rows do not spam the device tray on login, while any newly arriving unread rows afterward
     *   trigger immediate device notifications.
     * - When [isAlertsScreenOpen] is true, new IDs are recorded as seen without posting redundant
     *   system-tray popups over the already-open Alerts screen.
     */
    fun evaluateIncomingNotifications(
        previousSeenIds: Set<String>,
        rows: List<JsonObject>,
        isInitialSeed: Boolean,
        isAlertsScreenOpen: Boolean,
        postLatestOnInitialSeed: Boolean = false,
    ): DeviceAlertEvaluation {
        if (rows.isEmpty()) {
            return DeviceAlertEvaluation(
                alertsToPost = emptyList(),
                updatedSeenIds = previousSeenIds,
            )
        }

        val incomingIds = rows.mapNotNull { it.value("id").trim().takeIf(String::isNotBlank) }
        val combinedIds = LinkedHashSet<String>(previousSeenIds.size + incomingIds.size).apply {
            addAll(incomingIds)
            addAll(previousSeenIds)
        }.take(MAX_TRACKED_IDS).toSet()

        if (isAlertsScreenOpen) {
            return DeviceAlertEvaluation(
                alertsToPost = emptyList(),
                updatedSeenIds = combinedIds,
            )
        }

        if (isInitialSeed) {
            if (!postLatestOnInitialSeed) {
                return DeviceAlertEvaluation(
                    alertsToPost = emptyList(),
                    updatedSeenIds = combinedIds,
                )
            }
            val latestUnread = rows.firstOrNull {
                it.value("id").isNotBlank() && it.value("read_at").isBlank()
            }?.let(::toPayload)
            return DeviceAlertEvaluation(
                alertsToPost = listOfNotNull(latestUnread),
                updatedSeenIds = combinedIds,
            )
        }

        val newUnreadPayloads = rows
            .filter { row ->
                val id = row.value("id").trim()
                id.isNotBlank() &&
                    id !in previousSeenIds &&
                    row.value("read_at").isBlank()
            }
            .mapNotNull(::toPayload)
            .reversed() // Post oldest-to-newest so the newest sits on top of the tray

        return DeviceAlertEvaluation(
            alertsToPost = newUnreadPayloads,
            updatedSeenIds = combinedIds,
        )
    }

    fun toPayload(row: JsonObject): DeviceAlertPayload? {
        val id = row.value("id").trim()
        if (id.isBlank()) return null
        val title = row.value("title").trim().ifBlank { "Tanaw Alert" }
        val body = row.value("body").trim().ifBlank { "Open Tanaw to view the latest update." }
        val shipmentId = row.value("shipment_id").trim().takeIf(String::isNotBlank)
        val createdAt = row.value("created_at").trim()
        return DeviceAlertPayload(
            id = id,
            title = title,
            body = body,
            shipmentId = shipmentId,
            createdAt = createdAt,
        )
    }

    /**
     * Generates a stable, non-colliding Android notification ID from a notification UUID/string ID.
     * Reserves ID `1` for `TripTrackingService` foreground GPS tracking.
     */
    fun androidNotificationId(notificationId: String): Int {
        val hash = notificationId.trim().hashCode() and 0x7FFFFFFF
        return if (hash <= 10) hash + 1000 else hash
    }
}
