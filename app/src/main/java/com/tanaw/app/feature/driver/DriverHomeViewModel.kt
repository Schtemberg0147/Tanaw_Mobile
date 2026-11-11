package com.tanaw.app.feature.driver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanaw.app.domain.model.DriverProfile
import com.tanaw.app.domain.model.DriverStatus
import com.tanaw.app.domain.repository.DriverRepository
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DriverHomeViewModel @Inject constructor(
    private val driverRepository: DriverRepository,
    private val supabase: SupabaseClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _sideEffectChannel = Channel<DriverHomeSideEffect>(Channel.BUFFERED)
    val sideEffect = _sideEffectChannel.receiveAsFlow()

    private val refreshTrigger = MutableStateFlow(0)

    val uiState: StateFlow<DriverHomeUiState> = refreshTrigger
        .flatMapLatest {
            val user = runCatching { supabase.auth.currentUserOrNull() }.getOrNull()
            val userId = user?.id ?: ""
            val driverId = userId

            val profileFlow = driverRepository.observeDriverProfile(userId)
            val assignmentFlow = driverRepository.observeActiveAssignment(driverId)
            val unreadFlow = driverRepository.observeUnreadNotificationCount(userId)

            combine(profileFlow, assignmentFlow, unreadFlow) { profileRes, assignmentRes, unreadCount ->
                val profile = profileRes.getOrNull() ?: DriverProfile(
                    id = driverId,
                    userId = userId,
                    fullName = "Ramon Cruz",
                    vehicleTypeName = "10-Wheeler Wing Van",
                    status = DriverStatus.AVAILABLE
                )

                val assignment = assignmentRes.getOrNull()

                if (assignment != null) {
                    DriverHomeUiState.Assigned(
                        driver = profile.copy(status = DriverStatus.ON_TRIP),
                        trip = assignment,
                        unreadNotificationCount = unreadCount
                    )
                } else {
                    DriverHomeUiState.Empty(
                        driver = profile.copy(status = DriverStatus.AVAILABLE),
                        unreadNotificationCount = unreadCount
                    )
                }
            }
        }
        .catch { e ->
            emit(DriverHomeUiState.Error(e.message ?: "Failed to load driver home data."))
        }
        .flowOn(ioDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DriverHomeUiState.Loading
        )

    fun onEvent(event: DriverHomeUiEvent) {
        when (event) {
            DriverHomeUiEvent.Retry, DriverHomeUiEvent.Refresh -> {
                refreshTrigger.value += 1
            }
            DriverHomeUiEvent.OpenNotifications -> {
                viewModelScope.launch {
                    _sideEffectChannel.send(DriverHomeSideEffect.NavigateTo("notifications"))
                }
            }
            is DriverHomeUiEvent.OpenTripDetails -> {
                viewModelScope.launch {
                    _sideEffectChannel.send(DriverHomeSideEffect.NavigateTo("trip_details/${event.tripId}"))
                }
            }
        }
    }
}
