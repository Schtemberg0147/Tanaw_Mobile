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
    @Test fun refreshedAssignmentCannotDisplayAnOldVerifiedRoute() {
        val shipment=buildJsonObject{put("weight_kg",6000);put("pickup_lat",14.6);put("pickup_lng",120.96);put("dropoff_lat",14.65);put("dropoff_lng",120.98);put("route_geometry",buildJsonObject{})}
        val plan=JsonObject(shipment.filterKeys{it!="weight_kg" && it!="route_geometry"}+mapOf("truck_id" to JsonPrimitive("truck"),"cargo_weight_kg" to JsonPrimitive(6000),"geometry" to buildJsonObject{}))
        val routing=JsonObject(status()+mapOf("plan" to plan))
        assertEquals(routing,DriverRouteStatus.forAssignment(routing,shipment,buildJsonObject{put("truck_id","truck")}))
        assertTrue(DriverRouteStatus.forAssignment(routing,shipment,buildJsonObject{put("truck_id","replacement")}).containsKey("reason"))
    }
}
