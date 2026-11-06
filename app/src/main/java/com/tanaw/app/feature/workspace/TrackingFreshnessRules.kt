package com.tanaw.app.feature.workspace

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonObject
import java.time.Duration
import java.time.Instant

enum class PositionFreshnessStatus {
    FRESH,
    RECENT,
    STALE,
    UNAVAILABLE
}

data class PositionFreshness(
    val status: PositionFreshnessStatus,
    val ageSeconds: Long?,
    val relativeText: String,
    val accuracyText: String
)

data class CustomerEtaPresentation(
    val scheduledDeparture: String,
    val scheduledArrival: String,
    val liveEta: String,
    val liveEtaDetail: String,
    val isLive: Boolean,
    val freshness: PositionFreshness
)

object TrackingFreshnessRules {
    /**
     * Evaluates GPS position freshness based on recorded_at timestamp and accuracy in meters.
     * Thresholds:
     * - FRESH: age < 120s (2 minutes)
     * - RECENT: age between 120s and 300s (2 to 5 minutes)
     * - STALE: age > 300s (> 5 minutes)
     * - UNAVAILABLE: blank or unparseable recorded_at
     */
    fun evaluatePositionFreshness(
        recordedAt: String,
        accuracyMeters: String,
        now: Instant = Instant.now()
    ): PositionFreshness {
        val accuracyText = when {
            accuracyMeters.isBlank() -> "accuracy unknown"
            accuracyMeters.toDoubleOrNull() != null -> "accuracy $accuracyMeters m"
            else -> "accuracy unknown"
        }

        if (recordedAt.isBlank()) {
            return PositionFreshness(
                status = PositionFreshnessStatus.UNAVAILABLE,
                ageSeconds = null,
                relativeText = "GPS position unavailable",
                accuracyText = accuracyText
            )
        }

        val recordedInstant = runCatching { Instant.parse(recordedAt) }.getOrNull()
        if (recordedInstant == null) {
            return PositionFreshness(
                status = PositionFreshnessStatus.UNAVAILABLE,
                ageSeconds = null,
                relativeText = "GPS position unavailable",
                accuracyText = accuracyText
            )
        }

        val rawAge = Duration.between(recordedInstant, now).seconds
        // Guard against future timestamps due to minor clock skew or invalid future dates:
        val age = if (rawAge < 0) {
            if (rawAge >= -60) 0L else {
                return PositionFreshness(
                    status = PositionFreshnessStatus.UNAVAILABLE,
                    ageSeconds = null,
                    relativeText = "GPS timestamp invalid",
                    accuracyText = accuracyText
                )
            }
        } else {
            rawAge
        }

        val status = when {
            age < 120 -> PositionFreshnessStatus.FRESH
            age <= 300 -> PositionFreshnessStatus.RECENT
            else -> PositionFreshnessStatus.STALE
        }

        val relativeText = when {
            age < 60 -> "Updated ${age}s ago"
            age < 3600 -> {
                val mins = age / 60
                if (status == PositionFreshnessStatus.STALE) "Stale · ${mins}m ago" else "Updated ${mins}m ago"
            }
            else -> {
                val hours = age / 3600
                "Stale · ${hours}h ago"
            }
        }

        return PositionFreshness(
            status = status,
            ageSeconds = age,
            relativeText = relativeText,
            accuracyText = accuracyText
        )
    }

    /**
     * Builds CustomerEtaPresentation separating scheduled arrival (office plan)
     * from live GPS ETA and position freshness.
     */
    fun customerEtaSummary(
        shipment: JsonObject,
        delivery: JsonObject?,
        routing: JsonObject?,
        freshness: PositionFreshness,
        offline: Boolean,
        now: Instant = Instant.now()
    ): CustomerEtaPresentation {
        val rawDeparture = shipment.value("scheduled_departure")
        val scheduledDeparture = if (rawDeparture.isNotBlank()) MobilePresentation.time(rawDeparture) else "Not scheduled"

        val rawArrival = shipment.value("estimated_arrival")
        val scheduledArrival = if (rawArrival.isNotBlank()) MobilePresentation.time(rawArrival) else "Not scheduled"

        if (offline) {
            return CustomerEtaPresentation(
                scheduledDeparture = scheduledDeparture,
                scheduledArrival = scheduledArrival,
                liveEta = "Live ETA unavailable while offline",
                liveEtaDetail = "Showing scheduled arrival",
                isLive = false,
                freshness = freshness
            )
        }

        val deliveryStatus = delivery?.value("status").orEmpty()
        if (delivery == null || deliveryStatus !in listOf("assigned", "loading", "in_transit")) {
            val detail = when {
                delivery == null -> "No delivery assignment has been recorded"
                deliveryStatus == "delivered" -> "Shipment delivered"
                deliveryStatus == "cancelled" -> "Delivery cancelled"
                else -> "No active transit ETA"
            }
            return CustomerEtaPresentation(
                scheduledDeparture = scheduledDeparture,
                scheduledArrival = scheduledArrival,
                liveEta = "No active transit ETA",
                liveEtaDetail = detail,
                isLive = false,
                freshness = freshness
            )
        }

        if (routing != null) {
            val etaStr = DriverRouteStatus.eta(routing, offline = false, now = now)
            if (etaStr.startsWith("ETA to ") && freshness.status in listOf(PositionFreshnessStatus.FRESH, PositionFreshnessStatus.RECENT)) {
                return CustomerEtaPresentation(
                    scheduledDeparture = scheduledDeparture,
                    scheduledArrival = scheduledArrival,
                    liveEta = etaStr,
                    liveEtaDetail = "Office-confirmed truck route · live GPS",
                    isLive = true,
                    freshness = freshness
                )
            } else {
                return CustomerEtaPresentation(
                    scheduledDeparture = scheduledDeparture,
                    scheduledArrival = scheduledArrival,
                    liveEta = "Live route ETA unavailable",
                    liveEtaDetail = "Live route ETA unavailable · relying on scheduled arrival",
                    isLive = false,
                    freshness = freshness
                )
            }
        }

        return CustomerEtaPresentation(
            scheduledDeparture = scheduledDeparture,
            scheduledArrival = scheduledArrival,
            liveEta = "Live route ETA unavailable",
            liveEtaDetail = "Relying on scheduled arrival",
            isLive = false,
            freshness = freshness
        )
    }
}

@Composable
fun GpsFreshnessBadge(freshness: PositionFreshness) {
    val (color, label) = when (freshness.status) {
        PositionFreshnessStatus.FRESH -> MaterialTheme.colorScheme.primary to "Fresh GPS · ${freshness.relativeText}"
        PositionFreshnessStatus.RECENT -> MaterialTheme.colorScheme.secondary to "Recent GPS · ${freshness.relativeText}"
        PositionFreshnessStatus.STALE -> MaterialTheme.colorScheme.error to freshness.relativeText
        PositionFreshnessStatus.UNAVAILABLE -> MaterialTheme.colorScheme.outline to freshness.relativeText
    }
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}
