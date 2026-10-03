package com.tanaw.app.data.repository

enum class UserRole(val value: String) {
    CUSTOMER("customer"),
    DRIVER("driver"),
    ADMIN("admin");

    companion object {
        fun fromString(value: String?): UserRole {
            return entries.firstOrNull { it.value.equals(value, ignoreCase = true) } ?: CUSTOMER
        }
    }
}

interface AuthRepository {
    suspend fun login(email: String, password: String): AuthResult
    suspend fun signUp(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
        phone: String
    ): AuthResult
    suspend fun verifyEmailOtp(email: String, token: String): AuthResult
    suspend fun verifyPhoneOtp(phone: String, token: String): AuthResult
    suspend fun resendEmailOtp(email: String): AuthResult
    suspend fun resendPhoneOtp(phone: String): AuthResult
    suspend fun changePassword(newPassword: String): AuthResult
    suspend fun logout()
    fun getCurrentUserId(): String?
    suspend fun getCurrentUserRole(): UserRole
    fun isSessionValid(minimumDays: Int = 7): Boolean
}

sealed class AuthResult {
    data class Success(
        val userId: String,
        val role: UserRole = UserRole.CUSTOMER
    ) : AuthResult()
    data class Error(val message: String) : AuthResult()
}
