package com.tanaw.app.feature.workspace.customer.rules

import com.tanaw.app.R
import com.tanaw.app.core.validation.MobileValidation
import com.tanaw.app.feature.workspace.viewmodel.value
import com.tanaw.app.ui.components.SelectedAddress
import kotlinx.serialization.json.JsonObject
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

data class ScheduledSelectionValidation(
    val raw: String,
    val wasAdjusted: Boolean
)

data class BaseLinehaulEstimate(
    val amount: Double,
    val formattedAmount: String,
    val flooredAtMinCharge: Boolean
)

object CustomerBookingRules {
    private val displayTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val displayDayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE MMM d", Locale.ENGLISH)
    private val displayTimestampFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE MMM d, h:mm a", Locale.ENGLISH)

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

    fun minLeadTimeHours(vehicle: JsonObject?): Int {
        val explicit = vehicle?.value("min_lead_time_hours")?.toIntOrNull()
        if (explicit != null && explicit > 0) return explicit
        val name = vehicle?.value("name").orEmpty().lowercase(Locale.ENGLISH)
        return if ("refrig" in name || "reefer" in name || "cold" in name || "flat" in name) {
            MobileBookingRules.SPECIALIZED_MIN_LEAD_TIME_HOURS.toInt()
        } else {
            MobileBookingRules.DEFAULT_MIN_LEAD_TIME_HOURS.toInt()
        }
    }

    fun loadingBufferMinutes(vehicle: JsonObject?): Int {
        val explicit = vehicle?.value("loading_buffer_minutes")?.toIntOrNull()
        return if (explicit != null && explicit >= 0) {
            explicit
        } else {
            MobileBookingRules.DEFAULT_LOADING_BUFFER_MINUTES
        }
    }

    fun leadTimeHelperText(minLeadTimeHours: Int): String =
        if (minLeadTimeHours >= MobileBookingRules.SPECIALIZED_MIN_LEAD_TIME_HOURS.toInt()) {
            "$minLeadTimeHours hours advance prep required"
        } else {
            "Requires at least $minLeadTimeHours hours advance booking"
        }

    fun leadTimeBadge(minLeadTimeHours: Int): String =
        if (minLeadTimeHours >= MobileBookingRules.SPECIALIZED_MIN_LEAD_TIME_HOURS.toInt()) {
            "${minLeadTimeHours}h prep"
        } else {
            "${minLeadTimeHours}h min"
        }

    fun formatCapacity(rawKg: String): String {
        val value = rawKg.toDoubleOrNull()?.roundToInt() ?: return "Up to $rawKg kg"
        return "Up to ${NumberFormat.getIntegerInstance(Locale.US).format(value)} kg"
    }

    fun scheduledDays(
        now: Instant = Instant.now(),
        minLeadTimeHours: Int = MobileBookingRules.DEFAULT_MIN_LEAD_TIME_HOURS.toInt()
    ): List<LocalDate> {
        val minDate = MobileBookingRules.earliest(now, minLeadTimeHours.toLong()).toLocalDate()
        val maxDate = MobileBookingRules.latest(now).toLocalDate()
        val days = mutableListOf<LocalDate>()
        var cursor = minDate
        while (!cursor.isAfter(maxDate)) {
            days += cursor
            cursor = cursor.plusDays(1)
        }
        return days
    }

    fun availableHoursForDate(
        selectedDate: LocalDate,
        now: Instant = Instant.now(),
        minLeadTimeHours: Int = MobileBookingRules.DEFAULT_MIN_LEAD_TIME_HOURS.toInt()
    ): List<Int> {
        val minLocal = MobileBookingRules.earliest(now, minLeadTimeHours.toLong())
        val maxLocal = MobileBookingRules.latest(now)
        if (selectedDate.isBefore(minLocal.toLocalDate()) || selectedDate.isAfter(maxLocal.toLocalDate())) {
            return emptyList()
        }
        val minHour = if (selectedDate == minLocal.toLocalDate()) minLocal.hour else 0
        val maxHour = if (selectedDate == maxLocal.toLocalDate()) maxLocal.hour else 23
        return if (minHour <= maxHour) (minHour..maxHour).toList() else emptyList()
    }

    fun availableMinutesForDateTime(
        selectedDate: LocalDate,
        selectedHour24: Int,
        now: Instant = Instant.now(),
        minLeadTimeHours: Int = MobileBookingRules.DEFAULT_MIN_LEAD_TIME_HOURS.toInt()
    ): List<Int> {
        val minLocal = MobileBookingRules.earliest(now, minLeadTimeHours.toLong())
        val maxLocal = MobileBookingRules.latest(now)
        if (selectedDate.isBefore(minLocal.toLocalDate()) || selectedDate.isAfter(maxLocal.toLocalDate())) {
            return emptyList()
        }
        if (selectedDate == minLocal.toLocalDate() && selectedHour24 < minLocal.hour) return emptyList()
        if (selectedDate == maxLocal.toLocalDate() && selectedHour24 > maxLocal.hour) return emptyList()
        val minMinute = if (selectedDate == minLocal.toLocalDate() && selectedHour24 == minLocal.hour) minLocal.minute else 0
        val maxMinute = if (selectedDate == maxLocal.toLocalDate() && selectedHour24 == maxLocal.hour) maxLocal.minute else 59
        return if (minMinute <= maxMinute) (minMinute..maxMinute).toList() else emptyList()
    }

