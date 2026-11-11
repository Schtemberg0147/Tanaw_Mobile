package com.tanaw.app.feature.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanaw.app.domain.model.BookingItemUi
import com.tanaw.app.domain.model.BookingTabFilter
import com.tanaw.app.domain.repository.BookingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BookingsViewModel @Inject constructor(
    private val bookingsRepository: BookingsRepository,
    private val supabase: SupabaseClient,
    private val savedStateHandle: SavedStateHandle,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _sideEffectChannel = Channel<BookingsSideEffect>(Channel.BUFFERED)
    val sideEffect = _sideEffectChannel.receiveAsFlow()

    private val selectedTabIndexFlow = savedStateHandle.getStateFlow(KEY_TAB_INDEX, 0)
    private val refreshTrigger = MutableStateFlow(0)
    private val currentPageFlow = MutableStateFlow(1)
    private val PAGE_SIZE = 20

    val uiState: StateFlow<BookingsUiState> = combine(
        selectedTabIndexFlow,
        refreshTrigger,
        currentPageFlow
    ) { tabIdx, _, page ->
        val filter = indexToFilter(tabIdx)
        Triple(filter, page, System.currentTimeMillis())
    }.flatMapLatest { (tabFilter, page, _) ->
        val user = runCatching { supabase.auth.currentUserOrNull() }.getOrNull()
        val userId = user?.id ?: ""

        bookingsRepository.getCustomerId(userId).flatMapLatest { customerRes ->
            val customerId = customerRes.getOrNull() ?: userId

            bookingsRepository.observeBookings(customerId, tabFilter, page, PAGE_SIZE)
                .combine(bookingsRepository.observeRealtimeShipmentChanges(customerId)) { result, _ ->
                    result
                }
                .catch { emit(Result.failure(it)) }
                .flatMapLatest { res ->
                    flowOf(Pair(tabFilter, res))
                }
        }
    }.combine(MutableStateFlow(emptyList<BookingItemUi>())) { (tabFilter, res), existingList ->
        val items = res.getOrNull()
        if (res.isFailure) {
            BookingsUiState.Error(
                message = res.exceptionOrNull()?.message ?: "Failed to load bookings.",
                selectedTab = tabFilter
            )
        } else if (items.isNullOrEmpty() && existingList.isEmpty()) {
            BookingsUiState.Empty(selectedTab = tabFilter)
        } else {
            val allItems = if (currentPageFlow.value == 1) items.orEmpty() else (existingList + items.orEmpty()).distinctBy { it.id }
            BookingsUiState.Content(
                bookings = allItems,
                selectedTab = tabFilter,
                hasMore = items?.size == PAGE_SIZE,
                currentPage = currentPageFlow.value
            )
        }
    }
        .flowOn(ioDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BookingsUiState.Loading(selectedTab = indexToFilter(savedStateHandle.get<Int>(KEY_TAB_INDEX) ?: 0))
        )

    fun onEvent(event: BookingsEvent) {
        when (event) {
            is BookingsEvent.TabSelected -> {
                savedStateHandle[KEY_TAB_INDEX] = event.tabIndex
                currentPageFlow.value = 1
            }
            BookingsEvent.Refresh -> {
                currentPageFlow.value = 1
                refreshTrigger.value += 1
            }
            BookingsEvent.LoadMore -> {
                val currentState = uiState.value
                if (currentState is BookingsUiState.Content && currentState.hasMore && !currentState.isLoadingMore) {
                    currentPageFlow.value += 1
                }
            }
            BookingsEvent.Retry -> {
                currentPageFlow.value = 1
                refreshTrigger.value += 1
            }
            is BookingsEvent.TrackClicked -> {
                viewModelScope.launch {
                    _sideEffectChannel.send(BookingsSideEffect.NavigateToTrack(event.shipmentId))
                }
            }
            is BookingsEvent.ViewPodClicked -> {
                viewModelScope.launch {
                    _sideEffectChannel.send(BookingsSideEffect.NavigateToPod(event.shipmentId))
                }
            }
            is BookingsEvent.CardDetailsClicked -> {
                viewModelScope.launch {
                    _sideEffectChannel.send(BookingsSideEffect.NavigateToDetails(event.shipmentId))
                }
            }
            is BookingsEvent.RebookClicked -> {
                viewModelScope.launch {
                    _sideEffectChannel.send(
                        BookingsSideEffect.PreFillRebookAndNavigate(
                            pickupLocationId = event.booking.pickupLocationId,
                            pickupName = event.booking.pickupName,
                            dropoffLocationId = event.booking.dropoffLocationId,
                            destinationName = event.booking.destinationCity,
                            vehicleTypeId = event.booking.vehicleTypeId
                        )
                    )
                }
            }
        }
    }

    private fun indexToFilter(index: Int): BookingTabFilter {
        return when (index) {
            1 -> BookingTabFilter.ACTIVE
            2 -> BookingTabFilter.COMPLETED
            3 -> BookingTabFilter.CANCELLED
            else -> BookingTabFilter.ALL
        }
    }

    companion object {
        const val KEY_TAB_INDEX = "selected_tab_index"
    }
}
