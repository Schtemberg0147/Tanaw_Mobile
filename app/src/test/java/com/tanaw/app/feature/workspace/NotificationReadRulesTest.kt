package com.tanaw.app.feature.workspace

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationReadRulesTest {

    private fun notification(id: String, readAt: String?, createdAt: String = "2026-11-15T10:00:00Z"): JsonObject =
        buildJsonObject {
            put("id", id)
            put("title", "Title $id")
            put("body", "Body $id")
            put("created_at", createdAt)
            if (readAt == null) {
                put("read_at", JsonNull)
            } else {
                put("read_at", readAt)
            }
        }

    @Test
    fun markRowsReadStampsOnlyUnreadRowsAndPreservesExistingReadTimestamps() {
        val rows = listOf(
            notification("1", null),
            notification("2", ""),
            notification("3", "2026-11-14T08:00:00Z"),
        )
        val stamped = NotificationReadRules.markRowsRead(rows, "2026-11-15T12:00:00Z")

        assertEquals("2026-11-15T12:00:00Z", stamped[0].value("read_at"))
        assertEquals("2026-11-15T12:00:00Z", stamped[1].value("read_at"))
        assertEquals("2026-11-14T08:00:00Z", stamped[2].value("read_at"))
    }

    @Test
    fun revertOptimisticReadRestoresOnlyMatchingStamp() {
        val rows = listOf(
            notification("1", "2026-11-15T12:00:00Z"),
            notification("2", "2026-11-14T08:00:00Z")
        )
        val reverted = NotificationReadRules.revertOptimisticRead(rows, "2026-11-15T12:00:00Z")

        assertEquals("", reverted[0].value("read_at"))
        assertEquals("2026-11-14T08:00:00Z", reverted[1].value("read_at"))
    }

    @Test
    fun hasUnreadDetectsNullOrBlankReadAt() {
        assertFalse(NotificationReadRules.hasUnread(emptyList()))
        assertFalse(NotificationReadRules.hasUnread(listOf(notification("1", "2026-11-14T08:00:00Z"))))
        assertTrue(NotificationReadRules.hasUnread(listOf(notification("1", null))))
        assertTrue(NotificationReadRules.hasUnread(listOf(notification("1", ""))))
    }

    @Test
    fun markAllReadInDataUpdatesNotificationsAndPreservesOtherFields() {
        val data = buildJsonObject {
            put("profile", buildJsonObject { put("role", "customer") })
            put(
                "notifications",
                JsonArray(
                    listOf(
                        notification("1", null),
                        notification("2", "2026-11-14T08:00:00Z")
                    )
                )
            )
        }

        val updated = NotificationReadRules.markAllReadInData(data, "2026-11-15T12:00:00Z")!!
        assertEquals("customer", updated.obj("profile").value("role"))
        assertFalse(NotificationReadRules.hasUnread(updated.rows("notifications")))
        assertEquals("2026-11-15T12:00:00Z", updated.rows("notifications")[0].value("read_at"))
        assertEquals("2026-11-14T08:00:00Z", updated.rows("notifications")[1].value("read_at"))

        // Calling again when all are already read returns the same object instance
        assertSame(updated, NotificationReadRules.markAllReadInData(updated, "2026-11-15T13:00:00Z"))
    }

    @Test
    fun revertOptimisticReadInDataRestoresUnreadOnFailure() {
        val data = buildJsonObject {
            put(
                "notifications",
                JsonArray(
                    listOf(
                        notification("1", "2026-11-15T12:00:00Z"),
                        notification("2", "2026-11-14T08:00:00Z")
                    )
                )
            )
        }

        val reverted = NotificationReadRules.revertOptimisticReadInData(data, "2026-11-15T12:00:00Z")!!
        assertEquals("", reverted.rows("notifications")[0].value("read_at"))
        assertEquals("2026-11-14T08:00:00Z", reverted.rows("notifications")[1].value("read_at"))
    }

    @Test
    fun syncNotificationsPageMergesUpcomingAndPreservesOlderPaginatedItems() {
        assertNull(NotificationReadRules.syncNotificationsPage(null, listOf(notification("1", "2026-11-15T12:00:00Z"))))

        val existingPage = listOf(
            notification("2", "2026-11-15T09:00:00Z", "2026-11-15T09:00:00Z"),
            notification("older-page-2", "2026-11-10T08:00:00Z", "2026-11-10T08:00:00Z")
        )
        val incomingFromWorkspace = listOf(
            notification("3-new", "2026-11-15T12:00:00Z", "2026-11-15T12:00:00Z"),
            notification("2", "2026-11-15T12:00:00Z", "2026-11-15T09:00:00Z")
        )

        val synced = NotificationReadRules.syncNotificationsPage(existingPage, incomingFromWorkspace)!!
        assertEquals(listOf("3-new", "2", "older-page-2"), synced.map { it.value("id") })
        assertEquals("2026-11-15T12:00:00Z", synced[1].value("read_at"))
    }

    @Test
    fun shouldAutoMarkReadOnlyWhenCustomerAlertsOpenAndOnline() {
        assertTrue(NotificationReadRules.shouldAutoMarkRead(isCustomerAlertsOpen = true, role = "customer", isOffline = false))
        assertFalse(NotificationReadRules.shouldAutoMarkRead(isCustomerAlertsOpen = false, role = "customer", isOffline = false))
        assertFalse(NotificationReadRules.shouldAutoMarkRead(isCustomerAlertsOpen = true, role = "driver", isOffline = false))
        assertFalse(NotificationReadRules.shouldAutoMarkRead(isCustomerAlertsOpen = true, role = "customer", isOffline = true))
    }
}
