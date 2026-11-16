package com.tanaw.app.feature.workspace.rules

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.text.NumberFormat
import java.util.Locale

object MobilePresentation {
    private val date = DateTimeFormatter.ofPattern("MMM d, uuuu · h:mm a", Locale.ENGLISH).withZone(ZoneId.of("Asia/Manila"))
    private val shortOffsetRegex = Regex("(.*[+-]\\d{2})$")
    private val compactOffsetRegex = Regex("(.*[+-]\\d{2})(\\d{2})$")

    internal fun parseInstant(value: String): Instant? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        val spaceNormalized = trimmed.replace(' ', 'T')
        val iso = when {
            shortOffsetRegex.matches(spaceNormalized) -> "$spaceNormalized:00"
            compactOffsetRegex.matches(spaceNormalized) -> compactOffsetRegex.replace(spaceNormalized, "$1:$2")
            else -> spaceNormalized
        }
        return runCatching { Instant.parse(iso) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(iso).toInstant() }.getOrNull()
            ?: runCatching { ZonedDateTime.parse(iso).toInstant() }.getOrNull()
    }

    fun time(value: String): String = parseInstant(value)?.let { date.format(it) + " PHT" } ?: "Not recorded"
    fun money(value: String): String = value.toBigDecimalOrNull()?.let { NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-PH")).format(it) } ?: "Awaiting quotation"
    fun number(value: String, unit: String): String = value.toBigDecimalOrNull()?.let { NumberFormat.getNumberInstance(Locale.ENGLISH).format(it) + " " + unit } ?: "Not recorded"
    fun status(value: String): String = value.replace('_',' ').replaceFirstChar(Char::uppercase)
}
