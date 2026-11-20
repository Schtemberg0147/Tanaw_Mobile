package com.tanaw.app.feature.workspace.customer.rules
import com.tanaw.app.feature.workspace.model.RoutePreview
import java.time.Instant
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
class MobileBookingRulesTest {
    @Test
    fun departureUsesPhilippineTimeAndEnforcesLeadTimeAndMaxWindow() {
        val now = Instant.parse("2026-10-25T04:30:25Z") // 2026-10-25 12:30:25 PHT
        assertNull(MobileBookingRules.departure("2026-10-25 12:31", now)) // < 24h
        assertNull(MobileBookingRules.departure("2026-10-26 12:30", now)) // 1 min short of 24h
        assertEquals("2026-10-26T04:31:00Z", MobileBookingRules.departure("2026-10-26 12:31", now))

        // 48h specialized vehicle lead time
        assertNull(MobileBookingRules.departure("2026-10-26 12:31", now, 48L))
        assertNull(MobileBookingRules.departure("2026-10-27 12:30", now, 48L))
        assertEquals("2026-10-27T04:31:00Z", MobileBookingRules.departure("2026-10-27 12:31", now, 48L))

        // Invalid calendar date & > 14-day window
        assertNull(MobileBookingRules.departure("2026-02-30 13:00", now))
        assertNull(MobileBookingRules.departure("2026-11-08 12:31", now))
        assertEquals("2026-11-08T04:30:00Z", MobileBookingRules.departure("2026-11-08 12:30", now))
    }

    @Test
    fun departureExpiresAsClockAdvancesAndRollsToNextDay() {
        val raw = "2026-10-27 00:00" // 2026-10-26T16:00:00Z (24h prior = 2026-10-25T16:00:00Z)
        assertNotNull(MobileBookingRules.departure(raw, Instant.parse("2026-10-25T15:59:59Z")))
        assertNull(MobileBookingRules.departure(raw, Instant.parse("2026-10-25T16:00:00Z")))
    }

    @Test
    fun calculateEstimatedArrivalAddsRouteDurationAndLoadingBuffer() {
        val eta = MobileBookingRules.calculateEstimatedArrival(
            departureIso = "2026-10-26T04:31:00Z",
            routeDurationMinutes = 145,
            loadingBufferMinutes = 180
        )
        assertEquals("2026-10-26T09:56:00Z", eta)
    }

    @Test
    fun routeParserRejectsUnverifiedAndMalformedRoadGeometry() {
        val valid = Json.parseToJsonElement(
            """{"roadOnlyVerified":true,"distanceKm":3.5,"estimatedMinutes":8,"geojson":{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"LineString","coordinates":[[120.98,14.6],[121.0,14.61]]}}]}}"""
        ).jsonObject
        assertEquals(3.5, RoutePreview.parse(valid).distanceKm, 0.001)
        for (key in listOf("roadOnlyVerified", "geojson", "estimatedMinutes")) {
            assertTrue(runCatching { RoutePreview.parse(JsonObject(valid.filterKeys { it != key })) }.isFailure)
        }
    }
}
