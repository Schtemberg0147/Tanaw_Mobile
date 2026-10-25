package com.tanaw.app.feature.workspace

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class DriverDirectionsTest {
    private fun shipment(vararg fields:Pair<String,String>)=buildJsonObject {fields.forEach {put(it.first,it.second)}}
    @Test fun olderAssignmentUsesItsAddressWithoutInventingCoordinates() {
        val data=shipment("pickup_address" to "Manila South Harbor", "dropoff_address" to "Destination & gate #2")
        assertEquals("Manila South Harbor",DriverDirections.target(data,false))
        assertTrue(DriverDirections.url(data,true)!!.contains("Destination+%26+gate+%232"))
        assertTrue(DriverDirections.tripUrl(data)!!.contains("origin=Manila+South+Harbor&destination=Destination+%26+gate+%232"))
    }
    @Test fun confirmedPinsTakePriorityAndInvalidPinsFallBackToAddress() {
        assertEquals("14.6,120.9",DriverDirections.target(shipment("dropoff_lat" to "14.6","dropoff_lng" to "120.9"),true))
        assertEquals("Recorded address",DriverDirections.target(shipment("pickup_lat" to "NaN","pickup_lng" to "120","pickup_address" to "Recorded address"),false))
        assertNull(DriverDirections.url(shipment(),true))
        assertNull(DriverDirections.tripUrl(shipment("dropoff_address" to "Destination")))
    }
}
