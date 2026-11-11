package com.tanaw.app.feature.driver

import androidx.compose.runtime.Immutable

sealed interface DeliveryCompleteUiState {
    data object Loading : DeliveryCompleteUiState

    @Immutable
    data class Success(
        val deliveryId: String,
        val trackingNumber: String,
        val pickupLocationName: String,
        val dropoffLocationName: String,
        val formattedDistance: String,
        val formattedDuration: String,
        val allowanceAmount: Double,
        val formattedAllowance: String,
        val submittedExpensesAmount: Double = 0.0,
        val formattedSubmittedExpenses: String = "₱0"
    ) : DeliveryCompleteUiState

    data class Error(val message: String) : DeliveryCompleteUiState
}

sealed interface DeliveryCompleteSideEffect {
    data object NavigateToDriverHome : DeliveryCompleteSideEffect
}

sealed interface DeliveryCompleteUiEvent {
    data object BackToHome : DeliveryCompleteUiEvent
}
