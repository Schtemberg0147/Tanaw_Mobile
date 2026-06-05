package com.tanaw.app.ui.screens

data class HistoryBooking(
    val id: String,
    val pickup: LocationItem,
    val destination: LocationItem,
    val dateAndVehicle: String,
    val status: BookingStatus,
    val vehicle: Vehicle,
    val pricePhp: String,
    val podAvailable: Boolean = false,
    val cancelReason: String? = null,
    val currentLocationText: String = ""
)
