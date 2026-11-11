package com.tanaw.app.feature.driver

import androidx.compose.runtime.Immutable
import com.tanaw.app.domain.model.DriverProfile
import com.tanaw.app.domain.model.TripAssignment

@Immutable
sealed interface DriverHomeUiState {
    data object Loading : DriverHomeUiState

    data class Empty(
        val driver: DriverProfile,
        val unreadNotificationCount: Int = 1,
        val isRefreshing: Boolean = false
    ) : DriverHomeUiState

    data class Assigned(
        val driver: DriverProfile,
        val trip: TripAssignment,
        val unreadNotificationCount: Int = 1,
        val isRefreshing: Boolean = false
    ) : DriverHomeUiState

    data class Error(
        val message: String
    ) : DriverHomeUiState
}

sealed interface DriverHomeUiEvent {
    data object Retry : DriverHomeUiEvent
    data object Refresh : DriverHomeUiEvent
    data object OpenNotifications : DriverHomeUiEvent
    data class OpenTripDetails(val tripId: String) : DriverHomeUiEvent
}

sealed interface DriverHomeSideEffect {
    data class ShowToast(val message: String) : DriverHomeSideEffect
    data class NavigateTo(val route: String) : DriverHomeSideEffect
}
