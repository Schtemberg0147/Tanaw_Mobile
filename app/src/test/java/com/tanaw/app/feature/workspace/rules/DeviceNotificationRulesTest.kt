package com.tanaw.app.feature.workspace.rules

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceNotificationRulesTest {

    @Test
    fun `evaluateIncomingNotifications seeds initial snapshot without emitting device alerts`() {
        val rows = listOf(
            buildJsonObject {
                put("id", "n-1")
                put("title", "Quotation Ready")
                put("body", "Your FTL quote for TNW-101 is ready.")
                put("read_at", JsonNull)
            },
            buildJsonObject {
                put("id", "n-2")
                put("title", "Booking Received")
                put("body", "Office review pending.")
                put("read_at", "2026-11-20T08:00:00Z")
            }
        )

        val decision = DeviceNotificationRules.evaluateIncomingNotifications(
            previousSeenIds = emptySet(),
            rows = rows,
            isInitialSeed = true,
            isAlertsScreenOpen = false
        )

        assertTrue(decision.alertsToPost.isEmpty())
        assertEquals(setOf("n-1", "n-2"), decision.updatedSeenIds)
    }

    @Test
    fun `evaluateIncomingNotifications emits only newly arrived unread notifications when alerts screen is closed`() {
        val rows = listOf(
            buildJsonObject {
                put("id", "n-3")
                put("title", "Driver Assigned")
                put("body", "Juan Dela Cruz has been assigned to TNW-101.")
                put("shipment_id", "ship-101")
                put("type", "shipment_status")
            },
            buildJsonObject {
                put("id", "n-1")
                put("title", "Quotation Ready")
                put("body", "Your FTL quote for TNW-101 is ready.")
            }
        )

        val decision = DeviceNotificationRules.evaluateIncomingNotifications(
            previousSeenIds = setOf("n-1", "n-2"),
            rows = rows,
            isInitialSeed = false,
            isAlertsScreenOpen = false
        )

        assertEquals(1, decision.alertsToPost.size)
        val payload = decision.alertsToPost.first()
        assertEquals("n-3", payload.id)
        assertEquals("Driver Assigned", payload.title)
        assertEquals("ship-101", payload.shipmentId)
        assertTrue(DeviceNotificationRules.androidNotificationId(payload.id) > 10)
        assertEquals(setOf("n-1", "n-2", "n-3"), decision.updatedSeenIds)
    }

    @Test
    fun `evaluateIncomingNotifications suppresses heads-up notification when alerts screen is already open`() {
        val rows = listOf(
            buildJsonObject {
                put("id", "n-4")
                put("title", "Route Started")
                put("body", "Driver is on the way to pickup.")
            }
        )

        val decision = DeviceNotificationRules.evaluateIncomingNotifications(
            previousSeenIds = setOf("n-1"),
            rows = rows,
            isInitialSeed = false,
            isAlertsScreenOpen = true
        )

        assertTrue(decision.alertsToPost.isEmpty())
        assertEquals(setOf("n-1", "n-4"), decision.updatedSeenIds)
    }
}
