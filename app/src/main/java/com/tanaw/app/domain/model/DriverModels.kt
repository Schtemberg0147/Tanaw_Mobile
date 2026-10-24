package com.tanaw.app.domain.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.tanaw.app.ui.theme.CancelledAccentBar
import com.tanaw.app.ui.theme.ExpiredRed
import com.tanaw.app.ui.theme.ExpiredRedBg
import com.tanaw.app.ui.theme.ExpiringOrange
import com.tanaw.app.ui.theme.ExpiringOrangeBg
import com.tanaw.app.ui.theme.NavyBanner
import com.tanaw.app.ui.theme.OngoingAccentBar
import com.tanaw.app.ui.theme.OngoingBlue
import com.tanaw.app.ui.theme.OngoingBlueBg
import com.tanaw.app.ui.theme.StatusGreen
import com.tanaw.app.ui.theme.StatusGreenBg
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Immutable
data class DriverProfile(
    val id: String,
    val userId: String,
    val fullName: String,
    val vehicleTypeName: String,
    val status: DriverStatus
)

enum class DriverStatus {
    AVAILABLE,
    ON_TRIP,
    OFF_DUTY,
    MAINTENANCE
}

@Immutable
data class CargoDetails(
    val categoryName: String,
    val formattedWeight: String,
    val specialHandlingTag: String?
)

@Immutable
data class RoutePoint(
    val locationName: String,
    val fullAddress: String,
    val formattedScheduledTime: String? = null
)

@Immutable
data class TripRoute(
    val pickup: RoutePoint,
    val destination: RoutePoint
)

@Immutable
data class TripMeta(
    val distanceKm: Double,
    val formattedDistance: String,
    val formattedDuration: String,
    val hasToll: Boolean
)

@Immutable
data class TripAssignment(
    val deliveryId: String,
    val shipmentId: String,
    val trackingNumber: String,
    val cargo: CargoDetails,
    val route: TripRoute,
    val meta: TripMeta,
    val allowanceAmount: Double,
    val formattedAllowance: String,
    val notes: String?
)

enum class ActiveTripStage {
    ASSIGNED,
    PICKUP_ARRIVED,
    CUSTOMER_SHOWED,
    CARGO_LOADED,
    DELIVERED
}

data class TripExpenseRequest(
    val deliveryId: String,
    val category: String,
    val amount: Double,
    val receiptPhotoBytes: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as TripExpenseRequest
        if (deliveryId != other.deliveryId) return false
        if (category != other.category) return false
        if (amount != other.amount) return false
        if (receiptPhotoBytes != null) {
            if (other.receiptPhotoBytes == null) return false
            if (!receiptPhotoBytes.contentEquals(other.receiptPhotoBytes)) return false
        } else if (other.receiptPhotoBytes != null) return false
        return true
    }

    override fun hashCode(): Int {
        var result = deliveryId.hashCode()
        result = 31 * result + category.hashCode()
        result = 31 * result + amount.hashCode()
        result = 31 * result + (receiptPhotoBytes?.contentHashCode() ?: 0)
        return result
    }
}

data class PodSubmission(
    val deliveryId: String,
    val recipientName: String,
    val cargoPhotoBytes: ByteArray,
    val signaturePngBytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as PodSubmission
        if (deliveryId != other.deliveryId) return false
        if (recipientName != other.recipientName) return false
        if (!cargoPhotoBytes.contentEquals(other.cargoPhotoBytes)) return false
        if (!signaturePngBytes.contentEquals(other.signaturePngBytes)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = deliveryId.hashCode()
        result = 31 * result + recipientName.hashCode()
        result = 31 * result + cargoPhotoBytes.contentHashCode()
        result = 31 * result + signaturePngBytes.contentHashCode()
        return result
    }
}

sealed class DriverError : Exception() {
    data class OutsideOperatingHours(val nextWindowStart: String? = null) : DriverError()
    data class ArrivalTooFar(val distanceMeters: Double?, val maxRadiusMeters: Double?) : DriverError()
    data class InvalidPodFile(val reason: String?) : DriverError()
    class DeliveryNotFound : DriverError()
    data class InvalidTransition(val currentStatus: String?) : DriverError()
    data class UnknownError(override val message: String) : DriverError()
}

// ─── Compliance Domain Models & Calculation ───────────────────────────────────

enum class ComplianceBadgeState {
    VALID,
    EXPIRING_SOON,
    EXPIRED
}

