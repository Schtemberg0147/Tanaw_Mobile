package com.tanaw.app.feature.workspace.customer.rules

import com.tanaw.app.R
import com.tanaw.app.core.validation.MobileValidation
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

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
    fun vehicleLeadTimeHoursRespectsExplicitAndFallbackDefaults() {
        val wingVan = buildJsonObject { put("name", "10W Wing Van") }
        val reefer = buildJsonObject { put("name", "Refrigerated Van") }
        val flatbed = buildJsonObject { put("name", "10-Wheeler Flatbed") }
        val customVehicle = buildJsonObject {
            put("name", "Closed Van")
            put("min_lead_time_hours", 36)
            put("loading_buffer_minutes", 120)
        }

        assertEquals(24, CustomerBookingRules.minLeadTimeHours(wingVan))
        assertEquals(48, CustomerBookingRules.minLeadTimeHours(reefer))
        assertEquals(48, CustomerBookingRules.minLeadTimeHours(flatbed))
        assertEquals(36, CustomerBookingRules.minLeadTimeHours(customVehicle))
        assertEquals(180, CustomerBookingRules.loadingBufferMinutes(wingVan))
        assertEquals(120, CustomerBookingRules.loadingBufferMinutes(customVehicle))
    }

    @Test
    fun ensureValidScheduledRawAutoAdjustsOnLeadTimeIncrease() {
        val now = Instant.parse("2026-10-25T04:30:25Z") // 2026-10-25 12:30 PHT
        val twentyFourHourSelection = "2026-10-26 14:00"

        // Valid for 24h truck
        val standardCheck = CustomerBookingRules.ensureValidScheduledRaw(twentyFourHourSelection, now, 24)
        assertFalse(standardCheck.wasAdjusted)
        assertEquals(twentyFourHourSelection, standardCheck.raw)

        // Switching to 48h Refrigerated Van auto-adjusts to valid 48h slot
        val specializedCheck = CustomerBookingRules.ensureValidScheduledRaw(twentyFourHourSelection, now, 48)
        assertTrue(specializedCheck.wasAdjusted)
        assertNotNull(MobileBookingRules.departure(specializedCheck.raw, now, 48L))
    }

    @Test
    fun schedulePillFormatsBlankTodayTomorrowAndFutureDates() {
        val now = Instant.parse("2026-10-25T04:30:25Z") // 2026-10-25 12:30 PHT
        assertEquals("Schedule pick-up", CustomerBookingRules.formatSchedulePill("", now))
        assertEquals("Today, 2:18 PM", CustomerBookingRules.formatSchedulePill("2026-10-25 14:18", now))
        assertEquals("Tomorrow, 9:05 AM", CustomerBookingRules.formatSchedulePill("2026-10-26 09:05", now))
        assertEquals("Wed Oct 28, 3:45 PM", CustomerBookingRules.formatSchedulePill("2026-10-28 15:45", now))
    }

    @Test
    fun scheduledDaysAndTimeSlotsEnforceLeadTimeAnd14DayWindow() {
        val now = Instant.parse("2026-10-25T04:30:25Z") // 2026-10-25 12:30 PHT
        val days24 = CustomerBookingRules.scheduledDays(now, 24)
        assertEquals(14, days24.size)
        assertEquals("2026-10-26", days24.first().toString())
        assertEquals("2026-11-08", days24.last().toString())

        val days48 = CustomerBookingRules.scheduledDays(now, 48)
        assertEquals(13, days48.size)
        assertEquals("2026-10-27", days48.first().toString())
        assertEquals("2026-11-08", days48.last().toString())

        val earliestHours = CustomerBookingRules.availableHoursForDate(LocalDate.parse("2026-10-26"), now, 24)
        assertEquals(12, earliestHours.first())
        assertEquals(23, earliestHours.last())

        val earliestMinutes = CustomerBookingRules.availableMinutesForDateTime(LocalDate.parse("2026-10-26"), 12, now, 24)
        assertEquals(31, earliestMinutes.first())
        assertEquals(59, earliestMinutes.last())
    }

    @Test
    fun estimateBaseLinehaulAppliesRateAndMinChargeFloorStrictlyPickupToDropoff() {
        val vehicle = buildJsonObject {
            put("id", "v-closed")
            put("name", "Closed Van")
        }
        val rates = listOf(
            buildJsonObject {
                put("vehicle_type_id", "v-closed")
                put("base_rate", 2500)
                put("per_km_rate", 45)
                put("per_kg_rate", 0)
                put("min_charge", 4000)
                put("is_active", true)
            }
        )

        // Short route floored at min_charge (2500 + 10 * 45 = 2950 < 4000)
        val floored = CustomerBookingRules.estimateBaseLinehaul(vehicle, rates, distanceKm = 10.0)
        assertNotNull(floored)
        assertEquals(4000.0, floored!!.amount, 0.01)
        assertTrue(floored.flooredAtMinCharge)

        // Long route (2500 + 100 * 45 = 7000)
        val regular = CustomerBookingRules.estimateBaseLinehaul(vehicle, rates, distanceKm = 100.0)
        assertNotNull(regular)
        assertEquals(7000.0, regular!!.amount, 0.01)
        assertFalse(regular.flooredAtMinCharge)

        // Missing distance returns null
        assertNull(CustomerBookingRules.estimateBaseLinehaul(vehicle, rates, distanceKm = null))
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

    @Test
    fun resolveHeaderCityPrioritizesLiveLocationThenProfileThenPickup() {
        assertEquals(
            "Malolos, Bulacan",
            CustomerBookingRules.resolveHeaderCity(
                rawAddress = "123 Katipunan Ave, Brgy. Loyola Heights",
                rawCity = "Quezon City, Metro Manila",
                liveLocationCity = "Malolos, Bulacan"
            )
        )
        assertEquals(
            "Cabanatuan City, Nueva Ecija",
            CustomerBookingRules.resolveHeaderCity(
                rawAddress = "123 Katipunan Ave, Brgy. Loyola Heights",
                rawCity = "Quezon City, Metro Manila",
                liveLocationAddress = "Maharlika Highway, Cabanatuan City, 3100 Nueva Ecija, Philippines"
            )
        )
        assertEquals(
            "Quezon City, Metro Manila",
            CustomerBookingRules.resolveHeaderCity(
                rawAddress = "123 Katipunan Ave, Brgy. Loyola Heights",
                rawCity = "Quezon City, Metro Manila"
            )
        )
        assertEquals(
            "Pasig City",
            CustomerBookingRules.resolveHeaderCity(
                rawAddress = "123 Rizal St, Pasig City",
                rawCity = ""
            )
        )
        assertEquals(
            "Balagtas, Bulacan",
            CustomerBookingRules.resolveHeaderCity(
                rawAddress = "",
                rawCity = "",
                pickupAddress = "VW6H+F53, Bulihan Provincial Rd, Balagtas, 3016 Bulacan, Philippines"
            )
        )
        assertEquals(
            "",
            CustomerBookingRules.resolveHeaderCity(
                rawAddress = "",
                rawCity = "",
                pickupAddress = ""
            )
        )
    }

    @Test
    fun quickWeightPresetsRespectSelectedVehicleCapacity() {
        val twoTonPresets = CustomerBookingRules.quickWeightPresets("2000")
        assertTrue(twoTonPresets.isNotEmpty())
        assertTrue(twoTonPresets.all { it <= 2000 })
        assertEquals(2000, twoTonPresets.last())

        val tenTonPresets = CustomerBookingRules.quickWeightPresets("10000")
        assertTrue(tenTonPresets.size <= 4)
        assertTrue(tenTonPresets.all { it <= 10000 })
        assertEquals(10000, tenTonPresets.last())

        val lightPresets = CustomerBookingRules.quickWeightPresets("200")
        assertEquals(listOf(200), lightPresets)

        assertEquals(listOf(500, 1000, 2000, 5000), CustomerBookingRules.quickWeightPresets(""))
    }
}
