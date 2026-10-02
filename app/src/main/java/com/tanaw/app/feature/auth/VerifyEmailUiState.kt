package com.tanaw.app.feature.auth

data class VerifyEmailUiState(
    val isLoading: Boolean = false,
    val isResending: Boolean = false,
    val errorMessage: String? = null,
    val resendSuccessMessage: String? = null,
    val isVerified: Boolean = false
)
