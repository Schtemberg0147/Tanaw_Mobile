package com.tanaw.app.data.repository

// LoginViewModel only ever talks to this interface. Today it's bound to
// MockAuthRepositoryImpl (below); once Supabase is wired up, you write a
// SupabaseAuthRepositoryImpl that implements the same interface, swap the
// binding in RepositoryModule, and LoginViewModel/LoginScreen don't change at all.
interface AuthRepository {
    suspend fun login(email: String, password: String): AuthResult
    suspend fun signUp(email: String, password: String, role: String): AuthResult
    suspend fun logout()
    fun getCurrentUserId(): String?
}

sealed class AuthResult {
    data class Success(val userId: String) : AuthResult()
    data class Error(val message: String) : AuthResult()
}