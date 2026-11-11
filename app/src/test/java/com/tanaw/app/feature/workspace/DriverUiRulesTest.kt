package com.tanaw.app.feature.workspace

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DriverUiRulesTest {
    private fun obj(vararg fields:Pair<String,String>)=buildJsonObject {fields.forEach{put(it.first,it.second)}}
    @Test fun failedOrConfirmedEmptyDeliveryReadNeverFallsBackToOlderRows() {
        val older=buildJsonArray {add(obj("id" to "old","shipment_id" to "s","status" to "assigned"))}
        for(state in listOf("error","empty","unavailable")) {
            val data=buildJsonObject {
                put("deliveries",older)
                put("driver_sections",obj("driver_deliveries" to state))
                if(state=="empty")put("driver_deliveries",JsonArray(emptyList()))
            }
            assertTrue("Must not revive delivery after $state",DriverUiRules.delivery(data,"s").isEmpty())
        }
    }
    @Test fun missingActiveTruckNeverDisplaysPermanentTruckAsTripVehicle() {
        val data=buildJsonObject {
            put("driver",obj("id" to "driver"))
            put("driver_trucks",buildJsonArray {add(obj("id" to "roster","assigned_driver_id" to "driver"))})
            put("driver_deliveries",buildJsonArray {add(obj("status" to "assigned","truck_id" to "missing"))})
        }
        assertNull(DriverUiRules.truck(data))
    }
    @Test fun deliveredProofStaysAccessibleUntilOfficeCompletion() {
        assertTrue(DriverUiRules.isActive("delivered"))
        assertFalse(DriverUiRules.isActive("completed"))
        assertFalse(DriverUiRules.isActive("cancelled"))
        assertEquals(5,DriverUiRules.stage(obj("status" to "delivered")))
    }
    @Test fun milestonesUseRecordedBackendEvents() {
        assertEquals(0,DriverUiRules.stage(obj("status" to "assigned")))
        assertEquals(1,DriverUiRules.stage(obj("status" to "assigned","route_started_at" to "recorded")))
        assertEquals(2,DriverUiRules.stage(obj("status" to "assigned","pickup_arrived_at" to "recorded")))
        assertEquals(3,DriverUiRules.stage(obj("status" to "loading")))
        assertEquals(4,DriverUiRules.stage(obj("status" to "in_transit")))
    }
    @Test fun missingComplianceNeverBecomesValid() {
        val today=LocalDate.of(2026,10,25)
        assertEquals("NOT REVIEWED",DriverUiRules.compliance(obj(),today))
        assertEquals("EXPIRY UNKNOWN",DriverUiRules.compliance(obj("review_status" to "approved"),today))
        assertEquals("PENDING",DriverUiRules.compliance(obj("review_status" to "pending","expiry_date" to "2030-01-01"),today))
        assertEquals("EXPIRED",DriverUiRules.compliance(obj("review_status" to "approved","expiry_date" to "2026-10-24"),today))
    }
    @Test fun periodsUsePhilippineCalendarBoundaries() {
        val today=LocalDate.of(2026,10,25)
        assertTrue(DriverUiRules.inPeriod(obj("scheduled_departure" to "2026-09-30T16:00:00Z"),"This month",today))
        assertFalse(DriverUiRules.inPeriod(obj("scheduled_departure" to "2026-09-30T15:59:00Z"),"This month",today))
        assertTrue(DriverUiRules.inPeriod(obj(),"All records",today))
    }
    @Test fun missingAllowanceAndArrivalAreExplicit() {
        val records=listOf(obj("id" to "shipment","status" to "completed"))
        assertEquals("Not fully recorded",DriverUiRules.allowanceTotal(obj(),records))
        assertEquals("Not recorded",DriverUiRules.onTime(obj(),records))
    }
    @Test fun currentDeliveryTruckTakesPriorityOverRosterTruck() {
        val data=buildJsonObject {
            put("driver",obj("id" to "driver"))
            put("driver_trucks",buildJsonArray {
                add(obj("id" to "roster","assigned_driver_id" to "driver"))
                add(obj("id" to "trip"))
            })
            put("driver_deliveries",buildJsonArray {add(obj("status" to "assigned","truck_id" to "trip"))})
        }
        assertEquals("trip",DriverUiRules.truck(data)?.value("id"))
    }
    @Test fun deliveredAndReviewShipmentsCountAsDriverCompletedUnlessCorrectionRequested() {
        val reviewShipment=obj("id" to "s1","status" to "review","driver_allowance_php" to "500","scheduled_arrival" to "2026-10-25T10:00:00Z")
        val delivered=obj("id" to "d1","shipment_id" to "s1","status" to "delivered","proof_review_status" to "pending_review","arrived_at" to "2026-10-25T09:30:00Z")
        val correctionRequested=obj("id" to "d1","shipment_id" to "s1","status" to "delivered","proof_review_status" to "correction_requested","arrived_at" to "2026-10-25T09:30:00Z")

        assertTrue(DriverUiRules.isDriverCompleted(reviewShipment,delivered))
        assertFalse(DriverUiRules.isActiveAssignment(reviewShipment,delivered))
        assertTrue(DriverUiRules.matches(reviewShipment,delivered,"Completed"))
        assertFalse(DriverUiRules.matches(reviewShipment,delivered,"Active"))
        assertFalse(DriverUiRules.matches(reviewShipment,delivered,"Ongoing"))
        assertEquals("Delivered",DriverUiRules.tripStatusLabel(reviewShipment,delivered))

        assertFalse(DriverUiRules.isDriverCompleted(reviewShipment,correctionRequested))
        assertTrue(DriverUiRules.isActiveAssignment(reviewShipment,correctionRequested))
        assertFalse(DriverUiRules.matches(reviewShipment,correctionRequested,"Completed"))
        assertTrue(DriverUiRules.matches(reviewShipment,correctionRequested,"Active"))
        assertTrue(DriverUiRules.matches(reviewShipment,correctionRequested,"Ongoing"))
        assertEquals("Correction requested",DriverUiRules.tripStatusLabel(reviewShipment,correctionRequested))

        val data=buildJsonObject {put("driver_deliveries",buildJsonArray {add(delivered)})}
        assertEquals(MobilePresentation.money("500"),DriverUiRules.allowanceTotal(data,listOf(reviewShipment)))
        assertEquals("100% (1 recorded)",DriverUiRules.onTime(data,listOf(reviewShipment)))
    }
}
