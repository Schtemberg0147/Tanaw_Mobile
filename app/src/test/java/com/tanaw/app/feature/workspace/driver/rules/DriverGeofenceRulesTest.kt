package com.tanaw.app.feature.workspace.driver.rules

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriverGeofenceRulesTest {
    private val pickupLat = 15.4865
    private val pickupLng = 120.9679
    private val dropoffLat = 14.6158
    private val dropoffLng = 120.9585

    private val shipmentWithPins = buildJsonObject {
        put("id", "shp-1")
        put("pickup_lat", pickupLat)
        put("pickup_lng", pickupLng)
        put("dropoff_lat", dropoffLat)
        put("dropoff_lng", dropoffLng)
    }

    @Test
    fun `distanceMeters is zero for identical coordinates and accurate for short offsets`() {
        assertEquals(0.0, DriverGeofenceRules.distanceMeters(pickupLat, pickupLng, pickupLat, pickupLng), 0.01)

        // ~0.0003 deg latitude is ~33.3m
        val nearby = DriverGeofenceRules.distanceMeters(pickupLat, pickupLng, pickupLat + 0.0003, pickupLng)
        assertTrue("Expected ~33m, got $nearby", nearby in 30.0..37.0)

        // ~0.0006 deg latitude is ~66.7m
        val outside = DriverGeofenceRules.distanceMeters(pickupLat, pickupLng, pickupLat + 0.0006, pickupLng)
        assertTrue("Expected ~67m, got $outside", outside in 63.0..70.0)
    }

    @Test
    fun `checkArrival allows within 50m and rejects outside 50m for pickup`() {
        val allowed = DriverGeofenceRules.checkArrival(
            driverLat = pickupLat + 0.0003,
            driverLng = pickupLng,
            shipment = shipmentWithPins,
            details = null,
            isDestination = false
        )
        assertTrue(allowed is GeofenceCheckResult.Allowed)

        val tooFar = DriverGeofenceRules.checkArrival(
            driverLat = pickupLat + 0.001,
            driverLng = pickupLng,
            shipment = shipmentWithPins,
            details = null,
            isDestination = false
        )
        assertTrue(tooFar is GeofenceCheckResult.TooFar)
        tooFar as GeofenceCheckResult.TooFar
        assertEquals(50, tooFar.maxRadiusMeters)
        assertTrue(tooFar.distanceMeters > 50)
        assertEquals(
            "You are ${tooFar.distanceMeters}m from the pick-up location. Move within 50m to confirm pick-up arrival.",
            DriverGeofenceRules.message(tooFar, isDestination = false)
        )
    }

    @Test
    fun `checkArrival allows within 50m and rejects outside 50m for destination`() {
        val allowed = DriverGeofenceRules.checkArrival(
            driverLat = dropoffLat,
            driverLng = dropoffLng + 0.0002,
            shipment = shipmentWithPins,
            details = null,
            isDestination = true
        )
        assertTrue(allowed is GeofenceCheckResult.Allowed)

        val tooFar = DriverGeofenceRules.checkArrival(
            driverLat = dropoffLat,
            driverLng = dropoffLng + 0.001,
            shipment = shipmentWithPins,
            details = null,
            isDestination = true
        )
        assertTrue(tooFar is GeofenceCheckResult.TooFar)
        tooFar as GeofenceCheckResult.TooFar
        assertEquals(50, tooFar.maxRadiusMeters)
        assertEquals(
            "You are ${tooFar.distanceMeters}m from the destination. Move within 50m to mark as delivered.",
            DriverGeofenceRules.message(tooFar, isDestination = true)
        )
    }

    @Test
    fun `extractTarget falls back to matching routing plan when shipment lacks pins`() {
        val shipmentWithoutPins = buildJsonObject {
            put("id", "shp-1")
        }
        val matchingDetails = buildJsonObject {
            put("shipment_id", "shp-1")
            put("routing", buildJsonObject {
                put("plan", buildJsonObject {
                    put("pickup_lat", pickupLat)
                    put("pickup_lng", pickupLng)
                    put("dropoff_lat", dropoffLat)
                    put("dropoff_lng", dropoffLng)
                })
            })
        }
        val pickupTarget = DriverGeofenceRules.extractTarget(shipmentWithoutPins, matchingDetails, isDestination = false)
        assertNotNull(pickupTarget)
        assertEquals(pickupLat, pickupTarget!!.latitude, 0.00001)
        assertEquals(pickupLng, pickupTarget.longitude, 0.00001)

        val mismatchedDetails = buildJsonObject {
            put("shipment_id", "shp-other")
            put("routing", matchingDetails["routing"]!!)
        }
        assertNull(DriverGeofenceRules.extractTarget(shipmentWithoutPins, mismatchedDetails, isDestination = true))
    }

    @Test
    fun `missing coordinates distinguish loading vs missing and reject invalid driver coords`() {
        val emptyShipment = buildJsonObject { put("id", "shp-2") }

        val loading = DriverGeofenceRules.checkArrival(
            driverLat = pickupLat,
            driverLng = pickupLng,
            shipment = emptyShipment,
            details = null,
            isDestination = false,
            detailLoading = true
        )
        assertEquals(GeofenceCheckResult.LoadingCoordinates, loading)

        val missing = DriverGeofenceRules.checkArrival(
            driverLat = pickupLat,
            driverLng = pickupLng,
            shipment = emptyShipment,
            details = null,
            isDestination = true,
            detailLoading = false
        )
        assertEquals(GeofenceCheckResult.MissingTargetCoordinates, missing)
        assertTrue(DriverGeofenceRules.message(missing, isDestination = true).contains("Destination coordinates are missing"))

        val invalidDriver = DriverGeofenceRules.checkArrival(
            driverLat = 0.0,
            driverLng = 0.0,
            shipment = shipmentWithPins,
            details = null,
            isDestination = false
        )
        assertEquals(GeofenceCheckResult.InvalidDriverCoordinates, invalidDriver)
    }

    @Test
    fun `extractTarget rejects non-finite out-of-range and zero coordinates`() {
        val invalidShipments = listOf(
            buildJsonObject { put("pickup_lat", "NaN"); put("pickup_lng", 120.9) },
            buildJsonObject { put("pickup_lat", 95.0); put("pickup_lng", 120.9) },
            buildJsonObject { put("pickup_lat", 15.0); put("pickup_lng", 185.0) },
            buildJsonObject { put("pickup_lat", 0.0); put("pickup_lng", 0.0) }
        )
        invalidShipments.forEach { s ->
            assertNull(DriverGeofenceRules.extractTarget(s, null, isDestination = false))
        }
    }
}
