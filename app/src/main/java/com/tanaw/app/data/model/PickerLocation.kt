package com.tanaw.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PickerLocation(
    val id: String,
    val name: String,
    val sublabel: String? = null,
    val address: String? = null,
    val city: String? = null,
    val province: String? = null,
    val latitude: Double,
    val longitude: Double,
    val kind: String? = null,
    @SerialName("distance_m") val distanceM: Double? = null,
) {
    fun toLocationItem(): LocationItem {
        val sub = listOfNotNull(
            sublabel,
            address,
            city,
            province,
        ).filter { it.isNotBlank() }.distinct().joinToString(" • ")
        return LocationItem(
            id = id,
            name = name,
            subName = sub.ifBlank { name },
            latitude = latitude,
            longitude = longitude,
        )
    }
}
