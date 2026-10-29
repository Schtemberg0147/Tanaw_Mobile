package com.tanaw.app.feature.workspace

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class DriverStopPinsTest {
    @Test fun missingInvalidAndNonfinitePinsNeverBecomeMapLocations() {
        assertTrue(driverStopPins(buildJsonObject{}).isEmpty())
        for(value in listOf("NaN","Infinity","91","-91","invalid")) {
            assertTrue(driverStopPins(buildJsonObject{put("pickup_lat",value);put("pickup_lng","120")}).isEmpty())
        }
    }
    @Test fun previewUsesOnlySavedStopCoordinatesAndAcceptsZero() {
        val pins=driverStopPins(buildJsonObject{put("pickup_lat",0);put("pickup_lng",0);put("dropoff_lat",15);put("dropoff_lng",120)})
        assertEquals(listOf("Pickup","Destination"),pins.map{it.first})
        assertEquals(0.0,pins.first().second.latitude,0.0)
        assertEquals(120.0,pins.last().second.longitude,0.0)
    }
}
