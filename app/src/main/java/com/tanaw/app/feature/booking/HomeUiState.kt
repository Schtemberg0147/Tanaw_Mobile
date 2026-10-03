package com.tanaw.app.feature.booking

import com.tanaw.app.data.model.UserProfile

data class HomeUiState(
    val isLoading: Boolean = true,
    val userProfile: UserProfile? = null,
    val errorMessage: String? = null
)
