package com.tanaw.app.feature.auth.rules

import com.tanaw.app.feature.workspace.viewmodel.value
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

internal data class PendingPhoneSignup(
    val firstName: String,
    val lastName: String,
    val phone: String,
    val email: String,
    val password: String,
    val address: String,
)

/**
 * Pure-Kotlin request/response mapping for Request C13 verify-first phone signup
 * (`mobile-send-signup-otp` -> `mobile-complete-signup`).
 */
internal object VerifyFirstSignupRules {

    fun sendOtpPayload(phone: String): JsonObject = buildJsonObject {
        put("phone", phone)
    }

    fun completeSignupPayload(pending: PendingPhoneSignup, code: String): JsonObject = buildJsonObject {
        put("phone", pending.phone)
        put("code", code)
        put("first_name", pending.firstName)
        put("last_name", pending.lastName)
        put("email", pending.email)
        put("password", pending.password)
        put("address", pending.address)
    }

    fun parseEdgeErrorCode(statusCode: Int, rawBody: String): String {
        val bodyError = runCatching {
            Json.parseToJsonElement(rawBody).jsonObject.value("error").trim()
        }.getOrNull().orEmpty()

        if (bodyError.isNotBlank()) {
            return when {
                statusCode >= 500 && !bodyError.contains("sms_send_failed", ignoreCase = true) ->
                    "HTTP $statusCode $bodyError"
                else -> bodyError
            }
        }

        return when (statusCode) {
            409 -> "phone_exists"
            429 -> "over_sms_send_rate_limit"
            400, 401 -> "invalid_token"
            in 500..599 -> "HTTP $statusCode unexpected_failure"
            else -> "unexpected_failure"
        }
    }

    fun parseSessionTokens(rawBody: String): Pair<String, String> {
        val tokens = Json.parseToJsonElement(rawBody).jsonObject
        val accessToken = tokens.value("access_token")
        val refreshToken = tokens.value("refresh_token")
        require(accessToken.isNotBlank() && refreshToken.isNotBlank()) {
            "MISSING_SESSION_TOKENS"
        }
        return accessToken to refreshToken
    }
}
