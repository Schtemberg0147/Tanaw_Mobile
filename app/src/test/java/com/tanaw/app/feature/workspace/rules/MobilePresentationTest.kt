package com.tanaw.app.feature.workspace.rules
import org.junit.Assert.*
import org.junit.Test
class MobilePresentationTest {
 @Test fun utcDatesCrossIntoPhilippineNextDay() { assertEquals("Oct 25, 2026 · 3:00 AM PHT",MobilePresentation.time("2026-10-24T19:00:00Z")) }
 @Test fun offsetAndPostgresTimestampsFormatToPht() {
  assertEquals("Oct 25, 2026 · 3:00 AM PHT", MobilePresentation.time("2026-10-24T19:00:00.123456+00:00"))
  assertEquals("Oct 25, 2026 · 3:00 AM PHT", MobilePresentation.time("2026-10-25T03:00:00+08:00"))
  assertEquals("Oct 25, 2026 · 3:00 AM PHT", MobilePresentation.time("2026-10-24 19:00:00+00"))
  assertEquals("Oct 25, 2026 · 3:00 AM PHT", MobilePresentation.time("2026-10-25 03:00:00+0800"))
 }
 @Test fun absentDatesAndPricesStayUnknown() { assertEquals("Not recorded",MobilePresentation.time(""));assertEquals("Not recorded",MobilePresentation.time("not-a-timestamp"));assertEquals("Awaiting quotation",MobilePresentation.money("")) }
 @Test fun moneyAndUnitsRemainReadable() { assertTrue(MobilePresentation.money("2500.5").contains("2,500.50"));assertEquals("1,000 kg",MobilePresentation.number("1000.00","kg")) }
}
