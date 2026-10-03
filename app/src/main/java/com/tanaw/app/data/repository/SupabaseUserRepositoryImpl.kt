package com.tanaw.app.data.repository

import com.tanaw.app.data.model.UserProfile
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject

@Serializable
private data class DbUserDto(
    val id: String = "",
    val email: String? = null,
    val full_name: String? = null,
    val phone: String? = null,
    val role: String? = null,
    val avatar_url: String? = null,
    val created_at: String? = null
)

@Serializable
private data class DbCustomerDto(
    val id: String? = null,
    val customer_code: String? = null,
    val user_id: String? = null,
    val name: String? = null,
    val contact_person: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val city: String? = null,
    val province: String? = "Nueva Ecija",
    val created_at: String? = null
)

class SupabaseUserRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient
) : UserRepository {

    override suspend fun getUserProfile(): Result<UserProfile> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("No logged in user session found."))

            // 1. Fetch public.users record
            val dbUser = try {
                supabase.from("users")
                    .select { filter { eq("id", user.id) } }
                    .decodeList<DbUserDto>()
                    .firstOrNull()
            } catch (_: Exception) {
                null
            }

            // 2. Fetch public.customers record (query by user_id first, then email fallback)
            val dbCustomer = try {
                supabase.from("customers")
                    .select { filter { eq("user_id", user.id) } }
                    .decodeList<DbCustomerDto>()
                    .firstOrNull()
            } catch (_: Exception) {
                null
            } ?: try {
                val userEmail = user.email
                if (!userEmail.isNullOrBlank()) {
                    supabase.from("customers")
                        .select { filter { eq("email", userEmail) } }
                        .decodeList<DbCustomerDto>()
                        .firstOrNull()
                } else null
            } catch (_: Exception) {
                null
            }

            // 3. Auth user metadata as fallback/supplement
            val meta = user.userMetadata
            val metaFullName = meta?.get("full_name")?.jsonPrimitive?.contentOrNull
            val metaFirstName = meta?.get("first_name")?.jsonPrimitive?.contentOrNull
            val metaLastName = meta?.get("last_name")?.jsonPrimitive?.contentOrNull
            val metaPhone = meta?.get("phone")?.jsonPrimitive?.contentOrNull
            val metaCity = meta?.get("city")?.jsonPrimitive?.contentOrNull
            val metaAddress = meta?.get("address")?.jsonPrimitive?.contentOrNull

            val fullName = dbCustomer?.name?.takeIf { it.isNotBlank() }
                ?: dbUser?.full_name?.takeIf { it.isNotBlank() }
                ?: metaFullName?.takeIf { it.isNotBlank() }
                ?: listOfNotNull(metaFirstName, metaLastName).joinToString(" ").trim().takeIf { it.isNotBlank() }
                ?: user.email?.substringBefore("@")
                ?: "User"

            val firstName = metaFirstName
                ?: fullName.split(" ").firstOrNull()
                ?: ""

            val lastName = metaLastName
                ?: fullName.split(" ").drop(1).joinToString(" ")
                ?: ""

            val email = dbCustomer?.email?.takeIf { it.isNotBlank() }
                ?: dbUser?.email?.takeIf { it.isNotBlank() }
                ?: user.email
                ?: ""

            val phone = dbCustomer?.phone?.takeIf { it.isNotBlank() }
                ?: dbUser?.phone?.takeIf { it.isNotBlank() }
                ?: metaPhone
                ?: ""

            val city = dbCustomer?.city?.takeIf { it.isNotBlank() }
                ?: metaCity
                ?: "Cabanatuan City"

            val address = dbCustomer?.address?.takeIf { it.isNotBlank() }
                ?: metaAddress
                ?: "$city, NE"

            val createdAt = dbCustomer?.created_at?.takeIf { it.isNotBlank() }
                ?: dbUser?.created_at?.takeIf { it.isNotBlank() }
                ?: user.createdAt?.toString()
                ?: ""

            val profile = UserProfile(
                id = user.id,
                email = email,
                fullName = fullName,
                firstName = firstName,
                lastName = lastName,
                phone = phone,
                city = city,
                address = address,
                createdAt = createdAt
            )

            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateUserProfile(profile: UserProfile): Result<Unit> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("No logged in user session found."))

            // 1. Update Supabase Auth user metadata
            supabase.auth.updateUser {
                data = buildJsonObject {
                    put("full_name", profile.fullName)
                    put("first_name", profile.firstName)
                    put("last_name", profile.lastName)
                    put("phone", profile.phone)
                    put("city", profile.city)
                    put("address", profile.address)
                }
            }

            // 2. Update public.users record
            try {
                supabase.from("users").update(
                    buildJsonObject {
                        put("full_name", profile.fullName)
                        put("phone", profile.phone)
                    }
                ) {
                    filter { eq("id", user.id) }
                }
            } catch (_: Exception) {}

            // 3. Update public.customers record
            try {
                val userEmail = user.email ?: profile.email
                supabase.from("customers").update(
                    buildJsonObject {
                        put("user_id", user.id)
                        put("name", profile.fullName)
                        put("phone", profile.phone)
                        put("email", profile.email)
                        put("address", profile.address)
                        put("city", profile.city)
                    }
                ) {
                    filter {
                        or {
                            eq("user_id", user.id)
                            if (userEmail.isNotBlank()) eq("email", userEmail)
                        }
                    }
                }
            } catch (_: Exception) {}

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