@Immutable
data class ComplianceStatus(
    val state: ComplianceBadgeState,
    val badgeLabel: String,
    val detailText: String,
    val isWarning: Boolean,
    val badgeBackgroundColor: Color,
    val badgeTextColor: Color
) {
    companion object {
        const val EXPIRING_SOON_THRESHOLD_DAYS: Long = 60L

        fun from(expiryDate: LocalDate?, today: LocalDate = LocalDate.now()): ComplianceStatus {
            if (expiryDate == null) {
                return ComplianceStatus(
                    state = ComplianceBadgeState.VALID,
                    badgeLabel = "VALID",
                    detailText = "No expiry",
                    isWarning = false,
                    badgeBackgroundColor = StatusGreenBg,
                    badgeTextColor = StatusGreen
                )
            }

            return when {
                expiryDate.isBefore(today) -> {
                    val daysAgo = ChronoUnit.DAYS.between(expiryDate, today)
                    val daysText = if (daysAgo == 1L) "1 day ago" else "$daysAgo days ago"
                    ComplianceStatus(
                        state = ComplianceBadgeState.EXPIRED,
                        badgeLabel = "EXPIRED",
                        detailText = "Expired $daysText",
                        isWarning = true,
                        badgeBackgroundColor = ExpiredRedBg,
                        badgeTextColor = ExpiredRed
                    )
                }
                expiryDate.isBefore(today.plusDays(EXPIRING_SOON_THRESHOLD_DAYS + 1)) -> {
                    val daysLeft = ChronoUnit.DAYS.between(today, expiryDate)
                    val daysText = if (daysLeft == 1L) "1 day left" else "$daysLeft days left"
                    val monthYear = formatMonthYear(expiryDate)
                    ComplianceStatus(
                        state = ComplianceBadgeState.EXPIRING_SOON,
                        badgeLabel = "EXPIRING SOON",
                        detailText = "Exp: $monthYear · $daysText",
                        isWarning = true,
                        badgeBackgroundColor = ExpiringOrangeBg,
                        badgeTextColor = ExpiringOrange
                    )
                }
                else -> {
                    val monthYear = formatMonthYear(expiryDate)
                    ComplianceStatus(
                        state = ComplianceBadgeState.VALID,
                        badgeLabel = "VALID",
                        detailText = "Exp: $monthYear",
                        isWarning = false,
                        badgeBackgroundColor = StatusGreenBg,
                        badgeTextColor = StatusGreen
                    )
                }
            }
        }

        private fun formatMonthYear(date: LocalDate): String {
            val month = date.month.name.lowercase().take(3).replaceFirstChar { it.uppercase() }
            return "$month ${date.year}"
        }
    }
}

@Immutable
data class ComplianceDocumentItem(
    val id: String,
    val title: String,
    val docType: String,
    val documentNumber: String?,
    val expiryDate: LocalDate?,
    val status: ComplianceStatus
)

@Immutable
data class DriverProfileDetails(
    val driverId: String,
    val fullName: String,
    val initials: String,
    val vehicleTypeNameAndPlate: String,
    val driverSinceText: String,
    val email: String,
    val contactNumber: String
)

// ─── Trips Domain Models ──────────────────────────────────────────────────────

enum class TripTabFilter {
    ALL,
    COMPLETED,
    ONGOING,
    CANCELLED
}

enum class TripStatus {
    COMPLETED,
    ONGOING,
    CANCELLED
}

@Immutable
data class TripCardStyle(
    val statusLabel: String,
    val chipBackgroundColor: Color,
    val chipTextColor: Color,
    val accentBarColor: Color,
    val isAllowanceStrikethrough: Boolean,
    val isMuted: Boolean,
    val isTappable: Boolean
) {
    companion object {
        fun from(status: TripStatus): TripCardStyle {
            return when (status) {
                TripStatus.COMPLETED -> TripCardStyle(
                    statusLabel = "COMPLETED",
                    chipBackgroundColor = StatusGreenBg,
                    chipTextColor = StatusGreen,
                    accentBarColor = StatusGreen,
                    isAllowanceStrikethrough = false,
                    isMuted = false,
                    isTappable = false
                )
                TripStatus.ONGOING -> TripCardStyle(
                    statusLabel = "IN TRANSIT",
                    chipBackgroundColor = OngoingBlueBg,
                    chipTextColor = OngoingBlue,
                    accentBarColor = OngoingAccentBar,
                    isAllowanceStrikethrough = false,
                    isMuted = false,
                    isTappable = true
                )
                TripStatus.CANCELLED -> TripCardStyle(
                    statusLabel = "CANCELLED",
                    chipBackgroundColor = ExpiredRedBg,
                    chipTextColor = ExpiredRed,
                    accentBarColor = CancelledAccentBar,
                    isAllowanceStrikethrough = true,
                    isMuted = true,
                    isTappable = false
                )
            }
        }
    }
}

@Immutable
data class DriverTripItem(
    val id: String,
    val shipmentId: String,
    val trackingNumber: String,
    val status: TripStatus,
    val rawStatus: String,
    val originCity: String,
    val destinationCity: String,
    val formattedDate: String,
    val formattedDuration: String,
    val allowanceAmount: Double,
    val formattedAllowance: String,
    val cardStyle: TripCardStyle
)

@Immutable
data class DriverTripStats(
    val completedTripsCount: Int,
    val totalAllowance: Double,
    val formattedTotalAllowance: String,
    val onTimeRatePercentage: Int? // null when no completed trips, rendered as "—"
)

@Immutable
data class TripPeriod(
    val year: Int,
    val month: Int, // 1-12
    val label: String, // e.g. "October 2024" or "All Time"
    val isAllTime: Boolean = false
)

fun generateInitials(fullName: String): String {
    val parts = fullName.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
    if (parts.isEmpty()) return "U"
    if (parts.size == 1) return parts[0].take(1).uppercase()
    return (parts.first().take(1) + parts.last().take(1)).uppercase()
}
