package com.tanaw.app.feature.auth.rules

/**
 * Pure-Kotlin validation rules for Gmail OTP password change / reset.
 * Keeps client-side checks deterministic and unit-testable without ViewModel dependencies.
 */
internal object GmailOtpPasswordRules {
    const val OTP_LENGTH = 6
    const val MIN_PASSWORD_LENGTH = 8
    const val RESEND_COOLDOWN_SECONDS = 60

    private val GMAIL_REGEX = Regex("^[a-z0-9._%+-]+@gmail\\.com$")
    private val OTP_REGEX = Regex("^\\d{6}$")

    fun normalizeEmail(raw: String): String = raw.trim().lowercase()

    fun isGmail(raw: String): Boolean {
        val normalized = normalizeEmail(raw)
        return normalized.isNotEmpty() &&
            !raw.contains('\u0000') &&
            GMAIL_REGEX.matches(normalized)
    }

    fun isValidOtp(raw: String): Boolean = OTP_REGEX.matches(raw.trim())

    fun isValidNewPassword(password: String, confirmPassword: String): Boolean =
        password.length >= MIN_PASSWORD_LENGTH && password == confirmPassword

    fun shouldUseGmailOtpBackend(target: String, baseUrl: String): Boolean =
        baseUrl.isNotBlank() && isGmail(target)

    fun validateSendOtp(
        email: String,
        resendCooldownSeconds: Int,
        isBusy: Boolean
    ): String? {
        if (isBusy) return "Please wait for the current request to finish."
        if (resendCooldownSeconds > 0) {
            return "Please wait ${resendCooldownSeconds}s before requesting another code."
        }
        if (!isGmail(email)) {
            return "Email must be a valid @gmail.com address."
        }
        return null
    }

    fun validateReset(
        email: String,
        otp: String,
        newPassword: String,
        confirmPassword: String,
        isBusy: Boolean
    ): String? {
        if (isBusy) return "Please wait for the current request to finish."
        return when {
            !isGmail(email) -> "Email must be a valid @gmail.com address."
            !isValidOtp(otp) -> "Enter the 6-digit verification code sent to your Gmail."
            newPassword.length < MIN_PASSWORD_LENGTH ->
                "New password must be at least $MIN_PASSWORD_LENGTH characters."
            newPassword != confirmPassword -> "Passwords do not match."
            else -> null
        }
    }
}
