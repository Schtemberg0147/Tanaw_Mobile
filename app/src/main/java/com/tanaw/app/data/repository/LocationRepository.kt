package com.tanaw.app.data.repository

import com.tanaw.app.data.model.PickerLocation

interface LocationRepository {
    suspend fun nearby(
        lat: Double,
        lng: Double,
        radiusM: Int,
        vehicleTypeId: String? = null
    ): List<PickerLocation>
}
