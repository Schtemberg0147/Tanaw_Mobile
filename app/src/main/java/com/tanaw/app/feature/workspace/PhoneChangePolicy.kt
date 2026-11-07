package com.tanaw.app.feature.workspace

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class PendingProfileEdit(
    val name: String,
    val phone: String,
    val address: String,
    val city: String
)

/**
 * Policy governing verification and synchronization of user phone changes.
 * Ensures auth.users.phone and public.users/customers.phone remain synchronized,
 * maintaining SMS notification eligibility via the signed TextBee hook.
 */
internal object PhoneChangePolicy {
    const val CHALLENGE_AGE = 15 * 60 * 1000L // 15 minutes

    /**
     * Determines whether the requested phone number differs from the user's currently verified phone.
     * All numbers are normalized via [MobileValidation.phone].
     *
     * Returns false if the requested phone matches the existing profile contact or verified auth phone.
     * Returns true if a new or different phone number is requested.
     */
    fun isPhoneChanged(
        currentAuthPhone: String?,
        currentProfilePhone: String?,
        requestedPhone: String
    ): Boolean {
        val req = MobileValidation.phone(requestedPhone) ?: requestedPhone.trim()
        if (req.isBlank()) return false

        val auth = currentAuthPhone?.let { MobileValidation.phone(it) ?: it.trim() }
        val profile = currentProfilePhone?.let { MobileValidation.phone(it) ?: it.trim() }

        // If requested phone matches the existing profile phone, it hasn't changed in this profile edit.
        if (profile != null && req.equals(profile, ignoreCase = true)) {
            return false
        }
        // If requested phone matches the verified auth phone, it is already verified in Supabase Auth.
        if (auth != null && req.equals(auth, ignoreCase = true)) {
            return false
        }
        return true
    }

    /**
     * Prepares MobileState to enter the phone change OTP verification state.
     */
    fun begin(
        state: MobileState,
        owner: String,
        newPhone: String,
        pendingProfile: PendingProfileEdit
    ): MobileState {
        require(owner.isNotBlank()) { "OWNER_REQUIRED" }
        require(newPhone.isNotBlank()) { "PHONE_REQUIRED" }
        return state.copy(
            phoneChangeTarget = newPhone,
            phoneChangePendingName = pendingProfile.name,
            phoneChangePendingAddress = pendingProfile.address,
            phoneChangePendingCity = pendingProfile.city,
            resendSeconds = 60,
            error = null,
            message = null
        )
    }

    /**
     * Serializes a phone change challenge to be encrypted and persisted in WorkspaceRecoveryStore.
     */
    fun challenge(
        owner: String,
        newPhone: String,
        pendingProfile: PendingProfileEdit,
        now: Long
    ): JsonObject {
        require(owner.isNotBlank())
        require(newPhone.isNotBlank())
        return buildJsonObject {
            put("owner", owner)
            put("target", newPhone)
            put("name", pendingProfile.name)
            put("phone", pendingProfile.phone)
            put("address", pendingProfile.address)
            put("city", pendingProfile.city)
            put("created", now)
        }
    }

    /**
     * Validates whether a stored challenge is valid for restoration for this owner and age limit.
     */
    fun canRestore(data: JsonObject, owner: String, now: Long): Boolean {
        val age = now - (data.value("created").toLongOrNull() ?: return false)
        return age in 0..CHALLENGE_AGE &&
                data.value("owner") == owner &&
                data.value("target").isNotBlank()
    }

    /**
     * Deserializes pending profile fields from a stored challenge.
     */
    fun parsePendingProfile(data: JsonObject): PendingProfileEdit {
        return PendingProfileEdit(
            name = data.value("name"),
            phone = data.value("phone"),
            address = data.value("address"),
            city = data.value("city")
        )
    }

    /**
     * Restores the active phone change challenge into MobileState.
     */
    fun restore(state: MobileState, data: JsonObject): MobileState {
        return state.copy(
            phoneChangeTarget = data.value("target"),
            phoneChangePendingName = data.value("name"),
            phoneChangePendingAddress = data.value("address"),
            phoneChangePendingCity = data.value("city"),
            resendSeconds = 0,
            error = null
        )
    }

    /**
     * Clears all phone change state when cancelled.
     */
    fun cancel(state: MobileState): MobileState {
        return state.copy(
            phoneChangeTarget = null,
            phoneChangePendingName = null,
            phoneChangePendingAddress = null,
            phoneChangePendingCity = null,
            error = null
        )
    }
}
