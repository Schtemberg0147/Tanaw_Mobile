package com.tanaw.app.feature.auth

import com.tanaw.app.feature.workspace.MobileValidation
import org.junit.Assert.*
import org.junit.Test

class AuthFormValidationTest {

    @Test
    fun evaluatePasswordStrengthClassifiesCorrectly() {
        assertEquals(PasswordStrength.EMPTY, evaluatePasswordStrength(""))
        assertEquals(PasswordStrength.WEAK, evaluatePasswordStrength("short"))
        assertEquals(PasswordStrength.WEAK, evaluatePasswordStrength("password"))
        assertEquals(PasswordStrength.FAIR, evaluatePasswordStrength("password12"))
        assertEquals(PasswordStrength.STRONG, evaluatePasswordStrength("Password12!"))
    }

    @Test
    fun inlineValidationRejectsInvalidPhoneInputs() {
        val validPhones = listOf("09123456789", "9123456789", "+639123456789")
        val invalidPhones = listOf("12345", "08123456789", "0912345678", "091234567899", "abcdefghijk")

        validPhones.forEach { phone ->
            assertNotNull("Expected valid PH phone for $phone", MobileValidation.phone(phone))
        }
        invalidPhones.forEach { phone ->
            assertNull("Expected invalid phone for $phone", MobileValidation.phone(phone))
        }
    }

    @Test
    fun inlineValidationRejectsInvalidEmailInputs() {
        assertTrue(MobileValidation.email("user@example.com"))
        assertTrue(MobileValidation.email("user.name+tag@domain.co"))
        assertFalse(MobileValidation.email("user@"))
        assertFalse(MobileValidation.email("@domain.com"))
        assertFalse(MobileValidation.email("user@domain"))
        assertFalse(MobileValidation.email("user domain.com"))
    }

    @Test
    fun passwordLengthAndMismatchRules() {
        assertFalse("Password under 8 chars must fail", MobileValidation.password("1234567", "1234567"))
        assertTrue("Password with 8+ chars matching confirmation must succeed", MobileValidation.password("12345678", "12345678"))
        assertFalse("Mismatched passwords must fail", MobileValidation.password("Password123", "Password456"))
    }

    @Test
    fun emailValidationRejectsCrlfInjectionAndNullBytes() {
        // CRLF header injection attempts
        assertFalse(MobileValidation.email("user@example.com\r\nBcc: victim@example.com"))
        assertFalse(MobileValidation.email("user\n@example.com"))
        assertFalse(MobileValidation.email("user\r@example.com"))
        // user@example.com\n has whitespace trimmed by MobileValidation.email, test un-trimmed / mid-string
        assertFalse(MobileValidation.email("user@\nexample.com"))

        // Null-byte and whitespace attempts
        assertFalse(MobileValidation.email("user\u0000@example.com"))
        assertFalse(MobileValidation.email("user @example.com"))
        assertFalse(MobileValidation.email("user@ example.com"))
        assertFalse(MobileValidation.email("user@example .com"))
        assertFalse(MobileValidation.email(""))
        assertFalse(MobileValidation.email("   "))
    }

    @Test
    fun passwordStrengthBoundariesAndWhitespaceEdgeCases() {
        // Whitespace-only passwords: 8 spaces has length >= 8 (score 1) and !it.isLetterOrDigit() (score 2) -> FAIR
        assertEquals(PasswordStrength.FAIR, evaluatePasswordStrength("        "))
        // 7 spaces has length < 8 (score 0) and !it.isLetterOrDigit() (score 1) -> WEAK
        assertEquals(PasswordStrength.WEAK, evaluatePasswordStrength("       "))

        // Length < 8 with upper, digit, symbol: 3 points -> STRONG
        assertEquals(PasswordStrength.STRONG, evaluatePasswordStrength("Abc1!"))
        // 7 lowercase chars: 0 points -> WEAK
        assertEquals(PasswordStrength.WEAK, evaluatePasswordStrength("abcdefg"))
        // 8 lowercase chars: 1 point (length >= 8) -> WEAK
        assertEquals(PasswordStrength.WEAK, evaluatePasswordStrength("abcdefgh"))
        // 8 chars + uppercase: 2 points -> FAIR
        assertEquals(PasswordStrength.FAIR, evaluatePasswordStrength("Abcdefgh"))
        // 8 chars + uppercase + digit: 3 points -> STRONG
        assertEquals(PasswordStrength.STRONG, evaluatePasswordStrength("Abcdefg1"))
    }

    @Test
    fun nameTrimmingAndUnicodeHandling() {
        // Multi-byte UTF-16 names (ñ, accents, emojis)
        val validSpanishName = "José Peña"
        val validWithEmoji = "Maria Santos \uD83D\uDE9A"
        assertTrue(com.tanaw.app.feature.workspace.CustomerProfileRules.validateName(validSpanishName))
        assertTrue(com.tanaw.app.feature.workspace.CustomerProfileRules.validateName(validWithEmoji))

        // Whitespace and length boundary checks
        assertFalse("Whitespace-only name must be invalid", com.tanaw.app.feature.workspace.CustomerProfileRules.validateName("   "))
        assertFalse("Empty name must be invalid", com.tanaw.app.feature.workspace.CustomerProfileRules.validateName(""))
        assertFalse("Single-character name must be invalid", com.tanaw.app.feature.workspace.CustomerProfileRules.validateName("A"))
        assertTrue("Two-character name must be valid", com.tanaw.app.feature.workspace.CustomerProfileRules.validateName("Ed"))
        assertTrue("Name with surrounding whitespace must be trimmed and valid", com.tanaw.app.feature.workspace.CustomerProfileRules.validateName("  Ed  "))
        assertTrue("200-character name must be valid", com.tanaw.app.feature.workspace.CustomerProfileRules.validateName("A".repeat(200)))
        assertFalse("201-character name must be invalid", com.tanaw.app.feature.workspace.CustomerProfileRules.validateName("A".repeat(201)))
    }
}
