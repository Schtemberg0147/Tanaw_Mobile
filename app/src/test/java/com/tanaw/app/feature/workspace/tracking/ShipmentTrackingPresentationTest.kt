package com.tanaw.app.feature.workspace.tracking

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ShipmentTrackingPresentationTest {
    private val now = Instant.parse("2026-11-19T02:00:00Z") // Nov 19, 10:00 AM PHT

    @Test
    fun etaPillUsesLiveMinutesAndClockWhenLiveGpsActive() {
        val shipment = buildJsonObject {
            put("status", "in_transit")
            put("estimated_arrival", "2026-11-19T04:00:00Z")
        }
        val delivery = buildJsonObject {
            put("status", "in_transit")
            put("assigned_at", "2026-11-19T01:44:00Z")
        }
        val routing = buildJsonObject {
            put("eta_at", now.plusSeconds(25 * 60).toString()) // 10:25 AM PHT
        }
        val freshness = PositionFreshness(
            status = PositionFreshnessStatus.FRESH,
            ageSeconds = 20L,
            relativeText = "Updated 20s ago",
            accuracyText = "accuracy 8 m"
        )
        val eta = CustomerEtaPresentation(
            scheduledDeparture = "Nov 19, 2026 · 9:00 AM PHT",
            scheduledArrival = "Nov 19, 2026 · 12:00 PM PHT",
            liveEta = "ETA to destination: 10:25 AM PHT",
            liveEtaDetail = "Office-confirmed truck route · live GPS",
            isLive = true,
            freshness = freshness
        )

        val pill = ShipmentTrackingPresentation.etaPillState(shipment, delivery, routing, eta, now)
        assertTrue(pill.isLive)
        assertEquals("Arriving in 25 mins • 10:25 AM", pill.text)
    }

    @Test
    fun etaPillShowsScheduledArrivalWhenGpsInactive() {
        val shipment = buildJsonObject {
            put("status", "assigned")
            put("estimated_arrival", "2026-11-19T06:30:00Z") // 2:30 PM PHT same day
        }
        val delivery = buildJsonObject {
            put("status", "assigned")
            put("assigned_at", "2026-11-19T01:44:00Z")
        }

        val pill = ShipmentTrackingPresentation.etaPillState(shipment, delivery, null, null, now)
        assertFalse(pill.isLive)
        assertEquals("Scheduled Arrival • 2:30 PM", pill.text)
    }

    @Test
    fun etaPillShowsFriendlySchedulePendingWhenEstimatedArrivalMissing() {
        val shipment = buildJsonObject {
            put("status", "assigned")
        }
        val pill = ShipmentTrackingPresentation.etaPillState(shipment, null, null, null, now)
        assertFalse(pill.isLive)
        assertEquals("Scheduled Arrival • Pending schedule", pill.text)
    }

    @Test
    fun sheetSummaryAssignedStateHighlightsFirstStepAndShowsNextMilestone() {
        val shipment = buildJsonObject {
            put("status", "assigned")
            put("created_at", "2026-11-19T01:30:00Z")
        }
        val delivery = buildJsonObject {
            put("status", "assigned")
            put("assigned_at", "2026-11-19T01:44:00Z") // Nov 19 • 9:44 AM PHT
        }

        val summary = ShipmentTrackingPresentation.sheetSummary(
            shipment = shipment,
            delivery = delivery,
            freshness = null,
            offline = false
        )

        assertEquals("Driver Assigned", summary.currentStatusTitle)
        assertEquals("Nov 19 • 9:44 AM", summary.currentStatusTimestamp)
        assertEquals("Next: En Route to Pickup • Awaiting driver GPS signal", summary.subtitle)
        assertEquals(6, summary.steps.size)
        assertTrue(summary.steps[0].isActive)
        assertFalse(summary.steps[0].isCompleted)
        assertTrue(summary.steps.drop(1).all { !it.isCompleted && !it.isActive })
    }

    @Test
    fun sheetSummaryInTransitMarksPriorStepsCompletedAndInTransitActive() {
        val shipment = buildJsonObject {
            put("status", "in_transit")
        }
        val delivery = buildJsonObject {
            put("status", "in_transit")
            put("assigned_at", "2026-11-19T01:00:00Z")
            put("route_started_at", "2026-11-19T01:15:00Z")
            put("pickup_arrived_at", "2026-11-19T01:30:00Z")
            put("customer_showed_at", "2026-11-19T01:40:00Z")
            put("departed_at", "2026-11-19T01:55:00Z")
        }
        val freshness = PositionFreshness(
            status = PositionFreshnessStatus.FRESH,
            ageSeconds = 12L,
            relativeText = "Updated 12s ago",
            accuracyText = "accuracy 6 m"
        )

        val summary = ShipmentTrackingPresentation.sheetSummary(
            shipment = shipment,
            delivery = delivery,
            freshness = freshness,
            offline = false
        )

        assertEquals("In Transit", summary.currentStatusTitle)
        assertEquals("Next: Delivered", summary.subtitle)
        assertTrue(summary.steps.take(4).all { it.isCompleted && !it.isActive })
        assertTrue(summary.steps[4].isActive)
        assertFalse(summary.steps[5].isCompleted)
        assertFalse(summary.steps[5].isActive)
    }

    @Test
    fun sheetSummaryDeliveredCompletesAllSixSteps() {
        val shipment = buildJsonObject {
            put("status", "completed")
        }
        val delivery = buildJsonObject {
            put("status", "delivered")
            put("assigned_at", "2026-11-19T01:00:00Z")
            put("route_started_at", "2026-11-19T01:15:00Z")
            put("pickup_arrived_at", "2026-11-19T01:30:00Z")
            put("customer_showed_at", "2026-11-19T01:40:00Z")
            put("departed_at", "2026-11-19T01:55:00Z")
            put("arrived_at", "2026-11-19T03:00:00Z")
        }

        val summary = ShipmentTrackingPresentation.sheetSummary(
            shipment = shipment,
            delivery = delivery,
            freshness = null,
            offline = false
        )

        assertEquals("Delivered", summary.currentStatusTitle)
        assertEquals("All delivery milestones completed", summary.subtitle)
        assertTrue(summary.steps.all { it.isCompleted && !it.isActive })
    }

    @Test
    fun driverSummaryExtractsDriverVehicleAndPhoneWhenProvided() {
        val shipment = buildJsonObject { put("status", "assigned") }
        val delivery = buildJsonObject {
            put("status", "assigned")
            put("driver_name", "Juan Dela Cruz")
            put("driver_phone", "+63 917 123 4567")
            put("truck_type", "6-Wheeler Closed Van")
            put("plate_number", "NCV 4821")
        }

        val result = ShipmentTrackingPresentation.driverSummary(shipment, delivery)
        assertEquals("Juan Dela Cruz", result.driverName)
        assertEquals("JC", result.initials)
        assertEquals("6-Wheeler Closed Van • NCV 4821", result.vehicleSubtitle)
        assertEquals("+639171234567", result.phoneNumber)
        assertTrue(result.hasDriverAssigned)
    }

    @Test
    fun driverSummaryFallsBackGracefullyWhenDriverAndVehicleDetailsMissing() {
        val shipment = buildJsonObject { put("status", "assigned") }
        val delivery = buildJsonObject {
            put("status", "assigned")
            put("assigned_at", "2026-11-19T01:44:00Z")
        }

        val result = ShipmentTrackingPresentation.driverSummary(shipment, delivery)
        assertEquals("Assigned Driver", result.driverName)
        assertEquals("", result.initials)
        assertEquals("Assigning vehicle details", result.vehicleSubtitle)
        assertEquals(null, result.phoneNumber)
        assertTrue(result.hasDriverAssigned)
    }

    @Test
    fun driverSummaryShowsAwaitingDriverBeforeAssignment() {
        val shipment = buildJsonObject { put("status", "pending") }

        val result = ShipmentTrackingPresentation.driverSummary(shipment, null)
        assertEquals("Awaiting Driver", result.driverName)
        assertEquals("Assigning vehicle details", result.vehicleSubtitle)
        assertEquals(null, result.phoneNumber)
        assertFalse(result.hasDriverAssigned)
    }

    @Test
    fun formatAddressWithoutPlusCodeStripsGooglePlusCodesCleanly() {
        assertEquals(
            "Cabanatuan City Public Market, Nueva Ecija",
            ShipmentTrackingPresentation.formatAddressWithoutPlusCode("FRH7+5W3, Cabanatuan City Public Market, Nueva Ecija")
        )
        assertEquals(
            "Manila, Metro Manila, Philippines",
            ShipmentTrackingPresentation.formatAddressWithoutPlusCode("G32P+4M Manila, Metro Manila, Philippines")
        )
        assertEquals(
            "Brgy. Bakod Bayan, Cabanatuan City",
            ShipmentTrackingPresentation.formatAddressWithoutPlusCode("Brgy. Bakod Bayan, 7Q7R+X9G, Cabanatuan City")
        )
        assertEquals(
            "Km 12+500 Maharlika Hwy, Nueva Ecija",
            ShipmentTrackingPresentation.formatAddressWithoutPlusCode("Km 12+500 Maharlika Hwy, Nueva Ecija")
        )
        assertEquals(
            "7Q7R+X9G",
            ShipmentTrackingPresentation.formatAddressWithoutPlusCode("7Q7R+X9G")
        )
    }

    @Test
    fun cargoSummaryParsesContactAndNotesAndHidesEmptyNotes() {
        val shipmentWithNotes = buildJsonObject {
            put("cargo_description", "200 boxes")
            put("weight_kg", "2500")
            put("driver_notes", "Contact: +639175551234\nCall warehouse supervisor upon arrival.")
        }
        val withNotes = ShipmentTrackingPresentation.cargoSummary(shipmentWithNotes, null)
        assertEquals("200 boxes", withNotes.cargoText)
        assertEquals("2,500 kg", withNotes.weightText)
        assertEquals("+639175551234", withNotes.recipientContact)
        assertEquals("Call warehouse supervisor upon arrival.", withNotes.deliveryNotes)

        val shipmentEmptyNotes = buildJsonObject {
            put("cargo_description", "200 boxes")
            put("weight_kg", "2500")
            put("driver_notes", "Contact: +639175551234\n")
        }
        val emptyNotes = ShipmentTrackingPresentation.cargoSummary(shipmentEmptyNotes, null)
        assertEquals("+639175551234", emptyNotes.recipientContact)
        assertEquals("", emptyNotes.deliveryNotes)
    }

    @Test
    fun pricingSummaryCollapsesEqualAmountsAndShowsBothWhenDifferent() {
        val equalShipment = buildJsonObject {
            put("quoted_amount", "12500")
            put("final_amount", "12500.00")
        }
        val equalPricing = ShipmentTrackingPresentation.pricingSummary(equalShipment)
        assertFalse(equalPricing.awaitingQuotation)
        assertEquals(1, equalPricing.rows.size)
        assertEquals("Total Amount", equalPricing.rows[0].label)

        val differentShipment = buildJsonObject {
            put("quoted_amount", "12500")
            put("final_amount", "14200")
        }
        val diffPricing = ShipmentTrackingPresentation.pricingSummary(differentShipment)
        assertFalse(diffPricing.awaitingQuotation)
        assertEquals(2, diffPricing.rows.size)
        assertEquals("Quoted Amount", diffPricing.rows[0].label)
        assertEquals("Final Amount", diffPricing.rows[1].label)

        val awaitingShipment = buildJsonObject {}
        val awaitingPricing = ShipmentTrackingPresentation.pricingSummary(awaitingShipment)
        assertTrue(awaitingPricing.awaitingQuotation)
        assertEquals(1, awaitingPricing.rows.size)
        assertEquals("Total Amount", awaitingPricing.rows[0].label)
    }

    @Test
    fun proofAndCancelVisibilityFollowShipmentMilestones() {
        val assignedShipment = buildJsonObject { put("status", "assigned") }
        val assignedDelivery = buildJsonObject {
            put("status", "assigned")
            put("assigned_at", "2026-11-19T01:44:00Z")
        }
        assertFalse(ShipmentTrackingPresentation.shouldShowProofOfDelivery(assignedShipment, assignedDelivery))
        assertTrue(ShipmentTrackingPresentation.shouldShowCancelAction(assignedShipment, listOf(assignedDelivery)))

        val enRouteDelivery = buildJsonObject {
            put("status", "assigned")
            put("assigned_at", "2026-11-19T01:44:00Z")
            put("route_started_at", "2026-11-19T02:00:00Z")
        }
        assertFalse(ShipmentTrackingPresentation.shouldShowProofOfDelivery(assignedShipment, enRouteDelivery))
        assertFalse(ShipmentTrackingPresentation.shouldShowCancelAction(assignedShipment, listOf(enRouteDelivery)))

        val deliveredShipment = buildJsonObject { put("status", "completed") }
        val deliveredDelivery = buildJsonObject {
            put("status", "delivered")
            put("arrived_at", "2026-11-19T05:00:00Z")
        }
        assertTrue(ShipmentTrackingPresentation.shouldShowProofOfDelivery(deliveredShipment, deliveredDelivery))
        assertFalse(ShipmentTrackingPresentation.shouldShowCancelAction(deliveredShipment, listOf(deliveredDelivery)))
    }
}
