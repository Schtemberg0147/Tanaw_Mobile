package com.tanaw.app.feature.history

import androidx.compose.runtime.Immutable
import com.tanaw.app.domain.model.BookingItemUi
import com.tanaw.app.domain.model.BookingTabFilter

@Immutable
sealed interface BookingsUiState {
    val selectedTab: BookingTabFilter

    data class Loading(
        override val selectedTab: BookingTabFilter = BookingTabFilter.ALL
    ) : BookingsUiState

    data class Content(
        val bookings: List<BookingItemUi>,
        override val selectedTab: BookingTabFilter,
        val isRefreshing: Boolean = false,
        val isLoadingMore: Boolean = false,
        val hasMore: Boolean = false,
        val currentPage: Int = 1
    ) : BookingsUiState

    data class Empty(
        override val selectedTab: BookingTabFilter
    ) : BookingsUiState

    data class Error(
        val message: String,
        override val selectedTab: BookingTabFilter
    ) : BookingsUiState
}

sealed interface BookingsEvent {
    data class TabSelected(val tabIndex: Int) : BookingsEvent
    data object Refresh : BookingsEvent
    data object LoadMore : BookingsEvent
    data class TrackClicked(val shipmentId: String) : BookingsEvent
    data class RebookClicked(val booking: BookingItemUi) : BookingsEvent
    data class ViewPodClicked(val shipmentId: String) : BookingsEvent
    data class CardDetailsClicked(val shipmentId: String) : BookingsEvent
    data object Retry : BookingsEvent
}

sealed interface BookingsSideEffect {
    data class NavigateToTrack(val shipmentId: String) : BookingsSideEffect
    data class NavigateToPod(val shipmentId: String) : BookingsSideEffect
    data class NavigateToDetails(val shipmentId: String) : BookingsSideEffect
    data class PreFillRebookAndNavigate(
        val pickupLocationId: String?,
        val pickupName: String,
        val dropoffLocationId: String?,
        val destinationName: String,
        val vehicleTypeId: String?
    ) : BookingsSideEffect
    data class ShowToast(val message: String) : BookingsSideEffect
}
