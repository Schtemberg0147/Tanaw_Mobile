package com.tanaw.app
import com.tanaw.app.feature.workspace.*
import java.time.Instant
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
class MobileBookingRulesTest {
 @Test fun departureUsesPhilippineTimeAndRejectsPastCurrentMinuteAndImpossibleDates() {
  val now=Instant.parse("2026-10-25T04:30:25Z")
  assertNull(MobileBookingRules.departure("2026-10-24 23:59",now))
  assertNull(MobileBookingRules.departure("2026-10-25 12:30",now))
  assertEquals("2026-10-25T04:31:00Z",MobileBookingRules.departure("2026-10-25 12:31",now))
  assertNull(MobileBookingRules.departure("2026-02-30 13:00",now))
  assertNull(MobileBookingRules.departure("2027-10-26 12:31",now))
 }
 @Test fun departureExpiresAsClockAdvancesAndRollsToNextDay() {
  val raw="2026-10-26 00:00"
  assertNotNull(MobileBookingRules.departure(raw,Instant.parse("2026-10-25T15:59:59Z")))
  assertNull(MobileBookingRules.departure(raw,Instant.parse("2026-10-25T16:00:00Z")))
 }
 @Test fun weightMatchesWebsiteCapacityWindowAndRejectsInvalidNumbers() {
  assertTrue(MobileBookingRules.fits("1000","1000"));assertTrue(MobileBookingRules.fits("1000","3000"))
  assertFalse(MobileBookingRules.fits("1000","999"));assertFalse(MobileBookingRules.fits("1000","3001"))
  assertFalse(MobileBookingRules.fits("","2000"));assertFalse(MobileBookingRules.fits("NaN","2000"))
  assertFalse(MobileBookingRules.fits("1000","Infinity"))
 }
 @Test fun routeParserRejectsUnverifiedAndMalformedRoadGeometry() {
  val valid=Json.parseToJsonElement("""{"roadOnlyVerified":true,"distanceKm":3.5,"estimatedMinutes":8,"geojson":{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"LineString","coordinates":[[120.98,14.6],[121.0,14.61]]}}]}}""").jsonObject
  assertEquals(3.5,RoutePreview.parse(valid).distanceKm,0.001)
  for(key in listOf("roadOnlyVerified","geojson","estimatedMinutes")) {
   assertTrue(runCatching {RoutePreview.parse(JsonObject(valid.filterKeys {it!=key}))}.isFailure)
  }
 }
}
