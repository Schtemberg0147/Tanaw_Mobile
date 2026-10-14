package com.tanaw.app.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DbDriverDto(
    val id: String = "",
    @SerialName("user_id") val userId: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("contact_number") val contactNumber: String? = null,
    @SerialName("date_hired") val dateHired: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val status: String? = "available",
    val trucks: DbTruckDto? = null
)

@Serializable
data class DbTruckDto(
    val id: String = "",
    @SerialName("fleet_code") val fleetCode: String? = null,
    @SerialName("plate_number") val plateNumber: String? = null,
    @SerialName("model") val model: String? = null,
    @SerialName("vehicle_types") val vehicleType: DbVehicleTypeDto? = null
)

@Serializable
data class DbVehicleTypeDto(
    val id: String = "",
    val name: String? = null
)

@Serializable
data class DbCargoCategoryDto(
    val id: String = "",
    val name: String? = null,
    @SerialName("requires_special_handling") val requiresSpecialHandling: Boolean = false
)

@Serializable
data class DbLocationDto(
    val id: String = "",
    val name: String? = null,
    val address: String? = null,
    val city: String? = null
)

@Serializable
data class DbShipmentDto(
    val id: String = "",
    @SerialName("tracking_number") val trackingNumber: String = "",
    val status: String? = null,
    @SerialName("weight_kg") val weightKg: Double? = null,
    @SerialName("special_handling") val specialHandling: String? = null,
    @SerialName("cargo_categories") val cargoCategory: DbCargoCategoryDto? = null,
    @SerialName("pickup_location") val pickupLocation: DbLocationDto? = null,
    @SerialName("pickup_address") val pickupAddress: String? = null,
    @SerialName("dropoff_location") val dropoffLocation: DbLocationDto? = null,
    @SerialName("dropoff_address") val dropoffAddress: String? = null,
    @SerialName("distance_km") val distanceKm: Double? = null,
    @SerialName("scheduled_departure") val scheduledDeparture: String? = null,
    @SerialName("estimated_arrival") val estimatedArrival: String? = null,
    @SerialName("actual_delivery_at") val actualDeliveryAt: String? = null,
    @SerialName("driver_notes") val driverNotes: String? = null,
    @SerialName("has_toll") val hasToll: Boolean? = null,
    @SerialName("estimated_duration_minutes") val estimatedDurationMinutes: Int? = null
)

@Serializable
data class DbDeliveryDto(
    val id: String = "",
    @SerialName("shipment_id") val shipmentId: String = "",
    @SerialName("truck_id") val truckId: String? = null,
    @SerialName("driver_id") val driverId: String = "",
    val status: String = "assigned",
    val notes: String? = null,
    @SerialName("allowance_amount") val allowanceAmount: Double? = null,
    @SerialName("departed_at") val departedAt: String? = null,
    @SerialName("arrived_at") val arrivedAt: String? = null,
    @SerialName("assigned_at") val assignedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val shipments: DbShipmentDto? = null,
    val trucks: DbTruckDto? = null
)

@Serializable
data class DbComplianceDocumentDto(
    val id: String = "",
    @SerialName("driver_id") val driverId: String? = null,
    @SerialName("doc_type") val docType: String = "",
    @SerialName("document_number") val documentNumber: String? = null,
    @SerialName("issued_date") val issuedDate: String? = null,
    @SerialName("expiry_date") val expiryDate: String? = null,
    @SerialName("review_status") val reviewStatus: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class DbDriverTripStatsDto(
    @SerialName("completed_trips_count") val completedTripsCount: Int = 0,
    @SerialName("total_allowance") val totalAllowance: Double = 0.0,
    @SerialName("on_time_rate") val onTimeRate: Double? = null
)
