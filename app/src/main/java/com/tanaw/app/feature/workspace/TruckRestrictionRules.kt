package com.tanaw.app.feature.workspace

import kotlinx.serialization.json.JsonObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

data class TruckSpecSummary(
    val model: String,
    val plateNumber: String,
    val capacityKg: Double?,
    val cargoWeightKg: Double?,
    val tareWeightKg: Double?,
    val grossWeightKg: Double?,
    val heightMeters: Double?,
    val widthMeters: Double?,
    val lengthMeters: Double?,
    val axleCount: Int?,
    val isOverCapacity: Boolean,
    val dimensionsVerified: Boolean,
)

data class TruckBanAdvisory(
    val codingDayName: String?,
    val lastPlateDigit: Int?,
    val isCodingDayOnReferenceDate: Boolean,
    val departureInPeakBanWindow: Boolean,
    val etaInPeakBanWindow: Boolean,
    val warnings: List<String>,
)

object TruckRestrictionRules {
    private val MANILA_ZONE = ZoneId.of("Asia/Manila")

    fun evaluateTruckSpecs(
        truck: JsonObject?,
        shipment: JsonObject? = null,
        routingPlan: JsonObject? = null
    ): TruckSpecSummary {
        val model = truck?.value("model").orEmpty()
        val plateNumber = truck?.value("plate_number").orEmpty()

        val capacityKg = extractDouble(
            truck?.value("capacity_kg"),
            routingPlan?.value("capacity_kg"),
            routingPlan?.value("max_weight_kg")
        )
        val cargoWeightKg = extractDouble(
            shipment?.value("weight_kg"),
            routingPlan?.value("cargo_weight_kg"),
            allowZero = true
        )
        val tareWeightKg = extractDouble(
            truck?.value("tare_weight_kg"),
            routingPlan?.value("tare_weight_kg")
        )
        val explicitGross = extractDouble(
            truck?.value("gross_weight_kg"),
            routingPlan?.value("gross_weight_kg")
        )
        val calculatedGross = if ((tareWeightKg != null) && (cargoWeightKg != null)) {
            tareWeightKg + cargoWeightKg
        } else {
            null
        }
        val grossWeightKg = explicitGross ?: calculatedGross

        val heightMeters = extractDouble(
            truck?.value("height_m"),
            routingPlan?.value("height_m")
        )
        val widthMeters = extractDouble(
            truck?.value("width_m"),
            routingPlan?.value("width_m")
        )
        val lengthMeters = extractDouble(
            truck?.value("length_m"),
            routingPlan?.value("length_m")
        )
        val axleCount = extractInt(
            truck?.value("axle_count"),
            routingPlan?.value("axle_count")
        )

        val isOverCapacity = capacityKg != null && cargoWeightKg != null && cargoWeightKg > capacityKg
        val dimensionsVerified = heightMeters != null && widthMeters != null

        return TruckSpecSummary(
            model = model,
            plateNumber = plateNumber,
            capacityKg = capacityKg,
            cargoWeightKg = cargoWeightKg,
            tareWeightKg = tareWeightKg,
            grossWeightKg = grossWeightKg,
            heightMeters = heightMeters,
            widthMeters = widthMeters,
            lengthMeters = lengthMeters,
            axleCount = axleCount,
            isOverCapacity = isOverCapacity,
            dimensionsVerified = dimensionsVerified
        )
    }

    fun codingDayForPlate(plateNumber: String): Pair<Int, DayOfWeek>? {
        val lastDigitChar = plateNumber.lastOrNull { it in '0'..'9' } ?: return null
        val digit = lastDigitChar.digitToInt()
        val day = when (digit) {
            1, 2 -> DayOfWeek.MONDAY
            3, 4 -> DayOfWeek.TUESDAY
            5, 6 -> DayOfWeek.WEDNESDAY
            7, 8 -> DayOfWeek.THURSDAY
            9, 0 -> DayOfWeek.FRIDAY
            else -> return null
        }
        return Pair(digit, day)
    }

    fun isWithinPeakTruckBanWindow(instant: Instant): Boolean {
        val zdt = instant.atZone(MANILA_ZONE)
        if (zdt.dayOfWeek == DayOfWeek.SUNDAY) return false
        val hour = zdt.hour
        return hour in 6..9 || hour in 17..20
    }

