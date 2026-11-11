package com.tanaw.app.feature.driver

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanaw.app.domain.model.ActiveTripStage
import com.tanaw.app.domain.model.DriverError
import com.tanaw.app.domain.model.IncidentCategory
import com.tanaw.app.domain.model.TripAssignment
import com.tanaw.app.domain.repository.DriverRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class DriverNavigationViewModel @Inject constructor(
    private val driverRepository: DriverRepository,
    private val savedStateHandle: SavedStateHandle,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val deliveryId: String = savedStateHandle.get<String>("deliveryId") ?: ""

    private val _uiState = MutableStateFlow<DriverNavigationUiState>(DriverNavigationUiState.Loading)
    val uiState: StateFlow<DriverNavigationUiState> = _uiState.asStateFlow()

    private val _sideEffectChannel = Channel<DriverNavigationSideEffect>(Channel.BUFFERED)
    val sideEffect = _sideEffectChannel.receiveAsFlow()

    private var countdownJob: Job? = null

    // Driver location fallback defaults (e.g., Cabanatuan City, Nueva Ecija)
    private var currentLat: Double = savedStateHandle.get<Double>("lat") ?: 15.4828
    private var currentLng: Double = savedStateHandle.get<Double>("lng") ?: 120.9692

    init {
        loadActiveTrip()
    }

    fun loadActiveTrip() {
        viewModelScope.launch(ioDispatcher) {
            _uiState.value = DriverNavigationUiState.Loading
            val driverId = driverRepository.getDriverIdForCurrentUser() ?: ""

            driverRepository.observeActiveAssignment(driverId).collectLatest { result ->
                result.fold(
                    onSuccess = { assignment ->
                        if (assignment != null && (deliveryId.isEmpty() || assignment.deliveryId == deliveryId)) {
                            val stage = savedStateHandle.get<String>("stage_${assignment.deliveryId}")?.let { stageName ->
                                runCatching { ActiveTripStage.valueOf(stageName) }.getOrNull()
                            } ?: ActiveTripStage.ASSIGNED

                            _uiState.update {
                                DriverNavigationUiState.Success(
                                    trip = assignment,
                                    currentStage = stage,
                                    driverLat = currentLat,
                                    driverLng = currentLng
                                )
                            }

                            if (stage == ActiveTripStage.PICKUP_ARRIVED) {
                                startPickupCountdown()
                            }
                        } else if (deliveryId.isNotEmpty()) {
                            // Fetch profile/assignment fallback
                            _uiState.update {
                                DriverNavigationUiState.Error("Trip not found or no active trip assignment.")
                            }
                        } else {
                            _uiState.update {
                                DriverNavigationUiState.Error("No active trip assignment found.")
                            }
                        }
                    },
                    onFailure = { throwable ->
                        _uiState.update {
                            DriverNavigationUiState.Error(throwable.message ?: "Failed to load trip navigation.")
                        }
                    }
                )
            }
        }
    }

    fun onEvent(event: DriverNavigationUiEvent) {
        when (event) {
            DriverNavigationUiEvent.ArriveAtPickup -> handleArriveAtPickup()
            DriverNavigationUiEvent.ConfirmCustomerShowed -> handleConfirmCustomerShowed()
            DriverNavigationUiEvent.ConfirmCargoLoaded -> handleConfirmCargoLoaded()
            DriverNavigationUiEvent.MarkAsDelivered -> handleMarkAsDelivered()
            DriverNavigationUiEvent.ViewCompletionSummary -> handleViewCompletionSummary()
            is DriverNavigationUiEvent.UpdateDriverLocation -> {
                currentLat = event.lat
                currentLng = event.lng
                savedStateHandle["lat"] = event.lat
                savedStateHandle["lng"] = event.lng
                updateSuccessState { copy(driverLat = event.lat, driverLng = event.lng) }
            }
            DriverNavigationUiEvent.OpenExpenseSheet -> {
                updateSuccessState { copy(isExpenseSheetVisible = true, expenseErrorMessage = null, expenseSuccessMessage = null) }
            }
            DriverNavigationUiEvent.CloseExpenseSheet -> {
                updateSuccessState { copy(isExpenseSheetVisible = false) }
            }
            is DriverNavigationUiEvent.SubmitExpense -> handleSubmitExpense(event.category, event.amount, event.receiptBytes)
            DriverNavigationUiEvent.OpenIncidentSheet -> {
                updateSuccessState { copy(isIncidentSheetVisible = true, incidentErrorMessage = null) }
            }
            DriverNavigationUiEvent.CloseIncidentSheet -> {
                updateSuccessState { copy(isIncidentSheetVisible = false) }
            }
            is DriverNavigationUiEvent.SubmitIncident -> handleSubmitIncident(event.category, event.description, event.delayMinutes)
            DriverNavigationUiEvent.DismissError -> {
                updateSuccessState { copy(errorMessage = null) }
            }
        }
    }

    private fun handleArriveAtPickup() {
        val currentState = _uiState.value as? DriverNavigationUiState.Success ?: return
        if (currentState.isSubmitting) return

        viewModelScope.launch(ioDispatcher) {
            updateSuccessState { copy(isSubmitting = true, errorMessage = null) }

            val activeDeliveryId = currentState.trip.deliveryId
            val res = driverRepository.driverArriveAtPickup(activeDeliveryId, currentLat, currentLng)

            res.fold(
                onSuccess = {
                    savedStateHandle["stage_$activeDeliveryId"] = ActiveTripStage.PICKUP_ARRIVED.name
                    savedStateHandle["arrival_time_$activeDeliveryId"] = Instant.now().epochSecond

                    updateSuccessState {
                        copy(
                            currentStage = ActiveTripStage.PICKUP_ARRIVED,
                            isSubmitting = false,
                            countdownSeconds = 900L
                        )
                    }
                    startPickupCountdown()
                },
                onFailure = { error ->
                    val userMsg = formatErrorMessage(error)
                    updateSuccessState { copy(isSubmitting = false, errorMessage = userMsg) }
                }
            )
        }
    }

    private fun handleConfirmCustomerShowed() {
        val currentState = _uiState.value as? DriverNavigationUiState.Success ?: return
        if (currentState.isSubmitting) return

        viewModelScope.launch(ioDispatcher) {
            updateSuccessState { copy(isSubmitting = true, errorMessage = null) }

            val activeDeliveryId = currentState.trip.deliveryId
            val res = driverRepository.driverConfirmCustomerShowed(activeDeliveryId)

            res.fold(
                onSuccess = {
                    countdownJob?.cancel()
                    savedStateHandle["stage_$activeDeliveryId"] = ActiveTripStage.CUSTOMER_SHOWED.name

                    updateSuccessState {
                        copy(
                            currentStage = ActiveTripStage.CUSTOMER_SHOWED,
                            isSubmitting = false
                        )
                    }
                },
                onFailure = { error ->
                    val userMsg = formatErrorMessage(error)
                    updateSuccessState { copy(isSubmitting = false, errorMessage = userMsg) }
                }
            )
        }
    }

    private fun handleConfirmCargoLoaded() {
        val currentState = _uiState.value as? DriverNavigationUiState.Success ?: return
        if (currentState.isSubmitting) return

        viewModelScope.launch(ioDispatcher) {
            updateSuccessState { copy(isSubmitting = true, errorMessage = null) }

            val activeDeliveryId = currentState.trip.deliveryId
            val res = driverRepository.driverConfirmCargoLoaded(activeDeliveryId)

            res.fold(
                onSuccess = {
                    savedStateHandle["stage_$activeDeliveryId"] = ActiveTripStage.CARGO_LOADED.name

                    updateSuccessState {
                        copy(
                            currentStage = ActiveTripStage.CARGO_LOADED,
                            isSubmitting = false
                        )
                    }
                },
                onFailure = { error ->
                    val userMsg = formatErrorMessage(error)
                    updateSuccessState { copy(isSubmitting = false, errorMessage = userMsg) }
                }
            )
        }
    }

    private fun handleMarkAsDelivered() {
        val currentState = _uiState.value as? DriverNavigationUiState.Success ?: return
        viewModelScope.launch {
            _sideEffectChannel.send(DriverNavigationSideEffect.NavigateToSubmitPod(currentState.trip.deliveryId))
        }
    }

    private fun handleViewCompletionSummary() {
        val currentState = _uiState.value as? DriverNavigationUiState.Success ?: return
        viewModelScope.launch {
            _sideEffectChannel.send(DriverNavigationSideEffect.NavigateToDeliveryComplete(currentState.trip.deliveryId))
        }
    }

    private fun handleSubmitExpense(category: String, amount: Double, receiptBytes: ByteArray?) {
        val currentState = _uiState.value as? DriverNavigationUiState.Success ?: return
        if (currentState.expenseSubmitting) return

        if (category.isBlank()) {
            updateSuccessState { copy(expenseErrorMessage = "Please select or specify an expense category.") }
            return
        }
        if (amount <= 0.0) {
            updateSuccessState { copy(expenseErrorMessage = "Please enter a valid expense amount greater than ₱0.") }
            return
        }

        viewModelScope.launch(ioDispatcher) {
            updateSuccessState { copy(expenseSubmitting = true, expenseErrorMessage = null) }

            val res = driverRepository.addDeliveryExpense(
                deliveryId = currentState.trip.deliveryId,
                category = category,
                amount = amount,
                receiptBytes = receiptBytes
            )

            res.fold(
                onSuccess = {
                    updateSuccessState {
                        copy(
                            expenseSubmitting = false,
                            isExpenseSheetVisible = false,
                            expenseSuccessMessage = "Expense recorded successfully!"
                        )
                    }
                },
                onFailure = { error ->
                    updateSuccessState {
                        copy(
                            expenseSubmitting = false,
                            expenseErrorMessage = formatErrorMessage(error)
                        )
                    }
                }
            )
        }
    }

    private fun handleSubmitIncident(
        category: IncidentCategory,
        description: String?,
        delayMinutes: Int?
    ) {
        val currentState = _uiState.value as? DriverNavigationUiState.Success ?: return
        if (currentState.incidentSubmitting) return

        viewModelScope.launch(ioDispatcher) {
            updateSuccessState { copy(incidentSubmitting = true, incidentErrorMessage = null) }

            val res = driverRepository.reportDeliveryIncident(
                deliveryId = currentState.trip.deliveryId,
                shipmentId = currentState.trip.shipmentId,
                category = category,
                description = description,
                delayMinutes = delayMinutes,
                lat = currentLat,
                lng = currentLng
            )

            res.fold(
                onSuccess = {
                    updateSuccessState {
                        copy(
                            incidentSubmitting = false,
                            isIncidentSheetVisible = false
                        )
                    }
                    _sideEffectChannel.send(DriverNavigationSideEffect.ShowToast("Incident reported successfully."))
                },
                onFailure = { error ->
                    updateSuccessState {
                        copy(
                            incidentSubmitting = false,
                            incidentErrorMessage = formatErrorMessage(error)
                        )
                    }
                }
            )
        }
    }

    private fun startPickupCountdown() {
        countdownJob?.cancel()

        countdownJob = viewModelScope.launch(ioDispatcher) {
            var remainingSeconds = 900L
            while (remainingSeconds > 0L) {
                updateSuccessState { copy(countdownSeconds = remainingSeconds) }
                delay(1000L)
                remainingSeconds--
            }
            updateSuccessState { copy(countdownSeconds = 0L) }
        }
    }

    private inline fun updateSuccessState(transform: DriverNavigationUiState.Success.() -> DriverNavigationUiState.Success) {
        _uiState.update { state ->
            if (state is DriverNavigationUiState.Success) transform(state) else state
        }
    }

    private fun formatErrorMessage(error: Throwable): String {
        return when (error) {
            is DriverError.OutsideOperatingHours -> "System is outside operating hours (06:00 - 20:00)."
            is DriverError.ArrivalTooFar -> {
                val dist = error.distanceMeters?.toInt() ?: 0
                val maxRad = error.maxRadiusMeters?.toInt() ?: 100
                "You are too far from the pickup location (${dist}m away). Please get within ${maxRad}m."
            }
            is DriverError.InvalidPodFile -> "Invalid POD submission: ${error.reason ?: "Check files."}"
            is DriverError.DeliveryNotFound -> "Delivery record not found."
            is DriverError.InvalidTransition -> "Invalid stage transition (${error.currentStatus ?: "unknown"})."
            else -> error.message ?: "Action failed. Please try again."
        }
    }
}
