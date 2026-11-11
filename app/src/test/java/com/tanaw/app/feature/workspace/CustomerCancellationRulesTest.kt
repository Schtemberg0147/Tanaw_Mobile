package com.tanaw.app.feature.workspace

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomerCancellationRulesTest {

    @Test
    fun pendingAndBookedShipmentsWithoutDeliveriesAreCancellable() {
        val pendingShipment = buildJsonObject {
            put("id", "ship-1")
            put("status", "pending")
        }
        val bookedShipment = buildJsonObject {
            put("id", "ship-2")
            put("status", "booked")
        }

        assertTrue(CustomerCancellationRules.canCancelShipment(pendingShipment, emptyList()))
        assertTrue(CustomerCancellationRules.canCancelShipment(bookedShipment, emptyList()))
        assertNull(CustomerCancellationRules.ineligibilityReason(pendingShipment, emptyList()))
        assertNull(CustomerCancellationRules.ineligibilityReason(bookedShipment, emptyList()))
    }

    @Test
    fun assignedShipmentWithAssignedDeliveryBeforeRouteStartIsCancellable() {
        val assignedShipment = buildJsonObject {
            put("id", "ship-100")
            put("status", "assigned")
        }
        val deliveryNotStarted = buildJsonObject {
            put("id", "del-1")
            put("shipment_id", "ship-100")
            put("status", "assigned")
            put("route_started_at", "")
            put("pickup_arrived_at", "")
            put("customer_showed_at", "")
            put("departed_at", "")
            put("arrived_at", "")
        }

        assertTrue(CustomerCancellationRules.canCancelShipment(assignedShipment, listOf(deliveryNotStarted)))
        assertNull(CustomerCancellationRules.ineligibilityReason(assignedShipment, listOf(deliveryNotStarted)))
    }

    @Test
    fun assignedShipmentWhenDriverStartsRouteIsNotCancellable() {
        val assignedShipment = buildJsonObject {
            put("id", "ship-100")
            put("status", "assigned")
        }
        val deliveryEnRoute = buildJsonObject {
            put("id", "del-1")
            put("shipment_id", "ship-100")
            put("status", "assigned")
            put("route_started_at", "2026-11-11T12:00:00Z")
        }

        assertFalse(CustomerCancellationRules.canCancelShipment(assignedShipment, listOf(deliveryEnRoute)))
        assertEquals(
            CustomerCancellationRules.MSG_DRIVER_EN_ROUTE_LOCK,
            CustomerCancellationRules.ineligibilityReason(assignedShipment, listOf(deliveryEnRoute))
        )
    }

    @Test
    fun assignedShipmentWhenDriverArrivedAtPickupOrDepartedIsNotCancellable() {
        val assignedShipment = buildJsonObject {
            put("id", "ship-100")
            put("status", "assigned")
        }
        val deliveryArrivedAtPickup = buildJsonObject {
            put("id", "del-1")
            put("shipment_id", "ship-100")
            put("status", "assigned")
            put("route_started_at", "")
            put("pickup_arrived_at", "2026-11-11T12:15:00Z")
        }
        val deliveryInTransit = buildJsonObject {
            put("id", "del-2")
            put("shipment_id", "ship-100")
            put("status", "in_transit")
        }

        assertFalse(CustomerCancellationRules.canCancelShipment(assignedShipment, listOf(deliveryArrivedAtPickup)))
        assertFalse(CustomerCancellationRules.canCancelShipment(assignedShipment, listOf(deliveryInTransit)))
    }

    @Test
    fun assignedShipmentIgnoresCancelledPreviousDeliveries() {
        val assignedShipment = buildJsonObject {
            put("id", "ship-100")
            put("status", "assigned")
        }
        val previousCancelledDelivery = buildJsonObject {
            put("id", "del-old")
            put("shipment_id", "ship-100")
            put("status", "cancelled")
            put("route_started_at", "2026-11-10T09:00:00Z")
        }
        val currentAssignedDelivery = buildJsonObject {
            put("id", "del-new")
            put("shipment_id", "ship-100")
            put("status", "assigned")
            put("route_started_at", "")
        }

        assertTrue(
            CustomerCancellationRules.canCancelShipment(
                assignedShipment,
                listOf(previousCancelledDelivery, currentAssignedDelivery)
            )
        )
    }

    @Test
    fun terminalShipmentsAreNotCancellableAndReturnNoLockBanner() {
        val completedShipment = buildJsonObject {
            put("id", "ship-completed")
            put("status", "completed")
        }
        val cancelledShipment = buildJsonObject {
            put("id", "ship-cancelled")
            put("status", "cancelled")
        }
        val reviewShipment = buildJsonObject {
            put("id", "ship-review")
            put("status", "review")
        }

        assertFalse(CustomerCancellationRules.canCancelShipment(completedShipment, emptyList()))
        assertFalse(CustomerCancellationRules.canCancelShipment(cancelledShipment, emptyList()))
        assertFalse(CustomerCancellationRules.canCancelShipment(reviewShipment, emptyList()))

        assertNull(CustomerCancellationRules.ineligibilityReason(completedShipment, emptyList()))
        assertNull(CustomerCancellationRules.ineligibilityReason(cancelledShipment, emptyList()))
        assertNull(CustomerCancellationRules.ineligibilityReason(reviewShipment, emptyList()))
    }

    @Test
    fun reasonValidationEnforcesLengthBounds() {
        assertFalse(CustomerCancellationRules.validateReason(""))
        assertFalse(CustomerCancellationRules.validateReason("   "))
        assertFalse(CustomerCancellationRules.validateReason("no"))
        assertTrue(CustomerCancellationRules.validateReason("yes"))
        assertTrue(CustomerCancellationRules.validateReason("Change of schedule"))
        assertTrue(CustomerCancellationRules.validateReason("a".repeat(500)))
        assertFalse(CustomerCancellationRules.validateReason("a".repeat(501)))
    }

    @Test
    fun optimisticCancelUpdateModifiesWorkspaceAndCustomerShipments() {
        val initialShipment = buildJsonObject {
            put("id", "ship-123")
            put("status", "assigned")
            put("tracking_number", "TNW-123")
        }
        val initialDelivery = buildJsonObject {
            put("id", "del-1")
            put("shipment_id", "ship-123")
            put("status", "assigned")
        }
        val workspaceData = buildJsonObject {
            put("shipments", JsonArray(listOf(initialShipment)))
            put("deliveries", JsonArray(listOf(initialDelivery)))
        }
        val customerShipments = listOf(initialShipment)

        val (updatedData, updatedCustomerShipments) = CustomerCancellationRules.optimisticCancelUpdate(
            data = workspaceData,
            customerShipments = customerShipments,
            shipmentId = "ship-123",
            reason = "Booked by mistake",
            nowIso = "2026-11-11T12:00:00Z"
        )

        val updatedShipment = updatedData?.rows("shipments")?.first()!!
        assertEquals("cancelled", updatedShipment.value("status"))
        assertEquals("Booked by mistake", updatedShipment.value("cancelled_reason"))
        assertEquals("2026-11-11T12:00:00Z", updatedShipment.value("cancelled_at"))

        val updatedDelivery = updatedData.rows("deliveries").first()
        assertEquals("cancelled", updatedDelivery.value("status"))

        val updatedPaginatedShipment = updatedCustomerShipments?.first()!!
        assertEquals("cancelled", updatedPaginatedShipment.value("status"))
        assertEquals("Booked by mistake", updatedPaginatedShipment.value("cancelled_reason"))
    }

    @Test
    fun deliveredShipmentIsRecognizedAsTerminalAndNotCancellable() {
        val deliveredShipment = buildJsonObject {
            put("id", "ship-delivered")
            put("status", "delivered")
        }
        assertFalse(CustomerCancellationRules.canCancelShipment(deliveredShipment, emptyList()))
        assertNull(CustomerCancellationRules.ineligibilityReason(deliveredShipment, emptyList()))
        assertTrue(CustomerCancellationRules.TERMINAL_OR_REVIEW_SHIPMENT_STATUSES.contains("delivered"))
    }

    @Test
    fun deliveriesForOtherShipmentsDoNotBlockCancellation() {
        val targetShipment = buildJsonObject {
            put("id", "ship-target")
            put("status", "assigned")
        }
        val targetDelivery = buildJsonObject {
            put("id", "del-target")
            put("shipment_id", "ship-target")
            put("status", "assigned")
            put("route_started_at", "")
        }
        val otherActiveDelivery = buildJsonObject {
            put("id", "del-other")
            put("shipment_id", "ship-other")
            put("status", "in_transit")
            put("route_started_at", "2026-11-11T12:00:00Z")
        }

        assertTrue(
            CustomerCancellationRules.canCancelShipment(
                targetShipment,
                listOf(targetDelivery, otherActiveDelivery)
            )
        )
        assertNull(
            CustomerCancellationRules.ineligibilityReason(
                targetShipment,
                listOf(targetDelivery, otherActiveDelivery)
            )
        )
    }

    @Test
    fun deliveryWithBlankShipmentIdDoesNotMatchTargetShipment() {
        val targetShipment = buildJsonObject {
            put("id", "ship-target")
            put("status", "assigned")
        }
        val unlinkedActiveDelivery = buildJsonObject {
            put("id", "del-unlinked")
            put("shipment_id", "")
            put("status", "in_transit")
            put("route_started_at", "2026-11-11T12:00:00Z")
        }

        assertTrue(
            CustomerCancellationRules.canCancelShipment(
                targetShipment,
                listOf(unlinkedActiveDelivery)
            )
        )
    }

    @Test
    fun checkCancellationEligibilityDifferentiatesStatusFromDriverMilestones() {
        val inTransitShipment = buildJsonObject {
            put("id", "ship-1")
            put("status", "in_transit")
        }
        val assignedShipment = buildJsonObject {
            put("id", "ship-2")
            put("status", "assigned")
        }
        val deliveryEnRoute = buildJsonObject {
            put("id", "del-1")
            put("shipment_id", "ship-2")
            put("status", "assigned")
            put("route_started_at", "2026-11-11T12:00:00Z")
        }
        val deliveryNotStarted = buildJsonObject {
            put("id", "del-2")
            put("shipment_id", "ship-2")
            put("status", "assigned")
            put("route_started_at", "")
        }

        val statusResult = CustomerCancellationRules.checkCancellationEligibility(inTransitShipment, emptyList())
        assertTrue(statusResult is CancellationEligibility.IneligibleStatus)
        assertEquals("in_transit", (statusResult as CancellationEligibility.IneligibleStatus).status)

        val driverResult = CustomerCancellationRules.checkCancellationEligibility(assignedShipment, listOf(deliveryEnRoute))
        assertEquals(CancellationEligibility.DriverEnRoute, driverResult)

        val eligibleResult = CustomerCancellationRules.checkCancellationEligibility(assignedShipment, listOf(deliveryNotStarted))
        assertEquals(CancellationEligibility.Eligible, eligibleResult)
    }

    @Test
    fun whitespaceOnlyReasonRejectedRegardlessOfLength() {
        assertFalse(CustomerCancellationRules.validateReason("   "))
        assertFalse(CustomerCancellationRules.validateReason("\n\t  "))
        assertFalse(CustomerCancellationRules.validateReason("   \n\r\t   "))
        assertFalse(CustomerCancellationRules.validateReason(" ".repeat(10)))
        assertTrue(CustomerCancellationRules.validateReason("   abc   "))
    }

    @Test
    fun optimisticCancelPreservesChronologicalOrderForFallbackShipment() {
        val existingNewer = buildJsonObject {
            put("id", "ship-newer")
            put("status", "pending")
            put("created_at", "2026-11-11T15:00:00Z")
        }
        val existingOlder = buildJsonObject {
            put("id", "ship-older")
            put("status", "pending")
            put("created_at", "2026-11-11T10:00:00Z")
        }
        val workspaceData = buildJsonObject {
            put("shipments", JsonArray(listOf(existingNewer, existingOlder)))
            put("deliveries", JsonArray(emptyList()))
        }
        val fallbackMiddle = buildJsonObject {
            put("id", "ship-middle")
            put("status", "assigned")
            put("created_at", "2026-11-11T12:00:00Z")
        }

        val (updatedData, _) = CustomerCancellationRules.optimisticCancelUpdate(
            data = workspaceData,
            customerShipments = null,
            shipmentId = "ship-middle",
            reason = "Booked by mistake",
            nowIso = "2026-11-11T12:30:00Z",
            fallbackShipment = fallbackMiddle
        )

        val resultShipmentIds = updatedData?.rows("shipments")?.map { it.value("id") }
        assertEquals(listOf("ship-newer", "ship-middle", "ship-older"), resultShipmentIds)
    }
}
