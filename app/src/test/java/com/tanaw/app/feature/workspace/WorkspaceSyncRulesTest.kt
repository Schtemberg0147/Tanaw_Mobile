package com.tanaw.app.feature.workspace

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceSyncRulesTest {

    @Test
    fun shouldQueueBackgroundRefreshWhenBusy() {
        assertTrue(WorkspaceSyncRules.shouldQueueBackgroundRefresh(isBusy = true))
        assertFalse(WorkspaceSyncRules.shouldQueueBackgroundRefresh(isBusy = false))
    }

    @Test
    fun shouldDrainPendingRefreshOnlyWhenPendingAndNotBusy() {
        assertTrue(WorkspaceSyncRules.shouldDrainPendingRefresh(hasPendingRefresh = true, isBusy = false))
        assertFalse(WorkspaceSyncRules.shouldDrainPendingRefresh(hasPendingRefresh = true, isBusy = true))
        assertFalse(WorkspaceSyncRules.shouldDrainPendingRefresh(hasPendingRefresh = false, isBusy = false))
        assertFalse(WorkspaceSyncRules.shouldDrainPendingRefresh(hasPendingRefresh = false, isBusy = true))
    }

    @Test
    fun resolveShipmentToRefreshPrefersCurrentOpenDetails() {
        val resolved = WorkspaceSyncRules.resolveShipmentToRefresh(
            currentDetailsShipmentId = "shipment-open-1",
            deliveryId = "del-99",
            data = null,
        )
        assertEquals("shipment-open-1", resolved)
    }

    @Test
    fun resolveShipmentToRefreshResolvesFromDeliveriesWhenCurrentBlank() {
        val data = JsonObject(
            mapOf(
                "deliveries" to JsonArray(
                    listOf(
                        JsonObject(
                            mapOf(
                                "id" to JsonPrimitive("del-101"),
                                "shipment_id" to JsonPrimitive("shipment-from-delivery-101")
                            )
                        ),
                        JsonObject(
                            mapOf(
                                "id" to JsonPrimitive("del-102"),
                                "shipment_id" to JsonPrimitive("shipment-from-delivery-102")
                            )
                        )
                    )
                )
            )
        )

        val resolved = WorkspaceSyncRules.resolveShipmentToRefresh(
            currentDetailsShipmentId = "",
            deliveryId = "del-102",
            data = data
        )
        assertEquals("shipment-from-delivery-102", resolved)

        val unresolved = WorkspaceSyncRules.resolveShipmentToRefresh(
            currentDetailsShipmentId = null,
            deliveryId = "del-999",
            data = data
        )
        assertNull(unresolved)
    }

    @Test
    fun resolveShipmentToRefreshReturnsNullWhenBothBlank() {
        assertNull(
            WorkspaceSyncRules.resolveShipmentToRefresh(
                currentDetailsShipmentId = null,
                deliveryId = null,
                data = null
            )
        )
        assertNull(
            WorkspaceSyncRules.resolveShipmentToRefresh(
                currentDetailsShipmentId = "  ",
                deliveryId = "  ",
                data = null
            )
        )
    }

    @Test
    fun shouldRefreshDetailsOnlyWhenShipmentIdsMatch() {
        assertTrue(
            WorkspaceSyncRules.shouldRefreshDetails(
                targetShipmentId = "ship-1",
                currentDetailsShipmentId = "ship-1"
            )
        )
        assertFalse(
            WorkspaceSyncRules.shouldRefreshDetails(
                targetShipmentId = "ship-1",
                currentDetailsShipmentId = "ship-2"
            )
        )
        assertFalse(
            WorkspaceSyncRules.shouldRefreshDetails(
                targetShipmentId = null,
                currentDetailsShipmentId = "ship-1"
            )
        )
        assertFalse(
            WorkspaceSyncRules.shouldRefreshDetails(
                targetShipmentId = "ship-1",
                currentDetailsShipmentId = null
            )
        )
    }

    @Test
    fun shouldReloadWorkspaceForTableFiltersSelfDriverTrackingLogs() {
        assertFalse(WorkspaceSyncRules.shouldReloadWorkspaceForTable("tracking_logs", "driver"))
        assertTrue(WorkspaceSyncRules.shouldReloadWorkspaceForTable("deliveries", "driver"))
        assertTrue(WorkspaceSyncRules.shouldReloadWorkspaceForTable("shipments", "driver"))
        assertTrue(WorkspaceSyncRules.shouldReloadWorkspaceForTable("notifications", "driver"))

        // Customer still reloads tracking_logs for live pin updates
        assertTrue(WorkspaceSyncRules.shouldReloadWorkspaceForTable("tracking_logs", "customer"))
        assertTrue(WorkspaceSyncRules.shouldReloadWorkspaceForTable("tracking_logs", "staff"))
    }

    @Test
    fun activeTrackingDeliveryIdDetectsActiveDriverDelivery() {
        val nonDriverData = JsonObject(
            mapOf(
                "profile" to JsonObject(mapOf("role" to JsonPrimitive("customer"))),
                "deliveries" to JsonArray(
                    listOf(
                        JsonObject(
                            mapOf(
                                "id" to JsonPrimitive("del-1"),
                                "status" to JsonPrimitive("in_transit"),
                                "route_started_at" to JsonPrimitive("2026-11-08T00:00:00Z")
                            )
                        )
                    )
                )
            )
        )
        assertNull(WorkspaceSyncRules.activeTrackingDeliveryId(nonDriverData))

        val driverDataNoStartedRoute = JsonObject(
            mapOf(
                "profile" to JsonObject(mapOf("role" to JsonPrimitive("driver"))),
                "deliveries" to JsonArray(
                    listOf(
                        JsonObject(
                            mapOf(
                                "id" to JsonPrimitive("del-2"),
                                "status" to JsonPrimitive("assigned"),
                                "route_started_at" to JsonPrimitive("")
                            )
                        )
                    )
                )
            )
        )
        assertNull(WorkspaceSyncRules.activeTrackingDeliveryId(driverDataNoStartedRoute))

        val driverDataActive = JsonObject(
            mapOf(
                "profile" to JsonObject(mapOf("role" to JsonPrimitive("driver"))),
                "deliveries" to JsonArray(
                    listOf(
                        JsonObject(
                            mapOf(
                                "id" to JsonPrimitive("del-active"),
                                "status" to JsonPrimitive("in_transit"),
                                "route_started_at" to JsonPrimitive("2026-11-08T01:00:00Z")
                            )
                        )
                    )
                )
            )
        )
        assertEquals("del-active", WorkspaceSyncRules.activeTrackingDeliveryId(driverDataActive))
    }
}
