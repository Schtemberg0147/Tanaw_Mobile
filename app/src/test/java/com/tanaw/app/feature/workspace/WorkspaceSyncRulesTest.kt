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
        assertFalse(WorkspaceSyncRules.shouldQueueBackgroundRefresh(isBusy = true, page = "login"))
        assertFalse(WorkspaceSyncRules.shouldQueueBackgroundRefresh(isBusy = true, page = "verify_phone"))
        assertTrue(WorkspaceSyncRules.shouldQueueBackgroundRefresh(isBusy = true, page = "workspace"))
    }

    @Test
    fun shouldDrainPendingRefreshOnlyWhenPendingAndNotBusy() {
        assertTrue(WorkspaceSyncRules.shouldDrainPendingRefresh(hasPendingRefresh = true, isBusy = false))
        assertFalse(WorkspaceSyncRules.shouldDrainPendingRefresh(hasPendingRefresh = true, isBusy = true))
        assertFalse(WorkspaceSyncRules.shouldDrainPendingRefresh(hasPendingRefresh = false, isBusy = false))
        assertFalse(WorkspaceSyncRules.shouldDrainPendingRefresh(hasPendingRefresh = false, isBusy = true))
        assertFalse(WorkspaceSyncRules.shouldDrainPendingRefresh(hasPendingRefresh = true, isBusy = false, page = "login"))
        assertTrue(WorkspaceSyncRules.shouldDrainPendingRefresh(hasPendingRefresh = true, isBusy = false, page = "workspace"))
    }

    @Test
    fun resolveShipmentToRefreshPrefersCurrentOpenDetailsWhenDeliveryNotFound() {
        val resolved = WorkspaceSyncRules.resolveShipmentToRefresh(
            currentDetailsShipmentId = "shipment-open-1",
            deliveryId = "del-99",
            data = null,
        )
        assertEquals("shipment-open-1", resolved)
    }

    @Test
    fun resolveShipmentToRefreshPrefersTargetDeliveryOverStaleDetails() {
        val data = JsonObject(
            mapOf(
                "deliveries" to JsonArray(
                    listOf(
                        JsonObject(
                            mapOf(
                                "id" to JsonPrimitive("del-active"),
                                "shipment_id" to JsonPrimitive("ship-new")
                            )
                        )
                    )
                )
            )
        )
        val resolved = WorkspaceSyncRules.resolveShipmentToRefresh(
            currentDetailsShipmentId = "ship-stale-old",
            deliveryId = "del-active",
            data = data
        )
        assertEquals("ship-new", resolved)
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
    fun resolveShipmentToRefreshResolvesFromDriverDeliveriesWhenAvailable() {
        val data = JsonObject(
            mapOf(
                "driver_deliveries" to JsonArray(
                    listOf(
                        JsonObject(
                            mapOf(
                                "id" to JsonPrimitive("del-driver-1"),
                                "shipment_id" to JsonPrimitive("shipment-driver-1")
                            )
                        )
                    )
                ),
                "deliveries" to JsonArray(emptyList())
            )
        )
        val resolved = WorkspaceSyncRules.resolveShipmentToRefresh(
            currentDetailsShipmentId = null,
            deliveryId = "del-driver-1",
            data = data
        )
        assertEquals("shipment-driver-1", resolved)
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
    fun shouldRefreshDetailsAllowsNullCurrentDetailsToRecoverUnsetDetails() {
        // When currentDetails is null or blank, refreshing the resolved target shipment is allowed
        assertTrue(
            WorkspaceSyncRules.shouldRefreshDetails(
                targetShipmentId = "ship-target",
                currentDetailsShipmentId = null
            )
        )
        assertTrue(
            WorkspaceSyncRules.shouldRefreshDetails(
                targetShipmentId = "ship-target",
                currentDetailsShipmentId = ""
            )
        )
        assertTrue(
            WorkspaceSyncRules.shouldRefreshDetails(
                targetShipmentId = "ship-target",
                currentDetailsShipmentId = "ship-target"
            )
        )
        assertFalse(
            WorkspaceSyncRules.shouldRefreshDetails(
                targetShipmentId = "ship-target",
                currentDetailsShipmentId = "ship-other"
            )
        )
        assertFalse(
            WorkspaceSyncRules.shouldRefreshDetails(
                targetShipmentId = null,
                currentDetailsShipmentId = "ship-target"
            )
        )
        assertFalse(
            WorkspaceSyncRules.shouldRefreshDetails(
                targetShipmentId = "",
                currentDetailsShipmentId = "ship-target"
            )
        )
    }

    @Test
    fun shouldReloadWorkspaceForTableFiltersSelfDriverTrackingLogsAndMessages() {
        assertFalse(WorkspaceSyncRules.shouldReloadWorkspaceForTable("tracking_logs", "driver"))
        assertFalse(WorkspaceSyncRules.shouldReloadWorkspaceForTable("delivery_messages", "driver"))
        assertFalse(WorkspaceSyncRules.shouldReloadWorkspaceForTable("tracking_logs", ""))
        assertFalse(WorkspaceSyncRules.shouldReloadWorkspaceForTable("delivery_messages", ""))
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

        val driverDeliveriesActive = JsonObject(
            mapOf(
                "profile" to JsonObject(mapOf("role" to JsonPrimitive("driver"))),
                "driver_deliveries" to JsonArray(
                    listOf(
                        JsonObject(
                            mapOf(
                                "id" to JsonPrimitive("del-driver-section"),
                                "status" to JsonPrimitive("loading"),
                                "route_started_at" to JsonPrimitive("2026-11-08T02:00:00Z")
                            )
                        )
                    )
                ),
                "deliveries" to JsonArray(
                    listOf(
                        JsonObject(
                            mapOf(
                                "id" to JsonPrimitive("del-driver-section"),
                                "status" to JsonPrimitive("loading")
                            )
                        )
                    )
                )
            )
        )
        assertEquals("del-driver-section", WorkspaceSyncRules.activeTrackingDeliveryId(driverDeliveriesActive))
    }

    @Test
    fun shouldReloadWorkspaceForTablesHandlesBurstSets() {
        assertFalse(WorkspaceSyncRules.shouldReloadWorkspaceForTables(emptySet(), "driver"))
        assertFalse(WorkspaceSyncRules.shouldReloadWorkspaceForTables(setOf("tracking_logs"), "driver"))
        assertFalse(WorkspaceSyncRules.shouldReloadWorkspaceForTables(setOf("delivery_messages"), "driver"))
        assertFalse(WorkspaceSyncRules.shouldReloadWorkspaceForTables(setOf("tracking_logs", "delivery_messages"), "driver"))
        assertTrue(WorkspaceSyncRules.shouldReloadWorkspaceForTables(setOf("tracking_logs", "deliveries"), "driver"))
        assertTrue(WorkspaceSyncRules.shouldReloadWorkspaceForTables(setOf("delivery_messages", "notifications"), "driver"))
        assertTrue(WorkspaceSyncRules.shouldReloadWorkspaceForTables(setOf("tracking_logs"), "customer"))
        assertTrue(WorkspaceSyncRules.shouldReloadWorkspaceForTables(setOf("shipments", "notifications"), "driver"))
    }

    @Test
    fun shouldRefreshConversationUnreadForTablesOnlyWhenDriverAndNoFullReload() {
        assertTrue(WorkspaceSyncRules.shouldRefreshConversationUnreadForTables(setOf("delivery_messages"), "driver"))
        assertTrue(WorkspaceSyncRules.shouldRefreshConversationUnreadForTables(setOf("delivery_messages", "tracking_logs"), "driver"))
        assertFalse(WorkspaceSyncRules.shouldRefreshConversationUnreadForTables(setOf("delivery_messages", "deliveries"), "driver"))
        assertFalse(WorkspaceSyncRules.shouldRefreshConversationUnreadForTables(setOf("tracking_logs"), "driver"))
        assertFalse(WorkspaceSyncRules.shouldRefreshConversationUnreadForTables(setOf("delivery_messages"), "customer"))
        assertFalse(WorkspaceSyncRules.shouldRefreshConversationUnreadForTables(setOf("delivery_messages"), ""))
    }

    @Test
    fun shouldRefreshDetailsForTableChecksActiveShipmentAndSupportedTables() {
        assertFalse(WorkspaceSyncRules.shouldRefreshDetailsForTable("tracking_logs", null))
        assertFalse(WorkspaceSyncRules.shouldRefreshDetailsForTable("tracking_logs", ""))
        assertTrue(WorkspaceSyncRules.shouldRefreshDetailsForTable("tracking_logs", "shipment-1"))
        assertTrue(WorkspaceSyncRules.shouldRefreshDetailsForTable("deliveries", "shipment-1"))
        assertTrue(WorkspaceSyncRules.shouldRefreshDetailsForTable("shipments", "shipment-1"))
        assertTrue(WorkspaceSyncRules.shouldRefreshDetailsForTable("shipment_status_history", "shipment-1"))
        assertTrue(WorkspaceSyncRules.shouldRefreshDetailsForTable("delivery_expenses", "shipment-1"))
        assertTrue(WorkspaceSyncRules.shouldRefreshDetailsForTable("delivery_incidents", "shipment-1"))
        assertFalse(WorkspaceSyncRules.shouldRefreshDetailsForTable("drivers", "shipment-1"))
        assertFalse(WorkspaceSyncRules.shouldRefreshDetailsForTable("trucks", "shipment-1"))
    }

    @Test
    fun shouldExecuteDetailOnlyRefreshGuardsAgainstBusyOfflineLoadingAndPage() {
        assertTrue(
            WorkspaceSyncRules.shouldExecuteDetailOnlyRefresh(
                page = "workspace",
                isBusy = false,
                isOffline = false,
                isDetailLoading = false,
                currentDetailsShipmentId = "ship-1"
            )
        )
        assertFalse(
            WorkspaceSyncRules.shouldExecuteDetailOnlyRefresh(
                page = "login",
                isBusy = false,
                isOffline = false,
                isDetailLoading = false,
                currentDetailsShipmentId = "ship-1"
            )
        )
        assertFalse(
            WorkspaceSyncRules.shouldExecuteDetailOnlyRefresh(
                page = "workspace",
                isBusy = true,
                isOffline = false,
                isDetailLoading = false,
                currentDetailsShipmentId = "ship-1"
            )
        )
        assertFalse(
            WorkspaceSyncRules.shouldExecuteDetailOnlyRefresh(
                page = "workspace",
                isBusy = false,
                isOffline = true,
                isDetailLoading = false,
                currentDetailsShipmentId = "ship-1"
            )
        )
        assertFalse(
            WorkspaceSyncRules.shouldExecuteDetailOnlyRefresh(
                page = "workspace",
                isBusy = false,
                isOffline = false,
                isDetailLoading = true,
                currentDetailsShipmentId = "ship-1"
            )
        )
        assertFalse(
            WorkspaceSyncRules.shouldExecuteDetailOnlyRefresh(
                page = "workspace",
                isBusy = false,
                isOffline = false,
                isDetailLoading = false,
                currentDetailsShipmentId = null
            )
        )
        assertFalse(
            WorkspaceSyncRules.shouldExecuteDetailOnlyRefresh(
                page = "workspace",
                isBusy = false,
                isOffline = false,
                isDetailLoading = false,
                currentDetailsShipmentId = "   "
            )
        )
    }

    @Test
    fun shouldThrottleTrackingLogsDetailRefreshEnforcesMinIntervalAndHandlesEdgeCases() {
        val now = 100_000L
        assertTrue(WorkspaceSyncRules.shouldThrottleTrackingLogsDetailRefresh(lastDetailRefreshMs = 95_000L, nowMs = now, minIntervalMs = 15_000L))
        assertFalse(WorkspaceSyncRules.shouldThrottleTrackingLogsDetailRefresh(lastDetailRefreshMs = 85_000L, nowMs = now, minIntervalMs = 15_000L))
        assertFalse(WorkspaceSyncRules.shouldThrottleTrackingLogsDetailRefresh(lastDetailRefreshMs = 85_000L, nowMs = now))

        // Initial 0L timestamp should never throttle even when nowMs < minIntervalMs
        assertFalse(WorkspaceSyncRules.shouldThrottleTrackingLogsDetailRefresh(lastDetailRefreshMs = 0L, nowMs = 5_000L, minIntervalMs = 15_000L))

        // Clock skew (nowMs < lastDetailRefreshMs) should not permanently lock out detail refreshes
        assertFalse(WorkspaceSyncRules.shouldThrottleTrackingLogsDetailRefresh(lastDetailRefreshMs = 120_000L, nowMs = 100_000L, minIntervalMs = 15_000L))
    }

    @Test
    fun evaluateRealtimeBatchRoutesCoalescedEventsCorrectly() {
        // 1. Multi-table milestone burst coalesces into single workspace reload
        val milestoneDecision = WorkspaceSyncRules.evaluateRealtimeBatch(
            tables = setOf("deliveries", "shipments", "shipment_status_history", "notifications"),
            role = "driver",
            currentDetailsShipmentId = "ship-1",
            nowMs = 50_000L
        )
        assertTrue(milestoneDecision.reloadWorkspace)
        assertFalse(milestoneDecision.refreshDetailsOnly)
        assertFalse(milestoneDecision.refreshConversationUnreadOnly)

        // 2. Driver tracking_logs + delivery_messages burst avoids full workspace reload
        val lightweightDriverBurst = WorkspaceSyncRules.evaluateRealtimeBatch(
            tables = setOf("tracking_logs", "delivery_messages"),
            role = "driver",
            currentDetailsShipmentId = "ship-1",
            lastTrackingDetailRefreshMs = 10_000L,
            nowMs = 30_000L
        )
        assertFalse(lightweightDriverBurst.reloadWorkspace)
        assertTrue(lightweightDriverBurst.refreshDetailsOnly)
        assertTrue(lightweightDriverBurst.refreshConversationUnreadOnly)

        // 3. Driver tracking_logs within throttle window skips detail refresh
        val throttledTracking = WorkspaceSyncRules.evaluateRealtimeBatch(
            tables = setOf("tracking_logs"),
            role = "driver",
            currentDetailsShipmentId = "ship-1",
            lastTrackingDetailRefreshMs = 25_000L,
            nowMs = 30_000L
        )
        assertFalse(throttledTracking.reloadWorkspace)
        assertFalse(throttledTracking.refreshDetailsOnly)
        assertFalse(throttledTracking.refreshConversationUnreadOnly)

        // 4. Driver tracking_logs while busy or detailLoading skips detail-only refresh
        val busyTracking = WorkspaceSyncRules.evaluateRealtimeBatch(
            tables = setOf("tracking_logs"),
            role = "driver",
            isBusy = true,
            currentDetailsShipmentId = "ship-1",
            lastTrackingDetailRefreshMs = 0L,
            nowMs = 30_000L
        )
        assertFalse(busyTracking.reloadWorkspace)
        assertFalse(busyTracking.refreshDetailsOnly)

        // 5. Non-workspace page ignores Realtime batch
        val loginPageDecision = WorkspaceSyncRules.evaluateRealtimeBatch(
            tables = setOf("deliveries"),
            role = "driver",
            page = "login"
        )
        assertFalse(loginPageDecision.reloadWorkspace)
        assertFalse(loginPageDecision.refreshDetailsOnly)
        assertFalse(loginPageDecision.refreshConversationUnreadOnly)
    }

    @Test
    fun shouldStartLiveGuardsAgainstRedundantRebuilds() {
        assertFalse(WorkspaceSyncRules.shouldStartLive(isLiveActive = true, currentSubscribedUid = "user-1", targetUid = null))
        assertFalse(WorkspaceSyncRules.shouldStartLive(isLiveActive = true, currentSubscribedUid = "user-1", targetUid = ""))
        assertTrue(WorkspaceSyncRules.shouldStartLive(isLiveActive = false, currentSubscribedUid = null, targetUid = "user-1"))
        assertTrue(WorkspaceSyncRules.shouldStartLive(isLiveActive = false, currentSubscribedUid = "user-1", targetUid = "user-1"))
        assertFalse(WorkspaceSyncRules.shouldStartLive(isLiveActive = true, currentSubscribedUid = "user-1", targetUid = "user-1"))
        assertTrue(WorkspaceSyncRules.shouldStartLive(isLiveActive = true, currentSubscribedUid = "user-1", targetUid = "user-2"))
    }

    @Test
    fun shouldStopTrackingOnUserTransitionPreservesInitialHydration() {
        // Initial hydration (previous loadedUser is null) does NOT stop tracking for that authenticated user
        assertFalse(WorkspaceSyncRules.shouldStopTrackingOnUserTransition(previousLoadedUser = null, newAuthenticatedUser = "driver-1"))

        // Switching from one authenticated user to a different user MUST stop tracking
        assertTrue(WorkspaceSyncRules.shouldStopTrackingOnUserTransition(previousLoadedUser = "driver-1", newAuthenticatedUser = "driver-2"))

        // Same user re-authenticating does NOT stop tracking
        assertFalse(WorkspaceSyncRules.shouldStopTrackingOnUserTransition(previousLoadedUser = "driver-1", newAuthenticatedUser = "driver-1"))

        // Unauthenticated or null user MUST stop tracking
        assertTrue(WorkspaceSyncRules.shouldStopTrackingOnUserTransition(previousLoadedUser = "driver-1", newAuthenticatedUser = null))
        assertTrue(WorkspaceSyncRules.shouldStopTrackingOnUserTransition(previousLoadedUser = "driver-1", newAuthenticatedUser = ""))
    }

    @Test
    fun shouldAutoResumeTrackingRequiresActiveDeliveryAndPermission() {
        assertTrue(
            WorkspaceSyncRules.shouldAutoResumeTracking(
                activeDeliveryId = "del-123",
                currentTrackingDeliveryId = null,
                hasFineLocationPermission = true
            )
        )
        assertTrue(
            WorkspaceSyncRules.shouldAutoResumeTracking(
                activeDeliveryId = "del-123",
                currentTrackingDeliveryId = "del-other",
                hasFineLocationPermission = true
            )
        )
        // Already tracking this delivery: do not re-trigger start
        assertFalse(
            WorkspaceSyncRules.shouldAutoResumeTracking(
                activeDeliveryId = "del-123",
                currentTrackingDeliveryId = "del-123",
                hasFineLocationPermission = true
            )
        )
        // Missing fine location permission
        assertFalse(
            WorkspaceSyncRules.shouldAutoResumeTracking(
                activeDeliveryId = "del-123",
                currentTrackingDeliveryId = null,
                hasFineLocationPermission = false
            )
        )
        // No active delivery
        assertFalse(
            WorkspaceSyncRules.shouldAutoResumeTracking(
                activeDeliveryId = null,
                currentTrackingDeliveryId = null,
                hasFineLocationPermission = true
            )
        )
        assertFalse(
            WorkspaceSyncRules.shouldAutoResumeTracking(
                activeDeliveryId = "",
                currentTrackingDeliveryId = null,
                hasFineLocationPermission = true
            )
        )
    }
}
