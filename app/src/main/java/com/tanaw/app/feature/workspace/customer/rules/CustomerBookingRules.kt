package com.tanaw.app.feature.workspace.customer.rules

import com.tanaw.app.R
import com.tanaw.app.core.validation.MobileValidation
import com.tanaw.app.ui.components.SelectedAddress
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

object CustomerBookingRules {
    private val displayTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val displayDayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE MMM d", Locale.ENGLISH)

    fun greeting(now: Instant = Instant.now()): String {
        val hour = now.atZone(MobileBookingRules.zone).hour
        return when (hour) {
            in 5..11 -> "Good morning,"
            in 12..17 -> "Good afternoon,"
            else -> "Good evening,"
        }
    }

    fun vehicleBadge(name: String): String {
        val lower = name.lowercase(Locale.ENGLISH)
        return when {
            "refrig" in lower || "reefer" in lower || "cold" in lower -> "REFRIGERATED"
            "dropside" in lower || "flatbed" in lower || "open" in lower -> "OPEN"
            else -> "ENCLOSED"
        }
    }

    fun vehicleIconRes(name: String): Int {
        val lower = name.lowercase(Locale.ENGLISH)
        return when {
            "dropside" in lower -> R.drawable.ic_vehicle_dropside
            "flatbed" in lower -> R.drawable.ic_vehicle_flatbed
            "refrig" in lower || "reefer" in lower -> R.drawable.ic_vehicle_refrigerated
            "wing" in lower || "10-wheeler" in lower || "10 wheeler" in lower || "ten wheeler" in lower ->
                R.drawable.ic_vehicle_wing_van
            else -> R.drawable.ic_vehicle_closed_van
        }
    }

    fun formatCapacity(rawKg: String): String {
        val value = rawKg.toDoubleOrNull()?.roundToInt() ?: return "Up to $rawKg kg"
        return "Up to ${NumberFormat.getIntegerInstance(Locale.US).format(value)} kg"
    }

    fun resolveDepartureRaw(isPickupNow: Boolean, scheduledRaw: String, now: Instant = Instant.now()): String {
        if (!isPickupNow && scheduledRaw.isNotBlank() && MobileBookingRules.departure(scheduledRaw, now) != null) {
            return scheduledRaw.trim()
        }
        return MobileBookingRules.earliest(now).plusMinutes(5).format(MobileBookingRules.format)
    }

    fun scheduledDays(now: Instant = Instant.now()): List<LocalDate> {
        val tomorrow = now.atZone(MobileBookingRules.zone).toLocalDate().plusDays(1)
        return (0L..29L).map { offset -> tomorrow.plusDays(offset) }
    }

    fun formatSchedulePill(isPickupNow: Boolean, scheduledRaw: String, now: Instant = Instant.now()): String {
        if (isPickupNow || scheduledRaw.isBlank()) return "Pick-up now"
        val parsed = runCatching { LocalDateTime.parse(scheduledRaw.trim(), MobileBookingRules.format) }.getOrNull()
            ?: return "Pick-up now"
        val today = now.atZone(MobileBookingRules.zone).toLocalDate()
        val dayLabel = when (parsed.toLocalDate()) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            else -> parsed.toLocalDate().format(displayDayFormat)
        }
        return "$dayLabel, ${parsed.toLocalTime().format(displayTimeFormat)}"
    }

    fun formatSummarySchedule(isPickupNow: Boolean, scheduledRaw: String, now: Instant = Instant.now()): String {
        return if (isPickupNow || scheduledRaw.isBlank()) {
            "Immediate · ${formatSchedulePill(false, resolveDepartureRaw(true, "", now), now)} PHT"
        } else {
            "Scheduled: ${formatSchedulePill(false, scheduledRaw, now)} PHT"
        }
    }

    fun splitAddress(raw: String): Pair<String, String> {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return "" to ""
        val parts = trimmed.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return if (parts.size <= 1) {
            trimmed to "Philippines"
        } else {
            parts.first() to parts.drop(1).joinToString(", ")
        }
    }

    fun formatDuration(minutes: Int): String {
        if (minutes < 60) return "Est. ${minutes.coerceAtLeast(1)}min"
        val hrs = minutes / 60
        val mins = minutes % 60
        return if (mins == 0) "Est. ${hrs}h" else "Est. ${hrs}h ${mins}min"
    }

    fun weightFitsCapacity(weightRaw: String, capacityRaw: String): Boolean {
        val w = MobileValidation.weight(weightRaw) ?: return false
        val c = capacityRaw.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 } ?: return false
        return w <= c
    }

    val defaultSuggestions: List<SelectedAddress> = listOf(
        SelectedAddress("Cabanatuan City Public Market, Cabanatuan City, Nueva Ecija", 15.4865, 120.9679),
        SelectedAddress("SM City Cabanatuan, Maharlika Highway, Cabanatuan City, Nueva Ecija", 15.4673, 120.9547),
        SelectedAddress("Manila North Harbor, Tondo, Manila, Metro Manila", 14.6158, 120.9585),
        SelectedAddress("Valenzuela Gateway Complex, Paso de Blas, Valenzuela, Metro Manila", 14.7094, 120.9936),
        SelectedAddress("Bulihan Provincial Rd, Balagtas, 3016 Bulacan, Philippines", 14.8181, 120.9092),
        SelectedAddress("Clark Freeport Zone, Angeles City, Pampanga", 15.1859, 120.5600)
    )
}
