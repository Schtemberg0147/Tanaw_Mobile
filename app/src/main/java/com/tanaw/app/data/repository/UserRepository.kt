package com.tanaw.app.data.repository

import com.tanaw.app.data.model.UserProfile

interface UserRepository {
    suspend fun getUserProfile(): Result<UserProfile>
    suspend fun updateUserProfile(profile: UserProfile): Result<Unit>
}
