package com.tanaw.app.feature.auth

import com.tanaw.app.data.repository.UserRole

data class LoginUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isLoginSuccessful: Boolean = false,
    val userRole: UserRole = UserRole.CUSTOMER
)
