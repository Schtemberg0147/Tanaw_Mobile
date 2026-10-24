package com.tanaw.app.feature.notifications

import androidx.compose.runtime.Immutable

@Immutable
data class NotificationItem(
    val id: String,
    val title: String,
    val body: String,
    val formattedTimestamp: String,
    val isRead: Boolean,
    val type: String
)

sealed interface NotificationsUiState {
    data object Loading : NotificationsUiState

    @Immutable
    data class Content(
        val notifications: List<NotificationItem>,
        val unreadCount: Int = 0,
        val isRefreshing: Boolean = false,
        val errorMessage: String? = null
    ) : NotificationsUiState

    data class Error(val message: String) : NotificationsUiState
}

sealed interface NotificationsSideEffect {
    data object NavigateBack : NotificationsSideEffect
}

sealed interface NotificationsUiEvent {
    data class MarkAsRead(val notificationId: String) : NotificationsUiEvent
    data object MarkAllAsRead : NotificationsUiEvent
    data object Refresh : NotificationsUiEvent
}
