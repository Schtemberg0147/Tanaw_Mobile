package com.tanaw.app.feature.booking

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanaw.app.data.model.Vehicle
import com.tanaw.app.domain.model.BookingError
import com.tanaw.app.domain.model.BookingRequest
import com.tanaw.app.domain.repository.BookingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class BookingViewModel @Inject constructor(
    private val bookingsRepository: BookingsRepository
) : ViewModel() {

    var selectedVehicle by mutableStateOf<Vehicle?>(null)
    var distanceKm by mutableStateOf(0)
    var pickupName by mutableStateOf("")
    var destinationName by mutableStateOf("")

    var contactNumber by mutableStateOf("")
    var weightKg by mutableStateOf("")
    var handling by mutableStateOf(SpecialHandling.NONE)
    var notes by mutableStateOf("")
    var totalFee by mutableStateOf(0)

    var bookingReference by mutableStateOf("")

    var vehicleTypeId by mutableStateOf<String?>(null)
    var pickupLocationId by mutableStateOf<String?>(null)
    var pickupLat by mutableStateOf<Double?>(null)
    var pickupLng by mutableStateOf<Double?>(null)
    var dropoffLocationId by mutableStateOf<String?>(null)
    var dropoffLat by mutableStateOf<Double?>(null)
    var dropoffLng by mutableStateOf<Double?>(null)
    var scheduledDeparture by mutableStateOf<String?>(null)
    var cargoCategoryId by mutableStateOf<String?>(null)

    private var clientRequestId: String = UUID.randomUUID().toString()

    private val _uiState = MutableStateFlow<BookingUiState>(BookingUiState.Idle)
    val uiState: StateFlow<BookingUiState> = _uiState.asStateFlow()

    fun submit() {
        if (_uiState.value is BookingUiState.Submitting) return
        val vehicleId = vehicleTypeId ?: selectedVehicle?.id ?: return

        _uiState.value = BookingUiState.Submitting
        viewModelScope.launch {
            bookingsRepository.createShipment(
                BookingRequest(
                    clientRequestId = clientRequestId,
                    pickupLocationId = pickupLocationId,
                    pickupAddress = pickupName,
                    pickupLat = pickupLat,
                    pickupLng = pickupLng,
                    dropoffLocationId = dropoffLocationId,
                    dropoffAddress = destinationName,
                    dropoffLat = dropoffLat,
                    dropoffLng = dropoffLng,
                    vehicleTypeId = vehicleId,
                    cargoCategoryId = cargoCategoryId,
                    cargoDescription = notes.ifBlank { null },
                    weightKg = weightKg.toDoubleOrNull() ?: 100.0,
                    specialHandling = handling.label.takeIf { handling != SpecialHandling.NONE },
                    scheduledDeparture = scheduledDeparture
                )
            ).onSuccess { shipment ->
                bookingReference = shipment.trackingNumber
                totalFee = shipment.quotedAmount.toInt()
                _uiState.value = BookingUiState.Success(shipment.trackingNumber)
            }.onFailure { throwable ->
                val error = (throwable as? BookingError) ?: BookingError.UnknownError(throwable.message ?: "An unexpected error occurred.")
                _uiState.value = BookingUiState.Error(error)
            }
        }
    }

    fun consumeError() {
        _uiState.value = BookingUiState.Idle
    }

    fun reset() {
        clientRequestId = UUID.randomUUID().toString()
        _uiState.value = BookingUiState.Idle
        selectedVehicle = null
        distanceKm = 0
        pickupName = ""
        destinationName = ""
        contactNumber = ""
        weightKg = ""
        handling = SpecialHandling.NONE
        notes = ""
        totalFee = 0
        bookingReference = ""
        vehicleTypeId = null
        pickupLocationId = null
        pickupLat = null
        pickupLng = null
        dropoffLocationId = null
        dropoffLat = null
        dropoffLng = null
        scheduledDeparture = null
        cargoCategoryId = null
    }
}

sealed interface BookingUiState {
    data object Idle : BookingUiState
    data object Submitting : BookingUiState
    data class Success(val trackingNumber: String) : BookingUiState
    data class Error(val error: BookingError) : BookingUiState
}
