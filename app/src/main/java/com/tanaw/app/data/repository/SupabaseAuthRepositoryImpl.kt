package com.tanaw.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject

@Serializable
private data class UserRoleDto(
    val id: String = "",
    val role: String = "customer"
)

class SupabaseAuthRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient
) : AuthRepository {

    override suspend fun login(
        email: String,
        password: String
    ): AuthResult {
        return try {
            supabase.auth.signInWith(Email) {
                this.email = email.trim()
                this.password = password
            }

            val user = supabase.auth.currentUserOrNull()

            if (user != null) {
                val role = getCurrentUserRole()
                AuthResult.Success(user.id, role)
            } else {
                AuthResult.Error("Login succeeded, but no user session was found.")
            }

        } catch (e: Exception) {
            AuthResult.Error(
                e.message ?: "Unable to sign in. Please try again."
            )
        }
    }

    override suspend fun signUp(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
        phone: String
    ): AuthResult {
        return try {
            val user = supabase.auth.signUpWith(Email) {
                this.email = email.trim()
                this.password = password
                this.data = buildJsonObject {
                    put("full_name", "$firstName $lastName".trim())
                    put("first_name", firstName.trim())
                    put("last_name", lastName.trim())
                    put("phone", phone.trim())
                    put("role", "customer")
                }
            }

            val userId = user?.id ?: supabase.auth.currentUserOrNull()?.id ?: ""
            AuthResult.Success(userId, UserRole.CUSTOMER)

        } catch (e: Exception) {
            val friendlyMessage = when {
                (e.message?.contains("already registered", ignoreCase = true) == true) ||
                (e.message?.contains("already exists", ignoreCase = true) == true) ->
                    "An account with this email address already exists. Please sign in instead."
                else ->
                    e.message ?: "Unable to create account. Please check your details and try again."
            }
            AuthResult.Error(friendlyMessage)
        }
    }

    override suspend fun verifyEmailOtp(email: String, token: String): AuthResult {
        return try {
            supabase.auth.verifyEmailOtp(
                type = OtpType.Email.SIGNUP,
                email = email.trim(),
                token = token.trim()
            )
            val userId = supabase.auth.currentUserOrNull()?.id ?: ""
            val role = getCurrentUserRole()
            AuthResult.Success(userId, role)
        } catch (e: Exception) {
            val friendlyMessage = when {
                (e.message?.contains("expired", ignoreCase = true) == true) ->
                    "The verification code has expired. Please request a new code."
                (e.message?.contains("invalid", ignoreCase = true) == true) ->
                    "The verification code is incorrect. Please check your email and try again."
                (e.message?.contains("already verified", ignoreCase = true) == true) ||
                (e.message?.contains("already confirmed", ignoreCase = true) == true) ->
                    "This email address has already been verified."
                (e.message?.contains("rate", ignoreCase = true) == true) ||
                (e.message?.contains("limit", ignoreCase = true) == true) ->
                    "Too many verification attempts. Please wait a few minutes before trying again."
                else ->
                    e.message ?: "Email verification failed. Please check your code and try again."
            }
            AuthResult.Error(friendlyMessage)
        }
    }

    override suspend fun verifyPhoneOtp(phone: String, token: String): AuthResult {
        return try {
            supabase.auth.verifyPhoneOtp(
                type = OtpType.Phone.SMS,
                phone = phone.trim(),
                token = token.trim()
            )
            val userId = supabase.auth.currentUserOrNull()?.id ?: ""
            val role = getCurrentUserRole()
            AuthResult.Success(userId, role)
        } catch (e: Exception) {
            val friendlyMessage = when {
                (e.message?.contains("phone_provider_disabled", ignoreCase = true) == true) ||
                (e.message?.contains("Phone logins are disabled", ignoreCase = true) == true) ->
                    "Phone SMS Provider is disabled in your Supabase Dashboard (Authentication -> Providers -> Phone). Please enable Phone Auth in Supabase Dashboard or use Email Verification."
                (e.message?.contains("expired", ignoreCase = true) == true) ->
                    "The verification code has expired. Please request a new SMS code."
                (e.message?.contains("invalid", ignoreCase = true) == true) ->
                    "The verification code is incorrect. Please check the SMS and try again."
                (e.message?.contains("already verified", ignoreCase = true) == true) ||
                (e.message?.contains("already confirmed", ignoreCase = true) == true) ->
                    "This phone number has already been verified."
                (e.message?.contains("rate", ignoreCase = true) == true) ||
                (e.message?.contains("limit", ignoreCase = true) == true) ->
                    "Too many verification attempts. Please wait a few minutes before trying again."
                else ->
                    e.message ?: "Phone verification failed. Please try again."
            }
            AuthResult.Error(friendlyMessage)
        }
    }

    override suspend fun resendEmailOtp(email: String): AuthResult {
        return try {
            supabase.auth.resendEmail(
                type = OtpType.Email.SIGNUP,
                email = email.trim()
            )
            AuthResult.Success("")
        } catch (e: Exception) {
            AuthResult.Error(
                e.message ?: "Unable to resend verification code."
            )
        }
    }

    override suspend fun resendPhoneOtp(phone: String): AuthResult {
        return try {
            supabase.auth.resendPhone(
                type = OtpType.Phone.SMS,
                phone = phone.trim()
            )
            AuthResult.Success("")
        } catch (e: Exception) {
            val friendlyMessage = when {
                (e.message?.contains("phone_provider_disabled", ignoreCase = true) == true) ||
                (e.message?.contains("Phone logins are disabled", ignoreCase = true) == true) ->
                    "Phone SMS Provider is disabled in your Supabase Dashboard (Authentication -> Providers -> Phone). Please enable Phone Auth in Supabase Dashboard or use Email Verification."
                (e.message?.contains("rate", ignoreCase = true) == true) ||
                (e.message?.contains("limit", ignoreCase = true) == true) ->
                    "Too many code requests. Please wait a few minutes before trying again."
                else ->
                    e.message ?: "Unable to resend SMS code. Please try again later."
            }
            AuthResult.Error(friendlyMessage)
        }
    }

    override suspend fun changePassword(newPassword: String): AuthResult {
        return try {
            supabase.auth.updateUser {
                this.password = newPassword.trim()
            }
            AuthResult.Success("")
        } catch (e: Exception) {
            AuthResult.Error(
                e.message ?: "Unable to update password. Please check your connection and try again."
            )
        }
    }

    override suspend fun logout() {
        supabase.auth.signOut()
    }

    override fun getCurrentUserId(): String? {
        return supabase.auth.currentUserOrNull()?.id
    }

    override suspend fun getCurrentUserRole(): UserRole {
        val user = supabase.auth.currentUserOrNull() ?: return UserRole.CUSTOMER
        val roleMetadata = user.userMetadata?.get("role")?.jsonPrimitive?.contentOrNull
            ?: user.appMetadata?.get("role")?.jsonPrimitive?.contentOrNull

        if (roleMetadata != null) {
            return UserRole.fromString(roleMetadata)
        }

        return try {
            val userDto = supabase.from("users")
                .select { filter { eq("id", user.id) } }
                .decodeSingleOrNull<UserRoleDto>()
            UserRole.fromString(userDto?.role)
        } catch (_: Exception) {
            UserRole.CUSTOMER
        }
    }

    override fun isSessionValid(minimumDays: Int): Boolean {
        val session = supabase.auth.currentSessionOrNull() ?: return false
        val expiresAt = session.expiresAt
        if (expiresAt != null) {
            val nowSeconds = System.currentTimeMillis() / 1000
            val secondsRemaining = expiresAt.epochSeconds - nowSeconds
            val daysRemaining = secondsRemaining / (24 * 3600)
            return daysRemaining >= minimumDays
        }
        return supabase.auth.currentUserOrNull() != null
    }
}
