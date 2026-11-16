package com.tanaw.app.feature.auth.state

data class VerifyPhoneUiState(
    val isLoading: Boolean = false,
    val isResending: Boolean = false,
    val errorMessage: String? = null,
    val resendSuccessMessage: String? = null,
    val isVerified: Boolean = false
)
