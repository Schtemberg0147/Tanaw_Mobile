package com.tanaw.app.feature.workspace.customer.rules

import com.tanaw.app.feature.workspace.rules.MobilePresentation
import com.tanaw.app.feature.workspace.viewmodel.obj
import com.tanaw.app.feature.workspace.viewmodel.rows
import com.tanaw.app.feature.workspace.viewmodel.value
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal enum class PickupWaitPhase {
    NONE,
    WITHIN_GRACE,
    GRACE_ENDED,
    AT_RISK,
    EXPIRED_WAITING_CONFIRMATION
}

internal data class PickupWaitState(
    val phase: PickupWaitPhase = PickupWaitPhase.NONE,
    val deliveryId: String = "",
    val arrivedAt: Instant? = null,
    val graceEndsAt: Instant? = null,
    val autoCancelAt: Instant? = null,
    val totalWaitMinutes: Long = 30L,
    val elapsedOverGraceSeconds: Long = 0L,
    val remainingBeforeCancelSeconds: Long = 0L,
    val graceOverProgress: Float = 0f,
    val atRiskProgress: Float = 0f,
    val customerOnWayAt: String = "",
    val arrivedTimeText: String = "",
    val graceEndsTimeText: String = ""
) {
    val isUrgentBannerVisible: Boolean
        get() = phase == PickupWaitPhase.GRACE_ENDED ||
            phase == PickupWaitPhase.AT_RISK ||
            phase == PickupWaitPhase.EXPIRED_WAITING_CONFIRMATION

    val hasNotifiedOnWay: Boolean
        get() = customerOnWayAt.isNotBlank()
}

internal enum class CustomerStandingLevel {
    GOOD,
    COOLDOWN,
    SUSPENDED
}

internal data class CustomerStrikeRecord(
    val id: String,
    val strikeNumber: Int,
    val shipmentId: String,
    val trackingNumber: String,
    val confirmedAt: String,
    val formattedDate: String,
    val reason: String,
    val attemptNotes: String
)

internal data class CustomerAppealRecord(
    val id: String,
    val status: String,
    val reason: String,
    val adminNotes: String,
    val createdAt: String,
    val formattedCreatedAt: String,
    val reviewedAt: String
) {
    val isPending: Boolean get() = status.equals("pending", ignoreCase = true)
    val isRejected: Boolean get() = status.equals("rejected", ignoreCase = true)
}

internal data class CustomerStandingSummary(
    val strikeCount: Int = 0,
    val maxStrikes: Int = 3,
    val level: CustomerStandingLevel = CustomerStandingLevel.GOOD,
    val cooldownUntil: Instant? = null,
    val cooldownUntilIso: String = "",
    val cooldownRemainingText: String = "",
    val cooldownHoursHint: Int = 0,
    val suspendedAt: String = "",
    val formattedSuspendedDate: String = "",
    val suspensionReason: String = "",
    val isReinstated: Boolean = false,
    val strikes: List<CustomerStrikeRecord> = emptyList(),
    val latestAppeal: CustomerAppealRecord? = null
) {
    val isSuspended: Boolean get() = level == CustomerStandingLevel.SUSPENDED
    val isInCooldown: Boolean get() = level == CustomerStandingLevel.COOLDOWN
    val isBookingBlocked: Boolean get() = isSuspended || isInCooldown
    val displayStrikesClamped: Int get() = strikeCount.coerceIn(0, maxStrikes)
}

internal enum class NoShowTimelineNodeTone {
    SUCCESS,
    WARNING,
    DANGER
}

internal data class NoShowTimelineStep(
    val title: String,
    val subtitle: String = "",
    val tone: NoShowTimelineNodeTone
)

internal object CustomerNoShowRules {
    const val MAX_STRIKES = 3
    const val DEFAULT_GRACE_MINUTES = 15L
    const val DEFAULT_TOTAL_WAIT_MINUTES = 30L
    const val AT_RISK_WINDOW_SECONDS = 5L * 60L
    const val STRIKE_1_COOLDOWN_HOURS = 48
    const val STRIKE_2_COOLDOWN_HOURS = 72
    const val MIN_APPEAL_REASON_LENGTH = 10
    const val MAX_APPEAL_REASON_LENGTH = 1500

