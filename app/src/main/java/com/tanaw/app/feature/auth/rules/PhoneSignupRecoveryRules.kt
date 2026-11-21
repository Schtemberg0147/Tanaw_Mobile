package com.tanaw.app.feature.auth.rules

/**
 * Pure-Kotlin helpers for resuming SMS verification when a phone-registered user
 * signs in before confirming their signup OTP (e.g. after pressing Back on verify_phone).
 */
internal object PhoneSignupRecoveryRules {

    fun isPhoneNotConfirmed(error: Throwable?): Boolean {
        var current = error
        while (current != null) {
            val msg = current.message.orEmpty()
            if (msg.contains("phone_not_confirmed", ignoreCase = true) ||
                msg.contains("phone not confirmed", ignoreCase = true)
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    fun isSmsRateLimited(error: Throwable?): Boolean {
        var current = error
        while (current != null) {
            val msg = current.message.orEmpty()
            if (msg.contains("over_sms_send_rate_limit", ignoreCase = true) ||
                msg.contains("rate", ignoreCase = true) ||
                msg.contains("429")
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    fun shouldStartCountdown(resendSucceeded: Boolean, rateLimited: Boolean): Boolean =
        resendSucceeded || rateLimited

    fun buildResumeMessage(
        resendSucceeded: Boolean,
        rateLimited: Boolean,
        challengeSaved: Boolean
    ): String {
        val base = when {
            resendSucceeded ->
                "Your phone number is not verified yet. A new SMS verification code has been requested."
            rateLimited ->
                "Your phone number is not verified yet. Enter the SMS code sent earlier, or wait before requesting a new one."
            else ->
                "Your phone number is not verified yet. Enter your SMS code or tap Resend SMS."
        }
        return if (challengeSaved) base else "$base Keep this screen open; recovery context could not be saved."
    }
}
