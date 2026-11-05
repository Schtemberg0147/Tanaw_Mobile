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
}
