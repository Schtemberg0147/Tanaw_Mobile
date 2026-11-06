package com.tanaw.app.feature.workspace

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal object CustomerProfileRules {
    const val MSG_PROFILE_SAVED = "Profile saved."
    const val MSG_PROFILE_SAVED_REFRESH_FAILED = "Profile saved. Refresh to sync latest office records."

    /**
     * Optimistically updates the user's profile and customer records in the cached
     * workspace state so that UI components immediately reflect the saved values even
     * if a subsequent workspace reload fails or times out.
     */
    fun optimisticUpdate(
        data: JsonObject?,
        name: String,
        phone: String,
        address: String,
        city: String
    ): JsonObject? {
        if (data == null) return null
        val updates = mutableMapOf<String, JsonElement>()
        val p = data.obj("profile")
        updates["profile"] = JsonObject(p + mapOf(
            "full_name" to JsonPrimitive(name.trim()),
            "phone" to JsonPrimitive(phone.trim())
        ))
        val customer = data["customer"] as? JsonObject
        if (customer != null) {
            updates["customer"] = JsonObject(customer + mapOf(
                "name" to JsonPrimitive(name.trim()),
                "phone" to JsonPrimitive(phone.trim()),
                "address" to JsonPrimitive(address.trim()),
                "city" to JsonPrimitive(city.trim())
            ))
        }
        return JsonObject(data + updates)
    }

    fun resolveRefreshMessage(currentMessage: String?): String {
        return if (currentMessage == null || currentMessage == MSG_PROFILE_SAVED) {
            MSG_PROFILE_SAVED
        } else {
            currentMessage
        }
    }

    fun validateName(name: String): Boolean = name.trim().length in 2..200
    fun validateAddress(address: String): Boolean = address.trim().length <= 500
    fun validateCity(city: String): Boolean = city.trim().length <= 100

    suspend fun saveProfile(
        mutate: suspend () -> Unit,
        current: () -> Boolean,
        onOptimisticUpdate: () -> Unit,
        refresh: suspend () -> Unit,
        onRefreshFailed: () -> Unit
    ) {
        ConfirmedMutation.run(
            mutate = mutate,
            current = current,
            acknowledge = onOptimisticUpdate,
            refresh = refresh,
            refreshFailed = onRefreshFailed
        )
    }
}
