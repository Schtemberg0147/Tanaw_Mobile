package com.tanaw.app.feature.auth.rules

import org.junit.Assert.*
import org.junit.Test

class PhoneSignupRecoveryRulesTest {

    @Test fun detectsPhoneNotConfirmedDirectlyAndInCauseChain() {
        assertTrue(PhoneSignupRecoveryRules.isPhoneNotConfirmed(IllegalStateException("phone_not_confirmed")))
        assertTrue(PhoneSignupRecoveryRules.isPhoneNotConfirmed(RuntimeException("Phone not confirmed by user")))
        val nested = RuntimeException("Auth failed", IllegalStateException("code: PHONE_NOT_CONFIRMED"))
        assertTrue(PhoneSignupRecoveryRules.isPhoneNotConfirmed(nested))

        assertFalse(PhoneSignupRecoveryRules.isPhoneNotConfirmed(null))
        assertFalse(PhoneSignupRecoveryRules.isPhoneNotConfirmed(IllegalStateException("invalid_credentials")))
        assertFalse(PhoneSignupRecoveryRules.isPhoneNotConfirmed(IllegalStateException("phone_exists")))
    }

    @Test fun detectsSmsRateLimiting() {
        assertTrue(PhoneSignupRecoveryRules.isSmsRateLimited(RuntimeException("over_sms_send_rate_limit")))
        assertTrue(PhoneSignupRecoveryRules.isSmsRateLimited(RuntimeException("429 Too Many Requests")))
        assertTrue(PhoneSignupRecoveryRules.isSmsRateLimited(RuntimeException("outer", IllegalStateException("Rate limit exceeded"))))

        assertFalse(PhoneSignupRecoveryRules.isSmsRateLimited(null))
        assertFalse(PhoneSignupRecoveryRules.isSmsRateLimited(RuntimeException("sms_send_failed")))
    }

    @Test fun countdownStartsOnlyWhenResendSucceededOrRateLimited() {
        assertTrue(PhoneSignupRecoveryRules.shouldStartCountdown(resendSucceeded = true, rateLimited = false))
        assertTrue(PhoneSignupRecoveryRules.shouldStartCountdown(resendSucceeded = false, rateLimited = true))
        assertTrue(PhoneSignupRecoveryRules.shouldStartCountdown(resendSucceeded = true, rateLimited = true))
        assertFalse(PhoneSignupRecoveryRules.shouldStartCountdown(resendSucceeded = false, rateLimited = false))
    }

    @Test fun buildsClearResumeMessagesForEachOutcome() {
        val sent = PhoneSignupRecoveryRules.buildResumeMessage(
            resendSucceeded = true,
            rateLimited = false,
            challengeSaved = true
        )
        assertTrue(sent.contains("not verified yet"))
        assertTrue(sent.contains("new SMS verification code"))
        assertFalse(sent.contains("Keep this screen open"))

        val limited = PhoneSignupRecoveryRules.buildResumeMessage(
            resendSucceeded = false,
            rateLimited = true,
            challengeSaved = true
        )
        assertTrue(limited.contains("sent earlier"))
        assertTrue(limited.contains("wait"))

        val fallbackUnsaved = PhoneSignupRecoveryRules.buildResumeMessage(
            resendSucceeded = false,
            rateLimited = false,
            challengeSaved = false
        )
        assertTrue(fallbackUnsaved.contains("Resend SMS"))
        assertTrue(fallbackUnsaved.contains("Keep this screen open"))
    }
}
