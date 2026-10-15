package com.tanaw.app.data.mapper

import androidx.compose.ui.graphics.Color
import com.tanaw.app.data.dto.DbShipmentSummaryDto
import com.tanaw.app.domain.model.BookingCardStyle
import com.tanaw.app.domain.model.BookingItemUi
import com.tanaw.app.domain.model.BookingStatusEnum
import com.tanaw.app.domain.model.BookingTabFilter
import java.text.DecimalFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

fun DbShipmentSummaryDto.toUiModel(): BookingItemUi {
    val statusEnum = when (status.lowercase()) {
        "delivered", "completed" -> BookingStatusEnum.DELIVERED
        "cancelled", "rejected" -> BookingStatusEnum.CANCELLED
        "in_transit", "en_route", "picked_up" -> BookingStatusEnum.IN_TRANSIT
        "assigned" -> BookingStatusEnum.ASSIGNED
        else -> BookingStatusEnum.BOOKED
    }

    val style = mapBookingCardStyle(statusEnum)

    val pickupCityName = pickupLocation?.city?.takeIf { it.isNotBlank() }
        ?: pickupLocation?.name?.takeIf { it.isNotBlank() }
        ?: pickupAddress?.split(",")?.firstOrNull()?.trim()
        ?: "Cabanatuan City"

    val dropoffCityName = dropoffLocation?.city?.takeIf { it.isNotBlank() }
        ?: dropoffLocation?.name?.takeIf { it.isNotBlank() }
        ?: dropoffAddress?.split(",")?.firstOrNull()?.trim()
        ?: "Gapan City"

    val vehicleName = requestedVehicleType?.name ?: "10-Wheeler Wing Van"
    val formattedDate = formatShortDate(scheduledDeparture ?: createdAt)
    val dateAndVehicleText = "$formattedDate · $vehicleName"

    val priceVal = finalAmount ?: quotedAmount ?: 3530.0
    val formattedPriceText = "₱" + DecimalFormat("#,##0").format(priceVal)

    val hasPodAvailable = deliveryDocuments.isNotEmpty() || statusEnum == BookingStatusEnum.DELIVERED

    return BookingItemUi(
        id = trackingNumber.ifBlank { "SHP-NE-8063" },
        shipmentId = id,
        pickupCity = pickupCityName,
        destinationCity = dropoffCityName,
        formattedDateAndVehicle = dateAndVehicleText,
        formattedPrice = formattedPriceText,
        status = statusEnum,
        cardStyle = style,
        hasPod = hasPodAvailable,
        pickupLocationId = pickupLocationId,
        dropoffLocationId = dropoffLocationId,
        vehicleTypeId = requestedVehicleTypeId,
        pickupName = pickupLocation?.name ?: pickupCityName,
        destinationName = dropoffLocation?.name ?: dropoffCityName
    )
}

fun mapBookingCardStyle(status: BookingStatusEnum): BookingCardStyle {
    return when (status) {
        BookingStatusEnum.BOOKED, BookingStatusEnum.ASSIGNED, BookingStatusEnum.IN_TRANSIT -> BookingCardStyle(
            statusLabel = if (status == BookingStatusEnum.IN_TRANSIT) "IN TRANSIT" else status.name,
            chipBackgroundColor = Color(0xFFE0F2FE),
            chipTextColor = Color(0xFF0369A1),
            pickupDotColor = Color(0xFF0284C7),
            dropoffDotColor = Color(0xFFDC2626),
            timelineLineColor = Color(0xFF0284C7),
            isMuted = false,
            isPriceStrikethrough = false
        )
        BookingStatusEnum.DELIVERED -> BookingCardStyle(
            statusLabel = "DELIVERED",
            chipBackgroundColor = Color(0xFFDCFCE7),
            chipTextColor = Color(0xFF15803D),
            pickupDotColor = Color(0xFF0284C7),
            dropoffDotColor = Color(0xFFDC2626),
            timelineLineColor = Color(0xFF0284C7),
            isMuted = false,
            isPriceStrikethrough = false
        )
        BookingStatusEnum.CANCELLED -> BookingCardStyle(
            statusLabel = "CANCELLED",
            chipBackgroundColor = Color(0xFFFEE2E2),
            chipTextColor = Color(0xFFB91C1C),
            pickupDotColor = Color(0xFF94A3B8),
            dropoffDotColor = Color(0xFF94A3B8),
            timelineLineColor = Color(0xFFCBD5E1),
            isMuted = true,
            isPriceStrikethrough = true
        )
    }
}

fun BookingStatusEnum.belongsToTab(tab: BookingTabFilter): Boolean {
    return when (tab) {
        BookingTabFilter.ALL -> true
        BookingTabFilter.ACTIVE -> this == BookingStatusEnum.BOOKED || this == BookingStatusEnum.ASSIGNED || this == BookingStatusEnum.IN_TRANSIT
        BookingTabFilter.COMPLETED -> this == BookingStatusEnum.DELIVERED
        BookingTabFilter.CANCELLED -> this == BookingStatusEnum.CANCELLED
    }
}

fun formatShortDate(isoTimestamp: String?): String {
    if (isoTimestamp.isNullOrBlank()) return "Oct 24"
    return try {
        val zonedDateTime = try {
            ZonedDateTime.parse(isoTimestamp)
        } catch (_: Exception) {
            val instant = Instant.parse(isoTimestamp)
            ZonedDateTime.ofInstant(instant, ZoneId.of("Asia/Manila"))
        }
        val manilaZone = zonedDateTime.withZoneSameInstant(ZoneId.of("Asia/Manila"))
        val currentYear = ZonedDateTime.now(ZoneId.of("Asia/Manila")).year
        val pattern = if (manilaZone.year == currentYear) "MMM dd" else "MMM dd, yyyy"
        manilaZone.format(DateTimeFormatter.ofPattern(pattern, Locale.US))
    } catch (_: Exception) {
        "Oct 24"
    }
}
