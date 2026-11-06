package com.tanaw.app.feature.workspace

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class BookingRecoveryRulesTest {

    @Test
    fun defaultBookingDraftIsEmptyAndUnsubmitted() {
        val draft = BookingDraft()
        assertTrue(draft.isEmpty())
        assertFalse(draft.hasMeaningfulContent())
        assertFalse(draft.submitted)
        assertEquals("HOME", draft.stepName)
        assertEquals("", draft.pickup)
        assertEquals("", draft.dropoff)
        assertNull(draft.pickupLat)
        assertNull(draft.pickupLng)
        assertNull(draft.dropoffLat)
        assertNull(draft.dropoffLng)
        assertTrue(draft.isPickupNow)
        assertEquals("None", draft.handling)
        assertNotNull(UUID.fromString(draft.requestId))
    }

    @Test
    fun bookingDraftWithMeaningfulFieldsIsNotEmpty() {
        assertFalse(BookingDraft(pickup = "Cabanatuan City").isEmpty())
        assertFalse(BookingDraft(dropoff = "Manila Port").isEmpty())
        assertFalse(BookingDraft(cargo = "Agricultural Produce").isEmpty())
        assertFalse(BookingDraft(weight = "2500").isEmpty())
        assertFalse(BookingDraft(notes = "Fragile boxes").isEmpty())
        assertFalse(BookingDraft(stepName = "ADD_DETAILS").isEmpty())
        assertFalse(BookingDraft(stepName = "SUMMARY").isEmpty())
        assertFalse(BookingDraft(stepName = "PICKUP_MAP").isEmpty())
        assertFalse(BookingDraft(stepName = "DESTINATION_MAP").isEmpty())
        assertFalse(BookingDraft(submitted = true).isEmpty())
        assertFalse(BookingDraft(isPickupNow = false, scheduledDeparture = "2026-11-10 08:00 AM").isEmpty())
        assertFalse(BookingDraft(handling = "Tailgate Required").isEmpty())
        assertFalse(BookingDraft(selectedVehicleId = "v-6w").isEmpty())
        assertFalse(BookingDraft(pickupLat = 15.48, pickupLng = 120.96).isEmpty())
        assertFalse(BookingDraft(dropoffLat = 14.61, dropoffLng = 120.95).isEmpty())
    }

    @Test
    fun bookingDraftWhitespaceAndDefaultsRemainEmpty() {
        assertTrue(BookingDraft(pickup = "   ").isEmpty())
        assertTrue(BookingDraft(dropoff = "\t\n").isEmpty())
        assertTrue(BookingDraft(cargo = "  ").isEmpty())
        assertTrue(BookingDraft(notes = " ").isEmpty())
        assertTrue(BookingDraft(weight = "").isEmpty())
        assertTrue(BookingDraft(stepName = "HOME").isEmpty())
        assertTrue(BookingDraft(handling = "None").isEmpty())
        assertTrue(BookingDraft(handling = "  ").isEmpty())
        assertTrue(BookingDraft(isPickupNow = true, scheduledDeparture = "").isEmpty())
        assertTrue(BookingDraft(pickupLat = 15.0, pickupLng = null).isEmpty())
    }

    @Test
    fun bookingDraftJsonSerializationRoundTrip() {
        val reqId = UUID.randomUUID().toString()
        val original = BookingDraft(
            requestId = reqId,
            stepName = "SUMMARY",
            pickup = "Km 115 Maharlika Hwy, Cabanatuan City",
            pickupLat = 15.4865,
            pickupLng = 120.9679,
            dropoff = "North Harbor Pier 4, Tondo, Manila",
            dropoffLat = 14.6158,
            dropoffLng = 120.9585,
            isPickupNow = false,
            scheduledDeparture = "2026-11-10 08:00 AM",
            selectedVehicleId = "v-10-wheeler",
            contact = "+639171234567",
            cargo = "Electronics and appliances",
            weight = "4500",
            handling = "Tailgate Required",
            notes = "Call upon arrival at Gate 2",
            submitted = true,
            owner = "user-uuid-1234",
            createdAt = 1730850000000L
        )

        val json = original.toJson()
        val restored = BookingDraft.fromJson(json)

        assertEquals(original.requestId, restored.requestId)
        assertEquals(original.stepName, restored.stepName)
        assertEquals(original.pickup, restored.pickup)
        assertEquals(original.pickupLat!!, restored.pickupLat!!, 0.0001)
        assertEquals(original.pickupLng!!, restored.pickupLng!!, 0.0001)
        assertEquals(original.dropoff, restored.dropoff)
        assertEquals(original.dropoffLat!!, restored.dropoffLat!!, 0.0001)
        assertEquals(original.dropoffLng!!, restored.dropoffLng!!, 0.0001)
        assertEquals(original.isPickupNow, restored.isPickupNow)
        assertEquals(original.scheduledDeparture, restored.scheduledDeparture)
        assertEquals(original.selectedVehicleId, restored.selectedVehicleId)
        assertEquals(original.contact, restored.contact)
        assertEquals(original.cargo, restored.cargo)
        assertEquals(original.weight, restored.weight)
        assertEquals(original.handling, restored.handling)
        assertEquals(original.notes, restored.notes)
        assertEquals(original.submitted, restored.submitted)
        assertEquals(original.owner, restored.owner)
        assertEquals(original.createdAt, restored.createdAt)
    }

    @Test
    fun bookingDraftFromJsonHandlesMissingAndCorruptedFieldsGracefully() {
        val partialJson = buildJsonObject {
            put("pickup", "Warehouse Cabanatuan")
            put("is_pickup_now", false)
            put("pickup_lat", "15.4865")
            put("pickup_lng", 120.9679)
        }
        val restored = BookingDraft.fromJson(partialJson)

        assertEquals("Warehouse Cabanatuan", restored.pickup)
        assertFalse(restored.isPickupNow)
        assertEquals("HOME", restored.stepName)
        assertEquals("None", restored.handling)
        assertEquals("", restored.owner)
        assertFalse(restored.submitted)
        assertNotNull(restored.pickupLat)
        assertEquals(15.4865, restored.pickupLat!!, 0.0001)
        assertEquals(120.9679, restored.pickupLng!!, 0.0001)
        assertTrue(restored.createdAt > 0)
    }

    @Test
    fun reconcileNullDraftReturnsNoDraft() {
        val result = BookingRecoveryRules.reconcile(null, null)
        assertTrue(result is BookingReconciliationResult.NoDraft)
    }

    @Test
    fun reconcileUnsubmittedDraftReturnsNotSubmitted() {
        val draft = BookingDraft(pickup = "Cabanatuan", submitted = false)
        val result = BookingRecoveryRules.reconcile(draft, null)
        assertTrue(result is BookingReconciliationResult.NotSubmitted)
    }

    @Test
    fun reconcileUncertainSubmissionWhenServerCreatedShipmentReturnsConfirmed() {
        val draft = BookingDraft(
            requestId = "req-12345",
            stepName = "SUMMARY",
            pickup = "Warehouse A",
            dropoff = "Warehouse B",
            selectedVehicleId = "v-6w",
            submitted = true
        )
        val serverShipment = buildJsonObject {
            put("id", "shp-999")
            put("tracking_number", "TRK-2026-999")
            put("status", "pending")
        }

        val result = BookingRecoveryRules.reconcile(draft, serverShipment)
        assertTrue(result is BookingReconciliationResult.Confirmed)
        val confirmed = result as BookingReconciliationResult.Confirmed
        assertEquals("shp-999", confirmed.shipmentId)
        assertEquals("TRK-2026-999", confirmed.trackingNumber)
        assertEquals("Warehouse A", confirmed.draft.pickup)
        assertEquals("Warehouse B", confirmed.draft.dropoff)
    }

    @Test
    fun reconcileUncertainSubmissionWhenServerDidNotReceiveReturnsRestoredForRetry() {
        val draft = BookingDraft(
            requestId = "req-67890",
            stepName = "SUMMARY",
            pickup = "Warehouse A",
            dropoff = "Warehouse B",
            cargo = "Dry Goods",
            weight = "1200",
            notes = "Handle with care",
            handling = "Tailgate Required",
            contact = "+639171234567",
            selectedVehicleId = "v-6w",
            isPickupNow = false,
            scheduledDeparture = "2026-11-12 10:00 AM",
            owner = "user-999",
            submitted = true
        )

        val result = BookingRecoveryRules.reconcile(draft, matchingShipment = null)
        assertTrue(result is BookingReconciliationResult.RestoredForRetry)
        val restored = (result as BookingReconciliationResult.RestoredForRetry).draft
        assertFalse(restored.submitted)
        assertEquals("req-67890", restored.requestId)
        assertEquals("Warehouse A", restored.pickup)
        assertEquals("Warehouse B", restored.dropoff)
        assertEquals("Dry Goods", restored.cargo)
        assertEquals("1200", restored.weight)
        assertEquals("Handle with care", restored.notes)
        assertEquals("Tailgate Required", restored.handling)
        assertEquals("+639171234567", restored.contact)
        assertEquals("v-6w", restored.selectedVehicleId)
        assertFalse(restored.isPickupNow)
        assertEquals("2026-11-12 10:00 AM", restored.scheduledDeparture)
        assertEquals("user-999", restored.owner)
        assertEquals("SUMMARY", restored.stepName)
    }

    @Test
    fun reconcileUncertainSubmissionWithBlankServerFieldsReturnsRestoredForRetry() {
        val draft = BookingDraft(requestId = "req-555", submitted = true)
        val blankShipment = buildJsonObject {
            put("id", "")
            put("tracking_number", "")
        }

        val result = BookingRecoveryRules.reconcile(draft, blankShipment)
        assertTrue(result is BookingReconciliationResult.RestoredForRetry)
        assertFalse((result as BookingReconciliationResult.RestoredForRetry).draft.submitted)

        val whitespaceShipment = buildJsonObject {
            put("id", "   ")
            put("tracking_number", " \t ")
        }
        val resultWhitespace = BookingRecoveryRules.reconcile(draft, whitespaceShipment)
        assertTrue(resultWhitespace is BookingReconciliationResult.RestoredForRetry)

        val missingTracking = buildJsonObject {
            put("id", "shp-123")
            put("tracking_number", "")
        }
        val resultMissingTracking = BookingRecoveryRules.reconcile(draft, missingTracking)
        assertTrue(resultMissingTracking is BookingReconciliationResult.RestoredForRetry)
    }
}
