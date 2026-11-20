package com.tanaw.app.feature.workspace.customer.rules

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.time.temporal.ChronoUnit

object MobileBookingRules {
    const val DEFAULT_MIN_LEAD_TIME_HOURS = 24L
    const val SPECIALIZED_MIN_LEAD_TIME_HOURS = 48L
    const val MAX_ADVANCE_DAYS = 14L
    const val DEFAULT_LOADING_BUFFER_MINUTES = 180

    val zone: ZoneId = ZoneId.of("Asia/Manila")
    val format: DateTimeFormatter =
        DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(ResolverStyle.STRICT)

    fun earliest(
        now: Instant = Instant.now(),
        minLeadTimeHours: Long = DEFAULT_MIN_LEAD_TIME_HOURS
    ): LocalDateTime {
        val safeHours = minLeadTimeHours.coerceAtLeast(DEFAULT_MIN_LEAD_TIME_HOURS)
        return now.truncatedTo(ChronoUnit.MINUTES)
            .plusSeconds(60)
            .plus(safeHours, ChronoUnit.HOURS)
            .atZone(zone)
            .toLocalDateTime()
    }

    fun latest(now: Instant = Instant.now()): LocalDateTime =
        now.atZone(zone).plusDays(MAX_ADVANCE_DAYS).toLocalDateTime()

    fun departure(
        raw: String,
        now: Instant = Instant.now(),
        minLeadTimeHours: Long = DEFAULT_MIN_LEAD_TIME_HOURS
    ): String? = runCatching {
        val local = LocalDateTime.parse(raw.trim(), format)
        val minLocal = earliest(now, minLeadTimeHours)
        val maxLocal = latest(now)
        if (local.isBefore(minLocal) || local.isAfter(maxLocal)) {
            null
        } else {
            local.atZone(zone).toInstant().toString()
        }
    }.getOrNull()

    fun calculateEstimatedArrival(
        departureIso: String,
        routeDurationMinutes: Int?,
        loadingBufferMinutes: Int = DEFAULT_LOADING_BUFFER_MINUTES
    ): String? = runCatching {
        val depInstant = Instant.parse(departureIso.trim())
        val travelMinutes = (routeDurationMinutes ?: 0).coerceAtLeast(0).toLong()
        val bufferMinutes = loadingBufferMinutes.coerceAtLeast(0).toLong()
        depInstant.plus(travelMinutes + bufferMinutes, ChronoUnit.MINUTES).toString()
    }.getOrNull()
}
