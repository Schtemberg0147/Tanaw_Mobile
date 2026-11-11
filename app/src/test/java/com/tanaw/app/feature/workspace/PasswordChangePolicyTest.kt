package com.tanaw.app.feature.workspace

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class PasswordChangePolicyTest {
    private fun state(owner:String="driver",role:String="driver")=MobileState(page="workspace",data=buildJsonObject {
        put("profile",buildJsonObject{put("id",owner);put("role",role)})
    })

    @Test fun signedInChangeUsesAuthContactAndCancelKeepsTheSameWorkspace() {
        assertEquals("+639000000000",PasswordChangePolicy.target("639000000000",null))
        val start=state()
        val change=PasswordChangePolicy.begin(start,"driver","09000000000","alias@example.invalid")
        assertEquals("workspace",change.page);assertSame(start.data,change.data)
        assertEquals("+639000000000",change.email);assertEquals("driver",change.passwordChangeOwner)
        val cancelled=PasswordChangePolicy.cancel(change.copy(passwordChangeCodeSent=true,resendSeconds=40,error="old"),"driver")
        assertEquals("workspace",cancelled.page);assertSame(start.data,cancelled.data)
        assertNull(cancelled.passwordChangeOwner);assertFalse(cancelled.passwordChangeCodeSent)
        assertEquals("",cancelled.email);assertEquals(0,cancelled.resendSeconds);assertNull(cancelled.error)
    }

    @Test fun lostOrChangedSessionNeverReturnsToAnotherProfilesState() {
        val change=PasswordChangePolicy.begin(state(),"driver",null,"driver@example.invalid")
        listOf(null,"other").forEach {owner->
            val cancelled=PasswordChangePolicy.cancel(change,owner)
            assertEquals("login",cancelled.page);assertNull(cancelled.data);assertNull(cancelled.passwordChangeOwner)
        }
        assertTrue(runCatching{PasswordChangePolicy.begin(state().copy(offline=true),"driver",null,"driver@example.invalid")}.isFailure)
        assertTrue(runCatching{PasswordChangePolicy.begin(state(),"other",null,"driver@example.invalid")}.isFailure)
    }

    @Test fun signedInRestorationBindsChallengeToSameOwnerAndCurrentAuthContact() {
        val saved=RecoveryPolicy.challenge("recover_code","+639000000000",1000,"driver")
        assertTrue(RecoveryPolicy.valid(saved,1001))
        assertEquals(setOf("created","page","target","return_owner"),saved.keys)
        assertTrue(PasswordChangePolicy.canRestore(saved,"driver","09000000000",null))
        assertFalse(PasswordChangePolicy.canRestore(saved,"other","09000000000",null))
        assertFalse(PasswordChangePolicy.canRestore(saved,"driver","09111111111",null))
        assertFalse(PasswordChangePolicy.canRestore(RecoveryPolicy.challenge("recover_code","+639000000000",1000),"driver","09000000000",null))
        assertFalse(RecoveryPolicy.valid(saved,1001+RecoveryPolicy.CHALLENGE_AGE))
    }

    @Test fun customerCanStartVerifyAndCancelPasswordChange() {
        val customerState = state(owner = "cust-1", role = "customer")
        val change = PasswordChangePolicy.begin(customerState, "cust-1", "09123456789", "customer@example.com")
        assertEquals("workspace", change.page)
        assertSame(customerState.data, change.data)
        assertEquals("+639123456789", change.email)
        assertEquals("cust-1", change.passwordChangeOwner)
        assertTrue(PasswordChangePolicy.current(change, "cust-1"))

        val cancelled = PasswordChangePolicy.cancel(change.copy(passwordChangeCodeSent = true, resendSeconds = 30), "cust-1")
        assertEquals("workspace", cancelled.page)
        assertSame(customerState.data, cancelled.data)
        assertNull(cancelled.passwordChangeOwner)
        assertFalse(cancelled.passwordChangeCodeSent)
        assertEquals("", cancelled.email)
        assertEquals(0, cancelled.resendSeconds)
    }

    @Test fun customerEmailOnlyAccountCanStartAndVerifyPasswordChange() {
        val customerState = state(owner = "cust-email", role = "customer")
        val change = PasswordChangePolicy.begin(customerState, "cust-email", null, "  customer@example.com  ")
        assertEquals("workspace", change.page)
        assertSame(customerState.data, change.data)
        assertEquals("customer@example.com", change.email)
        assertEquals("cust-email", change.passwordChangeOwner)
        assertTrue(PasswordChangePolicy.current(change, "cust-email"))
    }

    @Test fun customerPhoneFormattingAndNormalization() {
        listOf(
            "09123456789",
            " 09123456789 ",
            "639123456789",
            "+639123456789",
            "+63 912 345 6789",
            "9123456789"
        ).forEach { rawPhone ->
            assertEquals("+639123456789", PasswordChangePolicy.target(rawPhone, "fallback@example.com"))
        }
        assertEquals("fallback@example.com", PasswordChangePolicy.target(null, "fallback@example.com"))
        assertEquals("fallback@example.com", PasswordChangePolicy.target("", "fallback@example.com"))
        assertEquals("fallback@example.com", PasswordChangePolicy.target("invalid-phone", "fallback@example.com"))
    }

    @Test fun customerRestorationBindsToOwnerAndCurrentAuthContact() {
        val phoneSaved = RecoveryPolicy.challenge("recover_code", "+639123456789", 1000, "cust-1")
        assertTrue(PasswordChangePolicy.canRestore(phoneSaved, "cust-1", "09123456789", null))
        assertFalse(PasswordChangePolicy.canRestore(phoneSaved, "cust-2", "09123456789", null))
        assertFalse(PasswordChangePolicy.canRestore(phoneSaved, "cust-1", "09999999999", null))

        val emailSaved = RecoveryPolicy.challenge("recover_code", "customer@example.com", 1000, "cust-1")
        assertTrue(PasswordChangePolicy.canRestore(emailSaved, "cust-1", null, "customer@example.com"))
        assertTrue(PasswordChangePolicy.canRestore(emailSaved, "cust-1", null, "Customer@EXAMPLE.com"))
        assertFalse(PasswordChangePolicy.canRestore(emailSaved, "cust-1", null, "other@example.com"))
        assertFalse(PasswordChangePolicy.canRestore(emailSaved, "cust-2", null, "customer@example.com"))
    }

    @Test fun vulnerabilityTestingUnsignedChallengeCannotBeHijackedByBlankOwner() {
        // Public signed-out password recovery challenges have no return_owner
        val unsignedChallenge = RecoveryPolicy.challenge("recover_code", "+639123456789", 1000)
        assertFalse(PasswordChangePolicy.canRestore(unsignedChallenge, "", "09123456789", null))
        assertFalse(PasswordChangePolicy.canRestore(unsignedChallenge, "   ", "09123456789", null))
        assertFalse(PasswordChangePolicy.canRestore(unsignedChallenge, "cust-1", "09123456789", null))

        // Owned challenge cannot be restored with blank owner argument
        val ownedSaved = RecoveryPolicy.challenge("recover_code", "+639123456789", 1000, "cust-1")
        assertFalse(PasswordChangePolicy.canRestore(ownedSaved, "", "09123456789", null))
        assertFalse(PasswordChangePolicy.canRestore(ownedSaved, "   ", "09123456789", null))
    }

    @Test fun vulnerabilityTestingBlankOrMalformedOwnerCannotPassCurrentOrBegin() {
        val customerState = state(owner = "cust-1", role = "customer")
        assertFalse(PasswordChangePolicy.current(customerState.copy(passwordChangeOwner = ""), ""))
        assertFalse(PasswordChangePolicy.current(customerState.copy(passwordChangeOwner = "   "), "   "))
        assertFalse(PasswordChangePolicy.current(customerState.copy(passwordChangeOwner = null), "cust-1"))
        assertFalse(PasswordChangePolicy.current(customerState.copy(passwordChangeOwner = "cust-1"), null))
        assertFalse(PasswordChangePolicy.current(customerState.copy(passwordChangeOwner = "cust-1"), ""))
        assertFalse(PasswordChangePolicy.current(customerState.copy(passwordChangeOwner = "cust-1"), "   "))

        assertTrue(runCatching { PasswordChangePolicy.begin(customerState, "", "09123456789", null) }.isFailure)
        assertTrue(runCatching { PasswordChangePolicy.begin(customerState, "   ", "09123456789", null) }.isFailure)
    }

    @Test fun customerValidationAndBoundaryEdgeCases() {
        val customerState = state(owner = "cust-1", role = "customer")

        // Offline customer
        assertTrue(runCatching { PasswordChangePolicy.begin(customerState.copy(offline = true), "cust-1", "09123456789", null) }.isFailure)

        // Invalid or missing contacts
        assertTrue(runCatching { PasswordChangePolicy.begin(customerState, "cust-1", null, null) }.isFailure)
        assertTrue(runCatching { PasswordChangePolicy.begin(customerState, "cust-1", "", "") }.isFailure)
        assertTrue(runCatching { PasswordChangePolicy.begin(customerState, "cust-1", "invalid-phone", "invalid-email") }.isFailure)

        // Invalid page
        assertTrue(runCatching { PasswordChangePolicy.begin(customerState.copy(page = "loading"), "cust-1", "09123456789", null) }.isFailure)
        assertTrue(runCatching { PasswordChangePolicy.begin(customerState.copy(page = "login"), "cust-1", "09123456789", null) }.isFailure)

        // Null data or missing profile
        assertTrue(runCatching { PasswordChangePolicy.begin(customerState.copy(data = null), "cust-1", "09123456789", null) }.isFailure)
        assertTrue(runCatching { PasswordChangePolicy.begin(MobileState(page = "workspace", data = buildJsonObject {}), "cust-1", "09123456789", null) }.isFailure)

        // Profile ID mismatch
        assertTrue(runCatching { PasswordChangePolicy.begin(customerState, "other-cust", "09123456789", null) }.isFailure)
    }

    @Test fun customerSessionLossOrMismatchOnCancelForcesReLogin() {
        val customerState = state(owner = "cust-1", role = "customer")
        val change = PasswordChangePolicy.begin(customerState, "cust-1", "09123456789", null)
        listOf(null, "", "   ", "other-cust").forEach { owner ->
            val cancelled = PasswordChangePolicy.cancel(change, owner)
            assertEquals("login", cancelled.page)
            assertNull(cancelled.data)
            assertNull(cancelled.passwordChangeOwner)
        }
    }

    @Test fun unsupportedRolesCannotStartPasswordChange() {
        listOf("staff", "admin", "manager", "unknown").forEach { role ->
            val invalidRoleState = state(owner = "user-1", role = role)
            assertTrue(runCatching { PasswordChangePolicy.begin(invalidRoleState, "user-1", null, "test@example.com") }.isFailure)
            assertFalse(PasswordChangePolicy.current(invalidRoleState.copy(passwordChangeOwner = "user-1"), "user-1"))
            assertFalse(PasswordChangePolicy.isAllowedRole(role))
        }
    }

    @Test fun allowedRolesAreExplicitlyDefined() {
        assertTrue(PasswordChangePolicy.isAllowedRole("driver"))
        assertTrue(PasswordChangePolicy.isAllowedRole("customer"))
        assertEquals(listOf("driver", "customer"), PasswordChangePolicy.ALLOWED_ROLES)
    }
}
