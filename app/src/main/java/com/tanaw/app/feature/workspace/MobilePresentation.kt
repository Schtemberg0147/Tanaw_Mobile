package com.tanaw.app.feature.workspace

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.text.NumberFormat
import java.util.Locale

object MobilePresentation {
    private val date = DateTimeFormatter.ofPattern("MMM d, uuuu · h:mm a", Locale.ENGLISH).withZone(ZoneId.of("Asia/Manila"))
    fun time(value: String): String = try { date.format(Instant.parse(value)) + " PHT" } catch (_: Exception) { "Not recorded" }
    fun money(value: String): String = value.toBigDecimalOrNull()?.let { NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-PH")).format(it) } ?: "Awaiting quotation"
    fun number(value: String, unit: String): String = value.toBigDecimalOrNull()?.let { NumberFormat.getNumberInstance(Locale.ENGLISH).format(it) + " " + unit } ?: "Not recorded"
    fun status(value: String): String = value.replace('_',' ').replaceFirstChar(Char::uppercase)
}
