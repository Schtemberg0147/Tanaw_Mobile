package com.tanaw.app.feature.workspace

/** Never show raw Auth exceptions: they can include submitted request data. */
object MobileAuthErrors {
    fun message(raw: String): String? = when {
        raw.contains("LOGIN_RATE_LIMIT") -> "Too many sign-in attempts. Wait five minutes before trying again."
        raw.contains("LOGIN_UNAVAILABLE") -> "Sign-in is temporarily unavailable. Try again later."
        raw.contains("phone_provider_disabled",true) -> "Phone signup is not enabled yet. Contact the office."
        raw.contains("phone_exists",true) -> "An account with this phone number already exists. Sign in or reset your password."
        raw.contains("phone_not_confirmed",true) -> "Verify your phone number with the SMS code before signing in."
        raw.contains("over_sms_send_rate_limit",true) -> "Too many SMS codes requested. Wait before trying again."
        raw.contains("sms_send_failed",true) || raw.contains("hook_timeout",true) || raw.contains("hook_error",true) ->
            "The verification SMS could not be queued. Try again later or contact the office."
        raw.contains("provider is not enabled", true) || Regex("\\bprovider_disabled\\b", RegexOption.IGNORE_CASE).containsMatchIn(raw) ->
            "Google sign-in is not enabled yet. Contact the office or use email sign-in."
        raw.contains("email_address_invalid", true) || Regex("email address.*is invalid", RegexOption.IGNORE_CASE).containsMatchIn(raw) ->
            "This email address was rejected by the email service. Check for typos or use another email address you own."
        raw.contains("email_address_not_authorized", true) ->
            "Verification email is unavailable for this address. Contact the office to enable email delivery."
        raw.contains("user_already_exists", true) || raw.contains("email_exists", true) || raw.contains("already registered", true) ->
            "An account already exists. Sign in or reset your password."
        raw.contains("weak_password", true) ->
            "This password does not meet the account security requirements. Use a stronger password."
        raw.contains("signup_disabled", true) || raw.contains("email_provider_disabled", true) ->
            "Account registration is currently unavailable. Contact the office."
        raw.contains("over_email_send_rate_limit", true) ->
            "Too many verification emails have been requested. Wait before trying again."
        raw.contains("Error sending confirmation", true) || raw.contains("Error sending verification", true) ->
            "The verification email could not be sent. Try again later or contact the office."
        raw.contains("Database error saving new user", true) || raw.contains("unexpected_failure", true) ->
            "The account service could not complete registration. Try again later or contact the office."
        else -> null
    }
}
