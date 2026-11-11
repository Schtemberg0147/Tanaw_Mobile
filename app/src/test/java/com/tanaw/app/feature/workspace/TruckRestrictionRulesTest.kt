package com.tanaw.app.feature.workspace

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class TruckRestrictionRulesTest {
    private val manilaZone = ZoneId.of("Asia/Manila")

    private fun json(vararg pairs: Pair<String, Any?>) = buildJsonObject {
        for ((key, value) in pairs) {
            when (value) {
                is String -> put(key, value)
                is Number -> put(key, value)
                is Boolean -> put(key, value)
                null -> {}
                else -> put(key, value.toString())
            }
        }
    }

    @Test
    fun allTenPlateEndingDigitsMapToCorrectCodingDays() {
        val testCases = listOf(
            "ABC-1011" to Pair(1, DayOfWeek.MONDAY),
            "NDB 5502" to Pair(2, DayOfWeek.MONDAY),
            "XYZ-993" to Pair(3, DayOfWeek.TUESDAY),
            "CAE-4004" to Pair(4, DayOfWeek.TUESDAY),
            "ABC 1235" to Pair(5, DayOfWeek.WEDNESDAY),
            "NCD-776" to Pair(6, DayOfWeek.WEDNESDAY),
            "TRK-0007" to Pair(7, DayOfWeek.THURSDAY),
            "AAA 888" to Pair(8, DayOfWeek.THURSDAY),
            "UOP-129" to Pair(9, DayOfWeek.FRIDAY),
            "NAA 100" to Pair(0, DayOfWeek.FRIDAY)
        )

        for ((plate, expected) in testCases) {
            val result = TruckRestrictionRules.codingDayForPlate(plate)
            assertNotNull("Plate $plate should have coding day", result)
            assertEquals("Last digit for $plate", expected.first, result?.first)
            assertEquals("Coding day for $plate", expected.second, result?.second)
        }
    }

    @Test
    fun plateWithNoDigitsReturnsNull() {
        assertNull(TruckRestrictionRules.codingDayForPlate(""))
        assertNull(TruckRestrictionRules.codingDayForPlate("ABC-DEF"))
    }

    @Test
    fun peakTruckBanDetectsMorningAndEveningWindowsOnWeekdaysAndSaturdays() {
        // Monday 2026-11-09
        val monMorningStart = ZonedDateTime.of(2026, 11, 9, 6, 0, 0, 0, manilaZone).toInstant()
        val monMorningMid = ZonedDateTime.of(2026, 11, 9, 8, 30, 0, 0, manilaZone).toInstant()
        val monMorningEnd = ZonedDateTime.of(2026, 11, 9, 9, 59, 59, 0, manilaZone).toInstant()
        val monMorningAfter = ZonedDateTime.of(2026, 11, 9, 10, 0, 0, 0, manilaZone).toInstant()

        assertTrue("06:00 is in ban window", TruckRestrictionRules.isWithinPeakTruckBanWindow(monMorningStart))
        assertTrue("08:30 is in ban window", TruckRestrictionRules.isWithinPeakTruckBanWindow(monMorningMid))
        assertTrue("09:59:59 is in ban window", TruckRestrictionRules.isWithinPeakTruckBanWindow(monMorningEnd))
        assertFalse("10:00:00 is outside ban window", TruckRestrictionRules.isWithinPeakTruckBanWindow(monMorningAfter))

        // Midday
        val monMidday = ZonedDateTime.of(2026, 11, 9, 13, 0, 0, 0, manilaZone).toInstant()
        assertFalse("13:00 is outside ban window", TruckRestrictionRules.isWithinPeakTruckBanWindow(monMidday))

        // Evening window: 17:00..20:59:59
        val monEveningStart = ZonedDateTime.of(2026, 11, 9, 17, 0, 0, 0, manilaZone).toInstant()
        val monEveningMid = ZonedDateTime.of(2026, 11, 9, 19, 15, 0, 0, manilaZone).toInstant()
        val monEveningEnd = ZonedDateTime.of(2026, 11, 9, 20, 59, 59, 0, manilaZone).toInstant()
        val monEveningAfter = ZonedDateTime.of(2026, 11, 9, 21, 0, 0, 0, manilaZone).toInstant()

        assertTrue("17:00 is in ban window", TruckRestrictionRules.isWithinPeakTruckBanWindow(monEveningStart))
        assertTrue("19:15 is in ban window", TruckRestrictionRules.isWithinPeakTruckBanWindow(monEveningMid))
        assertTrue("20:59:59 is in ban window", TruckRestrictionRules.isWithinPeakTruckBanWindow(monEveningEnd))
        assertFalse("21:00 is outside ban window", TruckRestrictionRules.isWithinPeakTruckBanWindow(monEveningAfter))

        // Saturday 2026-11-14 (Saturday is subject to truck ban)
        val satMorning = ZonedDateTime.of(2026, 11, 14, 7, 30, 0, 0, manilaZone).toInstant()
        assertTrue("Saturday morning peak is in ban window", TruckRestrictionRules.isWithinPeakTruckBanWindow(satMorning))

        // Sunday 2026-11-15 (Sunday is exempt)
        val sunMorning = ZonedDateTime.of(2026, 11, 15, 8, 0, 0, 0, manilaZone).toInstant()
        val sunEvening = ZonedDateTime.of(2026, 11, 15, 18, 0, 0, 0, manilaZone).toInstant()
        assertFalse("Sunday morning peak is exempt", TruckRestrictionRules.isWithinPeakTruckBanWindow(sunMorning))
        assertFalse("Sunday evening peak is exempt", TruckRestrictionRules.isWithinPeakTruckBanWindow(sunEvening))
    }

    @Test
    fun evaluateTruckSpecsExtractsDimensionsAndCalculatesGrossWeight() {
        val truck = json(
            "model" to "Isuzu Giga 10-Wheeler",
            "plate_number" to "NCD-1234",
            "capacity_kg" to "14000",
            "tare_weight_kg" to "6500",
            "height_m" to "3.8",
            "width_m" to "2.5",
            "length_m" to "9.2",
            "axle_count" to "3"
        )
        val shipment = json("weight_kg" to "8000")

        val spec = TruckRestrictionRules.evaluateTruckSpecs(truck, shipment)

        assertEquals("Isuzu Giga 10-Wheeler", spec.model)
        assertEquals("NCD-1234", spec.plateNumber)
        assertEquals(14000.0, spec.capacityKg ?: 0.0, 0.001)
        assertEquals(8000.0, spec.cargoWeightKg ?: 0.0, 0.001)
        assertEquals(6500.0, spec.tareWeightKg ?: 0.0, 0.001)
        assertEquals(14500.0, spec.grossWeightKg ?: 0.0, 0.001) // tare (6500) + cargo (8000)
        assertEquals(3.8, spec.heightMeters ?: 0.0, 0.001)
        assertEquals(2.5, spec.widthMeters ?: 0.0, 0.001)
        assertEquals(9.2, spec.lengthMeters ?: 0.0, 0.001)
        assertEquals(3, spec.axleCount)
        assertFalse("8000 kg <= 14000 kg", spec.isOverCapacity)
        assertTrue("Height and width present", spec.dimensionsVerified)
    }

    @Test
    fun evaluateTruckSpecsDetectsOverCapacity() {
        val truck = json(
            "model" to "Fuso Canter",
            "plate_number" to "ABC-123",
            "capacity_kg" to "4000"
        )
        val shipment = json("weight_kg" to "4500")

        val spec = TruckRestrictionRules.evaluateTruckSpecs(truck, shipment)

        assertTrue("4500 kg > 4000 kg", spec.isOverCapacity)
        assertFalse("Dimensions missing", spec.dimensionsVerified)
        assertNull("Tare missing", spec.tareWeightKg)
        assertNull("Gross missing", spec.grossWeightKg)
    }

    @Test
    fun evaluateTruckSpecsUsesExplicitGrossWeightWhenPresent() {
        val truck = json(
            "model" to "Hino 500",
            "plate_number" to "XYZ-789",
            "capacity_kg" to "10000",
            "tare_weight_kg" to "5000",
            "gross_weight_kg" to "16000"
        )
        val shipment = json("weight_kg" to "9000")

        val spec = TruckRestrictionRules.evaluateTruckSpecs(truck, shipment)

        assertEquals(16000.0, spec.grossWeightKg ?: 0.0, 0.001)
    }

    @Test
    fun evaluateTruckSpecsFallsBackToRoutingPlan() {
        val truck = json(
            "model" to "Hino 300",
            "plate_number" to "AAA-111"
        )
        val routingPlan = json(
            "capacity_kg" to "5000",
            "cargo_weight_kg" to "3200",
            "height_m" to "3.2",
            "width_m" to "2.3"
        )

        val spec = TruckRestrictionRules.evaluateTruckSpecs(truck, null, routingPlan)

        assertEquals(5000.0, spec.capacityKg ?: 0.0, 0.001)
        assertEquals(3200.0, spec.cargoWeightKg ?: 0.0, 0.001)
        assertEquals(3.2, spec.heightMeters ?: 0.0, 0.001)
        assertEquals(2.3, spec.widthMeters ?: 0.0, 0.001)
        assertTrue(spec.dimensionsVerified)
    }

    @Test
    fun evaluateBanAdvisoryBuildsWarningsForCodingDayAndPeakWindows() {
        // Tuesday 2026-11-10: Plate ending in 4 is coded on Tuesday
        val referenceTuesdayMorningPeak = ZonedDateTime.of(2026, 11, 10, 7, 30, 0, 0, manilaZone).toInstant()
        val advisory = TruckRestrictionRules.evaluateBanAdvisory(
            plateNumber = "ABC-1234",
            scheduledDepartureIso = "2026-11-10T07:30:00+08:00",
            etaIso = "2026-11-10T18:00:00+08:00",
            referenceNow = referenceTuesdayMorningPeak
        )

        assertEquals("Tuesday", advisory.codingDayName)
        assertEquals(4, advisory.lastPlateDigit)
        assertTrue(advisory.isCodingDayOnReferenceDate)
        assertTrue(advisory.departureInPeakBanWindow)
        assertTrue(advisory.etaInPeakBanWindow)
        assertEquals(3, advisory.warnings.size)
        assertTrue(advisory.warnings[0].contains("UVVRP Number Coding"))
        assertTrue(advisory.warnings[1].contains("Scheduled departure falls in peak truck-ban window"))
        assertTrue(advisory.warnings[2].contains("Estimated arrival falls in peak truck-ban window"))
    }

    @Test
    fun formatSpecLineProducesExpectedFormatting() {
        val fullSpec = TruckSpecSummary(
            model = "Isuzu Giga",
            plateNumber = "ABC-1234",
            capacityKg = 10000.0,
            cargoWeightKg = 6000.0,
            tareWeightKg = 5000.0,
            grossWeightKg = 11000.0,
            heightMeters = 3.8,
            widthMeters = 2.5,
            lengthMeters = null,
            axleCount = 3,
            isOverCapacity = false,
            dimensionsVerified = true
        )
        val formattedFull = TruckRestrictionRules.formatSpecLine(fullSpec)
        assertTrue(formattedFull.contains("Capacity: 10,000 kg"))
        assertTrue(formattedFull.contains("Cargo: 6,000 kg"))
        assertTrue(formattedFull.contains("Height: 3.8m · Width: 2.5m"))

        val unverifiedSpec = TruckSpecSummary(
            model = "Canter",
            plateNumber = "XYZ-001",
            capacityKg = null,
            cargoWeightKg = null,
            tareWeightKg = null,
            grossWeightKg = null,
            heightMeters = null,
            widthMeters = null,
            lengthMeters = null,
            axleCount = null,
            isOverCapacity = false,
            dimensionsVerified = false
        )
        val formattedUnverified = TruckRestrictionRules.formatSpecLine(unverifiedSpec)
        assertTrue(formattedUnverified.contains("Capacity not recorded"))
        assertTrue(formattedUnverified.contains("Height/width/axle limits not recorded on vehicle profile"))
    }

    @Test
    fun evaluateBanAdvisoryHandlesUnzonedIsoTimestamps() {
        // Unzoned local timestamp without offset (e.g., "2026-11-10T07:30:00")
        // Tuesday 07:30 is in the morning peak truck ban window (06:00-10:00)
        val referenceNow = ZonedDateTime.of(2026, 11, 10, 7, 0, 0, 0, manilaZone).toInstant()
        val advisory = TruckRestrictionRules.evaluateBanAdvisory(
            plateNumber = "ABC-1234",
            scheduledDepartureIso = "2026-11-10T07:30:00",
            referenceNow = referenceNow
        )

        assertTrue(
            "Unzoned departure timestamp at 07:30 must be detected in peak ban window",
            advisory.departureInPeakBanWindow
        )
        assertTrue(
            "Advisory must include peak truck-ban warning",
            advisory.warnings.any { it.contains("Scheduled departure falls in peak truck-ban window") }
        )
    }

    @Test
    fun evaluateBanAdvisoryDetectsActiveCodingAcrossDepartureArrivalAndToday() {
        // Plate ending in 4 is coded on Tuesday
        // Scenario A: Trip departed Monday night (2026-11-09T22:00:00+08:00), but currently driving Tuesday morning (2026-11-10T08:00:00+08:00)
        val tuesdayMorning = ZonedDateTime.of(2026, 11, 10, 8, 0, 0, 0, manilaZone).toInstant()
        val advisoryToday = TruckRestrictionRules.evaluateBanAdvisory(
            plateNumber = "ABC-1234",
            scheduledDepartureIso = "2026-11-09T22:00:00+08:00", // Monday
            referenceNow = tuesdayMorning // Tuesday (Coding Day)
        )

        assertTrue(
            "Must detect coding conflict active today even if departure was on Monday",
            advisoryToday.warnings.any { it.contains("UVVRP Number Coding") }
        )

        // Scenario B: Departure Monday afternoon, but arriving Tuesday morning (ETA on coding day)
        val mondayNoon = ZonedDateTime.of(2026, 11, 9, 12, 0, 0, 0, manilaZone).toInstant()
        val advisoryEta = TruckRestrictionRules.evaluateBanAdvisory(
            plateNumber = "XYZ-994", // Plate ends in 4 (Tuesday)
            scheduledDepartureIso = "2026-11-09T14:00:00+08:00", // Monday
            etaIso = "2026-11-10T09:00:00+08:00", // Tuesday morning
            referenceNow = mondayNoon // Monday
        )

        assertTrue(
            "Must detect coding conflict if estimated arrival intersects coding day",
            advisoryEta.warnings.any { it.contains("UVVRP Number Coding") }
        )
    }

    @Test
    fun evaluateTruckSpecsHandlesZeroCargoWeightForDeadheadTrips() {
        val truck = json(
            "model" to "Fuso Fighter 6-Wheeler",
            "plate_number" to "NDB-5502",
            "capacity_kg" to "8000",
            "tare_weight_kg" to "4500",
            "height_m" to "3.4",
            "width_m" to "2.4"
        )
        // Deadhead / empty run with explicit 0 cargo weight
        val shipment = json("weight_kg" to "0")

        val spec = TruckRestrictionRules.evaluateTruckSpecs(truck, shipment)

        assertEquals("Cargo weight must be preserved as 0.0 kg", 0.0, spec.cargoWeightKg ?: -1.0, 0.001)
        assertEquals("Tare weight must be 4500.0 kg", 4500.0, spec.tareWeightKg ?: -1.0, 0.001)
        assertEquals(
            "Gross weight on empty run must equal tare weight (4500 + 0)",
            4500.0,
            spec.grossWeightKg ?: -1.0,
            0.001
        )
        assertFalse("Empty run is not over capacity", spec.isOverCapacity)
    }

    @Test
    fun formatSpecLineIncludesCapacityUnrecordedWarningWhenDimensionsVerified() {
        val specWithDimsOnly = TruckSpecSummary(
            model = "Isuzu Forward",
            plateNumber = "TRK-0007",
            capacityKg = null,
            cargoWeightKg = null,
            tareWeightKg = null,
            grossWeightKg = null,
            heightMeters = 3.8,
            widthMeters = 2.5,
            lengthMeters = null,
            axleCount = 2,
            isOverCapacity = false,
            dimensionsVerified = true
        )

        val formatted = TruckRestrictionRules.formatSpecLine(specWithDimsOnly)

        assertTrue(
            "Spec line must state 'Capacity not recorded' even when dimensions are present",
            formatted.contains("Capacity not recorded")
        )
        assertTrue(
            "Spec line must contain formatted dimensions",
            formatted.contains("Height: 3.8m · Width: 2.5m")
        )
    }
}
