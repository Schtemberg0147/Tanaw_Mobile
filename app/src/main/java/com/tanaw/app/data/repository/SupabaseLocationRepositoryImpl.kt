package com.tanaw.app.data.repository

import com.tanaw.app.data.model.PickerLocation
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseLocationRepositoryImpl @Inject constructor(
    private val client: SupabaseClient
) : LocationRepository {

    override suspend fun nearby(
        lat: Double,
        lng: Double,
        radiusM: Int,
        vehicleTypeId: String?
    ): List<PickerLocation> {
        val params = buildJsonObject {
            put("p_lat", lat)
            put("p_lng", lng)
            put("p_radius_m", radiusM)
            if (vehicleTypeId != null) {
                put("p_vehicle_type_id", vehicleTypeId)
            }
        }
        val result = client.postgrest.rpc("nearby_locations", params)
        return result.decodeList<PickerLocation>()
    }
}
