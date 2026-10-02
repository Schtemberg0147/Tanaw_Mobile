package com.tanaw.app.feature.auth

data class CreateAccountUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSignUpSuccessful: Boolean = false,
    val registeredEmail: String = "",
    val registeredPhone: String = ""
)
