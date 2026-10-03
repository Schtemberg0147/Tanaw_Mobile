package com.tanaw.app.feature.profile

import com.tanaw.app.data.model.UserProfile

data class ProfileUiState(
    val isLoading: Boolean = true,
    val userProfile: UserProfile? = null,
    val errorMessage: String? = null,
    val isUpdating: Boolean = false,
    val updateSuccessMessage: String? = null
)
