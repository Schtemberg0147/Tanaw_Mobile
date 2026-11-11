package com.tanaw.app.feature.tracking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanaw.app.domain.repository.DriverRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class TrackDetailViewModel @Inject constructor(
    private val driverRepository: DriverRepository,
    private val savedStateHandle: SavedStateHandle,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val bookingId: String = savedStateHandle.get<String>("bookingId") ?: ""

    private val _uiState = MutableStateFlow<TrackDetailUiState>(TrackDetailUiState.Loading)
    val uiState: StateFlow<TrackDetailUiState> = _uiState.asStateFlow()

    init {
        loadTrackingData()
    }

    fun loadTrackingData() {
        viewModelScope.launch(ioDispatcher) {
            _uiState.value = TrackDetailUiState.Loading

            // Initial Content State
            val contentState = TrackDetailUiState.Content(
                bookingId = bookingId,
                trackingNumber = if (bookingId.isNotBlank()) "SHP-${bookingId.take(6).uppercase()}" else "SHP-NE-8063",
                driverName = "Ramon Cruz",
                vehicleDetails = "10-Wheeler Wing Van · ABC-1234",
                currentStatusLabel = "IN TRANSIT",
                currentLocationName = "Maharlika Highway, Cabanatuan",
                formattedFee = "₱3,530",
                etaText = "45 MIN",
                driverLat = 15.4828,
                driverLng = 120.9692,
                pickupLat = 15.4828,
                pickupLng = 120.9692,
                dropoffLat = 15.3180,
                dropoffLng = 120.9472,
                currentStageIndex = 2
            )
            _uiState.value = contentState

            // Real-time live location subscription
            driverRepository.observeDeliveryLocations(bookingId).collect { loc ->
                _uiState.update { state ->
                    if (state is TrackDetailUiState.Content) {
                        state.copy(
                            driverLat = loc.first,
                            driverLng = loc.second,
                            currentLocationName = "Live: Lat ${String.format(Locale.US, "%.4f", loc.first)}, Lng ${String.format(Locale.US, "%.4f", loc.second)}"
                        )
                    } else state
                }
            }
        }
    }
}
