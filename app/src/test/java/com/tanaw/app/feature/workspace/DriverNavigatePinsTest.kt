package com.tanaw.app.feature.workspace

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class DriverNavigatePinsTest {
    private val now = Instant.parse("2026-11-11T12:00:00Z")

    private fun shipment(vararg pairs: Pair<String, String>): JsonObject = buildJsonObject {
        pairs.forEach { put(it.first, it.second) }
    }

    private val luzonShipment = shipment(
        "id" to "shipment-001",
        "tracking_number" to "TN-LUZON-001",
        "pickup_address" to "Manila Port",
        "pickup_lat" to "14.6000",
        "pickup_lng" to "120.9800",
        "dropoff_address" to "Cabanatuan, Nueva Ecija",
        "dropoff_lat" to "15.4800",
        "dropoff_lng" to "120.9700"
    )

    private fun detailsWithPosition(
        shipmentId: String = "shipment-001",
        lat: String = "14.8000",
        lng: String = "120.9750",
        recordedAt: String = now.minusSeconds(60).toString(),
        accuracy: String = "12.5"
    ): JsonObject = buildJsonObject {
        put("shipment_id", shipmentId)
        put("position", buildJsonObject {
            put("latitude", lat)
            put("longitude", lng)
            put("recorded_at", recordedAt)
            put("accuracy_m", accuracy)
        })
    }

    @Test
    fun driverMapPinsAppendsVehicleWhenFreshAndNearStops() {
        val details = detailsWithPosition(
            lat = "14.8000",
            lng = "120.9750",
            recordedAt = now.minusSeconds(60).toString()
        )
        val pins = driverMapPins(luzonShipment, details, now)
        assertEquals(3, pins.size)
        assertEquals("Pickup", pins[0].first)
        assertEquals("Destination", pins[1].first)
        assertEquals("Vehicle", pins[2].first)
        assertEquals(14.8000, pins[2].second.latitude, 0.0001)
        assertEquals(120.9750, pins[2].second.longitude, 0.0001)
    }

    @Test
    fun driverMapPinsAppendsVehicleWhenRecentUnder300s() {
        val details = detailsWithPosition(
            recordedAt = now.minusSeconds(240).toString()
        )
        val pins = driverMapPins(luzonShipment, details, now)
        assertEquals(3, pins.size)
        assertEquals("Vehicle", pins.last().first)
    }

    @Test
    fun driverMapPinsExcludesVehicleWhenStaleOver300s() {
        val details = detailsWithPosition(
            recordedAt = now.minusSeconds(301).toString()
        )
        val pins = driverMapPins(luzonShipment, details, now)
        assertEquals(2, pins.size)
        assertTrue(pins.none { it.first == "Vehicle" })
    }

    @Test
    fun driverMapPinsExcludesVehicleWhenRecordedAtMissingOrInvalid() {
        val missingRecordedAt = buildJsonObject {
            put("shipment_id", "shipment-001")
            put("position", buildJsonObject {
                put("latitude", "14.8000")
                put("longitude", "120.9750")
            })
        }
        val pinsMissing = driverMapPins(luzonShipment, missingRecordedAt, now)
        assertEquals(2, pinsMissing.size)

        val invalidRecordedAt = detailsWithPosition(recordedAt = "not-an-iso-timestamp")
        val pinsInvalid = driverMapPins(luzonShipment, invalidRecordedAt, now)
        assertEquals(2, pinsInvalid.size)
    }

    @Test
    fun driverMapPinsExcludesVehicleWhenCoordinatesFarOutsideStops() {
        // California coordinates (e.g. Googleplex) while stops are in Manila
        val californiaDetails = detailsWithPosition(
            lat = "37.4220",
            lng = "-122.0841",
            recordedAt = now.minusSeconds(30).toString()
        )
        val pinsCal = driverMapPins(luzonShipment, californiaDetails, now)
        assertEquals(2, pinsCal.size)
        assertTrue(pinsCal.none { it.first == "Vehicle" })

        // Null Island (0.0, 0.0)
        val nullIslandDetails = detailsWithPosition(
            lat = "0.0",
            lng = "0.0",
            recordedAt = now.minusSeconds(30).toString()
        )
        val pinsNullIsland = driverMapPins(luzonShipment, nullIslandDetails, now)
        assertEquals(2, pinsNullIsland.size)
        assertTrue(pinsNullIsland.none { it.first == "Vehicle" })

        // Just outside the 5.0 degree threshold (Manila ~ 14.6, 120.98; test 20.5, 120.98 -> delta = 5.9)
        val outsideThresholdDetails = detailsWithPosition(
            lat = "20.5000",
            lng = "120.9800",
            recordedAt = now.minusSeconds(30).toString()
        )
        val pinsOutside = driverMapPins(luzonShipment, outsideThresholdDetails, now)
        assertEquals(2, pinsOutside.size)
        assertTrue(pinsOutside.none { it.first == "Vehicle" })
    }

    @Test
    fun driverMapPinsExcludesVehicleWhenCoordinatesInvalidOrMissing() {
        val invalidLatDetails = detailsWithPosition(lat = "95.0000")
        assertEquals(2, driverMapPins(luzonShipment, invalidLatDetails, now).size)

        val nanDetails = detailsWithPosition(lat = "NaN")
        assertEquals(2, driverMapPins(luzonShipment, nanDetails, now).size)

        val noPositionDetails = buildJsonObject { put("shipment_id", "shipment-001") }
        assertEquals(2, driverMapPins(luzonShipment, noPositionDetails, now).size)

        assertEquals(2, driverMapPins(luzonShipment, null, now).size)
    }

    @Test
    fun driverMapPinsExcludesVehicleWhenShipmentIdMismatched() {
        val mismatchedDetails = detailsWithPosition(
            shipmentId = "other-shipment-999",
            lat = "14.8000",
            lng = "120.9750",
            recordedAt = now.minusSeconds(30).toString()
        )
        val pins = driverMapPins(luzonShipment, mismatchedDetails, now)
        assertEquals(2, pins.size)
        assertTrue(pins.none { it.first == "Vehicle" })

        // Blank shipment_id matches active shipment
        val blankIdDetails = detailsWithPosition(
            shipmentId = "",
            lat = "14.8000",
            lng = "120.9750",
            recordedAt = now.minusSeconds(30).toString()
        )
        val pinsBlank = driverMapPins(luzonShipment, blankIdDetails, now)
        assertEquals(3, pinsBlank.size)
        assertEquals("Vehicle", pinsBlank.last().first)
    }

    @Test
    fun driverStopPinsReturnsOnlyStopsWithoutVehicle() {
        val stops = driverStopPins(luzonShipment)
        assertEquals(2, stops.size)
        assertEquals("Pickup", stops[0].first)
        assertEquals(14.6000, stops[0].second.latitude, 0.0001)
        assertEquals("Destination", stops[1].first)
        assertEquals(15.4800, stops[1].second.latitude, 0.0001)
    }

    @Test
    fun normalizeRouteGeoJsonHandlesVariousJsonElements() {
        assertNull(normalizeRouteGeoJson(null))
        assertNull(normalizeRouteGeoJson(JsonNull))
        assertNull(normalizeRouteGeoJson(buildJsonObject {}))

        val validObject = buildJsonObject {
            put("type", "LineString")
            put("coordinates", buildJsonArray {
                add(buildJsonArray { add(120.98); add(14.6) })
            })
        }
        val normalizedFromObject = normalizeRouteGeoJson(validObject)
        assertNotNull(normalizedFromObject)
        assertTrue(normalizedFromObject!!.contains("LineString"))

        val stringPrimitive = JsonPrimitive("  {\"type\":\"FeatureCollection\"}  ")
        val normalizedFromPrimitive = normalizeRouteGeoJson(stringPrimitive)
        assertEquals("{\"type\":\"FeatureCollection\"}", normalizedFromPrimitive)

        assertNull(normalizeRouteGeoJson(JsonPrimitive("not-a-json-object")))
        assertNull(normalizeRouteGeoJson(JsonPrimitive(true)))
        assertNull(normalizeRouteGeoJson(JsonPrimitive(12345)))
    }

    @Test
    fun driverMapPinsRejectsVehicleWhenStopsAreEmptyAndCoordsOutsidePhilippineBounds() {
        val emptyShipment = shipment("id" to "shipment-empty")
        assertTrue(driverStopPins(emptyShipment).isEmpty())

        // California coordinates (e.g. 37.4220, -122.0841)
        val calDetails = detailsWithPosition(
            shipmentId = "shipment-empty",
            lat = "37.4220",
            lng = "-122.0841",
            recordedAt = now.minusSeconds(30).toString()
        )
        val pinsCal = driverMapPins(emptyShipment, calDetails, now)
        assertTrue(pinsCal.isEmpty())

        // Null Island (0.0, 0.0)
        val nullIslandDetails = detailsWithPosition(
            shipmentId = "shipment-empty",
            lat = "0.0",
            lng = "0.0",
            recordedAt = now.minusSeconds(30).toString()
        )
        val pinsNullIsland = driverMapPins(emptyShipment, nullIslandDetails, now)
        assertTrue(pinsNullIsland.isEmpty())

        // Outside Philippine bounds (lat in 4.5..21.5 && lng in 116.0..127.0)
        val northOfPh = detailsWithPosition(
            shipmentId = "shipment-empty",
            lat = "22.0000",
            lng = "120.9800",
            recordedAt = now.minusSeconds(30).toString()
        )
        assertTrue(driverMapPins(emptyShipment, northOfPh, now).isEmpty())

        val southOfPh = detailsWithPosition(
            shipmentId = "shipment-empty",
            lat = "4.0000",
            lng = "120.9800",
            recordedAt = now.minusSeconds(30).toString()
        )
        assertTrue(driverMapPins(emptyShipment, southOfPh, now).isEmpty())

        val westOfPh = detailsWithPosition(
            shipmentId = "shipment-empty",
            lat = "14.6000",
            lng = "115.0000",
            recordedAt = now.minusSeconds(30).toString()
        )
        assertTrue(driverMapPins(emptyShipment, westOfPh, now).isEmpty())

        val eastOfPh = detailsWithPosition(
            shipmentId = "shipment-empty",
            lat = "14.6000",
            lng = "128.0000",
            recordedAt = now.minusSeconds(30).toString()
        )
        assertTrue(driverMapPins(emptyShipment, eastOfPh, now).isEmpty())

        // Valid coordinates within Philippine bounding box (e.g. Manila 14.6, 120.98)
        val validPhDetails = detailsWithPosition(
            shipmentId = "shipment-empty",
            lat = "14.6000",
            lng = "120.9800",
            recordedAt = now.minusSeconds(30).toString()
        )
        val pinsValidPh = driverMapPins(emptyShipment, validPhDetails, now)
        assertEquals(1, pinsValidPh.size)
        assertEquals("Vehicle", pinsValidPh.single().first)
        assertEquals(14.6000, pinsValidPh.single().second.latitude, 0.0001)
        assertEquals(120.9800, pinsValidPh.single().second.longitude, 0.0001)
    }

    @Test
    fun normalizeRouteGeoJsonRejectsEmptyOrTypelessJsonStrings() {
        assertNull(normalizeRouteGeoJson(JsonPrimitive("{}")))
        assertNull(normalizeRouteGeoJson(JsonPrimitive("{   }")))
        assertNull(normalizeRouteGeoJson(JsonPrimitive("{\"foo\":\"bar\"}")))

        assertNotNull(normalizeRouteGeoJson(JsonPrimitive("{\"type\":\"LineString\"}")))
        assertNotNull(normalizeRouteGeoJson(JsonPrimitive("{\"type\":\"FeatureCollection\"}")))

        assertNull(normalizeRouteGeoJson(buildJsonObject { put("foo", "bar") }))
        val validObj = buildJsonObject { put("type", "LineString") }
        assertNotNull(normalizeRouteGeoJson(validObj))
    }

    @Test
    fun driverMapPinsExpiresVehiclePinWhenClockTicksPast300Seconds() {
        val t0 = now
        val details = detailsWithPosition(
            lat = "14.8000",
            lng = "120.9750",
            recordedAt = t0.toString()
        )

        // At t0 + 290 seconds: still recent (<= 300s), Vehicle pin is included
        val pinsBefore = driverMapPins(luzonShipment, details, t0.plusSeconds(290))
        assertEquals(3, pinsBefore.size)
        assertEquals("Vehicle", pinsBefore.last().first)

        // At t0 + 301 seconds: stale (> 300s), Vehicle pin must be omitted
        val pinsAfter = driverMapPins(luzonShipment, details, t0.plusSeconds(301))
        assertEquals(2, pinsAfter.size)
        assertTrue(pinsAfter.none { it.first == "Vehicle" })
    }
}
