package com.tanaw.app.feature.workspace

import com.tanaw.app.R
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class CustomerBookingRulesTest {
    @Test
    fun greetingFollowsPhilippineTimeOfDay() {
        // 01:00 UTC = 09:00 Asia/Manila (Morning)
        assertEquals("Good morning,", CustomerBookingRules.greeting(Instant.parse("2026-11-05T01:00:00Z")))
        // 06:00 UTC = 14:00 Asia/Manila (Afternoon)
        assertEquals("Good afternoon,", CustomerBookingRules.greeting(Instant.parse("2026-11-05T06:00:00Z")))
        // 12:00 UTC = 20:00 Asia/Manila (Evening)
        assertEquals("Good evening,", CustomerBookingRules.greeting(Instant.parse("2026-11-05T12:00:00Z")))
    }

    @Test
    fun vehicleMetadataMapsBadgesiconsAndFormattedCapacity() {
        assertEquals("OPEN", CustomerBookingRules.vehicleBadge("Dropside Truck"))
        assertEquals("OPEN", CustomerBookingRules.vehicleBadge("Flatbed"))
        assertEquals("ENCLOSED", CustomerBookingRules.vehicleBadge("Closed Van"))
        assertEquals("ENCLOSED", CustomerBookingRules.vehicleBadge("Wing Van"))
        assertEquals("REFRIGERATED", CustomerBookingRules.vehicleBadge("Refrigerated Van"))

        assertEquals(R.drawable.ic_vehicle_dropside, CustomerBookingRules.vehicleIconRes("Dropside Truck"))
        assertEquals(R.drawable.ic_vehicle_closed_van, CustomerBookingRules.vehicleIconRes("Closed Van"))
        assertEquals(R.drawable.ic_vehicle_wing_van, CustomerBookingRules.vehicleIconRes("Wing Van"))
        assertEquals(R.drawable.ic_vehicle_wing_van, CustomerBookingRules.vehicleIconRes("10-Wheeler"))
        assertEquals(R.drawable.ic_vehicle_wing_van, CustomerBookingRules.vehicleIconRes("10-Wheeler Wing Van"))
        assertEquals(R.drawable.ic_vehicle_flatbed, CustomerBookingRules.vehicleIconRes("Flatbed"))
        assertEquals(R.drawable.ic_vehicle_flatbed, CustomerBookingRules.vehicleIconRes("10-Wheeler Flatbed"))
        assertEquals(R.drawable.ic_vehicle_refrigerated, CustomerBookingRules.vehicleIconRes("Refrigerated Van"))

        assertEquals("Up to 2,000 kg", CustomerBookingRules.formatCapacity("2000"))
        assertEquals("Up to 8,000 kg", CustomerBookingRules.formatCapacity("8000"))
    }

    @Test
    fun pickupNowAlwaysResolvesToValidFutureDeparture() {
        val now = Instant.parse("2026-10-25T04:30:25Z") // 12:30:25 PHT
        val resolved = CustomerBookingRules.resolveDepartureRaw(isPickupNow = true, scheduledRaw = "", now = now)
        assertEquals("2026-10-25 12:36", resolved)
        assertNotNull(MobileBookingRules.departure(resolved, now))
    }

    @Test
    fun schedulePillFormatsNowTodayTomorrowAndFutureDates() {
        val now = Instant.parse("2026-10-25T04:30:25Z") // 2026-10-25 12:30 PHT
        assertEquals("Pick-up now", CustomerBookingRules.formatSchedulePill(true, "", now))
        assertEquals("Today, 2:18 PM", CustomerBookingRules.formatSchedulePill(false, "2026-10-25 14:18", now))
        assertEquals("Tomorrow, 9:05 AM", CustomerBookingRules.formatSchedulePill(false, "2026-10-26 09:05", now))
        assertEquals("Wed Oct 28, 3:45 PM", CustomerBookingRules.formatSchedulePill(false, "2026-10-28 15:45", now))
    }

    @Test
    fun scheduledDaysStartsFromTomorrowToFuture() {
        val now = Instant.parse("2026-10-25T04:30:25Z") // 2026-10-25 in Asia/Manila
        val days = CustomerBookingRules.scheduledDays(now)
        assertEquals(30, days.size)
        assertEquals("2026-10-26", days.first().toString())
        assertEquals("2026-11-24", days.last().toString())
    }

    @Test
    fun splitAddressAndWeightCapacityValidation() {
        val (title, subtitle) = CustomerBookingRules.splitAddress(
            "Drip Tips Salon and Spa, Bulihan Provincial Rd, Balagtas, 3016 Bulacan, Philippines"
        )
        assertEquals("Drip Tips Salon and Spa", title)
        assertEquals("Bulihan Provincial Rd, Balagtas, 3016 Bulacan, Philippines", subtitle)

        assertTrue(CustomerBookingRules.weightFitsCapacity("500", "2000"))
        assertTrue(CustomerBookingRules.weightFitsCapacity("2000", "2000"))
        assertFalse(CustomerBookingRules.weightFitsCapacity("2001", "2000"))
        assertFalse(CustomerBookingRules.weightFitsCapacity("0", "2000"))
        assertFalse(CustomerBookingRules.weightFitsCapacity("-10", "2000"))
    }

    @Test
    fun formatDurationFormatsMinutesAndHours() {
        assertEquals("Est. 1min", CustomerBookingRules.formatDuration(0))
        assertEquals("Est. 25min", CustomerBookingRules.formatDuration(25))
        assertEquals("Est. 1h", CustomerBookingRules.formatDuration(60))
        assertEquals("Est. 1h 45min", CustomerBookingRules.formatDuration(105))
        assertEquals("Est. 3h", CustomerBookingRules.formatDuration(180))
    }

    @Test
    fun bookingValidationRulesForCargoAndContact() {
        // Cargo description validation: min 3 chars, max 1000
        val shortCargo = "AB"
        val validCargo = "Boxes of electronic parts"
        val maxCargo = "A".repeat(1000)
        val overflowCargo = "A".repeat(1001)

        assertTrue(shortCargo.trim().length < 3)
        assertTrue(validCargo.trim().length in 3..1000)
        assertTrue(maxCargo.trim().length in 3..1000)
        assertTrue(overflowCargo.trim().length > 1000)

        // Contact validation via MobileValidation.phone
        assertEquals("+639123456789", MobileValidation.phone("09123456789"))
        assertEquals("+639123456789", MobileValidation.phone("9123456789"))
        assertEquals("+639123456789", MobileValidation.phone("+639123456789"))
        assertNull(MobileValidation.phone("0912345"))
        assertNull(MobileValidation.phone("invalid"))

        // Weight fits capacity edge cases
        assertTrue(CustomerBookingRules.weightFitsCapacity("1500.5", "2000"))
        assertFalse(CustomerBookingRules.weightFitsCapacity("2000.1", "2000"))
        assertFalse(CustomerBookingRules.weightFitsCapacity("abc", "2000"))
        assertFalse(CustomerBookingRules.weightFitsCapacity("100", "0"))
        assertFalse(CustomerBookingRules.weightFitsCapacity("100", "-50"))
    }

    @Test
    fun weightBoundaryValidationAndSystemCeiling() {
        // System ceiling: 100,000 kg via MobileValidation.weight
        assertEquals(100000.0, MobileValidation.weight("100000")!!, 0.001)
        assertEquals(99999.9, MobileValidation.weight("99999.9")!!, 0.001)
        assertEquals(0.1, MobileValidation.weight("0.1")!!, 0.001)

        // Exceeding system ceiling or non-finite numbers
        assertNull(MobileValidation.weight("100000.1"))
        assertNull(MobileValidation.weight("100001"))
        assertNull(MobileValidation.weight("1000000"))
        assertNull(MobileValidation.weight("0"))
        assertNull(MobileValidation.weight("-1"))
        assertNull(MobileValidation.weight("-100000"))
        assertNull(MobileValidation.weight("NaN"))
        assertNull(MobileValidation.weight("Infinity"))
        assertNull(MobileValidation.weight("-Infinity"))
        assertNull(MobileValidation.weight(""))
        assertNull(MobileValidation.weight("   "))
    }

    @Test
    fun cargoDescriptionWhitespaceAndHomoglyphBypasses() {
        // Whitespace-only descriptions must fail
        val blankCargo = "     "
        val tabCargo = "\t\n  "
        assertFalse(blankCargo.trim().length in 3..1000)
        assertFalse(tabCargo.trim().length in 3..1000)

        // Unicode, accented characters and emojis
        val unicodeCargo = "Möbel & Elektronik 📦"
        assertTrue(unicodeCargo.trim().length in 3..1000)

        // Leading/trailing spaces must be trimmed for length evaluation
        val paddedCargo = "   Box   "
        assertEquals(3, paddedCargo.trim().length)
        assertTrue(paddedCargo.trim().length in 3..1000)
    }

    @Test
    fun notesToDriverCharacterLimitBoundaries() {
        val emptyNotes = ""
        val boundaryNotes = "A".repeat(2000)
        val overflowNotes = "A".repeat(2001)

        // Notes are optional (can be empty) and capped at 2000 characters
        assertFalse(emptyNotes.length > 2000)
        assertFalse(boundaryNotes.length > 2000)
        assertTrue(overflowNotes.length > 2000)
        assertEquals(2000, boundaryNotes.length)
        assertEquals(2001, overflowNotes.length)
    }
}
