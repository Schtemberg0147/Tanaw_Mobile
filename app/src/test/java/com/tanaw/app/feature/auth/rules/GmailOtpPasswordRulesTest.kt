package com.tanaw.app.feature.auth.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GmailOtpPasswordRulesTest {

    @Test
    fun isGmailAcceptsValidGmailAndRejectsOtherDomains() {
        assertTrue(GmailOtpPasswordRules.isGmail("driver@gmail.com"))
        assertTrue(GmailOtpPasswordRules.isGmail("  Driver.Test+1@GMAIL.COM  "))
        assertFalse(GmailOtpPasswordRules.isGmail("driver@yahoo.com"))
        assertFalse(GmailOtpPasswordRules.isGmail("driver@googlemail.com"))
        assertFalse(GmailOtpPasswordRules.isGmail("driver@gmail.com.ph"))
        assertFalse(GmailOtpPasswordRules.isGmail("@gmail.com"))
        assertFalse(GmailOtpPasswordRules.isGmail(""))
        assertFalse(GmailOtpPasswordRules.isGmail("driver\u0000@gmail.com"))
    }

    @Test
    fun isValidOtpRequiresExactlySixDigits() {
        assertTrue(GmailOtpPasswordRules.isValidOtp("123456"))
        assertTrue(GmailOtpPasswordRules.isValidOtp(" 000001 "))
        assertFalse(GmailOtpPasswordRules.isValidOtp("12345"))
        assertFalse(GmailOtpPasswordRules.isValidOtp("1234567"))
        assertFalse(GmailOtpPasswordRules.isValidOtp("12345a"))
        assertFalse(GmailOtpPasswordRules.isValidOtp(""))
    }

    @Test
    fun validateSendOtpEnforcesGmailCooldownAndBusyState() {
        assertNull(GmailOtpPasswordRules.validateSendOtp("user@gmail.com", resendCooldownSeconds = 0, isBusy = false))
        assertNotNull(GmailOtpPasswordRules.validateSendOtp("user@outlook.com", resendCooldownSeconds = 0, isBusy = false))
        assertNotNull(GmailOtpPasswordRules.validateSendOtp("user@gmail.com", resendCooldownSeconds = 42, isBusy = false))
        assertNotNull(GmailOtpPasswordRules.validateSendOtp("user@gmail.com", resendCooldownSeconds = 0, isBusy = true))
    }

    @Test
    fun validateResetEnforcesGmailSixDigitOtpAndMatchingEightCharPassword() {
        assertNull(
            GmailOtpPasswordRules.validateReset(
                email = "user@gmail.com",
                otp = "654321",
                newPassword = "password123",
                confirmPassword = "password123",
                isBusy = false
            )
        )
        assertNotNull(
            GmailOtpPasswordRules.validateReset(
                email = "user@yahoo.com",
                otp = "654321",
                newPassword = "password123",
                confirmPassword = "password123",
                isBusy = false
            )
        )
        assertNotNull(
            GmailOtpPasswordRules.validateReset(
                email = "user@gmail.com",
                otp = "65432",
                newPassword = "password123",
                confirmPassword = "password123",
                isBusy = false
            )
        )
        assertNotNull(
            GmailOtpPasswordRules.validateReset(
                email = "user@gmail.com",
                otp = "654321",
                newPassword = "short",
                confirmPassword = "short",
                isBusy = false
            )
        )
        assertNotNull(
            GmailOtpPasswordRules.validateReset(
                email = "user@gmail.com",
                otp = "654321",
                newPassword = "password123",
                confirmPassword = "password124",
                isBusy = false
            )
        )
    }

    @Test
    fun shouldUseGmailOtpBackendRequiresBothBaseUrlAndGmailTarget() {
        assertTrue(GmailOtpPasswordRules.shouldUseGmailOtpBackend("user@gmail.com", "https://api.example.com"))
        assertFalse(GmailOtpPasswordRules.shouldUseGmailOtpBackend("user@gmail.com", ""))
        assertFalse(GmailOtpPasswordRules.shouldUseGmailOtpBackend("+639123456789", "https://api.example.com"))
        assertEquals("user@gmail.com", GmailOtpPasswordRules.normalizeEmail("  User@GMAIL.com "))
    }
}
