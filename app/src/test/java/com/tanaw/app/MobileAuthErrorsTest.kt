package com.tanaw.app

import com.tanaw.app.feature.workspace.MobileAuthErrors
import org.junit.Assert.*
import org.junit.Test

class MobileAuthErrorsTest {
    @Test fun phoneFailuresExplainPhoneRecoveryWithoutPrivateValues() {
        assertTrue(MobileAuthErrors.message("phone_exists private payload")!!.contains("phone number"))
        assertTrue(MobileAuthErrors.message("phone_not_confirmed")!!.contains("SMS code"))
        assertTrue(MobileAuthErrors.message("phone_provider_disabled")!!.contains("not enabled"))
        assertTrue(MobileAuthErrors.message("over_sms_send_rate_limit")!!.contains("Wait"))
        assertFalse(MobileAuthErrors.message("user_already_exists")!!.contains("email"))
        assertFalse(MobileAuthErrors.message("phone_exists private payload")!!.contains("private"))
    }
    @Test fun rejectedEmailIsNotReportedAsConnectionFailure() {
        val result = MobileAuthErrors.message("email_address_invalid: Email address \"private@example.com\" is invalid")!!
        assertTrue(result.contains("rejected"))
        assertFalse(result.contains("private@example.com"))
        assertEquals(result, MobileAuthErrors.message("Email address \"another@example.com\" is invalid"))
    }

    @Test fun emailConfigurationAndRateLimitsHaveDifferentRecoveryMessages() {
        assertTrue(MobileAuthErrors.message("email_address_not_authorized")!!.contains("office"))
        assertTrue(MobileAuthErrors.message("over_email_send_rate_limit")!!.contains("Wait"))
        assertTrue(MobileAuthErrors.message("weak_password")!!.contains("stronger"))
        assertTrue(MobileAuthErrors.message("user_already_exists")!!.contains("Sign in"))
    }

    @Test fun unrelatedErrorsKeepExistingHandlingWithoutExposingPayloads() {
        assertTrue(MobileAuthErrors.message("provider_disabled")!!.contains("Google"))
        assertTrue(MobileAuthErrors.message("email_provider_disabled")!!.contains("registration"))
        assertNull(MobileAuthErrors.message("CAPACITY_EXCEEDED request=private"))
        assertNull(MobileAuthErrors.message("network unreachable"))
        assertFalse(MobileAuthErrors.message("Database error saving new user: private payload")!!.contains("private"))
    }
}
