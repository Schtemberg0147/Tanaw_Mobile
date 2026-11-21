package com.tanaw.app.feature.auth.rules

import com.tanaw.app.core.deeplink.DeepLinkParser
import com.tanaw.app.core.deeplink.DeepLinkResult
import com.tanaw.app.core.validation.MobileValidation

/**
 * Pure-Kotlin state and validation rules for the two-step password recovery flow:
 * Step 2 ("recover_code": verify OTP/token on the server) -> Step 3 ("recover_password": set new password).
 */
internal object PasswordRecoveryRules {
    const val PAGE_REQUEST = "recover"
    const val PAGE_VERIFY_CODE = "recover_code"
    const val PAGE_RESET_PASSWORD = "recover_password"

    private val OTP_6_TO_8_REGEX = Regex("^[0-9]{6,8}$")
    private val TOKEN_HASH_REGEX = Regex("^[A-Za-z0-9_-]{20,500}$")

    fun isPhoneTarget(target: String): Boolean = MobileValidation.phone(target) != null

    fun isSessionPreVerified(code: String?): Boolean = code == "session"

    fun recoveryStepForCode(code: String?): String =
        if (isSessionPreVerified(code)) PAGE_RESET_PASSWORD else PAGE_VERIFY_CODE

    fun shouldSignOutOnCancel(page: String, passwordChangeOwner: String?): Boolean =
        page == PAGE_RESET_PASSWORD && passwordChangeOwner == null

    fun canVerifyRecoveryCode(
        rawCode: String,
        target: String,
        useGmailOtpBackend: Boolean,
        projectHost: String
    ): Boolean {
        val trimmed = rawCode.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed == "session") return true
        if (isPhoneTarget(target)) {
            return OTP_6_TO_8_REGEX.matches(trimmed)
        }
        if (useGmailOtpBackend) {
            return GmailOtpPasswordRules.isValidOtp(trimmed) || isRecoveryDeepLinkOrToken(trimmed, projectHost)
        }
        return OTP_6_TO_8_REGEX.matches(trimmed) || isRecoveryDeepLinkOrToken(trimmed, projectHost)
    }

    fun isRecoveryDeepLinkOrToken(rawCode: String, projectHost: String): Boolean {
        val trimmed = rawCode.trim()
        if (TOKEN_HASH_REGEX.matches(trimmed)) return true
        return when (val parsed = DeepLinkParser.parse(trimmed, projectHost)) {
            is DeepLinkResult.Otp -> parsed.isRecovery
            is DeepLinkResult.Code -> parsed.isRecovery
            is DeepLinkResult.Session -> parsed.isRecovery
            else -> false
        }
    }

    fun validateVerifiedPasswordUpdate(
        password: String,
        confirm: String,
        isBusy: Boolean,
        isOffline: Boolean
    ): String? {
        if (isBusy) return "Please wait for the current request to finish."
        if (isOffline) return "Reconnect to the internet before updating your password."
        if (password.length < GmailOtpPasswordRules.MIN_PASSWORD_LENGTH) {
            return "New password must be at least ${GmailOtpPasswordRules.MIN_PASSWORD_LENGTH} characters."
        }
        if (password != confirm) return "Passwords do not match."
        return null
    }
}
