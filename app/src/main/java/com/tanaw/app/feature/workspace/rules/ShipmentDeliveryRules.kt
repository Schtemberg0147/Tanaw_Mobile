package com.tanaw.app.feature.workspace.rules

import com.tanaw.app.feature.workspace.viewmodel.value
import kotlinx.serialization.json.JsonObject

internal object ShipmentDeliveryRules {
    private val ACTIVE_DELIVERY_STATUSES = setOf("assigned", "loading", "in_transit")

    fun displayDeliveries(shipment: JsonObject, allDeliveries: List<JsonObject>): List<JsonObject> {
        val shipmentId = shipment.value("id").trim()
        val matching = if (shipmentId.isNotBlank()) {
            allDeliveries.filter { it.value("shipment_id").trim() == shipmentId }
        } else {
            allDeliveries
        }
        if (matching.isEmpty()) return emptyList()

        val nonCancelled = matching.filterNot {
            it.value("status").trim().equals("cancelled", ignoreCase = true)
        }
        if (nonCancelled.isNotEmpty()) {
            return nonCancelled
        }

        return if (shipment.value("status").trim().equals("cancelled", ignoreCase = true)) {
            listOfNotNull(latestByAssignedAt(matching))
        } else {
            emptyList()
        }
    }

    fun primaryDelivery(displayDeliveries: List<JsonObject>): JsonObject? {
        if (displayDeliveries.isEmpty()) return null
        val active = displayDeliveries.filter {
            it.value("status").trim().lowercase() in ACTIVE_DELIVERY_STATUSES
        }
        if (active.isNotEmpty()) {
            return latestByAssignedAt(active)
        }
        return latestByAssignedAt(displayDeliveries)
    }

    private fun latestByAssignedAt(deliveries: List<JsonObject>): JsonObject? =
        deliveries.maxWithOrNull { a, b ->
            val aInstant = MobilePresentation.parseInstant(a.value("assigned_at"))
            val bInstant = MobilePresentation.parseInstant(b.value("assigned_at"))
            when {
                aInstant != null && bInstant != null -> aInstant.compareTo(bInstant)
                aInstant != null -> 1
                bInstant != null -> -1
                else -> a.value("assigned_at").compareTo(b.value("assigned_at"))
            }
        }
}
