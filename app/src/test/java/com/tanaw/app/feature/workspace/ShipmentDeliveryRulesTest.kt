package com.tanaw.app.feature.workspace

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test

class ShipmentDeliveryRulesTest {

    private fun shipment(id: String = "s1", status: String = "delivered") = buildJsonObject {
        put("id", id)
        put("status", status)
    }

    private fun delivery(
        id: String,
        shipmentId: String = "s1",
        status: String,
        assignedAt: String
    ) = buildJsonObject {
        put("id", id)
        put("shipment_id", shipmentId)
        put("status", status)
        put("assigned_at", assignedAt)
    }

    @Test
    fun deliveredShipmentExcludesSupersededCancelledDeliveries() {
        val deliveries = listOf(
            delivery("d-cancelled", status = "cancelled", assignedAt = "2026-10-25T08:00:00+00:00"),
            delivery("d-delivered", status = "delivered", assignedAt = "2026-10-25T09:00:00+00:00")
        )
        val result = ShipmentDeliveryRules.displayDeliveries(shipment(status = "delivered"), deliveries)
        assertEquals(1, result.size)
        assertEquals("d-delivered", result.single().value("id"))
        assertEquals("d-delivered", ShipmentDeliveryRules.primaryDelivery(result)?.value("id"))
    }

    @Test
    fun inTransitShipmentExcludesSupersededCancelledDeliveriesRegardlessOfOrder() {
        val deliveries = listOf(
            delivery("d-transit", status = "in_transit", assignedAt = "2026-10-25T09:00:00Z"),
            delivery("d-cancelled", status = "cancelled", assignedAt = "2026-10-25T08:00:00Z")
        )
        val result = ShipmentDeliveryRules.displayDeliveries(shipment(status = "in_transit"), deliveries)
        assertEquals(1, result.size)
        assertEquals("d-transit", result.single().value("id"))
        assertEquals("d-transit", ShipmentDeliveryRules.primaryDelivery(result)?.value("id"))
    }

    @Test
    fun nonCancelledShipmentWithOnlyCancelledDeliveriesShowsNone() {
        val deliveries = listOf(
            delivery("d-cancelled", status = "cancelled", assignedAt = "2026-10-25T08:00:00Z")
        )
        val result = ShipmentDeliveryRules.displayDeliveries(shipment(status = "booked"), deliveries)
        assertTrue(result.isEmpty())
        assertNull(ShipmentDeliveryRules.primaryDelivery(result))
    }

    @Test
    fun cancelledShipmentShowsLatestCancelledDeliveryOnly() {
        val deliveries = listOf(
            delivery("d-old", status = "cancelled", assignedAt = "2026-10-25T08:00:00+00:00"),
            delivery("d-new", status = "cancelled", assignedAt = "2026-10-25T10:00:00+00:00"),
            delivery("d-other", shipmentId = "s2", status = "delivered", assignedAt = "2026-10-25T11:00:00+00:00")
        )
        val result = ShipmentDeliveryRules.displayDeliveries(shipment(status = "cancelled"), deliveries)
        assertEquals(1, result.size)
        assertEquals("d-new", result.single().value("id"))
        assertEquals("d-new", ShipmentDeliveryRules.primaryDelivery(result)?.value("id"))
    }

    @Test
    fun primaryDeliveryPrefersActiveTransitStatus() {
        val deliveries = listOf(
            delivery("d-delivered", status = "delivered", assignedAt = "2026-10-25T08:00:00Z"),
            delivery("d-transit", status = "in_transit", assignedAt = "2026-10-25T09:00:00Z")
        )
        assertEquals("d-transit", ShipmentDeliveryRules.primaryDelivery(deliveries)?.value("id"))
    }
}