    fun evaluateBanAdvisory(
        plateNumber: String,
        scheduledDepartureIso: String = "",
        etaIso: String? = null,
        referenceNow: Instant = Instant.now()
    ): TruckBanAdvisory {
        val coding = codingDayForPlate(plateNumber)
        val codingDayName = coding?.second?.name?.lowercase(Locale.US)?.replaceFirstChar { it.titlecase(Locale.US) }
        val lastPlateDigit = coding?.first

        val depInstant = parseIsoInstant(scheduledDepartureIso)
        val etaInstant = etaIso?.let { parseIsoInstant(it) }

        val nowDay = referenceNow.atZone(MANILA_ZONE).dayOfWeek
        val depDay = depInstant?.atZone(MANILA_ZONE)?.dayOfWeek
        val etaDay = etaInstant?.atZone(MANILA_ZONE)?.dayOfWeek

        val isCodingToday = coding != null && nowDay == coding.second
        val isCodingOnDep = coding != null && depDay != null && depDay == coding.second
        val isCodingOnEta = coding != null && etaDay != null && etaDay == coding.second
        val isCodingDayActive = isCodingToday || isCodingOnDep || isCodingOnEta

        val departureInPeakBanWindow = depInstant?.let { isWithinPeakTruckBanWindow(it) } ?: false
        val etaInPeakBanWindow = etaInstant?.let { isWithinPeakTruckBanWindow(it) } ?: false

        val warnings = mutableListOf<String>()
        if (isCodingDayActive && lastPlateDigit != null && codingDayName != null) {
            val contextMsg = when {
                isCodingToday -> "today"
                isCodingOnDep -> "on scheduled departure"
                else -> "at estimated arrival"
            }
            warnings.add("UVVRP Number Coding: Plate ends in $lastPlateDigit ($codingDayName, active $contextMsg). Verify MMDA/municipal truck coding exemption.")
        }
        if (departureInPeakBanWindow) {
            warnings.add("Scheduled departure falls in peak truck-ban window (06:00–10:00 or 17:00–21:00 PHT). Confirm route ordinance.")
        }
        if (etaInPeakBanWindow) {
            warnings.add("Estimated arrival falls in peak truck-ban window (06:00–10:00 or 17:00–21:00 PHT). Confirm route ordinance.")
        }

        return TruckBanAdvisory(
            codingDayName = codingDayName,
            lastPlateDigit = lastPlateDigit,
            isCodingDayOnReferenceDate = isCodingDayActive,
            departureInPeakBanWindow = departureInPeakBanWindow,
            etaInPeakBanWindow = etaInPeakBanWindow,
            warnings = warnings
        )
    }

    fun formatSpecLine(spec: TruckSpecSummary): String {
        val weightPart = when {
            spec.capacityKg != null && spec.cargoWeightKg != null ->
                "Capacity: %,.0f kg · Cargo: %,.0f kg".format(Locale.US, spec.capacityKg, spec.cargoWeightKg)
            spec.capacityKg != null ->
                "Capacity: %,.0f kg".format(Locale.US, spec.capacityKg)
            spec.cargoWeightKg != null ->
                "Capacity not recorded · Cargo: %,.0f kg".format(Locale.US, spec.cargoWeightKg)
            else ->
                "Capacity not recorded"
        }
        val dimPart = if (spec.dimensionsVerified) {
            val h = "%.1fm".format(Locale.US, spec.heightMeters)
            val w = "%.1fm".format(Locale.US, spec.widthMeters)
            if (spec.lengthMeters != null) {
                "Height: $h · Width: $w · Length: ${"%.1fm".format(Locale.US, spec.lengthMeters)}"
            } else {
                "Height: $h · Width: $w"
            }
        } else {
            "Height/width/axle limits not recorded on vehicle profile"
        }
        return "$weightPart · $dimPart"
    }

    private fun extractDouble(vararg candidates: String?, allowZero: Boolean = false): Double? {
        for (candidate in candidates) {
            val parsed = candidate?.trim()?.toDoubleOrNull()
            if (parsed != null && parsed.isFinite() && (if (allowZero) parsed >= 0.0 else parsed > 0.0)) {
                return parsed
            }
        }
        return null
    }

    private fun extractInt(vararg candidates: String?): Int? {
        for (candidate in candidates) {
            val parsed = candidate?.trim()?.toIntOrNull()
            if (parsed != null && parsed > 0) {
                return parsed
            }
        }
        return null
    }

    private fun parseIsoInstant(iso: String): Instant? {
        if (iso.isBlank()) return null
        return try {
            Instant.parse(iso)
        } catch (_: Exception) {
            try {
                OffsetDateTime.parse(iso).toInstant()
            } catch (_: Exception) {
                try {
                    ZonedDateTime.parse(iso).toInstant()
                } catch (_: Exception) {
                    try {
                        LocalDateTime.parse(iso).atZone(MANILA_ZONE).toInstant()
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        }
    }
}
