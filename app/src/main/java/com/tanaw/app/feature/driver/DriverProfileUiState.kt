package com.tanaw.app.feature.driver

import androidx.compose.runtime.Immutable
import com.tanaw.app.domain.model.ComplianceDocumentItem
import com.tanaw.app.domain.model.DriverProfileDetails

@Immutable
sealed interface DriverProfileUiState {
    data object Loading : DriverProfileUiState

    data class Content(
        val details: DriverProfileDetails,
        val complianceDocs: List<ComplianceDocumentItem>,
        val isRefreshing: Boolean = false,
        val docsErrorMessage: String? = null
    ) : DriverProfileUiState

    data class Error(val message: String) : DriverProfileUiState
}

sealed interface DriverProfileEvent {
    data object Refresh : DriverProfileEvent
    data object Retry : DriverProfileEvent
    data object RetryDocs : DriverProfileEvent
    data object LogoutClicked : DriverProfileEvent
}

sealed interface DriverProfileSideEffect {
    data object NavigateToLogin : DriverProfileSideEffect
    data class ShowToast(val message: String) : DriverProfileSideEffect
}
