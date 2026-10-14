package com.tanaw.app.feature.driver

import androidx.compose.runtime.Immutable
import com.tanaw.app.domain.model.DriverTripItem
import com.tanaw.app.domain.model.DriverTripStats
import com.tanaw.app.domain.model.TripPeriod
import com.tanaw.app.domain.model.TripStatus
import com.tanaw.app.domain.model.TripTabFilter
import java.time.LocalDate
import java.time.ZoneId

@Immutable
sealed interface TripStatsUiState {
    data object Loading : TripStatsUiState
    data class Content(val stats: DriverTripStats) : TripStatsUiState
    data class Error(val message: String) : TripStatsUiState
}

@Immutable
sealed interface TripsContentUiState {
    data object Loading : TripsContentUiState
    data class Content(
        val trips: List<DriverTripItem>,
        val isRefreshing: Boolean = false,
        val isLoadingMore: Boolean = false,
        val hasMore: Boolean = false,
        val currentPage: Int = 1
    ) : TripsContentUiState
    data class Empty(val tab: TripTabFilter) : TripsContentUiState
    data class Error(val message: String) : TripsContentUiState
}

@Immutable
data class MyTripsUiState(
    val selectedTab: TripTabFilter = TripTabFilter.COMPLETED,
    val period: TripPeriod = defaultCurrentPeriod(),
    val availablePeriods: List<TripPeriod> = defaultAvailablePeriods(),
    val statsState: TripStatsUiState = TripStatsUiState.Loading,
    val tripsState: TripsContentUiState = TripsContentUiState.Loading,
    val isFilterBottomSheetVisible: Boolean = false
)

sealed interface MyTripsEvent {
    data class TabSelected(val tab: TripTabFilter) : MyTripsEvent
    data class PeriodChanged(val period: TripPeriod) : MyTripsEvent
    data object FilterClicked : MyTripsEvent
    data object DismissFilter : MyTripsEvent
    data object Refresh : MyTripsEvent
    data object LoadMore : MyTripsEvent
    data class TripClicked(val shipmentId: String, val status: TripStatus) : MyTripsEvent
    data object Retry : MyTripsEvent
}

sealed interface MyTripsSideEffect {
    data class NavigateToActiveTrip(val shipmentId: String) : MyTripsSideEffect
    data class ShowToast(val message: String) : MyTripsSideEffect
}

fun defaultCurrentPeriod(): TripPeriod {
    val now = LocalDate.now(ZoneId.of("Asia/Manila"))
    val monthName = now.month.name.lowercase().replaceFirstChar { it.uppercase() }
    return TripPeriod(now.year, now.monthValue, "$monthName ${now.year}")
}

fun defaultAvailablePeriods(): List<TripPeriod> {
    val current = defaultCurrentPeriod()
    val now = LocalDate.now(ZoneId.of("Asia/Manila"))

    val periods = mutableListOf<TripPeriod>()
    periods.add(current)

    for (i in 1..11) {
        val prev = now.minusMonths(i.toLong())
        val monthName = prev.month.name.lowercase().replaceFirstChar { it.uppercase() }
        periods.add(TripPeriod(prev.year, prev.monthValue, "$monthName ${prev.year}"))
    }

    periods.add(TripPeriod(0, 0, "All Time", isAllTime = true))
    return periods
}
