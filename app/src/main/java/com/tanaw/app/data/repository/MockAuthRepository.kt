package com.tanaw.app.data.repository

import kotlinx.coroutines.delay
import javax.inject.Inject

// This is your old MOCK_EMAIL / MOCK_PASSWORD / delay(1500L) logic from
// MainActivity, moved here unchanged. Nothing about the *behavior* changes —
// only *where* it lives. When you're ready for Supabase, create
// SupabaseAuthRepositoryImpl alongside this one and flip the binding in
// RepositoryModule; you won't need to touch LoginViewModel or LoginScreen.
class MockAuthRepositoryImpl @Inject constructor() : AuthRepository {

    override suspend fun login(email: String, password: String): AuthResult {
        delay(1_500L) // fake network latency, same as the old scope.launch { delay(1_500L) }

        return if (email.trim() == MOCK_EMAIL && password == MOCK_PASSWORD) {
            AuthResult.Success(userId = "mock-user-id")
        } else {
            AuthResult.Error("Incorrect email or password. Please try again.")
        }
    }

    override suspend fun signUp(email: String, password: String, role: String): AuthResult {
        delay(1_500L)
        // TODO: wire up once CreateAccountScreen gets the same treatment
        return AuthResult.Success(userId = "mock-user-id")
    }

    override suspend fun logout() {
        // no-op for the mock — nothing to clear yet
    }

    override fun getCurrentUserId(): String? {
        // TODO: return a persisted mock session id if you want "stay logged in"
        // behavior to work even with mock data
        return null
    }

    companion object {
        private const val MOCK_EMAIL = "test@tanaw.com"
        private const val MOCK_PASSWORD = "password123"
    }
}