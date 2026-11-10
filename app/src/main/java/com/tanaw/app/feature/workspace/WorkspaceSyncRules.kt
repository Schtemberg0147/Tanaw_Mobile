package com.tanaw.app.feature.workspace

import kotlinx.serialization.json.JsonObject

object WorkspaceSyncRules {

    fun shouldQueueBackgroundRefresh(isBusy: Boolean, page: String = "workspace"): Boolean =
        isBusy && page == "workspace"

    fun shouldDrainPendingRefresh(hasPendingRefresh: Boolean, isBusy: Boolean, page: String = "workspace"): Boolean =
        hasPendingRefresh && !isBusy && page == "workspace"

    fun resolveShipmentToRefresh(
        currentDetailsShipmentId: String?,
        deliveryId: String?,
        data: JsonObject?
    ): String? {
        val targetDelivery = deliveryId?.takeIf { it.isNotBlank() }
        if (targetDelivery != null && data != null) {
            val deliveryRows = DriverUiRules.deliveryRows(data)
            val matchedShipment = deliveryRows.firstOrNull { it.value("id") == targetDelivery }
                ?.value("shipment_id")
                ?.takeIf { it.isNotBlank() }
            if (matchedShipment != null) return matchedShipment
        }

        return currentDetailsShipmentId?.takeIf { it.isNotBlank() }
    }

    fun shouldRefreshDetails(targetShipmentId: String?, currentDetailsShipmentId: String?): Boolean {
        if (targetShipmentId.isNullOrBlank()) return false
        if (currentDetailsShipmentId.isNullOrBlank()) return true
        return targetShipmentId == currentDetailsShipmentId
    }

    fun shouldReloadWorkspaceForTable(table: String, role: String): Boolean {
        if ((role == "driver" || role.isBlank()) && table in setOf("tracking_logs", "delivery_messages")) {
            return false
        }
        return true
    }

    fun shouldReloadWorkspaceForTables(tables: Set<String>, role: String): Boolean {
        if (tables.isEmpty()) return false
        return tables.any { shouldReloadWorkspaceForTable(it, role) }
    }

    fun shouldRefreshConversationUnreadForTables(tables: Set<String>, role: String): Boolean {
        if (role != "driver" || "delivery_messages" !in tables) return false
        return !shouldReloadWorkspaceForTables(tables, role)
    }

    fun shouldRefreshDetailsForTable(table: String, currentDetailsShipmentId: String?): Boolean {
        if (currentDetailsShipmentId.isNullOrBlank()) return false
        return table in setOf(
            "tracking_logs",
            "deliveries",
            "shipments",
            "shipment_status_history",
            "delivery_expenses",
            "delivery_incidents"
        )
    }

    fun shouldExecuteDetailOnlyRefresh(
        page: String,
        isBusy: Boolean,
        isOffline: Boolean,
        isDetailLoading: Boolean,
        currentDetailsShipmentId: String?
    ): Boolean {
        if (page != "workspace") return false
        if (isBusy || isOffline || isDetailLoading) return false
        return !currentDetailsShipmentId.isNullOrBlank()
    }

    fun shouldThrottleTrackingLogsDetailRefresh(
        lastDetailRefreshMs: Long,
        nowMs: Long,
        minIntervalMs: Long = 15_000L
    ): Boolean {
        if (lastDetailRefreshMs <= 0L) return false
        if (nowMs < lastDetailRefreshMs) return false
        return (nowMs - lastDetailRefreshMs) < minIntervalMs
    }

    data class RealtimeBatchDecision(
        val reloadWorkspace: Boolean,
        val refreshDetailsOnly: Boolean,
        val refreshConversationUnreadOnly: Boolean
    )

    fun evaluateRealtimeBatch(
        tables: Set<String>,
        role: String,
        page: String = "workspace",
        isBusy: Boolean = false,
        isOffline: Boolean = false,
        isDetailLoading: Boolean = false,
        currentDetailsShipmentId: String? = null,
        lastTrackingDetailRefreshMs: Long = 0L,
        nowMs: Long = 0L,
        minIntervalMs: Long = 15_000L
    ): RealtimeBatchDecision {
        if (tables.isEmpty() || page != "workspace") {
            return RealtimeBatchDecision(
                reloadWorkspace = false,
                refreshDetailsOnly = false,
                refreshConversationUnreadOnly = false
            )
        }
        val reload = shouldReloadWorkspaceForTables(tables, role)
        if (reload) {
            return RealtimeBatchDecision(
                reloadWorkspace = true,
                refreshDetailsOnly = false,
                refreshConversationUnreadOnly = false
            )
        }
        val refreshUnread = shouldRefreshConversationUnreadForTables(tables, role) && !isOffline
        val refreshDetails = tables.contains("tracking_logs") &&
            shouldRefreshDetailsForTable("tracking_logs", currentDetailsShipmentId) &&
            shouldExecuteDetailOnlyRefresh(page, isBusy, isOffline, isDetailLoading, currentDetailsShipmentId) &&
            !shouldThrottleTrackingLogsDetailRefresh(lastTrackingDetailRefreshMs, nowMs, minIntervalMs)
        return RealtimeBatchDecision(
            reloadWorkspace = false,
            refreshDetailsOnly = refreshDetails,
            refreshConversationUnreadOnly = refreshUnread
        )
    }

    fun shouldStartLive(
        isLiveActive: Boolean,
        currentSubscribedUid: String?,
        targetUid: String?
    ): Boolean {
        if (targetUid.isNullOrBlank()) return false
        if (!isLiveActive) return true
        return currentSubscribedUid != targetUid
    }

    fun activeTrackingDeliveryId(data: JsonObject?): String? {
        if (data == null) return null
        val role = data.obj("profile").value("role")
        if (role != "driver") return null
        val activeStatuses = setOf("assigned", "loading", "in_transit")
        return DriverUiRules.deliveryRows(data)
            .firstOrNull {
                it.value("status") in activeStatuses && it.value("route_started_at").isNotBlank()
            }
            ?.value("id")
            ?.takeIf { it.isNotBlank() }
    }

    fun shouldStopTrackingOnUserTransition(
        previousLoadedUser: String?,
        newAuthenticatedUser: String?
    ): Boolean {
        if (newAuthenticatedUser.isNullOrBlank()) return true
        if (previousLoadedUser == null) return false
        return previousLoadedUser != newAuthenticatedUser
    }

    fun shouldAutoResumeTracking(
        activeDeliveryId: String?,
        currentTrackingDeliveryId: String?,
        hasFineLocationPermission: Boolean
    ): Boolean {
        if (activeDeliveryId.isNullOrBlank() || !hasFineLocationPermission) return false
        return currentTrackingDeliveryId != activeDeliveryId
    }
}
