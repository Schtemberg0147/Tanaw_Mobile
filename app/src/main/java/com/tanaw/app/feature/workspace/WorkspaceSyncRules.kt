package com.tanaw.app.feature.workspace

import kotlinx.serialization.json.JsonObject

object WorkspaceSyncRules {

    fun shouldQueueBackgroundRefresh(isBusy: Boolean): Boolean = isBusy

    fun shouldDrainPendingRefresh(hasPendingRefresh: Boolean, isBusy: Boolean): Boolean =
        hasPendingRefresh && !isBusy

    fun resolveShipmentToRefresh(
        currentDetailsShipmentId: String?,
        deliveryId: String?,
        data: JsonObject?
    ): String? {
        val current = currentDetailsShipmentId?.takeIf { it.isNotBlank() }
        if (current != null) return current

        val targetDelivery = deliveryId?.takeIf { it.isNotBlank() } ?: return null
        val deliveries = data?.rows("deliveries") ?: return null
        return deliveries.firstOrNull { it.value("id") == targetDelivery }
            ?.value("shipment_id")
            ?.takeIf { it.isNotBlank() }
    }

    fun shouldRefreshDetails(targetShipmentId: String?, currentDetailsShipmentId: String?): Boolean {
        if (targetShipmentId.isNullOrBlank() || currentDetailsShipmentId.isNullOrBlank()) return false
        return targetShipmentId == currentDetailsShipmentId
    }

    fun shouldReloadWorkspaceForTable(table: String, role: String): Boolean {
        if (role == "driver" && table == "tracking_logs") {
            return false
        }
        return true
    }

    fun activeTrackingDeliveryId(data: JsonObject?): String? {
        if (data == null) return null
        val role = data.obj("profile").value("role")
        if (role != "driver") return null
        val activeStatuses = setOf("assigned", "loading", "in_transit")
        return data.rows("deliveries")
            .firstOrNull {
                it.value("status") in activeStatuses && it.value("route_started_at").isNotBlank()
            }
            ?.value("id")
            ?.takeIf { it.isNotBlank() }
    }
}
