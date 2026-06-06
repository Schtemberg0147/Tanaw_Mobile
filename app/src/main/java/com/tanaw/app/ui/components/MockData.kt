package com.tanaw.app.ui.screens

import com.tanaw.app.R

// ─── Shared Data Models ───────────────────────────────────────────────────────

data class Vehicle(
    val id          : String,
    val name        : String,
    val type        : VehicleType,
    val maxWeightKg : Int,
    val ratePerKm   : Int,
    val iconRes     : Int
    // pricePhp and distanceKm are now computed dynamically
) {
    fun computePrice(distanceKm: Int): Int = ratePerKm * distanceKm
}

enum class VehicleType(val label: String) {
    OPEN("OPEN"),
    ENCLOSED("ENCLOSED"),
    REFRIGERATED("REFRIGERATED")
}

data class LocationItem(
    val id       : String,
    val name     : String,
    val subName  : String,
    val isRecent : Boolean  = false
)

// ─── Predefined mock data (replaces DB until backend is ready) ────────────────

object MockData {

    val vehicles = listOf(
        Vehicle("v1", "6-Wheeler Dropside",   VehicleType.OPEN,         4_000,  55, R.drawable.ic_vehicle_dropside),
        Vehicle("v2", "6-Wheeler Closed Van", VehicleType.ENCLOSED,     4_000,  65, R.drawable.ic_vehicle_closed_van),
        Vehicle("v3", "10-Wheeler Wing Van",  VehicleType.ENCLOSED,    15_000,  85, R.drawable.ic_vehicle_wing_van),
        Vehicle("v4", "10-Wheeler Flatbed",   VehicleType.OPEN,        18_000,  80, R.drawable.ic_vehicle_flatbed),
        Vehicle("v5", "Refrigerated Van",     VehicleType.REFRIGERATED, 3_000,  90, R.drawable.ic_vehicle_refrigerated),
    )

    val pickupLocations = listOf(
        LocationItem("l1", "Hub Alpha",      "Cabanatuan City"),
        LocationItem("l2", "Agri-Terminal",  "San Jose City"),
        LocationItem("l3", "Cold Storage B", "Guimba"),
    )

    val recentDestinations = listOf(
        LocationItem("d1", "Gapan City", "Nueva Ecija, PH", isRecent = true),
        LocationItem("d2", "Talavera",   "Nueva Ecija, PH", isRecent = true),
    )

    val suggestedDestinations = listOf(
        LocationItem("d3", "Hub Alpha",      "Cabanatuan City"),
        LocationItem("d4", "Agri-Terminal",  "San Jose City"),
        LocationItem("d5", "Cold Storage B", "Guimba"),
    )

    fun computeDistance(pickup: LocationItem, destination: LocationItem): Int {
        val key = pickup.id to destination.id
        return when (key) {
            "l1" to "d1" -> 25
            "l1" to "d2" -> 38
            "l1" to "d3" -> 52
            "l2" to "d1" -> 18
            "l2" to "d2" -> 30
            "l2" to "d3" -> 44
            "l3" to "d1" -> 60
            "l3" to "d2" -> 45
            "l3" to "d3" -> 20
            else         -> 38
        }
    }
}


