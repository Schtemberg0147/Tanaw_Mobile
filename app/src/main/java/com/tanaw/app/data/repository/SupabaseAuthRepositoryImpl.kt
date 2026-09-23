package com.tanaw.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import javax.inject.Inject

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
                AuthResult.Success(user.id)
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
        role: String
    ): AuthResult {
        return try {
            val user = supabase.auth.signUpWith(Email) {
                this.email = email.trim()
                this.password = password
            }

            AuthResult.Success(user?.id ?:"")

        } catch (e: Exception) {
            AuthResult.Error(
                e.message ?: "Unable to create the account."
            )
        }
    }

    override suspend fun logout() {
        supabase.auth.signOut()
    }

    override fun getCurrentUserId(): String? {
        return supabase.auth.currentUserOrNull()?.id
    }
}