    private val manilaZone: ZoneId = ZoneId.of("Asia/Manila")
    private val clockFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH).withZone(manilaZone)
    private val dateOnlyFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.ENGLISH).withZone(manilaZone)
    private val cancelledHeaderFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d, uuuu • hh:mm a", Locale.ENGLISH).withZone(manilaZone)

    fun evaluatePickupWait(
        shipment: JsonObject,
        delivery: JsonObject?,
        now: Instant = Instant.now()
    ): PickupWaitState {
        if (delivery == null) return PickupWaitState()
        val shipmentStatus = shipment.value("status").trim().lowercase(Locale.US)
        val deliveryStatus = delivery.value("status").trim().lowercase(Locale.US)

        if (shipmentStatus in setOf("cancelled", "completed", "delivered") ||
            deliveryStatus != "assigned"
        ) {
            return PickupWaitState()
        }

        val arrivedRaw = delivery.value("pickup_arrived_at")
        val showedRaw = delivery.value("customer_showed_at")
        if (arrivedRaw.isBlank() || showedRaw.isNotBlank()) {
            return PickupWaitState()
        }

        val arrivedAt = MobilePresentation.parseInstant(arrivedRaw) ?: return PickupWaitState()
        val graceEndsAt = MobilePresentation.parseInstant(delivery.value("grace_ends_at"))
            ?: arrivedAt.plusSeconds(DEFAULT_GRACE_MINUTES * 60L)
        val autoCancelAt = MobilePresentation.parseInstant(delivery.value("auto_cancel_at"))
            ?: arrivedAt.plusSeconds(DEFAULT_TOTAL_WAIT_MINUTES * 60L)

        val effectiveAutoCancel = if (autoCancelAt.isAfter(graceEndsAt)) {
            autoCancelAt
        } else {
            graceEndsAt.plusSeconds(DEFAULT_GRACE_MINUTES * 60L)
        }

        val totalWaitMinutes = Duration.between(arrivedAt, effectiveAutoCancel)
            .toMinutes()
            .coerceAtLeast(1L)
        val postGraceDurationSec = Duration.between(graceEndsAt, effectiveAutoCancel)
            .seconds
            .coerceAtLeast(1L)

        val remainingSec = Duration.between(now, effectiveAutoCancel).seconds
        val elapsedOverGraceSec = Duration.between(graceEndsAt, now).seconds.coerceAtLeast(0L)

        val phase = when {
            now.isBefore(graceEndsAt) -> PickupWaitPhase.WITHIN_GRACE
            remainingSec <= 0L -> PickupWaitPhase.EXPIRED_WAITING_CONFIRMATION
            remainingSec <= AT_RISK_WINDOW_SECONDS -> PickupWaitPhase.AT_RISK
            else -> PickupWaitPhase.GRACE_ENDED
        }

        val graceOverProgress = (elapsedOverGraceSec.toFloat() / postGraceDurationSec.toFloat())
            .coerceIn(0f, 1f)
        val atRiskWindow = minOf(AT_RISK_WINDOW_SECONDS, postGraceDurationSec).coerceAtLeast(1L)
        val atRiskProgress = (remainingSec.coerceIn(0L, atRiskWindow).toFloat() / atRiskWindow.toFloat())
            .coerceIn(0f, 1f)

        return PickupWaitState(
            phase = phase,
            deliveryId = delivery.value("id"),
            arrivedAt = arrivedAt,
            graceEndsAt = graceEndsAt,
            autoCancelAt = effectiveAutoCancel,
            totalWaitMinutes = totalWaitMinutes,
            elapsedOverGraceSeconds = elapsedOverGraceSec,
            remainingBeforeCancelSeconds = remainingSec.coerceAtLeast(0L),
            graceOverProgress = graceOverProgress,
            atRiskProgress = atRiskProgress,
            customerOnWayAt = delivery.value("customer_on_way_at"),
            arrivedTimeText = formatClock(arrivedRaw),
            graceEndsTimeText = formatClock(graceEndsAt.toString())
        )
    }

    fun evaluateStanding(
        data: JsonObject?,
        now: Instant = Instant.now()
    ): CustomerStandingSummary {
        if (data == null) return CustomerStandingSummary()
        val customer = data.obj("customer")
        val shipmentsById = data.rows("shipments").associateBy { it.value("id") }

        val rawStrikeRows = data.rows("customer_strikes")
            .ifEmpty { data.rows("customer_no_show_strikes") }
        val strikes = rawStrikeRows
            .mapIndexed { index, row ->
                val shipmentId = row.value("shipment_id")
                val tracking = row.value("tracking_number")
                    .ifBlank { shipmentsById[shipmentId]?.value("tracking_number").orEmpty() }
                val confirmedAt = row.value("confirmed_at").ifBlank { row.value("created_at") }
                val strikeNum = row.value("strike_number").toIntOrNull() ?: (index + 1)
                CustomerStrikeRecord(
                    id = row.value("id").ifBlank { "strike-$strikeNum" },
                    strikeNumber = strikeNum,
                    shipmentId = shipmentId,
                    trackingNumber = tracking,
                    confirmedAt = confirmedAt,
                    formattedDate = formatDateOnly(confirmedAt),
                    reason = row.value("reason").ifBlank { "Confirmed no-show" },
                    attemptNotes = row.value("attempt_notes")
                )
            }
            .sortedBy { it.strikeNumber }

        val explicitCount = customer.value("no_show_strike_count").toIntOrNull() ?: 0
        val strikeCount = maxOf(explicitCount, strikes.size)

        val standingRaw = customer.value("standing_status").trim().lowercase(Locale.US)
        val suspendedAtRaw = customer.value("suspended_at")
            .ifBlank { if (standingRaw == "suspended") strikes.lastOrNull()?.confirmedAt.orEmpty() else "" }
        val isReinstated = standingRaw == "reinstated" || customer.value("reinstated_at").isNotBlank()

        val isSuspended = standingRaw == "suspended" ||
            (!isReinstated && strikeCount >= MAX_STRIKES)

        val cooldownIso = customer.value("booking_cooldown_until")
        val cooldownUntil = MobilePresentation.parseInstant(cooldownIso)
        val isInCooldown = !isSuspended && cooldownUntil != null && cooldownUntil.isAfter(now)

        val level = when {
            isSuspended -> CustomerStandingLevel.SUSPENDED
            isInCooldown -> CustomerStandingLevel.COOLDOWN
            else -> CustomerStandingLevel.GOOD
        }

        val cooldownHoursHint = when (strikeCount) {
            1 -> STRIKE_1_COOLDOWN_HOURS
            2 -> STRIKE_2_COOLDOWN_HOURS
            else -> 0
        }

        val rawAppeals = data.rows("customer_appeals")
        val latestAppeal = rawAppeals.firstOrNull()?.let { row ->
            val createdAt = row.value("created_at")
            CustomerAppealRecord(
                id = row.value("id"),
                status = row.value("status").ifBlank { "pending" },
                reason = row.value("reason"),
                adminNotes = row.value("admin_notes"),
                createdAt = createdAt,
                formattedCreatedAt = formatDateOnly(createdAt),
                reviewedAt = row.value("reviewed_at")
            )
        }

        val suspensionReason = customer.value("suspension_reason").ifBlank {
            if (strikeCount >= 4) {
                "$strikeCount confirmed no-shows (post-reinstatement review)"
            } else {
                "$MAX_STRIKES confirmed no-shows"
            }
        }

        return CustomerStandingSummary(
            strikeCount = strikeCount,
            maxStrikes = MAX_STRIKES,
            level = level,
            cooldownUntil = cooldownUntil,
            cooldownUntilIso = cooldownIso,
            cooldownRemainingText = if (isInCooldown) {
                formatCooldownRemaining(cooldownUntil, now)
            } else {
                ""
            },
            cooldownHoursHint = cooldownHoursHint,
            suspendedAt = suspendedAtRaw,
            formattedSuspendedDate = formatDateOnly(suspendedAtRaw),
            suspensionReason = suspensionReason,
            isReinstated = isReinstated,
            strikes = strikes,
            latestAppeal = latestAppeal
        )
    }

    fun isNoShowCancellation(
        shipment: JsonObject,
        delivery: JsonObject?,
        detail: JsonObject? = null,
        strikes: List<CustomerStrikeRecord> = emptyList()
    ): Boolean {
        val shipmentStatus = shipment.value("status").trim().lowercase(Locale.US)
        val deliveryStatus = delivery?.value("status")?.trim()?.lowercase(Locale.US).orEmpty()
        if (shipmentStatus != "cancelled" && deliveryStatus != "cancelled") return false

        val cancellationType = shipment.value("cancellation_type").trim().lowercase(Locale.US)
        if (cancellationType == "no_show") return true

        val shipmentId = shipment.value("id")
        if (shipmentId.isNotBlank() && strikes.any { it.shipmentId == shipmentId }) return true

        if (delivery?.value("no_show_confirmed_at")?.isNotBlank() == true) return true
        if (detail?.obj("delivery")?.value("no_show_confirmed_at")?.isNotBlank() == true) return true

        val reason = shipment.value("cancelled_reason").lowercase(Locale.US)
        return reason.contains("no-show") || reason.contains("no show") || reason.contains("no_show")
    }

    fun strikeNumberForShipment(
        shipment: JsonObject,
        standing: CustomerStandingSummary
    ): Int {
        val shipmentId = shipment.value("id")
        val matched = standing.strikes.firstOrNull { it.shipmentId == shipmentId }
        return matched?.strikeNumber ?: standing.strikeCount.coerceAtLeast(1)
    }

    fun buildNoShowTimeline(
        shipment: JsonObject,
        delivery: JsonObject?,
        detail: JsonObject? = null,
        strikeRecord: CustomerStrikeRecord? = null
    ): List<NoShowTimelineStep> {
        val detailDelivery = detail?.obj("delivery")?.takeIf { it.isNotEmpty() }
        val arrivedRaw = delivery?.value("pickup_arrived_at").orEmpty()
            .ifBlank { detailDelivery?.value("pickup_arrived_at").orEmpty() }
        val graceRaw = delivery?.value("grace_ends_at").orEmpty()
            .ifBlank { detailDelivery?.value("grace_ends_at").orEmpty() }
        val confirmedRaw = strikeRecord?.confirmedAt.orEmpty()
            .ifBlank { delivery?.value("no_show_confirmed_at").orEmpty() }
            .ifBlank { detailDelivery?.value("no_show_confirmed_at").orEmpty() }
            .ifBlank { shipment.value("cancelled_at") }
            .ifBlank { shipment.value("updated_at") }

        val arrivedInstant = MobilePresentation.parseInstant(arrivedRaw)
        val derivedGraceRaw = graceRaw.ifBlank {
            arrivedInstant?.plusSeconds(DEFAULT_GRACE_MINUTES * 60L)?.toString().orEmpty()
        }

        val arrivedTime = formatClock(arrivedRaw)
        val graceTime = formatClock(derivedGraceRaw)
        val confirmedTime = formatClock(confirmedRaw)
        val pickupShort = shipment.value("pickup_address")
            .split(',')
            .firstOrNull()
            ?.trim()
            ?.take(42)
            .orEmpty()

        val attemptSubtitle = strikeRecord?.attemptNotes?.takeIf { it.isNotBlank() }
            ?: "Confirmed by driver / office verification"

        return listOf(
            NoShowTimelineStep(
                title = if (arrivedTime.isNotBlank()) "Driver arrived at $arrivedTime" else "Driver arrived at pick-up",
                subtitle = if (pickupShort.isNotBlank()) "At $pickupShort" else "Pick-up location verified",
                tone = NoShowTimelineNodeTone.SUCCESS
            ),
            NoShowTimelineStep(
                title = if (graceTime.isNotBlank()) "Grace period ended at $graceTime" else "Grace period ended",
                subtitle = "$DEFAULT_GRACE_MINUTES-minute waiting grace elapsed",
                tone = NoShowTimelineNodeTone.SUCCESS
            ),
            NoShowTimelineStep(
                title = "Grace period exceeded",
                subtitle = "Customer did not check in at pick-up",
                tone = NoShowTimelineNodeTone.WARNING
            ),
            NoShowTimelineStep(
                title = if (confirmedTime.isNotBlank()) "No-show confirmed at $confirmedTime" else "No-show confirmed",
                subtitle = attemptSubtitle,
                tone = NoShowTimelineNodeTone.DANGER
            )
        )
    }

    fun formatMmSs(totalSeconds: Long): String {
        val safe = totalSeconds.coerceAtLeast(0L)
        val mins = safe / 60L
        val secs = safe % 60L
        return String.format(Locale.US, "%02d:%02d", mins, secs)
    }

    fun formatClock(raw: String): String =
        MobilePresentation.parseInstant(raw)?.let(clockFormatter::format).orEmpty()

    fun formatDateOnly(raw: String): String =
        MobilePresentation.parseInstant(raw)?.let(dateOnlyFormatter::format).orEmpty()

    fun formatCancelledTimestamp(raw: String): String =
        MobilePresentation.parseInstant(raw)?.let(cancelledHeaderFormatter::format).orEmpty()

    fun formatCooldownRemaining(cooldownUntil: Instant, now: Instant = Instant.now()): String {
        val remainingMinutes = Duration.between(now, cooldownUntil).toMinutes().coerceAtLeast(0L)
        if (remainingMinutes <= 0L) return "Cooldown ended"
        val hours = remainingMinutes / 60L
        val mins = remainingMinutes % 60L
        return when {
            hours >= 24L -> "${hours}h ${mins}m remaining"
            hours > 0L -> "${hours}h ${mins}m remaining"
            else -> "${mins}m remaining"
        }
    }

    fun cooldownHelperSentence(strikeNumber: Int, standing: CustomerStandingSummary): String {
        val hours = when {
            strikeNumber == 1 -> STRIKE_1_COOLDOWN_HOURS
            strikeNumber >= 2 -> STRIKE_2_COOLDOWN_HOURS
            standing.cooldownHoursHint > 0 -> standing.cooldownHoursHint
            else -> STRIKE_1_COOLDOWN_HOURS
        }
        return if (standing.isInCooldown && standing.cooldownRemainingText.isNotBlank()) {
            "For the next $hours hours you won't be able to book a shipment (${standing.cooldownRemainingText})."
        } else {
            "For the next $hours hours you won't be able to book a shipment."
        }
    }

    fun validateAppealReason(reason: String): Boolean {
        val len = reason.trim().length
        return len in MIN_APPEAL_REASON_LENGTH..MAX_APPEAL_REASON_LENGTH
    }

    fun optimisticMarkCustomerOnWay(
        data: JsonObject?,
        deliveryId: String,
        nowIso: String
    ): JsonObject? {
        if (data == null || deliveryId.isBlank()) return data
        val updatedDeliveries = data.rows("deliveries").map { row ->
            if (row.value("id") == deliveryId) {
                JsonObject(row + ("customer_on_way_at" to JsonPrimitive(nowIso)))
            } else {
                row
            }
        }
        return JsonObject(data + ("deliveries" to JsonArray(updatedDeliveries)))
    }

    fun optimisticRecordAppeal(
        data: JsonObject?,
        appealId: String,
        reason: String,
        nowIso: String
    ): JsonObject? {
        if (data == null) return null
        val newAppeal = JsonObject(
            mapOf(
                "id" to JsonPrimitive(appealId.ifBlank { "pending-appeal" }),
                "status" to JsonPrimitive("pending"),
                "reason" to JsonPrimitive(reason.trim()),
                "admin_notes" to JsonPrimitive(""),
                "created_at" to JsonPrimitive(nowIso),
                "reviewed_at" to JsonPrimitive("")
            )
        )
        val existing = data.rows("customer_appeals").filterNot { it.value("id") == appealId }
        return JsonObject(data + ("customer_appeals" to JsonArray(listOf(newAppeal) + existing)))
    }
}
