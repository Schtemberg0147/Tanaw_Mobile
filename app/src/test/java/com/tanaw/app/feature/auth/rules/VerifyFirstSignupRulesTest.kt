package com.tanaw.app.feature.auth.rules

import com.tanaw.app.core.error.MobileErrorClassifier
import com.tanaw.app.feature.workspace.viewmodel.value
import org.junit.Assert.*
import org.junit.Test

class VerifyFirstSignupRulesTest {

    @Test fun buildsExpectedPayloadsWithoutMutatingInput() {
        val sendPayload = VerifyFirstSignupRules.sendOtpPayload("+639995130476")
        assertEquals("+639995130476", sendPayload.value("phone"))

        val pending = PendingPhoneSignup(
            firstName = "Juan",
            lastName = "Dela Cruz",
            phone = "+639995130476",
            email = "juan@example.com",
            password = "Password123!",
            address = "123 Mabini St, Cabanatuan",
        )
        val completePayload = VerifyFirstSignupRules.completeSignupPayload(pending, "123456")
        assertEquals("+639995130476", completePayload.value("phone"))
        assertEquals("123456", completePayload.value("code"))
        assertEquals("Juan", completePayload.value("first_name"))
        assertEquals("Dela Cruz", completePayload.value("last_name"))
        assertEquals("juan@example.com", completePayload.value("email"))
        assertEquals("Password123!", completePayload.value("password"))
        assertEquals("123 Mabini St, Cabanatuan", completePayload.value("address"))
    }

    @Test fun mapsBackendErrorCodesToFriendlyClassifierMessages() {
        val dup = VerifyFirstSignupRules.parseEdgeErrorCode(409, """{"error":"phone_exists"}""")
        assertEquals("phone_exists", dup)
        assertTrue(MobileErrorClassifier.friendlyMessage(IllegalStateException(dup)).contains("already registered"))

        val rate = VerifyFirstSignupRules.parseEdgeErrorCode(429, """{"error":"over_sms_send_rate_limit"}""")
        assertEquals("over_sms_send_rate_limit", rate)
        assertTrue(MobileErrorClassifier.friendlyMessage(IllegalStateException(rate)).contains("Wait"))

        val expired = VerifyFirstSignupRules.parseEdgeErrorCode(400, """{"error":"otp_expired"}""")
        assertEquals("otp_expired", expired)
        assertTrue(MobileErrorClassifier.friendlyMessage(IllegalStateException(expired)).contains("invalid or your session expired"))

        val smsErr = VerifyFirstSignupRules.parseEdgeErrorCode(502, """{"error":"sms_send_failed"}""")
        assertEquals("sms_send_failed", smsErr)
        assertTrue(MobileErrorClassifier.friendlyMessage(IllegalStateException(smsErr)).contains("verification SMS"))

        val serverErr = VerifyFirstSignupRules.parseEdgeErrorCode(500, "")
        assertTrue(MobileErrorClassifier.isContractError(IllegalStateException(serverErr)))
    }

    @Test fun parsesSessionTokensAndRejectsMissingTokens() {
        val (access, refresh) = VerifyFirstSignupRules.parseSessionTokens(
            """{"access_token":"jwt-access","refresh_token":"jwt-refresh"}""",
        )
        assertEquals("jwt-access", access)
        assertEquals("jwt-refresh", refresh)

        assertThrows(IllegalArgumentException::class.java) {
            VerifyFirstSignupRules.parseSessionTokens("""{"access_token":"only-access"}""")
        }
    }
}
