package com.tanaw.app.feature.workspace

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class DriverDirectionsTest {
    private fun shipment(vararg fields: Pair<String, String>) = buildJsonObject { fields.forEach { put(it.first, it.second) } }

    @Test
    fun olderAssignmentUsesItsAddressWithoutInventingCoordinates() {
        val data = shipment("pickup_address" to "Manila South Harbor", "dropoff_address" to "Destination & gate #2")
        assertEquals("Manila South Harbor", DriverDirections.target(data, false))
        assertTrue(DriverDirections.url(data, true)!!.contains("Destination+%26+gate+%232"))
        assertTrue(DriverDirections.tripUrl(data)!!.contains("origin=Manila+South+Harbor&destination=Destination+%26+gate+%232"))
    }

    @Test
    fun confirmedPinsTakePriorityAndInvalidPinsFallBackToAddress() {
        assertEquals("14.6,120.9", DriverDirections.target(shipment("dropoff_lat" to "14.6", "dropoff_lng" to "120.9"), true))
        assertEquals("Recorded address", DriverDirections.target(shipment("pickup_lat" to "NaN", "pickup_lng" to "120", "pickup_address" to "Recorded address"), false))
        assertNull(DriverDirections.url(shipment(), true))
        assertNull(DriverDirections.tripUrl(shipment("dropoff_address" to "Destination")))
    }

    @Test
    fun extractWaypointsWith10CoordinatesFeatureCollection() {
        // 10 coordinates along a route:
        // [120.00, 14.00] (start)
        // [120.01, 14.01] ... [120.08, 14.08] (8 intermediate points)
        // [120.09, 14.09] (end)
        val coords = (0..9).map { i ->
            val lng = 120.00 + i * 0.01
            val lat = 14.00 + i * 0.01
            "[${String.format(java.util.Locale.US, "%.2f", lng)}, ${String.format(java.util.Locale.US, "%.2f", lat)}]"
        }.joinToString(",")

        val geoJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "LineString",
                    "coordinates": [$coords]
                  }
                }
              ]
            }
        """.trimIndent()

        val waypoints = DriverDirections.extractWaypoints(geoJson, maxWaypoints = 6)

        // Verifies downsampling to maxWaypoints = 6
        assertEquals(6, waypoints.size)

        // Verifies [lng, lat] GeoJSON input converts to Pair(lat, lng)
        // For candidates (indices 1..8):
        // (1*8)/7 = 1 -> index 2 in original (14.02, 120.02)
        assertEquals(14.02, waypoints.first().first, 0.001)
        assertEquals(120.02, waypoints.first().second, 0.001)

        // Verifies endpoints (14.00, 120.00) and (14.09, 120.09) are excluded
        waypoints.forEach { (lat, lng) ->
            assertFalse(lat == 14.00 && lng == 120.00)
            assertFalse(lat == 14.09 && lng == 120.09)
        }

        // Also test corridorWaypointsParam formatting
        val param = DriverDirections.corridorWaypointsParam(geoJson, maxWaypoints = 6)
        assertNotNull(param)
        assertTrue(param!!.contains("|"))
        val parts = param.split("|")
        assertEquals(6, parts.size)
        assertEquals("14.02000,120.02000", parts[0])
    }

    @Test
    fun extractWaypointsEdgeCases() {
        // String-wrapped GeoJSON (JSON-encoded string)
        val wrapped = "\"{\\\"type\\\":\\\"LineString\\\",\\\"coordinates\\\":[[120.0,14.0],[120.05,14.05],[120.1,14.1]]}\""
        val fromWrapped = DriverDirections.extractWaypoints(wrapped)
        assertEquals(1, fromWrapped.size)
        assertEquals(14.05, fromWrapped.first().first, 0.001)
        assertEquals(120.05, fromWrapped.first().second, 0.001)

        // Direct LineString geometry
        val directLine = """{"type":"LineString","coordinates":[[120.0,14.0],[120.05,14.05],[120.1,14.1]]}"""
        assertEquals(1, DriverDirections.extractWaypoints(directLine).size)

        // Direct Feature
        val feature = """{"type":"Feature","geometry":{"type":"LineString","coordinates":[[120.0,14.0],[120.05,14.05],[120.1,14.1]]}}"""
        assertEquals(1, DriverDirections.extractWaypoints(feature).size)

        // 2-point line returns empty list (no intermediate waypoints)
        val twoPoint = """{"type":"LineString","coordinates":[[120.98,14.6],[120.94,15.4]]}"""
        assertTrue(DriverDirections.extractWaypoints(twoPoint).isEmpty())
        assertNull(DriverDirections.corridorWaypointsParam(twoPoint))

        // Malformed JSON returns empty list
        assertTrue(DriverDirections.extractWaypoints("not a json").isEmpty())
        assertNull(DriverDirections.corridorWaypointsParam("not a json"))

        // Null or blank returns empty list
        assertTrue(DriverDirections.extractWaypoints(null).isEmpty())
        assertTrue(DriverDirections.extractWaypoints("   ").isEmpty())
        assertNull(DriverDirections.corridorWaypointsParam(null))

        // maxWaypoints <= 0 returns empty list
        assertTrue(DriverDirections.extractWaypoints(directLine, maxWaypoints = 0).isEmpty())

        // Intermediate point too close to endpoint (< 0.002 degrees) is filtered out
        val nearEndpoint = """{"type":"LineString","coordinates":[[120.0,14.0],[120.001,14.001],[120.05,14.05]]}"""
        assertTrue(DriverDirections.extractWaypoints(nearEndpoint).isEmpty())
    }

    @Test
    fun urlAndTripUrlWithAndWithoutConfirmedGeoJson() {
        val shipment = shipment(
            "pickup_lat" to "14.5995",
            "pickup_lng" to "120.9842",
            "dropoff_lat" to "14.6500",
            "dropoff_lng" to "121.0500"
        )
        val geoJson = """{"type":"LineString","coordinates":[[120.9842,14.5995],[121.0100,14.6200],[121.0500,14.6500]]}"""

        // Without confirmedGeoJson (or null)
        val pickupUrlNoGeo = DriverDirections.url(shipment, false)
        val destUrlNoGeo = DriverDirections.url(shipment, true)
        val tripUrlNoGeo = DriverDirections.tripUrl(shipment)

        assertNotNull(pickupUrlNoGeo)
        assertNotNull(destUrlNoGeo)
        assertNotNull(tripUrlNoGeo)

        assertFalse(pickupUrlNoGeo!!.contains("&waypoints="))
        assertFalse(destUrlNoGeo!!.contains("&waypoints="))
        assertFalse(tripUrlNoGeo!!.contains("&waypoints="))

        // With confirmedGeoJson
        val pickupUrlWithGeo = DriverDirections.url(shipment, false, geoJson)
        val destUrlWithGeo = DriverDirections.url(shipment, true, geoJson)
        val tripUrlWithGeo = DriverDirections.tripUrl(shipment, geoJson)

        assertNotNull(pickupUrlWithGeo)
        assertNotNull(destUrlWithGeo)
        assertNotNull(tripUrlWithGeo)

        // Pickup directions must NOT append corridor waypoints
        assertFalse(pickupUrlWithGeo!!.contains("&waypoints="))

        // Destination directions and trip route MUST append encoded waypoints
        assertTrue(destUrlWithGeo!!.contains("&waypoints="))
        assertTrue(destUrlWithGeo.contains("14.62000%2C121.01000"))

        assertTrue(tripUrlWithGeo!!.contains("&waypoints="))
        assertTrue(tripUrlWithGeo.contains("14.62000%2C121.01000"))
    }

    @Test
    fun nullIslandCoordinatesFallBackToTextAddress() {
        val data = shipment(
            "dropoff_lat" to "0.0",
            "dropoff_lng" to "0.0",
            "dropoff_address" to "123 Warehouse Way",
            "pickup_lat" to "0",
            "pickup_lng" to "0.000",
            "pickup_address" to "456 Port Terminal"
        )
        assertEquals("123 Warehouse Way", DriverDirections.target(data, true))
        assertEquals("456 Port Terminal", DriverDirections.target(data, false))
    }

    @Test
    fun extractWaypointsSupportsMultiLineString() {
        val multiLine = """
            {
              "type": "MultiLineString",
              "coordinates": [
                [[120.0, 14.0], [120.02, 14.02]],
                [[120.04, 14.04], [120.06, 14.06], [120.08, 14.08]]
              ]
            }
        """.trimIndent()
        val waypoints = DriverDirections.extractWaypoints(multiLine)
        assertEquals(3, waypoints.size)
        assertEquals(14.02, waypoints.first().first, 0.01)
        assertEquals(120.02, waypoints.first().second, 0.01)

        val featureWithMulti = """
            {
              "type": "Feature",
              "geometry": {
                "type": "MultiLineString",
                "coordinates": [
                  [[120.0, 14.0], [120.02, 14.02]],
                  [[120.04, 14.04], [120.06, 14.06]]
                ]
              }
            }
        """.trimIndent()
        assertEquals(2, DriverDirections.extractWaypoints(featureWithMulti).size)
    }

    @Test
    fun waypointCappingConstrainsToNine() {
        val coords = (0..21).map { i ->
            val lng = 120.00 + i * 0.01
            val lat = 14.00 + i * 0.01
            "[${String.format(java.util.Locale.US, "%.2f", lng)}, ${String.format(java.util.Locale.US, "%.2f", lat)}]"
        }.joinToString(",")
        val geoJson = """{"type":"LineString","coordinates":[$coords]}"""

        val waypoints = DriverDirections.extractWaypoints(geoJson, maxWaypoints = 20)
        assertEquals(9, waypoints.size)

        val param = DriverDirections.corridorWaypointsParam(geoJson, maxWaypoints = 20)
        assertNotNull(param)
        assertEquals(9, param!!.split("|").size)
    }

    @Test
    fun twoPointLineAndInvalidCoordsReturnNullWaypointsParam() {
        // 2-point line yields 0 intermediate waypoints
        val twoPoint = """{"type":"LineString","coordinates":[[120.98,14.6],[120.94,15.4]]}"""
        assertNull(DriverDirections.corridorWaypointsParam(twoPoint))

        // Coordinates with Null Island (0,0) in the middle are ignored
        val nullIslandRoute = """
            {
              "type": "LineString",
              "coordinates": [
                [120.98, 14.60],
                [0.0, 0.0],
                [120.94, 15.40]
              ]
            }
        """.trimIndent()
        assertNull(DriverDirections.corridorWaypointsParam(nullIslandRoute))
    }

    @Test
    fun stageDirectionsUrlsWithClampedWaypoints() {
        val shipment = shipment(
            "pickup_address" to "Manila North Harbor",
            "dropoff_address" to "Clark Logistics Hub"
        )
        val coords = (0..20).map { i ->
            val lng = 120.98 + i * 0.01
            val lat = 14.60 + i * 0.05
            "[${String.format(java.util.Locale.US, "%.2f", lng)}, ${String.format(java.util.Locale.US, "%.2f", lat)}]"
        }.joinToString(",")
        val longGeoJson = """{"type":"LineString","coordinates":[$coords]}"""

        val destUrl = DriverDirections.url(shipment, true, longGeoJson)
        assertNotNull(destUrl)
        assertTrue(destUrl!!.contains("destination=Clark+Logistics+Hub"))
        assertTrue(destUrl.contains("&waypoints="))

        // Verify waypoints in URL are clamped to 9
        val waypointsSub = destUrl.substringAfter("&waypoints=").substringBefore("&")
        val decodedWaypoints = java.net.URLDecoder.decode(waypointsSub, "UTF-8")
        val waypointsCount = decodedWaypoints.split("|").size
        assertEquals(9, waypointsCount)

        val tripUrl = DriverDirections.tripUrl(shipment, longGeoJson)
        assertNotNull(tripUrl)
        assertTrue(tripUrl!!.contains("origin=Manila+North+Harbor"))
        assertTrue(tripUrl.contains("destination=Clark+Logistics+Hub"))
        val tripWaypointsSub = tripUrl.substringAfter("&waypoints=").substringBefore("&")
        val decodedTripWaypoints = java.net.URLDecoder.decode(tripWaypointsSub, "UTF-8")
        assertEquals(9, decodedTripWaypoints.split("|").size)
    }
}
