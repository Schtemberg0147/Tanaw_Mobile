package com.tanaw.app.feature.workspace.customer.rules

import com.tanaw.app.feature.workspace.rules.MobilePresentation
import com.tanaw.app.feature.workspace.tracking.ShipmentTrackingPresentation
import com.tanaw.app.feature.workspace.viewmodel.value
import kotlinx.serialization.json.JsonObject

data class ShipmentProofDocuments(
    val podDocument: JsonObject? = null,
    val signatureDocument: JsonObject? = null
)

object CustomerHistoryPresentation {

    fun isCompletedStatus(status: String): Boolean {
        return status.trim().lowercase() in setOf("completed", "delivered")
    }

    fun isCancelledStatus(status: String): Boolean {
        return status.trim().lowercase() == "cancelled"
    }

    fun cleanAddress(raw: String): String {
        return ShipmentTrackingPresentation.formatAddressWithoutPlusCode(raw)
            .ifBlank { "Not provided" }
    }

    fun footerTimestamp(
        shipment: JsonObject,
        deliveries: List<JsonObject> = emptyList()
    ): String {
        val status = shipment.value("status").trim().lowercase()
        val shipmentId = shipment.value("id")
        val matchingDelivery = if (shipmentId.isNotBlank()) {
            deliveries.firstOrNull { it.value("shipment_id") == shipmentId }
        } else {
            null
        }

        val (prefix, rawTime) = when {
            isCompletedStatus(status) -> {
                val ts = shipment.value("actual_delivery_at")
                    .ifBlank { matchingDelivery?.value("arrived_at").orEmpty() }
                    .ifBlank { shipment.value("updated_at") }
                    .ifBlank { shipment.value("created_at") }
                "Delivered" to ts
            }
            isCancelledStatus(status) -> {
                val ts = shipment.value("cancelled_at")
                    .ifBlank { shipment.value("updated_at") }
                    .ifBlank { shipment.value("created_at") }
                "Cancelled" to ts
            }
            else -> {
                val ts = shipment.value("created_at")
                "Booked" to ts
            }
        }

        val formatted = MobilePresentation.time(rawTime)
        return if (formatted == "Not recorded") formatted else "$prefix · $formatted"
    }

    fun formattedAmount(shipment: JsonObject): String {
        val status = shipment.value("status").trim().lowercase()
        val raw = shipment.value("final_amount")
            .ifBlank { shipment.value("override_amount") }
            .ifBlank { shipment.value("quoted_amount") }
        return if (raw.toBigDecimalOrNull() != null) {
            MobilePresentation.money(raw)
        } else if (isCancelledStatus(status)) {
            "No quote"
        } else {
            "Awaiting quotation"
        }
    }

    fun resolveProofDocuments(
        shipmentId: String,
        documents: List<JsonObject>
    ): ShipmentProofDocuments {
        if (shipmentId.isBlank() || documents.isEmpty()) return ShipmentProofDocuments()
        val forShipment = documents.filter { it.value("shipment_id") == shipmentId }
        val podDoc = forShipment.firstOrNull {
            it.value("kind").trim().lowercase() == "proof_of_delivery"
        }
        val signatureDoc = forShipment.firstOrNull {
            it.value("kind").trim().lowercase() == "signature"
        }
        return ShipmentProofDocuments(
            podDocument = podDoc,
            signatureDocument = signatureDoc
        )
    }

    fun isRedundantOfflineError(offline: Boolean, error: String?): Boolean {
        if (!offline || error.isNullOrBlank()) return false
        return error.startsWith("Connection issue", ignoreCase = true) ||
            error.contains("network connection", ignoreCase = true) ||
            error.contains("offline", ignoreCase = true)
    }
}
