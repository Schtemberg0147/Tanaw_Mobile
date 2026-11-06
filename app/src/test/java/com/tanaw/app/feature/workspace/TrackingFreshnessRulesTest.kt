package com.tanaw.app.feature.workspace

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class TrackingFreshnessRulesTest {
    private val now = Instant.parse("2026-10-28T12:00:00Z")

    @Test
    fun evaluateFreshPositionUnderTwoMinutes() {
        val thirtySecAgo = now.minusSeconds(30).toString()
        val result = TrackingFreshnessRules.evaluatePositionFreshness(thirtySecAgo, "15.5", now)
        assertEquals(PositionFreshnessStatus.FRESH, result.status)
        assertEquals(30L, result.ageSeconds)
        assertEquals("Updated 30s ago", result.relativeText)
        assertEquals("accuracy 15.5 m", result.accuracyText)
    }

    @Test
    fun evaluateRecentPositionBetweenTwoAndFiveMinutes() {
        val threeMinutesAgo = now.minusSeconds(180).toString()
        val result = TrackingFreshnessRules.evaluatePositionFreshness(threeMinutesAgo, "8", now)
        assertEquals(PositionFreshnessStatus.RECENT, result.status)
        assertEquals(180L, result.ageSeconds)
        assertEquals("Updated 3m ago", result.relativeText)
        assertEquals("accuracy 8 m", result.accuracyText)
    }

    @Test
    fun evaluateStalePositionOverFiveMinutes() {
        val sixMinutesAgo = now.minusSeconds(360).toString()
        val result = TrackingFreshnessRules.evaluatePositionFreshness(sixMinutesAgo, "25", now)
        assertEquals(PositionFreshnessStatus.STALE, result.status)
        assertEquals(360L, result.ageSeconds)
        assertEquals("Stale · 6m ago", result.relativeText)
        assertEquals("accuracy 25 m", result.accuracyText)
    }

    @Test
    fun evaluateStalePositionOverOneHour() {
        val twoHoursAgo = now.minusSeconds(7200).toString()
        val result = TrackingFreshnessRules.evaluatePositionFreshness(twoHoursAgo, "", now)
        assertEquals(PositionFreshnessStatus.STALE, result.status)
        assertEquals(7200L, result.ageSeconds)
        assertEquals("Stale · 2h ago", result.relativeText)
        assertEquals("accuracy unknown", result.accuracyText)
    }

    @Test
    fun evaluateBlankOrInvalidTimestamp() {
        val blank = TrackingFreshnessRules.evaluatePositionFreshness("", "10", now)
        assertEquals(PositionFreshnessStatus.UNAVAILABLE, blank.status)
        assertNull(blank.ageSeconds)
        assertEquals("GPS position unavailable", blank.relativeText)

        val invalid = TrackingFreshnessRules.evaluatePositionFreshness("not-a-date", "invalid", now)
        assertEquals(PositionFreshnessStatus.UNAVAILABLE, invalid.status)
        assertNull(invalid.ageSeconds)
        assertEquals("GPS position unavailable", invalid.relativeText)
        assertEquals("accuracy unknown", invalid.accuracyText)
    }

    @Test
    fun evaluateFutureTimestampGuard() {
        // Minor clock skew (-10s in future) is clamped to 0s FRESH
        val slightFuture = now.plusSeconds(10).toString()
        val slightResult = TrackingFreshnessRules.evaluatePositionFreshness(slightFuture, "5", now)
        assertEquals(PositionFreshnessStatus.FRESH, slightResult.status)
        assertEquals(0L, slightResult.ageSeconds)
        assertEquals("Updated 0s ago", slightResult.relativeText)

        // Wildly future timestamp (> 60s in future) is rejected as UNAVAILABLE
        val wildFuture = now.plusSeconds(300).toString()
        val wildResult = TrackingFreshnessRules.evaluatePositionFreshness(wildFuture, "5", now)
        assertEquals(PositionFreshnessStatus.UNAVAILABLE, wildResult.status)
        assertEquals("GPS timestamp invalid", wildResult.relativeText)
    }

    private fun sampleShipment() = buildJsonObject {
        put("id", "shipment-1")
        put("tracking_number", "TRK-001")
        put("scheduled_departure", "2026-10-28T08:00:00Z")
        put("estimated_arrival", "2026-10-28T14:00:00Z")
    }

    private fun sampleDelivery(status: String = "in_transit") = buildJsonObject {
        put("id", "delivery-1")
        put("shipment_id", "shipment-1")
        put("status", status)
    }

    private fun sampleRouting(verified: Boolean = true) = buildJsonObject {
        put("route_verified", verified)
        put("calculated_at", now.minusSeconds(15).toString())
        put("position_at", now.minusSeconds(20).toString())
        put("expires_at", now.plusSeconds(100).toString())
        put("eta_at", now.plusSeconds(1800).toString())
        put("leg", "destination")
    }

    @Test
    fun customerEtaSummaryOfflineFallback() {
        val freshness = TrackingFreshnessRules.evaluatePositionFreshness(now.minusSeconds(30).toString(), "10", now)
        val result = TrackingFreshnessRules.customerEtaSummary(
            shipment = sampleShipment(),
            delivery = sampleDelivery(),
            routing = sampleRouting(),
            freshness = freshness,
            offline = true,
            now = now
        )
        assertFalse(result.isLive)
        assertEquals("Live ETA unavailable while offline", result.liveEta)
        assertEquals("Showing scheduled arrival", result.liveEtaDetail)
        assertTrue(result.scheduledArrival.contains("PHT"))
        assertTrue(result.scheduledDeparture.contains("PHT"))
    }

    @Test
    fun customerEtaSummaryNoDeliveryOrClosedDelivery() {
        val freshness = TrackingFreshnessRules.evaluatePositionFreshness(now.minusSeconds(30).toString(), "10", now)
        val noDelivery = TrackingFreshnessRules.customerEtaSummary(
            shipment = sampleShipment(),
            delivery = null,
            routing = null,
            freshness = freshness,
            offline = false,
            now = now
        )
        assertFalse(noDelivery.isLive)
        assertEquals("No active transit ETA", noDelivery.liveEta)
        assertEquals("No delivery assignment has been recorded", noDelivery.liveEtaDetail)

        val completedDelivery = TrackingFreshnessRules.customerEtaSummary(
            shipment = sampleShipment(),
            delivery = sampleDelivery("delivered"),
            routing = null,
            freshness = freshness,
            offline = false,
            now = now
        )
        assertFalse(completedDelivery.isLive)
        assertEquals("No active transit ETA", completedDelivery.liveEta)
        assertEquals("Shipment delivered", completedDelivery.liveEtaDetail)
    }

    @Test
    fun customerEtaSummaryVerifiedLiveRouteEta() {
        val freshness = TrackingFreshnessRules.evaluatePositionFreshness(now.minusSeconds(20).toString(), "10", now)
        val result = TrackingFreshnessRules.customerEtaSummary(
            shipment = sampleShipment(),
            delivery = sampleDelivery("in_transit"),
            routing = sampleRouting(verified = true),
            freshness = freshness,
            offline = false,
            now = now
        )
        assertTrue(result.isLive)
        assertTrue(result.liveEta.startsWith("ETA to destination:"))
        assertEquals("Office-confirmed truck route · live GPS", result.liveEtaDetail)
    }

    @Test
    fun customerEtaSummaryUnverifiedOrFailedRoutingFallback() {
        val freshness = TrackingFreshnessRules.evaluatePositionFreshness(now.minusSeconds(20).toString(), "10", now)
        val failedRouting = buildJsonObject { put("reason", "Live route calculation unavailable") }
        val result = TrackingFreshnessRules.customerEtaSummary(
            shipment = sampleShipment(),
            delivery = sampleDelivery("in_transit"),
            routing = failedRouting,
            freshness = freshness,
            offline = false,
            now = now
        )
        assertFalse(result.isLive)
        assertEquals("Live route ETA unavailable", result.liveEta)
        assertEquals("Live route ETA unavailable · relying on scheduled arrival", result.liveEtaDetail)
    }

    @Test
    fun customerEtaSummaryStaleGpsFallback() {
        val staleFreshness = TrackingFreshnessRules.evaluatePositionFreshness(now.minusSeconds(600).toString(), "10", now)
        val result = TrackingFreshnessRules.customerEtaSummary(
            shipment = sampleShipment(),
            delivery = sampleDelivery("in_transit"),
            routing = sampleRouting(verified = true),
            freshness = staleFreshness,
            offline = false,
            now = now
        )
        assertFalse(result.isLive)
        assertEquals("Live route ETA unavailable", result.liveEta)
        assertEquals("Live route ETA unavailable · relying on scheduled arrival", result.liveEtaDetail)
    }
}
