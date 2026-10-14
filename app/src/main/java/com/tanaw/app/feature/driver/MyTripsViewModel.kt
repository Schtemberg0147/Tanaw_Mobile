package com.tanaw.app.feature.driver

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanaw.app.domain.model.DriverTripItem
import com.tanaw.app.domain.model.TripPeriod
import com.tanaw.app.domain.model.TripStatus
import com.tanaw.app.domain.model.TripTabFilter
import com.tanaw.app.domain.repository.DriverRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MyTripsViewModel @Inject constructor(
    private val driverRepository: DriverRepository,
    private val savedStateHandle: SavedStateHandle,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _sideEffectChannel = Channel<MyTripsSideEffect>(Channel.BUFFERED)
    val sideEffect = _sideEffectChannel.receiveAsFlow()

    private val selectedTabFlow = savedStateHandle.getStateFlow(
        KEY_SELECTED_TAB,
        TripTabFilter.COMPLETED.name
    )

    private val periodYearFlow = savedStateHandle.getStateFlow(KEY_PERIOD_YEAR, defaultCurrentPeriod().year)
    private val periodMonthFlow = savedStateHandle.getStateFlow(KEY_PERIOD_MONTH, defaultCurrentPeriod().month)
    private val periodLabelFlow = savedStateHandle.getStateFlow(KEY_PERIOD_LABEL, defaultCurrentPeriod().label)
    private val periodIsAllTimeFlow = savedStateHandle.getStateFlow(KEY_PERIOD_ALL_TIME, false)

    private val isFilterBottomSheetVisible = MutableStateFlow(false)
    private val refreshTrigger = MutableStateFlow(0)
    private val currentPageFlow = MutableStateFlow(1)
    private val accumulatedTrips = MutableStateFlow<List<DriverTripItem>>(emptyList())
    private val isRefreshingFlow = MutableStateFlow(false)
    private val isLoadingMoreFlow = MutableStateFlow(false)

    private val PAGE_SIZE = 20

    private val currentPeriodFlow = combine(
        periodYearFlow,
        periodMonthFlow,
        periodLabelFlow,
        periodIsAllTimeFlow
    ) { year, month, label, isAllTime ->
        TripPeriod(year, month, label, isAllTime)
    }

    private val statsFlow = combine(
        currentPeriodFlow,
        refreshTrigger
    ) { period, _ -> period }.mapLatest { period ->
        try {
            val statsRes = driverRepository.getDriverTripStats(period)
            if (statsRes.isSuccess) {
                TripStatsUiState.Content(statsRes.getOrThrow())
            } else {
                TripStatsUiState.Error(statsRes.exceptionOrNull()?.message ?: "Failed to load stats")
            }
        } catch (e: Exception) {
            TripStatsUiState.Error(e.message ?: "Failed to load stats")
        }
    }

    val uiState: StateFlow<MyTripsUiState> = combine(
        selectedTabFlow,
        currentPeriodFlow,
        currentPageFlow,
        refreshTrigger,
        isFilterBottomSheetVisible
    ) { tabName, period, page, _, bottomSheetVisible ->
        val tab = try { TripTabFilter.valueOf(tabName) } catch (_: Exception) { TripTabFilter.COMPLETED }
        Tuple5(tab, period, page, bottomSheetVisible, System.currentTimeMillis())
    }.flatMapLatest { (tab, period, page, bottomSheetVisible, _) ->
        combine(
            driverRepository.observeDriverTrips(tab, period, page, PAGE_SIZE),
            driverRepository.observeRealtimeTripChanges(),
            statsFlow
        ) { tripsRes, _, statsState ->
            val previousList = if (page == 1) emptyList() else accumulatedTrips.value

            val tripsContentState: TripsContentUiState = if (tripsRes.isFailure) {
                val err = tripsRes.exceptionOrNull()?.message ?: "Failed to load trips."
                TripsContentUiState.Error(err)
            } else {
                val newItems = tripsRes.getOrNull().orEmpty()
                val merged = (previousList + newItems).distinctBy { it.id }
                accumulatedTrips.value = merged

                if (merged.isEmpty()) {
                    TripsContentUiState.Empty(tab)
                } else {
                    TripsContentUiState.Content(
                        trips = merged,
                        isRefreshing = isRefreshingFlow.value,
                        isLoadingMore = isLoadingMoreFlow.value,
                        hasMore = newItems.size == PAGE_SIZE,
                        currentPage = page
                    )
                }
            }

            MyTripsUiState(
                selectedTab = tab,
                period = period,
                availablePeriods = defaultAvailablePeriods(),
                statsState = statsState,
                tripsState = tripsContentState,
                isFilterBottomSheetVisible = bottomSheetVisible
            )
        }.catch {
            emit(
                MyTripsUiState(
                    selectedTab = tab,
                    period = period,
                    availablePeriods = defaultAvailablePeriods(),
                    statsState = TripStatsUiState.Error("Realtime error"),
                    tripsState = TripsContentUiState.Error(it.message ?: "Failed to load trips"),
                    isFilterBottomSheetVisible = bottomSheetVisible
                )
            )
        }
    }
        .flowOn(ioDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MyTripsUiState()
        )

    fun onEvent(event: MyTripsEvent) {
        when (event) {
            is MyTripsEvent.TabSelected -> {
                savedStateHandle[KEY_SELECTED_TAB] = event.tab.name
                accumulatedTrips.value = emptyList()
                currentPageFlow.value = 1
            }
            is MyTripsEvent.PeriodChanged -> {
                savedStateHandle[KEY_PERIOD_YEAR] = event.period.year
                savedStateHandle[KEY_PERIOD_MONTH] = event.period.month
                savedStateHandle[KEY_PERIOD_LABEL] = event.period.label
                savedStateHandle[KEY_PERIOD_ALL_TIME] = event.period.isAllTime
                isFilterBottomSheetVisible.value = false
                accumulatedTrips.value = emptyList()
                currentPageFlow.value = 1
                refreshTrigger.value += 1
            }
            MyTripsEvent.FilterClicked -> {
                isFilterBottomSheetVisible.value = true
            }
            MyTripsEvent.DismissFilter -> {
                isFilterBottomSheetVisible.value = false
            }
            MyTripsEvent.Refresh -> {
                viewModelScope.launch {
                    isRefreshingFlow.value = true
                    accumulatedTrips.value = emptyList()
                    currentPageFlow.value = 1
                    refreshTrigger.value += 1
                    isRefreshingFlow.value = false
                }
            }
            MyTripsEvent.LoadMore -> {
                val currentState = uiState.value.tripsState
                if (currentState is TripsContentUiState.Content && currentState.hasMore && !isLoadingMoreFlow.value) {
                    viewModelScope.launch {
                        isLoadingMoreFlow.value = true
                        currentPageFlow.value += 1
                        isLoadingMoreFlow.value = false
                    }
                }
            }
            is MyTripsEvent.TripClicked -> {
                viewModelScope.launch {
                    if (event.status == TripStatus.ONGOING) {
                        _sideEffectChannel.send(MyTripsSideEffect.NavigateToActiveTrip(event.shipmentId))
                    }
                }
            }
            MyTripsEvent.Retry -> {
                accumulatedTrips.value = emptyList()
                currentPageFlow.value = 1
                refreshTrigger.value += 1
            }
        }
    }

    private data class Tuple5<T1, T2, T3, T4, T5>(
        val t1: T1, val t2: T2, val t3: T3, val t4: T4, val t5: T5
    )

    companion object {
        const val KEY_SELECTED_TAB = "selected_tab"
        const val KEY_PERIOD_YEAR = "period_year"
        const val KEY_PERIOD_MONTH = "period_month"
        const val KEY_PERIOD_LABEL = "period_label"
        const val KEY_PERIOD_ALL_TIME = "period_all_time"
    }
}
