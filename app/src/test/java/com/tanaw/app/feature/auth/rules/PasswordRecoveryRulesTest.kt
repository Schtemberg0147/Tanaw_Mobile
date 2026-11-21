package com.tanaw.app.feature.auth.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordRecoveryRulesTest {

    private val projectHost = "example.supabase.co"

    @Test
    fun isPhoneTargetDetectsPhilippineNumbersOnly() {
        assertTrue(PasswordRecoveryRules.isPhoneTarget("+639123456789"))
        assertTrue(PasswordRecoveryRules.isPhoneTarget("09995130476"))
        assertFalse(PasswordRecoveryRules.isPhoneTarget("driver@gmail.com"))
        assertFalse(PasswordRecoveryRules.isPhoneTarget(""))
    }

    @Test
    fun canVerifyRecoveryCodeValidatesPhoneAndEmailFormatsStrictly() {
        assertTrue(PasswordRecoveryRules.canVerifyRecoveryCode("123456", "+639123456789", false, projectHost))
        assertFalse(PasswordRecoveryRules.canVerifyRecoveryCode("12345", "+639123456789", false, projectHost))
        assertFalse(PasswordRecoveryRules.canVerifyRecoveryCode("abcdef1234567890abcdef1234567890", "+639123456789", false, projectHost))

        assertTrue(PasswordRecoveryRules.canVerifyRecoveryCode("654321", "user@gmail.com", true, projectHost))
        assertFalse(PasswordRecoveryRules.canVerifyRecoveryCode("65432", "user@gmail.com", true, projectHost))
        assertTrue(
            PasswordRecoveryRules.canVerifyRecoveryCode(
                "tanaw://auth/callback?token_hash=abcdef1234567890abcdef1234567890&type=recovery",
                "user@gmail.com",
                true,
                projectHost
            )
        )

        assertTrue(
            PasswordRecoveryRules.canVerifyRecoveryCode(
                "abcdef1234567890abcdef1234567890",
                "driver@company.com",
                false,
                projectHost
            )
        )
        assertTrue(
            PasswordRecoveryRules.canVerifyRecoveryCode(
                "tanaw://auth/callback?code=pkce-code-123&type=recovery",
                "driver@company.com",
                false,
                projectHost
            )
        )
        assertFalse(
            PasswordRecoveryRules.canVerifyRecoveryCode(
                "tanaw://auth/callback?token_hash=abcdef1234567890abcdef1234567890&type=signup",
                "driver@company.com",
                false,
                projectHost
            )
        )
    }

    @Test
    fun recoveryStepAdvancesOnlyAfterSessionPreVerification() {
        assertEquals(PasswordRecoveryRules.PAGE_VERIFY_CODE, PasswordRecoveryRules.recoveryStepForCode(null))
        assertEquals(PasswordRecoveryRules.PAGE_VERIFY_CODE, PasswordRecoveryRules.recoveryStepForCode("123456"))
        assertEquals(PasswordRecoveryRules.PAGE_RESET_PASSWORD, PasswordRecoveryRules.recoveryStepForCode("session"))
    }

    @Test
    fun shouldSignOutOnCancelOnlyWhenSignedOutResetReachedPasswordStep() {
        assertTrue(PasswordRecoveryRules.shouldSignOutOnCancel(PasswordRecoveryRules.PAGE_RESET_PASSWORD, null))
        assertFalse(PasswordRecoveryRules.shouldSignOutOnCancel(PasswordRecoveryRules.PAGE_VERIFY_CODE, null))
        assertFalse(PasswordRecoveryRules.shouldSignOutOnCancel(PasswordRecoveryRules.PAGE_RESET_PASSWORD, "user-1"))
    }

    @Test
    fun validateVerifiedPasswordUpdateEnforcesConnectionLengthAndMatch() {
        assertNull(PasswordRecoveryRules.validateVerifiedPasswordUpdate("Password1!", "Password1!", isBusy = false, isOffline = false))
        assertNotNull(PasswordRecoveryRules.validateVerifiedPasswordUpdate("Password1!", "Password1!", isBusy = true, isOffline = false))
        assertNotNull(PasswordRecoveryRules.validateVerifiedPasswordUpdate("Password1!", "Password1!", isBusy = false, isOffline = true))
        assertNotNull(PasswordRecoveryRules.validateVerifiedPasswordUpdate("short", "short", isBusy = false, isOffline = false))
        assertNotNull(PasswordRecoveryRules.validateVerifiedPasswordUpdate("Password1!", "Password2!", isBusy = false, isOffline = false))
    }
}
