package com.tanaw.app.feature.workspace

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Result of checking whether a customer can cancel a shipment.
 */
internal sealed interface CancellationEligibility {
    data object Eligible : CancellationEligibility
    data object DriverEnRoute : CancellationEligibility
    data class IneligibleStatus(val status: String) : CancellationEligibility
}

/**
 * Pure-Kotlin business rules for customer self-service shipment cancellation (Backlog C10).
 *
 * Eligibility rule:
 * A customer may cancel their shipment as long as:
 * 1. The shipment status is pre-transit (`pending`, `booked`, or `assigned`), AND
 * 2. No active (non-cancelled) delivery linked to the shipment has started its route
 *    (`route_started_at`, `pickup_arrived_at`, `customer_showed_at`, `departed_at`,
 *    and `arrived_at` are all blank, and delivery status is not `loading`, `in_transit`,
 *    or `delivered`).
 */
internal object CustomerCancellationRules {
    val CANCELLABLE_SHIPMENT_STATUSES = setOf("pending", "booked", "assigned")
    private val ACTIVE_EN_ROUTE_DELIVERY_STATUSES = setOf("loading", "in_transit", "delivered")
    val TERMINAL_OR_REVIEW_SHIPMENT_STATUSES = setOf("review", "completed", "cancelled", "delivered")

    const val MIN_REASON_LENGTH = 3
    const val MAX_REASON_LENGTH = 500

    const val MSG_SHIPMENT_CANCELLED = "Shipment cancelled."
    const val MSG_SHIPMENT_CANCELLED_REFRESH_FAILED = "Shipment cancelled. Refresh to sync latest office records."
    const val MSG_DRIVER_EN_ROUTE_LOCK =
        "Cancellation is unavailable because the driver is already on the way. Contact the office if you need assistance."

    val PRESET_REASONS = listOf(
        "Change of schedule",
        "Booked by mistake",
        "Cargo not ready",
        "Found alternative transport",
        "Incorrect pickup or drop-off details"
    )

    /**
     * Returns true if a non-cancelled delivery has already started heading to pickup
     * or progressed into loading/transit/delivered.
     */
    fun isDeliveryEnRouteOrStarted(delivery: JsonObject): Boolean {
        val status = delivery.value("status").trim().lowercase()
        if (status == "cancelled") return false
        if (status in ACTIVE_EN_ROUTE_DELIVERY_STATUSES) return true
        return delivery.value("route_started_at").isNotBlank() ||
            delivery.value("pickup_arrived_at").isNotBlank() ||
            delivery.value("customer_showed_at").isNotBlank() ||
            delivery.value("departed_at").isNotBlank() ||
            delivery.value("arrived_at").isNotBlank()
    }

    /**
     * Returns true if none of the provided deliveries have started their route.
     */
    fun areDeliveriesCancellable(deliveries: List<JsonObject>): Boolean =
        deliveries.none { isDeliveryEnRouteOrStarted(it) }

    /**
     * Checks if a raw status string represents a cancellable shipment status.
     */
    fun isStatusCancellable(status: String): Boolean =
        status.trim().lowercase() in CANCELLABLE_SHIPMENT_STATUSES

    /**
     * Checks cancellation eligibility, returning a discriminated result to distinguish
     * between ineligible status (e.g. terminal or en-route status) and driver milestone locks.
     */
    fun checkCancellationEligibility(shipment: JsonObject, deliveries: List<JsonObject>): CancellationEligibility {
        val status = shipment.value("status").trim().lowercase()
        if (!isStatusCancellable(status)) {
            return CancellationEligibility.IneligibleStatus(status)
        }
        val shipmentId = shipment.value("id").trim()
        val relevant = if (shipmentId.isNotBlank()) {
            deliveries.filter { it.value("shipment_id").trim() == shipmentId }
        } else {
            deliveries
        }

        return if (areDeliveriesCancellable(relevant)) {
            CancellationEligibility.Eligible
        } else {
            CancellationEligibility.DriverEnRoute
        }
    }