    fun defaultScheduledRaw(
        now: Instant = Instant.now(),
        minLeadTimeHours: Int = MobileBookingRules.DEFAULT_MIN_LEAD_TIME_HOURS.toInt()
    ): String {
        val minLocal = MobileBookingRules.earliest(now, minLeadTimeHours.toLong())
        val maxLocal = MobileBookingRules.latest(now)
        val remainder = minLocal.minute % 15
        val rounded = if (remainder == 0) minLocal else minLocal.plusMinutes((15 - remainder).toLong())
        val chosen = if (rounded.isAfter(maxLocal)) minLocal else rounded
        return chosen.format(MobileBookingRules.format)
    }

    fun ensureValidScheduledRaw(
        raw: String,
        now: Instant = Instant.now(),
        minLeadTimeHours: Int = MobileBookingRules.DEFAULT_MIN_LEAD_TIME_HOURS.toInt()
    ): ScheduledSelectionValidation {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) {
            return ScheduledSelectionValidation(
                raw = defaultScheduledRaw(now, minLeadTimeHours),
                wasAdjusted = false
            )
        }
        return if (MobileBookingRules.departure(trimmed, now, minLeadTimeHours.toLong()) != null) {
            ScheduledSelectionValidation(raw = trimmed, wasAdjusted = false)
        } else {
            ScheduledSelectionValidation(
                raw = defaultScheduledRaw(now, minLeadTimeHours),
                wasAdjusted = true
            )
        }
    }

    fun resolveDepartureRaw(
        scheduledRaw: String,
        now: Instant = Instant.now(),
        minLeadTimeHours: Int = MobileBookingRules.DEFAULT_MIN_LEAD_TIME_HOURS.toInt()
    ): String = ensureValidScheduledRaw(scheduledRaw, now, minLeadTimeHours).raw

    fun formatSchedulePill(scheduledRaw: String, now: Instant = Instant.now()): String {
        if (scheduledRaw.isBlank()) return "Schedule pick-up"
        val parsed = runCatching { LocalDateTime.parse(scheduledRaw.trim(), MobileBookingRules.format) }.getOrNull()
            ?: return "Schedule pick-up"
        val today = now.atZone(MobileBookingRules.zone).toLocalDate()
        val dayLabel = when (parsed.toLocalDate()) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            else -> parsed.toLocalDate().format(displayDayFormat)
        }
        return "$dayLabel, ${parsed.toLocalTime().format(displayTimeFormat)}"
    }

    fun formatSummarySchedule(scheduledRaw: String, now: Instant = Instant.now()): String {
        if (scheduledRaw.isBlank()) return "Schedule required"
        return "${formatSchedulePill(scheduledRaw, now)} PHT"
    }

    fun formatEstimatedArrival(estimatedArrivalIso: String): String {
        val parsed = runCatching { Instant.parse(estimatedArrivalIso.trim()) }.getOrNull() ?: return "Pending route"
        return "${parsed.atZone(MobileBookingRules.zone).toLocalDateTime().format(displayTimestampFormat)} PHT"
    }

    fun estimateBaseLinehaul(
        vehicle: JsonObject?,
        pricingRates: List<JsonObject>,
        distanceKm: Double?,
        weightKg: Double? = null
    ): BaseLinehaulEstimate? {
        if (vehicle == null || distanceKm == null || !distanceKm.isFinite() || distanceKm <= 0.0) {
            return null
        }
        val vehicleId = vehicle.value("id")
        val rateRow = pricingRates.firstOrNull { row ->
            row.value("vehicle_type_id") == vehicleId && row.value("is_active") != "false"
        } ?: pricingRates.firstOrNull { row ->
            row.value("vehicle_type_id") == vehicleId
        }

        val baseRate = rateRow?.value("base_rate")?.toDoubleOrNull()
            ?: vehicle.value("base_rate").toDoubleOrNull()
        val perKmRate = rateRow?.value("per_km_rate")?.toDoubleOrNull()
            ?: vehicle.value("per_km_rate").toDoubleOrNull()
        val perKgRate = rateRow?.value("per_kg_rate")?.toDoubleOrNull()
            ?: vehicle.value("per_kg_rate").toDoubleOrNull()
        val minCharge = rateRow?.value("min_charge")?.toDoubleOrNull()
            ?: vehicle.value("min_charge").toDoubleOrNull()

        if (baseRate == null && perKmRate == null && minCharge == null) {
            return null
        }

        val safeWeight = (weightKg ?: 0.0).takeIf { it.isFinite() && it > 0.0 } ?: 0.0
        val computed = (baseRate ?: 0.0) +
            distanceKm * (perKmRate ?: 0.0) +
            safeWeight * (perKgRate ?: 0.0)
        val floor = (minCharge ?: 0.0).coerceAtLeast(0.0)
        val amount = maxOf(computed, floor)
        if (!amount.isFinite() || amount <= 0.0) return null

        val formatter = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-PH"))
        return BaseLinehaulEstimate(
            amount = amount,
            formattedAmount = formatter.format(amount),
            flooredAtMinCharge = floor > 0.0 && computed < floor
        )
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

    private val plusCodeSegmentRegex =
        Regex("^[23456789CFGHJMPQRVWX]{2,8}\\+[23456789CFGHJMPQRVWX]{2,3}$", RegexOption.IGNORE_CASE)
    private val leadingPostalCodeRegex = Regex("^\\d{4}\\s+")
    private val streetOrBarangayHintRegex =
        Regex("(?i)(?:^\\d|^#|^brgy\\.?\\b|^barangay\\b|\\b(?:st\\.?|street|ave\\.?|avenue|rd\\.?|road|hwy\\.?|highway|blvd\\.?|boulevard|dr\\.?|drive|ln\\.?|lane|purok|sitio|subd\\.?|subdivision)\\b)")

    fun resolveHeaderCity(
        rawAddress: String,
        rawCity: String,
        explicitBarangay: String = "",
        explicitProvince: String = "",
        pickupAddress: String = "",
        liveLocationCity: String = "",
        liveLocationAddress: String = ""
    ): String {
        val trimmedLiveCity = liveLocationCity.trim()
        if (trimmedLiveCity.isNotBlank()) {
            return trimmedLiveCity
        }
        extractCityProvinceFromAddress(liveLocationAddress)?.let { return it }

        val parsed = CustomerAddressRules.parseFromSaved(
            rawAddress = rawAddress,
            rawCity = rawCity,
            explicitBarangay = explicitBarangay,
            explicitProvince = explicitProvince
        )
        val city = parsed.city.trim()
        val province = parsed.province.trim()
        when {
            city.isNotBlank() && province.isNotBlank() -> return "$city, $province"
            city.isNotBlank() -> return city
            province.isNotBlank() -> return province
        }
        extractCityProvinceFromAddress(rawAddress)?.let { return it }
        extractCityProvinceFromAddress(pickupAddress)?.let { return it }
        return ""
    }

    private fun extractCityProvinceFromAddress(address: String): String? {
        val segments = address
            .split(",")
            .map { it.trim() }
            .filter {
                it.isNotBlank() &&
                    !it.equals("Philippines", ignoreCase = true) &&
                    !plusCodeSegmentRegex.matches(it)
            }
            .map { it.replace(leadingPostalCodeRegex, "").trim() }
            .filter { it.isNotBlank() }

        return when (segments.size) {
            0 -> null
            1 -> segments.first()
            2 -> {
                val first = segments[0]
                val second = segments[1]
                if (streetOrBarangayHintRegex.containsMatchIn(first)) second else "$first, $second"
            }
            else -> {
                val city = segments[segments.size - 2]
                val province = segments.last()
                if (streetOrBarangayHintRegex.containsMatchIn(city)) province else "$city, $province"
            }
        }
    }

    val COMMON_CARGO_PRESETS: List<String> = listOf(
        "Rice / Grains",
        "Fresh Produce",
        "Dry Goods / Retail",
        "Construction Materials",
        "Beverages / Bottled",
        "Machinery / Equipment"
    )

    val COMMON_DRIVER_NOTE_PRESETS: List<String> = listOf(
        "Call upon arrival",
        "Loading dock available",
        "Gate pass required",
        "Handle with care"
    )

    fun quickWeightPresets(capacityRaw: String): List<Int> {
        val cap = capacityRaw.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }?.roundToInt()
            ?: return listOf(500, 1000, 2000, 5000)
        val candidates = listOf(250, 500, 1000, 1500, 2000, 3000, 5000, 8000, 10000, 15000, 20000)
        val fitting = candidates.filter { it <= cap }
        return when {
            fitting.isEmpty() -> listOf(cap)
            fitting.size <= 4 -> fitting
            else -> {
                val picked = linkedSetOf(
                    fitting[fitting.size / 4],
                    fitting[fitting.size / 2],
                    fitting[(fitting.size * 3) / 4],
                    fitting.last()
                )
                picked.toList()
            }
        }
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
