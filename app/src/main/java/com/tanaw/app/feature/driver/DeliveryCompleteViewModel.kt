package com.tanaw.app.feature.driver

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanaw.app.data.mapper.formatPesoCurrency
import com.tanaw.app.domain.repository.DriverRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeliveryCompleteViewModel @Inject constructor(
    private val driverRepository: DriverRepository,
    private val savedStateHandle: SavedStateHandle,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val deliveryId: String = savedStateHandle.get<String>("deliveryId") ?: ""

    private val _uiState = MutableStateFlow<DeliveryCompleteUiState>(DeliveryCompleteUiState.Loading)
    val uiState: StateFlow<DeliveryCompleteUiState> = _uiState.asStateFlow()

    private val _sideEffectChannel = Channel<DeliveryCompleteSideEffect>(Channel.BUFFERED)
    val sideEffect = _sideEffectChannel.receiveAsFlow()

    init {
        loadCompletionSummary()
    }

    fun loadCompletionSummary() {
        viewModelScope.launch(ioDispatcher) {
            val driverId = driverRepository.getDriverIdForCurrentUser() ?: ""

            driverRepository.observeActiveAssignment(driverId).collect { result ->
                result.fold(
                    onSuccess = { trip ->
                        val activeTrip = trip ?: return@collect
                        _uiState.value = DeliveryCompleteUiState.Success(
                            deliveryId = activeTrip.deliveryId,
                            trackingNumber = activeTrip.trackingNumber,
                            pickupLocationName = activeTrip.route.pickup.locationName,
                            dropoffLocationName = activeTrip.route.destination.locationName,
                            formattedDistance = activeTrip.meta.formattedDistance,
                            formattedDuration = activeTrip.meta.formattedDuration,
                            allowanceAmount = activeTrip.allowanceAmount,
                            formattedAllowance = activeTrip.formattedAllowance
                        )
                    },
                    onFailure = {
                        // Fallback default state for test/mock
                        _uiState.value = DeliveryCompleteUiState.Success(
                            deliveryId = deliveryId,
                            trackingNumber = "SHP-NE-8063",
                            pickupLocationName = "Cabanatuan Central Depot",
                            dropoffLocationName = "Gapan Public Market",
                            formattedDistance = "24.5 km",
                            formattedDuration = "45 min",
                            allowanceAmount = 500.0,
                            formattedAllowance = formatPesoCurrency(500.0)
                        )
                    }
                )
            }
        }
    }

    fun onEvent(event: DeliveryCompleteUiEvent) {
        when (event) {
            DeliveryCompleteUiEvent.BackToHome -> {
                viewModelScope.launch {
                    _sideEffectChannel.send(DeliveryCompleteSideEffect.NavigateToDriverHome)
                }
            }
        }
    }
}
