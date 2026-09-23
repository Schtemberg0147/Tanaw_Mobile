package com.tanaw.app.feature.auth

// Replaces the `var isLoading` / `var errorMessage` remember { } pair that
// used to live inside TanawNavHost's composable(Routes.LOGIN) block.
data class LoginUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isLoginSuccessful: Boolean = false
)