    /**
     * Evaluates whether a shipment can be cancelled by the customer.
     */
    fun canCancelShipment(shipment: JsonObject, deliveries: List<JsonObject>): Boolean =
        checkCancellationEligibility(shipment, deliveries) is CancellationEligibility.Eligible

    /**
     * Returns an explanatory message when an active shipment cannot be cancelled because
     * the driver is already on the way or the trip is underway. Returns null if the shipment
     * is eligible for cancellation or is already in a terminal/review state.
     */
    fun ineligibilityReason(shipment: JsonObject, deliveries: List<JsonObject>): String? {
        val status = shipment.value("status").trim().lowercase()
        if (status.isBlank() || status in TERMINAL_OR_REVIEW_SHIPMENT_STATUSES) return null
        if (canCancelShipment(shipment, deliveries)) return null
        return MSG_DRIVER_EN_ROUTE_LOCK
    }

    /**
     * Validates the customer-provided cancellation reason against the database check
     * constraint (`cancelled_reason IS NOT NULL` and non-blank within bounds).
     */
    fun validateReason(reason: String): Boolean {
        val trimmed = reason.trim()
        return trimmed.length in MIN_REASON_LENGTH..MAX_REASON_LENGTH
    }

    fun normalizeReason(reason: String): String = reason.trim()

    /**
     * Applies an optimistic cancellation update to workspace `data` and `customerShipments`
     * so the UI reflects the cancelled status and reason immediately.
     */
    fun optimisticCancelUpdate(
        data: JsonObject?,
        customerShipments: List<JsonObject>?,
        shipmentId: String,
        reason: String,
        nowIso: String,
        fallbackShipment: JsonObject? = null
    ): Pair<JsonObject?, List<JsonObject>?> {
        val trimmedReason = normalizeReason(reason)

        fun markShipmentCancelled(row: JsonObject): JsonObject = JsonObject(
            row + mapOf(
                "status" to JsonPrimitive("cancelled"),
                "cancelled_reason" to JsonPrimitive(trimmedReason),
                "cancelled_at" to JsonPrimitive(nowIso),
                "updated_at" to JsonPrimitive(nowIso)
            )
        )

        val updatedData = data?.let { currentData ->
            val updates = mutableMapOf<String, JsonElement>()
            val existingShipments = currentData.rows("shipments")
            var foundInData = false
            val updatedShipments = existingShipments.map { row ->
                if (row.value("id") == shipmentId) {
                    foundInData = true
                    markShipmentCancelled(row)
                } else {
                    row
                }
            }.let { list ->
                if (!foundInData && fallbackShipment != null && fallbackShipment.value("id") == shipmentId) {
                    (list + markShipmentCancelled(fallbackShipment))
                        .sortedWith { a, b ->
                            CustomerPaginationRules.compareTimestampsDesc(
                                a.value("created_at"),
                                b.value("created_at")
                            )
                        }
                } else {
                    list
                }
            }
            updates["shipments"] = JsonArray(updatedShipments)

            val existingDeliveries = currentData.rows("deliveries")
            if (existingDeliveries.isNotEmpty()) {
                val updatedDeliveries = existingDeliveries.map { d ->
                    if (d.value("shipment_id") == shipmentId && d.value("status").trim().equals("assigned", ignoreCase = true)) {
                        JsonObject(
                            d + mapOf(
                                "status" to JsonPrimitive("cancelled"),
                                "updated_at" to JsonPrimitive(nowIso)
                            )
                        )
                    } else {
                        d
                    }
                }
                updates["deliveries"] = JsonArray(updatedDeliveries)
            }
            JsonObject(currentData + updates)
        }

        val updatedCustomerShipments = customerShipments?.map { row ->
            if (row.value("id") == shipmentId) {
                markShipmentCancelled(row)
            } else {
                row
            }
        }

        return updatedData to updatedCustomerShipments
    }
}
