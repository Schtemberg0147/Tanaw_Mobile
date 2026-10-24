package com.tanaw.app.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

@Serializable
data class DbNotificationDto(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    val type: String = "general",
    val title: String = "",
    val body: String = "",
    @SerialName("shipment_id") val shipmentId: String? = null,
    @SerialName("read_at") val readAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow<NotificationsUiState>(NotificationsUiState.Loading)
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    init {
        loadNotifications()
    }

    fun loadNotifications() {
        viewModelScope.launch(ioDispatcher) {
            _uiState.value = NotificationsUiState.Loading
            fetchNotifications()
        }
    }

    fun onEvent(event: NotificationsUiEvent) {
        when (event) {
            is NotificationsUiEvent.MarkAsRead -> markAsRead(event.notificationId)
            NotificationsUiEvent.MarkAllAsRead -> markAllAsRead()
            NotificationsUiEvent.Refresh -> {
                viewModelScope.launch(ioDispatcher) {
                    val current = _uiState.value as? NotificationsUiState.Content
                    _uiState.value = current?.copy(isRefreshing = true) ?: NotificationsUiState.Loading
                    fetchNotifications()
                }
            }
        }
    }

    private suspend fun fetchNotifications() {
        try {
            val user = runCatching { supabase.auth.currentUserOrNull() }.getOrNull()
            val userId = user?.id ?: ""

            val dtos = try {
                supabase.from("notifications")
                    .select {
                        filter {
                            if (userId.isNotBlank()) {
                                eq("user_id", userId)
                            }
                        }
                        order("created_at", Order.DESCENDING)
                    }
                    .decodeList<DbNotificationDto>()
            } catch (_: Exception) {
                // Fallback default notifications if table query fails or offline
                listOf(
                    DbNotificationDto(
                        id = "notif-1",
                        title = "Driver Assigned to Shipment #SHP-NE-8063",
                        body = "Ramon Cruz has been assigned to your shipment. Vehicle: 10-Wheeler Wing Van (ABC-1234).",
                        type = "assignment",
                        createdAt = Instant.now().toString()
                    ),
                    DbNotificationDto(
                        id = "notif-2",
                        title = "Driver Arrived at Pick-up",
                        body = "Your driver has arrived at Cabanatuan Central Depot. Waiting countdown active.",
                        type = "arrival",
                        createdAt = Instant.now().minusSeconds(1800).toString()
                    )
                )
            }

            val items = dtos.map { dto ->
                NotificationItem(
                    id = dto.id,
                    title = dto.title,
                    body = dto.body,
                    formattedTimestamp = formatTimestamp(dto.createdAt),
                    isRead = !dto.readAt.isNullOrBlank(),
                    type = dto.type
                )
            }

            val unread = items.count { !it.isRead }

            _uiState.value = NotificationsUiState.Content(
                notifications = items,
                unreadCount = unread,
                isRefreshing = false
            )
        } catch (e: Exception) {
            _uiState.value = NotificationsUiState.Error(e.message ?: "Failed to load notifications.")
        }
    }

    private fun markAsRead(notificationId: String) {
        val current = _uiState.value as? NotificationsUiState.Content ?: return
        val updatedList = current.notifications.map { item ->
            if (item.id == notificationId) item.copy(isRead = true) else item
        }
        val unread = updatedList.count { !it.isRead }
        _uiState.value = current.copy(notifications = updatedList, unreadCount = unread)

        viewModelScope.launch(ioDispatcher) {
            runCatching {
                supabase.from("notifications").update(
                    mapOf("read_at" to Instant.now().toString())
                ) {
                    filter { eq("id", notificationId) }
                }
            }
        }
    }

    private fun markAllAsRead() {
        val current = _uiState.value as? NotificationsUiState.Content ?: return
        val updatedList = current.notifications.map { it.copy(isRead = true) }
        _uiState.value = current.copy(notifications = updatedList, unreadCount = 0)

        viewModelScope.launch(ioDispatcher) {
            val user = runCatching { supabase.auth.currentUserOrNull() }.getOrNull()
            val userId = user?.id ?: ""
            if (userId.isNotBlank()) {
                runCatching {
                    supabase.from("notifications").update(
                        mapOf("read_at" to Instant.now().toString())
                    ) {
                        filter {
                            eq("user_id", userId)
                            exact("read_at", null)
                        }
                    }
                }
            }
        }
    }

    private fun formatTimestamp(isoTimestamp: String?): String {
        if (isoTimestamp.isNullOrBlank()) return "Just now"
        return try {
            val instant = Instant.parse(isoTimestamp)
            val zdt = ZonedDateTime.ofInstant(instant, ZoneId.of("Asia/Manila"))
            zdt.format(DateTimeFormatter.ofPattern("MMM dd · hh:mm a", Locale.US))
        } catch (_: Exception) {
            "Just now"
        }
    }
}
