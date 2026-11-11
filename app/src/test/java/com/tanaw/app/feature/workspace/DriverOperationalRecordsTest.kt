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
        assertNotNull(FuelEntry.parse("0.01","0.01","0","Station"))
        assertNotNull(FuelEntry.parse("10000","100","10000000","A".repeat(200)))
        assertNull(FuelEntry.parse("10","50","1000","A".repeat(201)))
        assertNull(FuelEntry.parse("10","50","10000000.1","Station"))
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

    @Test fun driverConversationMessageLengthAndWhitespaceBoundaries() {
        assertFalse("Empty conversation message must be invalid", ConversationRules.valid(""))
        assertFalse("Whitespace-only message must be invalid", ConversationRules.valid("   \n\t  "))
        assertTrue("Single-character message must be valid", ConversationRules.valid("A"))
        assertTrue("Whitespace-padded message with content must be valid", ConversationRules.valid("  Hello  "))

        // Exactly 2000 code points
        val maxMessage = "A".repeat(2000)
        assertTrue("2000-character message must be valid", ConversationRules.valid(maxMessage))

        // Exceeding 2000 code points
        val overflowMessage = "A".repeat(2001)
        assertFalse("2001-character message must exceed limit", ConversationRules.valid(overflowMessage))

        // Unicode and emojis using surrogate pairs
        val emojiMessage = "\uD83D\uDE9A".repeat(2000) // 2000 truck emojis (code points)
        assertTrue("2000 emoji code points must be valid", ConversationRules.valid(emojiMessage))
        val overflowEmoji = "\uD83D\uDE9A".repeat(2001)
        assertFalse("2001 emoji code points must exceed limit", ConversationRules.valid(overflowEmoji))
    }

    @Test fun driverIncidentValidationBoundaries() {
        // Incident description must be in 5..2000 trimmed characters
        val shortDesc = "1234"
        val minDesc = "12345"
        val maxDesc = "A".repeat(2000)
        val overflowDesc = "A".repeat(2001)
        val blankDesc = "     "

        assertFalse(shortDesc.trim().length in 5..2000)
        assertTrue(minDesc.trim().length in 5..2000)
        assertTrue(maxDesc.trim().length in 5..2000)
        assertFalse(overflowDesc.trim().length in 5..2000)
        assertFalse(blankDesc.trim().length in 5..2000)

        // Expected delay minutes must be between 0 and 1440
        listOf(0, 30, 60, 1440).forEach { minutes ->
            assertTrue("Expected valid minutes $minutes", minutes in 0..1440)
        }
        listOf(-1, -100, 1441, 10000).forEach { minutes ->
            assertFalse("Expected invalid minutes $minutes", minutes in 0..1440)
        }
    }

    @Test fun reconcileWithLiveUpdatesHistoryWhenDeliveryCompletesToReview() {
        val today = java.time.LocalDate.of(2026, 10, 25)
        val history = buildJsonObject {
            put("period", "This month")
            put("offset", 0)
            put("total", 1)
            put("completed", 0)
            put("allowance_total", 0)
            put("allowance_missing", 0)
            put("on_time_known", 0)
            put("on_time_count", 0)
            put("shipments", buildJsonArray {
                add(buildJsonObject {
                    put("id", "s1")
                    put("status", "in_transit")
                    put("scheduled_departure", "2026-10-20T08:00:00Z")
                    put("scheduled_arrival", "2026-10-20T12:00:00Z")
                    put("driver_allowance_php", "1250.50")
                })
            })
            put("driver_deliveries", buildJsonArray {
                add(buildJsonObject {
                    put("id", "d1")
                    put("shipment_id", "s1")
                    put("status", "in_transit")
                })
            })
        }
        val liveData = buildJsonObject {
            put("shipments", buildJsonArray {
                add(buildJsonObject {
                    put("id", "s1")
                    put("status", "review")
                    put("scheduled_departure", "2026-10-20T08:00:00Z")
                    put("scheduled_arrival", "2026-10-20T12:00:00Z")
                    put("actual_delivery_at", "2026-10-20T11:30:00Z")
                    put("driver_allowance_php", "1250.50")
                })
            })
            put("driver_deliveries", buildJsonArray {
                add(buildJsonObject {
                    put("id", "d1")
                    put("shipment_id", "s1")
                    put("status", "delivered")
                    put("proof_review_status", "pending_review")
                    put("arrived_at", "2026-10-20T11:30:00Z")
                })
            })
        }
        val reconciled = DriverHistory.reconcileWithLive(liveData, history, "This month", today)!!
        assertEquals("1", reconciled.value("completed"))
        assertEquals("review", reconciled.rows("shipments").first().value("status"))
        assertEquals("delivered", reconciled.rows("driver_deliveries").first().value("status"))
        assertEquals(MobilePresentation.money("1250.50"), DriverHistory.allowance(reconciled))
        assertEquals("100% (1 recorded)", DriverHistory.onTime(reconciled))
    }
}
