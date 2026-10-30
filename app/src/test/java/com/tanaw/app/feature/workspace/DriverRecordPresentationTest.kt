package com.tanaw.app.feature.workspace

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class DriverRecordPresentationTest {
    private fun obj(vararg fields:Pair<String,String>)=buildJsonObject {fields.forEach {put(it.first,it.second)}}
    @Test fun timelineUsesRpcTransitionsNotesAndEventsInActualTimeOrder() {
        val details=buildJsonObject {
            put("history",JsonArray(listOf(obj("from_status" to "assigned","to_status" to "in_transit","note" to "Cargo loaded","created_at" to "2026-10-28T10:00:00+08:00"))))
            put("events",JsonArray(listOf(buildJsonObject {put("event_type","pickup_arrived");put("occurred_at","2026-10-28T01:00:00Z");put("meta",obj("reason" to "Fresh location"))})))
        }
        val rows=DriverRecordPresentation.timeline(details)
        assertEquals("Pickup arrived · Fresh location",rows[0].label)
        assertEquals("Assigned → In transit · Cargo loaded",rows[1].label)
    }
    @Test fun incompleteTimelineIsExplicitAndUndatedRowsSortLast() {
        val rows=DriverRecordPresentation.timeline(buildJsonObject {
            put("history",JsonArray(listOf(obj("status" to "wrong_field"))))
            put("events",JsonArray(listOf(obj("event_type" to "route_started","occurred_at" to "2026-10-28T01:00:00Z"))))
        })
        assertEquals("Route started",rows[0].label);assertEquals("Status unavailable",rows[1].label)
    }
    @Test fun missingFailedOrWrongTripExpensesNeverBecomeZero() {
        assertTrue(DriverRecordPresentation.expenseTotal(null,"s").startsWith("Unavailable"))
        assertTrue(DriverRecordPresentation.expenseTotal(obj("shipment_id" to "s"),"s").startsWith("Unavailable"))
        for(details in listOf(buildJsonObject {put("shipment_id","s");put("expenses",JsonArray(emptyList()));put("evidence_error","denied")},buildJsonObject {put("shipment_id","other");put("expenses",JsonArray(emptyList()))})) {
            assertTrue(DriverRecordPresentation.expenseTotal(details,"s").startsWith("Unavailable"))
        }
    }
    @Test fun confirmedEmptyIsZeroAndDecimalTotalsAreExact() {
        fun details(vararg amounts:String)=buildJsonObject {put("shipment_id","s");put("expenses",JsonArray(amounts.map {obj("amount" to it)}))}
        assertEquals(MobilePresentation.money("0"),DriverRecordPresentation.expenseTotal(details(),"s"))
        assertEquals(MobilePresentation.money("0.30"),DriverRecordPresentation.expenseTotal(details("0.10","0.20"),"s"))
        for(value in listOf("NaN","","0","-1","not recorded"))assertTrue(DriverRecordPresentation.expenseTotal(details(value),"s").startsWith("Unavailable"))
    }
    @Test fun freshWorkspaceWinsAndHistoricalActiveTripsCannotSupplyActions() {
        val live=buildJsonObject {put("shipments",JsonArray(listOf(obj("id" to "s","status" to "in_transit"))))}
        val history=buildJsonObject {put("shipments",JsonArray(listOf(obj("id" to "s","status" to "assigned"),obj("id" to "gone","status" to "assigned"),obj("id" to "old","status" to "completed"))))}
        val records=DriverRecordPresentation.shipments(live,history)
        assertEquals(listOf("s","old"),records.map {it.value("id")});assertEquals("in_transit",records.first().value("status"))
    }
    @Test fun sectionErrorsAreNotEmptyAndLegacyMissingIsUnknown() {
        assertEquals("unavailable",DriverRecordPresentation.section(buildJsonObject{},"compliance"))
        assertEquals("empty",DriverRecordPresentation.section(buildJsonObject {put("compliance",JsonArray(emptyList()))},"compliance"))
        assertEquals("error",DriverRecordPresentation.section(buildJsonObject {put("driver_sections",obj("compliance" to "error"))},"compliance"))
    }
    @Test fun historyCannotReplaceLiveDeliveryOrRestoreAnActiveAssignment() {
        val live=buildJsonObject {put("shipments",JsonArray(listOf(obj("id" to "s"))));put("driver_deliveries",JsonArray(emptyList()))}
        val history=buildJsonObject {
            put("shipments",JsonArray(listOf(obj("id" to "s","status" to "assigned"),obj("id" to "old","status" to "completed"))))
            put("driver_deliveries",JsonArray(listOf(obj("id" to "stale","shipment_id" to "s"),obj("id" to "historical","shipment_id" to "old"))))
        }
        assertEquals(listOf("historical"),DriverRecordPresentation.deliveries(live,history).map{it.value("id")})
    }
    @Test fun offlineSnapshotPreservesUnavailableSectionMetadata() {
        val data=buildJsonObject {put("profile",obj("id" to "owner","role" to "driver"));put("driver_sections",obj("compliance" to "error"))}
        assertEquals("error",DriverRecordPresentation.section(RecoveryPolicy.workspace(data,"owner")!!,"compliance"))
    }
}
