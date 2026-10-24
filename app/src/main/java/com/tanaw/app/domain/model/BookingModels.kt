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

sealed class BookingError : Exception() {
    data class OutsideOperatingHours(val nextWindowStart: String?) : BookingError()
    data class CustomerBlocked(val blockedUntil: String?) : BookingError()
    data class CustomerSuspended(val suspendedAt: String?) : BookingError()
    class NoPricingRate : BookingError()
    class OutsideServiceArea : BookingError()
    class MissingCoordinates : BookingError()
    class NoCustomerProfile : BookingError()
    class AccountInactive : BookingError()
    class InvalidVehicleType : BookingError()
    class InvalidWeight : BookingError()
    class InvalidSchedule : BookingError()
    data class UnknownError(override val message: String) : BookingError()
}

data class CreatedShipment(
    val id: String,
    val trackingNumber: String,
    val status: String,
    val quotedAmount: Double
)

data class BookingRequest(
    val clientRequestId: String,
    val pickupLocationId: String? = null,
    val pickupAddress: String,
    val pickupLat: Double?,
    val pickupLng: Double?,
    val dropoffLocationId: String? = null,
    val dropoffAddress: String,
    val dropoffLat: Double?,
    val dropoffLng: Double?,
    val vehicleTypeId: String,
    val cargoCategoryId: String? = null,
    val cargoDescription: String? = null,
    val weightKg: Double,
    val specialHandling: String? = null,
    val scheduledDeparture: String? = null
)
