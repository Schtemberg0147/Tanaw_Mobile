package com.tanaw.app.feature.workspace

import java.time.Instant
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class DriverRouteStatusTest {
    private val now=Instant.parse("2026-10-28T12:00:00Z")
    private fun status()=buildJsonObject {
        put("route_verified",true);put("eta_at",now.plusSeconds(600).toString());put("calculated_at",now.minusSeconds(10).toString())
        put("position_at",now.minusSeconds(20).toString());put("expires_at",now.plusSeconds(100).toString());put("leg","destination")
    }
    @Test fun freshTruckEstimateShowsTargetAndPhilippineArrivalTime() {
        assertTrue(DriverRouteStatus.eta(status(),false,now).startsWith("ETA to destination:"))
        assertTrue(DriverRouteStatus.eta(status(),false,now).contains("PHT"))
    }
    @Test fun offlineStaleAndUnverifiedDataCannotBecomeLiveEta() {
        assertTrue(DriverRouteStatus.eta(status(),true,now).contains("offline"))
        assertTrue(DriverRouteStatus.eta(status(),false,now.plusSeconds(101)).contains("expired"))
        assertTrue(DriverRouteStatus.eta(buildJsonObject{},false,now).contains("office-confirmed"))
        for(patch in listOf("position_at" to now.minusSeconds(121).toString(),"calculated_at" to now.plusSeconds(60).toString(),"eta_at" to "invalid","leg" to "unknown")) {
            val record=JsonObject(status()+mapOf(patch.first to JsonPrimitive(patch.second)))
            assertFalse(DriverRouteStatus.eta(record,false,now).startsWith("ETA to"))
        }
    }
    @Test fun providerReasonIsVisibleAndNeverReusesScheduledArrival() {
        val record=JsonObject(status()+mapOf("reason" to JsonPrimitive("Fresh GPS required")))
        assertEquals("ETA unavailable: Fresh GPS required",DriverRouteStatus.eta(record,false,now))
    }
    @Test fun oldCalculationAndOverlongExpiryCannotMasqueradeAsFreshEta() {
        for(patch in listOf("calculated_at" to now.minusSeconds(121).toString(),"expires_at" to now.plusSeconds(121).toString())) {
            assertFalse(DriverRouteStatus.eta(JsonObject(status()+mapOf(patch.first to JsonPrimitive(patch.second))),false,now).startsWith("ETA to"))
        }
    }
    @Test fun loadingStatusShowsLoadingMessageAndBypassesAssignmentMismatch() {
        val loading = buildJsonObject { put("loading", true) }
        val shipment = buildJsonObject { put("weight_kg", 6000) }
        val delivery = buildJsonObject { put("truck_id", "truck") }
        assertEquals(loading, DriverRouteStatus.forAssignment(loading, shipment, delivery))
        assertEquals("Loading live route status and ETA…", DriverRouteStatus.eta(loading, false, now))
        assertEquals("Live ETA unavailable while offline", DriverRouteStatus.eta(loading, true, now))
    }
    @Test fun refreshedAssignmentCannotDisplayAnOldVerifiedRoute() {
        val shipment=buildJsonObject{put("weight_kg",6000);put("pickup_lat",14.6);put("pickup_lng",120.96);put("dropoff_lat",14.65);put("dropoff_lng",120.98);put("route_geometry",buildJsonObject{})}
        val plan=JsonObject(shipment.filterKeys{it!="weight_kg" && it!="route_geometry"}+mapOf("truck_id" to JsonPrimitive("truck"),"cargo_weight_kg" to JsonPrimitive(6000),"geometry" to buildJsonObject{}))
        val routing=JsonObject(status()+mapOf("plan" to plan))
        assertEquals(routing,DriverRouteStatus.forAssignment(routing,shipment,buildJsonObject{put("truck_id","truck")}))
        assertTrue(DriverRouteStatus.forAssignment(routing,shipment,buildJsonObject{put("truck_id","replacement")}).containsKey("reason"))
    }
    @Test fun extractGeoJsonHandlesObjectsStringsAndNulls() {
        assertNull(DriverRouteStatus.extractGeoJson(null))
        assertNull(DriverRouteStatus.extractGeoJson(JsonNull))
        assertNull(DriverRouteStatus.extractGeoJson(buildJsonObject {}))
        assertNull(DriverRouteStatus.extractGeoJson(JsonPrimitive("")))
        assertNull(DriverRouteStatus.extractGeoJson(JsonPrimitive("   ")))
        assertNull(DriverRouteStatus.extractGeoJson(JsonPrimitive("not a json")))
        assertNull(DriverRouteStatus.extractGeoJson(JsonPrimitive(12345)))

        val geoObj = buildJsonObject {
            put("type", "LineString")
            put("coordinates", buildJsonArray {})
        }
        assertEquals(geoObj.toString(), DriverRouteStatus.extractGeoJson(geoObj))

        val geoStr = "{\"type\":\"LineString\",\"coordinates\":[]}"
        assertEquals(geoStr, DriverRouteStatus.extractGeoJson(JsonPrimitive(geoStr)))
        assertEquals(geoStr, DriverRouteStatus.extractGeoJson(JsonPrimitive("  $geoStr  ")))
    }
    @Test fun resolvedGeometryPrefersShipmentAndFallsBackToVerifiedRouting() {
        val geoStr = "{\"type\":\"LineString\",\"coordinates\":[]}"
        val shipmentWithGeom = buildJsonObject {
            put("route_geometry", geoStr)
        }
        val plan = buildJsonObject {
            put("geometry", buildJsonObject { put("type", "Fallback") })
        }
        val verifiedRouting = buildJsonObject {
            put("route_verified", true)
            put("plan", plan)
        }
        val unverifiedRouting = buildJsonObject {
            put("route_verified", false)
            put("plan", plan)
        }

        assertEquals(geoStr, DriverRouteStatus.resolvedGeometry(shipmentWithGeom, verifiedRouting))

        val shipmentWithoutGeom = buildJsonObject {}
        assertEquals(plan.obj("geometry").toString(), DriverRouteStatus.resolvedGeometry(shipmentWithoutGeom, verifiedRouting))
        assertNull(DriverRouteStatus.resolvedGeometry(shipmentWithoutGeom, unverifiedRouting))
        assertNull(DriverRouteStatus.resolvedGeometry(shipmentWithoutGeom, null))
    }
    @Test fun forAssignmentMatchesWhenGeometryIsStringOrOmittedFromShipment() {
        val geoStr = "{\"type\":\"LineString\",\"coordinates\":[]}"
        val geoObj = buildJsonObject {
            put("type", "LineString")
            put("coordinates", buildJsonArray {})
        }
        val baseShipment = buildJsonObject {
            put("weight_kg", 6000)
            put("pickup_lat", 14.6)
            put("pickup_lng", 120.96)
            put("dropoff_lat", 14.65)
            put("dropoff_lng", 120.98)
        }
        val basePlan = JsonObject(baseShipment.filterKeys { it != "weight_kg" } + mapOf(
            "truck_id" to JsonPrimitive("truck"),
            "cargo_weight_kg" to JsonPrimitive(6000),
            "geometry" to geoObj
        ))
        val routing = JsonObject(status() + mapOf("plan" to basePlan))
        val delivery = buildJsonObject { put("truck_id", "truck") }

        // 1. String-encoded route_geometry matching plan's JsonObject geometry
        val shipmentWithStringGeom = JsonObject(baseShipment + mapOf("route_geometry" to JsonPrimitive(geoStr)))
        assertEquals(routing, DriverRouteStatus.forAssignment(routing, shipmentWithStringGeom, delivery))

        // 2. Omitted route_geometry on shipment summary row allows verified routing to match
        assertEquals(routing, DriverRouteStatus.forAssignment(routing, baseShipment, delivery))

        // 3. Differing route_geometry is rejected
        val differingShipment = JsonObject(baseShipment + mapOf("route_geometry" to JsonPrimitive("{\"type\":\"Point\"}")))
        assertTrue(DriverRouteStatus.forAssignment(routing, differingShipment, delivery).containsKey("reason"))
    }

    @Test fun forAssignmentMatchesWhenJsonFormattingAndKeyOrderDiffer() {
        val baseShipment = buildJsonObject {
            put("weight_kg", 6000)
            put("pickup_lat", 14.6)
            put("pickup_lng", 120.96)
            put("dropoff_lat", 14.65)
            put("dropoff_lng", 120.98)
        }
        val planGeom = buildJsonObject {
            put("coordinates", buildJsonArray {
                add(buildJsonArray { add(120.96); add(14.6) })
                add(buildJsonArray { add(120.98); add(14.65) })
            })
            put("type", "LineString")
        }
        val plan = JsonObject(baseShipment.filterKeys { it != "weight_kg" } + mapOf(
            "truck_id" to JsonPrimitive("truck"),
            "cargo_weight_kg" to JsonPrimitive(6000),
            "geometry" to planGeom
        ))
        val routing = JsonObject(status() + mapOf("plan" to plan))
        val delivery = buildJsonObject { put("truck_id", "truck") }

        val formattedShipmentGeom = """
            {
              "type": "LineString",
              "coordinates": [
                [120.96, 14.6],
                [120.98, 14.65]
              ]
            }
        """.trimIndent()
        val shipment = JsonObject(baseShipment + mapOf("route_geometry" to JsonPrimitive(formattedShipmentGeom)))

        assertEquals(routing, DriverRouteStatus.forAssignment(routing, shipment, delivery))
    }

    @Test fun forAssignmentRejectsCorruptedOrInvalidJsonStringsWithoutMatchingOnNull() {
        val baseShipment = buildJsonObject {
            put("weight_kg", 6000)
            put("pickup_lat", 14.6)
            put("pickup_lng", 120.96)
            put("dropoff_lat", 14.65)
            put("dropoff_lng", 120.98)
        }
        val planWithoutGeom = JsonObject(baseShipment.filterKeys { it != "weight_kg" } + mapOf(
            "truck_id" to JsonPrimitive("truck"),
            "cargo_weight_kg" to JsonPrimitive(6000)
        ))
        val routingWithoutGeom = JsonObject(status() + mapOf("plan" to planWithoutGeom))
        val delivery = buildJsonObject { put("truck_id", "truck") }

        val corruptedShipment = JsonObject(baseShipment + mapOf("route_geometry" to JsonPrimitive("corrupted")))
        assertTrue(DriverRouteStatus.forAssignment(routingWithoutGeom, corruptedShipment, delivery).containsKey("reason"))

        val invalidJsonShipment = JsonObject(baseShipment + mapOf("route_geometry" to JsonPrimitive("{invalid_json}")))
        assertTrue(DriverRouteStatus.forAssignment(routingWithoutGeom, invalidJsonShipment, delivery).containsKey("reason"))

        val validGeom = buildJsonObject {
            put("type", "LineString")
            put("coordinates", buildJsonArray {})
        }
        val routingWithValidGeom = JsonObject(status() + mapOf("plan" to JsonObject(planWithoutGeom + mapOf("geometry" to validGeom))))
        assertTrue(DriverRouteStatus.forAssignment(routingWithValidGeom, corruptedShipment, delivery).containsKey("reason"))
        assertTrue(DriverRouteStatus.forAssignment(routingWithValidGeom, invalidJsonShipment, delivery).containsKey("reason"))
    }

    @Test fun extractGeoJsonRejectsEmptyOrTypeMissingJsonObjects() {
        assertNull(DriverRouteStatus.extractGeoJson(JsonPrimitive("{}")))
        assertNull(DriverRouteStatus.extractGeoJson(JsonPrimitive("{   }")))
        assertNull(DriverRouteStatus.extractGeoJson(JsonPrimitive("{invalid_json}")))
        assertNull(DriverRouteStatus.extractGeoJson(buildJsonObject { put("coordinates", buildJsonArray {}) }))
        assertNull(DriverRouteStatus.extractGeoJson(JsonPrimitive("{\"coordinates\":[]}")))
        assertNull(DriverRouteStatus.extractGeoJson(buildJsonObject { put("type", "") }))
        assertNull(DriverRouteStatus.extractGeoJson(buildJsonObject { put("type", "   ") }))
    }

    @Test fun resolvedGeometryFallsBackWhenShipmentGeometryIsEmptyJsonString() {
        val planGeom = buildJsonObject {
            put("type", "LineString")
            put("coordinates", buildJsonArray {})
        }
        val verifiedRouting = buildJsonObject {
            put("route_verified", true)
            put("plan", buildJsonObject { put("geometry", planGeom) })
        }

        val shipmentWithEmptyJson = buildJsonObject {
            put("route_geometry", "{}")
        }
        assertEquals(planGeom.toString(), DriverRouteStatus.resolvedGeometry(shipmentWithEmptyJson, verifiedRouting))

        val shipmentWithBlankObjJson = buildJsonObject {
            put("route_geometry", "{   }")
        }
        assertEquals(planGeom.toString(), DriverRouteStatus.resolvedGeometry(shipmentWithBlankObjJson, verifiedRouting))
    }

    @Test fun etaAcceptsMinorClockSkewOnGpsPositionTimestamp() {
        val skewedStatus = JsonObject(status() + mapOf("position_at" to JsonPrimitive(now.plusSeconds(3).toString())))
        assertTrue(DriverRouteStatus.eta(skewedStatus, false, now).startsWith("ETA to destination:"))

        val excessiveFutureStatus = JsonObject(status() + mapOf("position_at" to JsonPrimitive(now.plusSeconds(6).toString())))
        assertFalse(DriverRouteStatus.eta(excessiveFutureStatus, false, now).startsWith("ETA to"))
    }
}
