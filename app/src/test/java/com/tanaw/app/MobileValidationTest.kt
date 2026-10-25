package com.tanaw.app

import com.tanaw.app.feature.workspace.MobileValidation
import org.junit.Assert.*
import org.junit.Test

class MobileValidationTest {
    @Test fun verificationLinksMustMatchProjectHostPathAndPurpose() {
        val host="test.supabase.co";val token="abcdefghijklmnopqrstuvwxyz12345"
        assertEquals(token,MobileValidation.linkToken("https://$host/auth/v1/verify?token=$token&type=recovery",host,"recovery"))
        assertNull(MobileValidation.linkToken("https://evil.example/auth/v1/verify?token=$token&type=recovery",host,"recovery"))
        assertNull(MobileValidation.linkToken("https://$host/auth/v1/verify?token=$token&type=signup",host,"recovery"))
        assertNull(MobileValidation.linkToken("http://$host/auth/v1/verify?token=$token&type=recovery",host,"recovery"))
        assertNull(MobileValidation.linkToken("https://$host/other?token=$token&type=recovery",host,"recovery"))
    }
    @Test fun normalizesPhilippineMobileNumbersWithoutInventingDigits() {
        listOf("09123456789","9123456789","+63 912-345-6789").forEach { assertEquals("+639123456789",MobileValidation.phone(it)) }
        listOf("", "0912345678", "+631234567890", "+6391234567890", "abc09123456789").forEach { assertNull(MobileValidation.phone(it)) }
    }
    @Test fun weightRejectsNonFiniteZeroNegativeAndUnboundedInput() {
        listOf("0","-1","NaN","Infinity","1e309","100001","cargo").forEach { assertNull(MobileValidation.weight(it)) }
        assertEquals(1250.5,MobileValidation.weight("1250.5")!!,0.001)
    }
    @Test fun passwordConfirmationRequiredAndSpacesAreSignificant() {
        assertFalse(MobileValidation.password("good-password",""))
        assertFalse(MobileValidation.password("good-password ","good-password"))
        assertTrue(MobileValidation.password(" good-password "," good-password "))
        assertFalse(MobileValidation.password("short","short"))
    }
    @Test fun emailValidationRejectsMissingOrWhitespaceAddresses() {
        assertTrue(MobileValidation.email("person+tag@example.com"))
        listOf("", "a@", "a b@example.com", "a@example").forEach { assertFalse(MobileValidation.email(it)) }
    }
}
