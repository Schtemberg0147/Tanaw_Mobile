package com.tanaw.app.feature.workspace

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object NotificationReadRules {

    /**
     * Marks any notification row with a blank `read_at` using [readAtIso].
     * Already-read rows retain their existing `read_at` timestamp.
     */
    fun markRowsRead(rows: List<JsonObject>, readAtIso: String): List<JsonObject> {
        if (rows.isEmpty() || readAtIso.isBlank()) return rows
        return rows.map { row ->
            if (row.value("read_at").isBlank()) {
                JsonObject(row + ("read_at" to JsonPrimitive(readAtIso)))
            } else {
                row
            }
        }
    }

    /**
     * Reverts optimistic `read_at` stamps matching [readAtIso] back to null when the
     * server update fails or is cancelled before completion.
     */
    fun revertOptimisticRead(rows: List<JsonObject>, readAtIso: String): List<JsonObject> {
        if (rows.isEmpty() || readAtIso.isBlank()) return rows
        return rows.map { row ->
            if (row.value("read_at") == readAtIso) {
                JsonObject(row + ("read_at" to JsonNull))
            } else {
                row
            }
        }
    }

    /**
     * Returns true if any notification in [rows] has a blank `read_at`.
     */
    fun hasUnread(rows: List<JsonObject>): Boolean =
        rows.any { it.value("read_at").isBlank() }

    /**
     * Updates the `"notifications"` array inside workspace [data] so any unread entries
     * receive [readAtIso]. Returns [data] unchanged if [data] is null or has no unread entries.
     */
    fun markAllReadInData(data: JsonObject?, readAtIso: String): JsonObject? {
        if ((data == null) || readAtIso.isBlank()) return data
        val existing = data.rows("notifications")
        if (existing.isEmpty() || !hasUnread(existing)) return data
        val updated = markRowsRead(existing, readAtIso)
        return JsonObject(data + ("notifications" to JsonArray(updated)))
    }

    /**
     * Reverts optimistic `read_at` stamps matching [readAtIso] inside workspace [data].
     */
    fun revertOptimisticReadInData(data: JsonObject?, readAtIso: String): JsonObject? {
        if ((data == null) || readAtIso.isBlank()) return data
        val existing = data.rows("notifications")
        if (existing.none { it.value("read_at") == readAtIso }) return data
        val reverted = revertOptimisticRead(existing, readAtIso)
        return JsonObject(data + ("notifications" to JsonArray(reverted)))
    }

    /**
     * Reconciles newly fetched workspace notifications into an already-loaded [existingPage]
     * while preserving older paginated items beyond the workspace window.
     */
    fun syncNotificationsPage(
        existingPage: List<JsonObject>?,
        incomingWorkspaceNotifications: List<JsonObject>,
    ): List<JsonObject>? {
        if (existingPage == null) return null
        return CustomerPaginationRules.mergePage(
            existing = existingPage,
            incoming = incomingWorkspaceNotifications,
            reset = false
        )
    }

    /**
     * Returns true when incoming or loaded notifications should be marked read automatically:
     * only while the customer alerts screen is open, the user is not a driver, and online.
     */
    fun shouldAutoMarkRead(
        isCustomerAlertsOpen: Boolean,
        role: String,
        isOffline: Boolean
    ): Boolean = isCustomerAlertsOpen && role != "driver" && !isOffline
}
