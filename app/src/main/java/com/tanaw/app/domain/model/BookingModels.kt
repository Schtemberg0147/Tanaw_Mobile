package com.tanaw.app.domain.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

enum class BookingTabFilter {
    ALL,
    ACTIVE,
    COMPLETED,
    CANCELLED
}

enum class BookingStatusEnum {
    BOOKED,
    ASSIGNED,
    IN_TRANSIT,
    DELIVERED,
    CANCELLED
}

@Immutable
data class BookingCardStyle(
    val statusLabel: String,
    val chipBackgroundColor: Color,
    val chipTextColor: Color,
    val pickupDotColor: Color,
    val dropoffDotColor: Color,
    val timelineLineColor: Color,
    val isMuted: Boolean,
    val isPriceStrikethrough: Boolean
)

@Immutable
data class BookingItemUi(
    val id: String,
    val shipmentId: String,
    val pickupCity: String,
    val destinationCity: String,
    val formattedDateAndVehicle: String,
    val formattedPrice: String,
    val status: BookingStatusEnum,
    val cardStyle: BookingCardStyle,
    val hasPod: Boolean,
    val pickupLocationId: String? = null,
    val dropoffLocationId: String? = null,
    val vehicleTypeId: String? = null,
    val pickupName: String = "",
    val destinationName: String = ""
)
