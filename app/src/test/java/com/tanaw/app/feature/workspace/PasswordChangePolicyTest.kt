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
    @Test fun unsupportedRolesCannotStartPasswordChange() {
        listOf("staff", "admin", "manager", "unknown").forEach { role ->
            val invalidRoleState = state(owner = "user-1", role = role)
            assertTrue(runCatching { PasswordChangePolicy.begin(invalidRoleState, "user-1", null, "test@example.com") }.isFailure)
            assertFalse(PasswordChangePolicy.current(invalidRoleState.copy(passwordChangeOwner = "user-1"), "user-1"))
        }
    }
}
