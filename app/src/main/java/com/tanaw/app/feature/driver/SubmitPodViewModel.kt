package com.tanaw.app.feature.driver

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanaw.app.domain.model.DriverError
import com.tanaw.app.domain.repository.DriverRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SubmitPodViewModel @Inject constructor(
    private val driverRepository: DriverRepository,
    private val savedStateHandle: SavedStateHandle,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val deliveryId: String = savedStateHandle.get<String>("deliveryId") ?: ""

    private val _uiState = MutableStateFlow(SubmitPodUiState(deliveryId = deliveryId))
    val uiState: StateFlow<SubmitPodUiState> = _uiState.asStateFlow()

    private val _sideEffectChannel = Channel<SubmitPodSideEffect>(Channel.BUFFERED)
    val sideEffect = _sideEffectChannel.receiveAsFlow()

    init {
        loadTripDetails()
    }

    private fun loadTripDetails() {
        viewModelScope.launch(ioDispatcher) {
            val driverId = driverRepository.getDriverIdForCurrentUser() ?: ""
            driverRepository.observeActiveAssignment(driverId).collect { result ->
                result.getOrNull()?.let { trip ->
                    if (trip.deliveryId == deliveryId || deliveryId.isEmpty()) {
                        _uiState.update { it.copy(deliveryId = trip.deliveryId, trackingNumber = trip.trackingNumber) }
                    }
                }
            }
        }
    }

    fun onEvent(event: SubmitPodUiEvent) {
        when (event) {
            is SubmitPodUiEvent.RecipientNameChanged -> {
                _uiState.update { it.copy(recipientName = event.name, errorMessage = null) }
            }
            is SubmitPodUiEvent.CargoPhotoSelected -> {
                val bytes = event.bytes
                val hasPhoto = bytes != null && bytes.isNotEmpty()
                _uiState.update { it.copy(cargoPhotoBytes = bytes, hasCargoPhoto = hasPhoto, errorMessage = null) }
            }
            is SubmitPodUiEvent.SignatureCaptured -> {
                val bytes = event.bytes
                val hasSig = bytes != null && bytes.isNotEmpty()
                _uiState.update { it.copy(signatureBytes = bytes, hasSignature = hasSig, errorMessage = null) }
            }
            SubmitPodUiEvent.SubmitPod -> submitPod()
            SubmitPodUiEvent.DismissError -> {
                _uiState.update { it.copy(errorMessage = null) }
            }
        }
    }

    private fun submitPod() {
        val currentState = _uiState.value
        if (currentState.isSubmitting) return

        if (currentState.recipientName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter recipient full name.") }
            return
        }
        val photoBytes = currentState.cargoPhotoBytes
        if (photoBytes == null || photoBytes.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Please capture or select a delivery cargo photo.") }
            return
        }
        val signatureBytes = currentState.signatureBytes
        if (signatureBytes == null || signatureBytes.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Recipient signature is required.") }
            return
        }

        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }

            val result = driverRepository.driverSubmitPod(
                deliveryId = currentState.deliveryId,
                recipientName = currentState.recipientName,
                photoBytes = photoBytes,
                signatureBytes = signatureBytes
            )

            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isSubmitting = false, isSuccess = true) }
                    _sideEffectChannel.send(SubmitPodSideEffect.NavigateToDeliveryComplete(currentState.deliveryId))
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = formatErrorMessage(error)
                        )
                    }
                }
            )
        }
    }

    private fun formatErrorMessage(error: Throwable): String {
        return when (error) {
            is DriverError.InvalidPodFile -> "Invalid POD submission: ${error.reason ?: "Please re-take signature or photo."}"
            is DriverError.DeliveryNotFound -> "Delivery ID not found in database."
            is DriverError.InvalidTransition -> "Cannot submit POD for delivery in status ${error.currentStatus}."
            else -> error.message ?: "POD submission failed. Please try again."
        }
    }
}
