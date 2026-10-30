package com.tanaw.app.domain.model

import androidx.compose.runtime.Immutable

enum class IncidentCategory(val dbValue: String, val label: String) {
    TRAFFIC("traffic", "Traffic Congestion"),
    WEATHER("weather", "Severe Weather"),
    LATE_LOADING("late_loading", "Late Loading"),
    BREAKDOWN("breakdown", "Vehicle Breakdown"),
    ACCIDENT("accident", "Road Accident"),
    ROAD_CLOSURE("road_closure", "Road Closure"),
    OTHER("other", "Other Delay");

    companion object {
        fun fromDbValue(dbValue: String?): IncidentCategory {
            if (dbValue == null) return OTHER
            return entries.find { it.dbValue.equals(dbValue, ignoreCase = true) } ?: OTHER
        }
    }
}

@Immutable
data class DeliveryIncidentRequest(
    val deliveryId: String,
    val shipmentId: String,
    val category: IncidentCategory,
    val description: String?,
    val delayMinutes: Int?,
    val latitude: Double? = null,
    val longitude: Double? = null
)

@Immutable
data class DeliveryIncidentItem(
    val id: String,
    val deliveryId: String,
    val shipmentId: String,
    val category: IncidentCategory,
    val categoryLabel: String,
    val description: String,
    val formattedDelay: String,
    val formattedTimestamp: String,
    val isResolved: Boolean
)
