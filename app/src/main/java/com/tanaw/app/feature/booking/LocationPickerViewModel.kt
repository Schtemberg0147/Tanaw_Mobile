package com.tanaw.app.feature.booking

import android.content.Context
import android.location.Geocoder
import android.location.Location
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanaw.app.data.model.PickerLocation
import com.tanaw.app.data.repository.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class PickerUiState(
    val myLocation: Pair<Double, Double>? = null,
    val myLocationName: String? = null,
    val locations: List<PickerLocation> = emptyList(),
    val query: String = "",
    val selected: PickerLocation? = null,
    val loading: Boolean = true,
    val error: String? = null,
) {
    val searchResults: List<PickerLocation>
        get() = if (query.isBlank()) emptyList() else locations.filter {
            it.name.contains(query, ignoreCase = true) ||
                (it.sublabel?.contains(query, ignoreCase = true) == true) ||
                (it.city?.contains(query, ignoreCase = true) == true) ||
                (it.address?.contains(query, ignoreCase = true) == true)
        }.take(8)
}

@HiltViewModel
class LocationPickerViewModel @Inject constructor(
    private val repo: LocationRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    var vehicleTypeId: String? = savedStateHandle.get<String>("vehicleTypeId")
        private set

    private val _state = MutableStateFlow(PickerUiState())
    val state: StateFlow<PickerUiState> = _state.asStateFlow()

    fun setVehicleTypeId(typeId: String?) {
        vehicleTypeId = typeId
    }

    fun onMyLocation(context: Context, lat: Double, lng: Double) {
        _state.update { it.copy(myLocation = lat to lng) }
        viewModelScope.launch(Dispatchers.IO) {
            val shortName = reverseGeocodeShort(context, lat, lng)
            _state.update { it.copy(myLocationName = shortName) }
        }
        load(lat, lng, radiusM = 50_000)
    }

    /** Permission denied or no GPS fix: center on Manila with a wide radius. */
    fun onNoLocation() = load(14.5995, 120.9842, radiusM = 500_000)

    private fun load(lat: Double, lng: Double, radiusM: Int) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { repo.nearby(lat, lng, radiusM, vehicleTypeId) }
                .onSuccess { list -> _state.update { it.copy(locations = list, loading = false) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message ?: "Failed to load locations") } }
        }
    }

    fun onQuery(q: String) = _state.update { it.copy(query = q) }

    fun select(location: PickerLocation) =
        _state.update { it.copy(selected = location, query = "") }

    fun selectById(id: String) {
        _state.value.locations.firstOrNull { it.id == id }?.let(::select)
    }

    fun selectCurrentLocation(context: Context) {
        val myLoc = _state.value.myLocation
        if (myLoc != null) {
            viewModelScope.launch(Dispatchers.IO) {
                val loc = reverseGeocode(context, myLoc.first, myLoc.second)
                val currentPin = loc.copy(
                    id = "current_gps",
                    name = "Current Location (${loc.name})",
                )
                _state.update { it.copy(selected = currentPin, query = "") }
            }
        }
    }

    fun onCameraIdle(context: Context, lat: Double, lng: Double) {
        val currentSelected = _state.value.selected
        if (currentSelected != null) {
            val dist = FloatArray(1)
            Location.distanceBetween(
                currentSelected.latitude, currentSelected.longitude,
                lat, lng,
                dist
            )
            if (dist[0] < 30f) return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val resolved = reverseGeocode(context, lat, lng)
            _state.update { it.copy(selected = resolved) }
        }
    }

    private fun reverseGeocodeShort(context: Context, lat: Double, lng: Double): String {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                val barangay = addr.subLocality ?: addr.featureName
                val municipality = addr.locality ?: addr.subAdminArea ?: addr.adminArea ?: "Nueva Ecija"
                listOfNotNull(barangay, municipality).filter { it.isNotBlank() }.distinct().joinToString(", ")
            } else "Current GPS Location"
        } catch (_: Exception) {
            "Current GPS Location"
        }
    }

    private fun reverseGeocode(context: Context, lat: Double, lng: Double): PickerLocation {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                val feature = addr.featureName
                val street = listOfNotNull(addr.subThoroughfare, addr.thoroughfare).joinToString(" ").ifBlank { null }
                val barangay = addr.subLocality
                val municipality = addr.locality ?: addr.subAdminArea
                val province = addr.adminArea ?: "Nueva Ecija"

                val landmarkOrStreet = when {
                    !feature.isNullOrBlank() && !feature.matches(Regex("^\\d+$")) && feature != street -> feature
                    !street.isNullOrBlank() -> street
                    !barangay.isNullOrBlank() -> "Brgy. $barangay"
                    else -> "Pinned Location"
                }

                val titleName = landmarkOrStreet
                val subLabel = listOfNotNull(barangay?.let { "Brgy. $it" }, municipality, province)
                    .filter { it.isNotBlank() && it != landmarkOrStreet }.distinct().joinToString(", ")

                PickerLocation(
                    id = "pin_${lat}_${lng}",
                    name = titleName,
                    sublabel = subLabel.ifBlank { "Luzon, PH" },
                    address = addr.getAddressLine(0),
                    city = municipality,
                    province = province,
                    latitude = lat,
                    longitude = lng
                )
            } else {
                fallbackPin(lat, lng)
            }
        } catch (_: Exception) {
            fallbackPin(lat, lng)
        }
    }

    private fun fallbackPin(lat: Double, lng: Double): PickerLocation {
        val nearest = _state.value.locations.minByOrNull { loc ->
            val res = FloatArray(1)
            Location.distanceBetween(loc.latitude, loc.longitude, lat, lng, res)
            res[0]
        }
        if (nearest != null) {
            val res = FloatArray(1)
            Location.distanceBetween(nearest.latitude, nearest.longitude, lat, lng, res)
            if (res[0] <= 100f) return nearest
        }
        return PickerLocation(
            id = "custom_${lat}_${lng}",
            name = "Pinned Location",
            sublabel = "Custom Map Location",
            address = "Lat: %.4f, Lng: %.4f".format(lat, lng),
            latitude = lat,
            longitude = lng
        )
    }
}
