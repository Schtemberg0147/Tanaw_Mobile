package com.tanaw.app.feature.driver

import androidx.compose.runtime.Immutable
import com.tanaw.app.domain.model.ActiveTripStage
import com.tanaw.app.domain.model.IncidentCategory
import com.tanaw.app.domain.model.TripAssignment

sealed interface DriverNavigationUiState {
    data object Loading : DriverNavigationUiState

    @Immutable
    data class Success(
        val trip: TripAssignment,
        val currentStage: ActiveTripStage,
        val driverLat: Double? = null,
        val driverLng: Double? = null,
        val countdownSeconds: Long = 900L, // 15 mins default
        val isSubmitting: Boolean = false,
        val errorMessage: String? = null,
        val isExpenseSheetVisible: Boolean = false,
        val expenseSubmitting: Boolean = false,
        val expenseSuccessMessage: String? = null,
        val expenseErrorMessage: String? = null,
        val isIncidentSheetVisible: Boolean = false,
        val incidentSubmitting: Boolean = false,
        val incidentErrorMessage: String? = null
    ) : DriverNavigationUiState

    data class Error(val message: String) : DriverNavigationUiState
}

sealed interface DriverNavigationSideEffect {
    data class NavigateToSubmitPod(val deliveryId: String) : DriverNavigationSideEffect
    data class NavigateToDeliveryComplete(val deliveryId: String) : DriverNavigationSideEffect
    data class ShowToast(val message: String) : DriverNavigationSideEffect
    data object NavigateBack : DriverNavigationSideEffect
}

sealed interface DriverNavigationUiEvent {
    data object ArriveAtPickup : DriverNavigationUiEvent
    data object ConfirmCustomerShowed : DriverNavigationUiEvent
    data object ConfirmCargoLoaded : DriverNavigationUiEvent
    data object MarkAsDelivered : DriverNavigationUiEvent
    data object ViewCompletionSummary : DriverNavigationUiEvent
    data class UpdateDriverLocation(val lat: Double, val lng: Double) : DriverNavigationUiEvent
    data object OpenExpenseSheet : DriverNavigationUiEvent
    data object CloseExpenseSheet : DriverNavigationUiEvent
    data class SubmitExpense(
        val category: String,
        val amount: Double,
        val receiptBytes: ByteArray?
    ) : DriverNavigationUiEvent
    data object OpenIncidentSheet : DriverNavigationUiEvent
    data object CloseIncidentSheet : DriverNavigationUiEvent
    data class SubmitIncident(
        val category: IncidentCategory,
        val description: String?,
        val delayMinutes: Int?
    ) : DriverNavigationUiEvent
    data object DismissError : DriverNavigationUiEvent
}
