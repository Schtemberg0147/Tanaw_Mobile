package com.tanaw.app.data.repository

import kotlinx.coroutines.delay
import javax.inject.Inject

class MockAuthRepositoryImpl @Inject constructor() : AuthRepository {

    override suspend fun login(email: String, password: String): AuthResult {
        delay(1_500L)

        return if (email.trim() == MOCK_EMAIL && password == MOCK_PASSWORD) {
            AuthResult.Success(userId = "mock-user-id", role = UserRole.CUSTOMER)
        } else {
            AuthResult.Error("Incorrect email or password. Please try again.")
        }
    }

    override suspend fun signUp(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
        phone: String
    ): AuthResult {
        delay(1_500L)
        return AuthResult.Success(userId = "mock-user-id", role = UserRole.CUSTOMER)
    }

    override suspend fun verifyEmailOtp(email: String, token: String): AuthResult {
        delay(1_000L)
        return if (token.length == 6) {
            AuthResult.Success(userId = "mock-user-id", role = UserRole.CUSTOMER)
        } else {
            AuthResult.Error("Invalid email OTP code. Please try again.")
        }
    }

    override suspend fun verifyPhoneOtp(phone: String, token: String): AuthResult {
        delay(1_000L)
        return if (token.length == 6) {
            AuthResult.Success(userId = "mock-user-id", role = UserRole.CUSTOMER)
        } else {
            AuthResult.Error("Invalid phone OTP code. Please try again.")
        }
    }

    override suspend fun resendEmailOtp(email: String): AuthResult {
        delay(1_000L)
        return AuthResult.Success(userId = "mock-user-id", role = UserRole.CUSTOMER)
    }

    override suspend fun resendPhoneOtp(phone: String): AuthResult {
        delay(1_000L)
        return AuthResult.Success(userId = "mock-user-id", role = UserRole.CUSTOMER)
    }

    override suspend fun logout() {
        // no-op for the mock
    }

    override fun getCurrentUserId(): String? {
        return null
    }

    override suspend fun getCurrentUserRole(): UserRole {
        return UserRole.CUSTOMER
    }

    override fun isSessionValid(minimumDays: Int): Boolean {
        return false
    }

    companion object {
        private const val MOCK_EMAIL = "test@tanaw.com"
        private const val MOCK_PASSWORD = "password123"
    }
}
