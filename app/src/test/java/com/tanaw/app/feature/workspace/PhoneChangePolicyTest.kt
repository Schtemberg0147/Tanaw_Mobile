package com.tanaw.app.feature.workspace

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test

class PhoneChangePolicyTest {

    @Test
    fun isPhoneChangedDetectsUnchangedNumbersAcrossFormats() {
        val verifiedAuth = "+639123456789"
        val savedProfile = "09123456789"

        listOf(
            "09123456789",
            "+639123456789",
            " 09123456789 ",
            "9123456789",
            "639123456789"
        ).forEach { input ->
            assertFalse(
                "Expected unchanged for format: $input",
                PhoneChangePolicy.isPhoneChanged(verifiedAuth, savedProfile, input)
            )
        }
    }

    @Test
    fun isPhoneChangedDetectsChangedNumbers() {
        val verifiedAuth = "+639123456789"
        val savedProfile = "+639123456789"

        assertTrue(
            "Different mobile number must trigger phone change",
            PhoneChangePolicy.isPhoneChanged(verifiedAuth, savedProfile, "09987654321")
        )
        assertTrue(
            "International format differing number must trigger phone change",
            PhoneChangePolicy.isPhoneChanged(verifiedAuth, savedProfile, "+639987654321")
        )
    }

    @Test
    fun isPhoneChangedEnrollsPhoneForEmailOnlyAccount() {
        assertTrue(
            "First-time phone enrollment for email-only account must trigger verification",
            PhoneChangePolicy.isPhoneChanged(null, null, "09123456789")
        )
        assertTrue(
            "First-time phone enrollment with blank profile must trigger verification",
            PhoneChangePolicy.isPhoneChanged(null, "", "09123456789")
        )
    }

    @Test
    fun isPhoneChangedIgnoresBlankOrEmptyRequestedPhone() {
        assertFalse(PhoneChangePolicy.isPhoneChanged("+639123456789", "+639123456789", ""))
        assertFalse(PhoneChangePolicy.isPhoneChanged("+639123456789", "+639123456789", "   "))
    }

    @Test
    fun beginPreparesMobileStateCorrectly() {
        val initial = MobileState(
            page = "workspace",
            error = "Old error",
            message = "Old message",
            resendSeconds = 0
        )
        val pending = PendingProfileEdit(
            name = "Maria Santos",
            phone = "+639987654321",
            address = "123 Main St, Brgy. Central",
            city = "Cabanatuan City, Nueva Ecija"
        )

        val next = PhoneChangePolicy.begin(initial, "user-123", "+639987654321", pending)

        assertEquals("+639987654321", next.phoneChangeTarget)
        assertEquals("Maria Santos", next.phoneChangePendingName)
        assertEquals("123 Main St, Brgy. Central", next.phoneChangePendingAddress)
        assertEquals("Cabanatuan City, Nueva Ecija", next.phoneChangePendingCity)
        assertEquals(60, next.resendSeconds)
        assertNull(next.error)
        assertNull(next.message)
    }

    @Test(expected = IllegalArgumentException::class)
    fun beginRejectsBlankOwner() {
        val pending = PendingProfileEdit("Name", "+639987654321", "Addr", "City")
        PhoneChangePolicy.begin(MobileState(), "", "+639987654321", pending)
    }

    @Test(expected = IllegalArgumentException::class)
    fun beginRejectsBlankTargetPhone() {
        val pending = PendingProfileEdit("Name", "", "Addr", "City")
        PhoneChangePolicy.begin(MobileState(), "owner-1", "", pending)
    }

    @Test
    fun challengeSerializesAndRestoresCorrectly() {
        val now = 100_000L
        val pending = PendingProfileEdit(
            name = "Juan Dela Cruz",
            phone = "+639987654321",
            address = "Block 1 Lot 2, Brgy. San Jose",
            city = "Gapan City, Nueva Ecija"
        )

        val challenge = PhoneChangePolicy.challenge("user-456", "+639987654321", pending, now)
        assertEquals("user-456", challenge.value("owner"))
        assertEquals("+639987654321", challenge.value("target"))
        assertEquals("Juan Dela Cruz", challenge.value("name"))
        assertEquals("+639987654321", challenge.value("phone"))
        assertEquals("Block 1 Lot 2, Brgy. San Jose", challenge.value("address"))
        assertEquals("Gapan City, Nueva Ecija", challenge.value("city"))
        assertEquals(now.toString(), challenge.value("created"))

        // Active within 15 minutes
        assertTrue(PhoneChangePolicy.canRestore(challenge, "user-456", now + 60_000L))
        assertTrue(PhoneChangePolicy.canRestore(challenge, "user-456", now + PhoneChangePolicy.CHALLENGE_AGE))

        // Fails for different owner
        assertFalse(PhoneChangePolicy.canRestore(challenge, "other-user", now + 60_000L))

        // Fails if expired
        assertFalse(PhoneChangePolicy.canRestore(challenge, "user-456", now + PhoneChangePolicy.CHALLENGE_AGE + 1L))

        // Fails on future timestamp (clock drift)
        assertFalse(PhoneChangePolicy.canRestore(challenge, "user-456", now - 1000L))

        val parsed = PhoneChangePolicy.parsePendingProfile(challenge)
        assertEquals(pending, parsed)

        val restoredState = PhoneChangePolicy.restore(MobileState(page = "workspace"), challenge)
        assertEquals("+639987654321", restoredState.phoneChangeTarget)
        assertEquals("Juan Dela Cruz", restoredState.phoneChangePendingName)
        assertEquals("Block 1 Lot 2, Brgy. San Jose", restoredState.phoneChangePendingAddress)
        assertEquals("Gapan City, Nueva Ecija", restoredState.phoneChangePendingCity)
        assertEquals(0, restoredState.resendSeconds)
    }

    @Test
    fun cancelClearsPhoneChangeState() {
        val state = MobileState(
            phoneChangeTarget = "+639987654321",
            phoneChangePendingName = "Name",
            phoneChangePendingAddress = "Address",
            phoneChangePendingCity = "City",
            error = "OTP error"
        )

        val cancelled = PhoneChangePolicy.cancel(state)
        assertNull(cancelled.phoneChangeTarget)
        assertNull(cancelled.phoneChangePendingName)
        assertNull(cancelled.phoneChangePendingAddress)
        assertNull(cancelled.phoneChangePendingCity)
        assertNull(cancelled.error)
    }
}
