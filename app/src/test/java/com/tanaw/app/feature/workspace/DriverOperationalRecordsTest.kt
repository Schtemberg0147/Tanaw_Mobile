package com.tanaw.app.feature.workspace

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class DriverOperationalRecordsTest {
    @Test fun changedDatasetCannotBeMergedAcrossPages() {
        val first=buildJsonObject {put("revision","first")}
        assertTrue(DriverHistory.sameRevision(first,first))
        assertFalse(DriverHistory.sameRevision(first,buildJsonObject {put("revision","changed")}))
    }
    private fun page(period:String,offset:Int,vararg ids:String)=buildJsonObject {
        put("period",period);put("offset",offset);put("total",225)
        put("shipments",buildJsonArray {ids.forEach {add(buildJsonObject {put("id",it)})}})
        put("driver_deliveries",buildJsonArray {ids.forEach {add(buildJsonObject {put("id","delivery-$it");put("shipment_id",it)})}})
    }
    @Test fun fuelUsesDecimalMoneyAndRejectsUnsupportedPrecision() {
        assertEquals("684.50",FuelEntry.parse("10.25","66.78","12000.1","Station")?.total?.toPlainString())
        listOf("NaN","Infinity","-1","0","10001","1.234").forEach {assertNull(FuelEntry.parse(it,"66.78","12000.1",""))}
        assertNull(FuelEntry.parse("10","2.001","12000.1",""))
        assertNull(FuelEntry.parse("10","60","12000.12",""))
        assertNull(FuelEntry.parse("10000","10000","0",""))
    }
    @Test fun pagesKeepLinkedDeliveriesAndDeduplicateOverlaps() {
        val first=page("This month",0,"a","b")
        val merged=DriverHistory.merge(first,page("This month",2,"b","c"))
        assertEquals(listOf("a","b","c"),merged.rows("shipments").map {it.value("id")})
        assertEquals(listOf("a","b","c"),merged.rows("driver_deliveries").map {it.value("shipment_id")})
        assertEquals("225",merged.value("total"))
    }
    @Test fun periodSwitchAndRefreshReplacePreviousRecords() {
        val first=page("This month",0,"a")
        assertEquals(listOf("b"),DriverHistory.merge(first,page("Last month",0,"b")).rows("shipments").map {it.value("id")})
        assertEquals(listOf("c"),DriverHistory.merge(first,page("This month",0,"c")).rows("shipments").map {it.value("id")})
    }
    @Test fun unknownAggregatesStayUnknown() {
        assertEquals("Not fully recorded",DriverHistory.allowance(buildJsonObject {put("allowance_missing",1);put("allowance_total",0)}))
        assertEquals("Not recorded",DriverHistory.onTime(buildJsonObject {put("on_time_known",0)}))
        assertEquals("75% (4 recorded)",DriverHistory.onTime(buildJsonObject {put("on_time_known",4);put("on_time_count",3)}))
    }
}
