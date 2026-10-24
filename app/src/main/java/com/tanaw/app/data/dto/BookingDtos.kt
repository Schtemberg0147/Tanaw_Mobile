package com.tanaw.app.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DbCreatedShipmentDto(
    val id: String = "",
    @SerialName("tracking_number") val trackingNumber: String = "",
    val status: String = "pending",
    @SerialName("quoted_amount") val quotedAmount: Double = 0.0
)

@Serializable
data class DbDocumentSummaryDto(
    val id: String = "",
    val kind: String? = null
)

@Serializable
data class DbLocationSummaryDto(
    val id: String = "",
    val name: String? = null,
    val city: String? = null,
    val address: String? = null
)

@Serializable
data class DbVehicleTypeSummaryDto(
    val id: String = "",
    val name: String? = null
)

@Serializable
data class DbShipmentSummaryDto(
    val id: String = "",
    @SerialName("tracking_number") val trackingNumber: String = "",
    @SerialName("customer_id") val customerId: String = "",
    val status: String = "booked",
    @SerialName("final_amount") val finalAmount: Double? = null,
    @SerialName("quoted_amount") val quotedAmount: Double? = null,
    @SerialName("scheduled_departure") val scheduledDeparture: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("pickup_address") val pickupAddress: String? = null,
    @SerialName("dropoff_address") val dropoffAddress: String? = null,
    @SerialName("pickup_location_id") val pickupLocationId: String? = null,
    @SerialName("dropoff_location_id") val dropoffLocationId: String? = null,
    @SerialName("requested_vehicle_type_id") val requestedVehicleTypeId: String? = null,
    @SerialName("pickup_location") val pickupLocation: DbLocationSummaryDto? = null,
    @SerialName("dropoff_location") val dropoffLocation: DbLocationSummaryDto? = null,
    @SerialName("requested_vehicle_type") val requestedVehicleType: DbVehicleTypeSummaryDto? = null,
    @SerialName("delivery_documents") val deliveryDocuments: List<DbDocumentSummaryDto> = emptyList()
)
