package com.tanaw.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelProvider
import com.tanaw.app.feature.workspace.*
import io.github.jan.supabase.auth.auth
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Opt-in existing-session navigation only. Never requests an OTP or changes a password. */
class DevPasswordNavigationTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun readyProfile() {
        val vm=ViewModelProvider(compose.activity)[MobileWorkspaceViewModel::class.java]
        try {compose.waitUntil(60000){val state=vm.state.value;state.page=="workspace" && state.data!=null && !state.busy && !state.offline}}
        catch(failure:AssertionError) {
            val state=vm.state.value
            throw AssertionError("Verified workspace unavailable: page=${state.page}, offline=${state.offline}, busy=${state.busy}, issue=${state.error}",failure)
        }
        compose.waitUntil(60000){compose.onAllNodesWithText("PROFILE").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("PROFILE").assertIsEnabled().performClick()
        compose.onNodeWithText("My profile").assertExists()
        compose.onNodeWithText("Change Password").performScrollTo()
        try {compose.waitUntil(30000){compose.onAllNodes(hasText("Change Password") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}}
        catch(failure:AssertionError) {
            val state=ViewModelProvider(compose.activity)[MobileWorkspaceViewModel::class.java].state.value
            throw AssertionError("Verified Profile unavailable: page=${state.page}, offline=${state.offline}, busy=${state.busy}, issue=${state.error}",failure)
        }
    }
    @Test fun actualSignedInDriverCancelsBackToTheSameProfile() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("allowPasswordNavigationAcceptance")=="true")
        readyProfile()
        val owner=requireNotNull(compose.activity.supabase.auth.currentUserOrNull()).id
        compose.onNodeWithText("My profile").assertExists()
        compose.onNodeWithText("Change Password").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {
            val state=ViewModelProvider(compose.activity)[MobileWorkspaceViewModel::class.java].state.value
            assertEquals("Change Password did not open: ${state.error}",owner,state.passwordChangeOwner)
        }
        compose.onNodeWithText("Change password").assertExists()
        compose.onNodeWithText("Back to sign in").assertDoesNotExist()
        compose.onNodeWithText("Back to Profile").performScrollTo().performClick()
        compose.onNodeWithText("My profile").assertExists()
        assertEquals(owner,compose.activity.supabase.auth.currentUserOrNull()!!.id)
        compose.onNodeWithText("Change Password").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Change password").assertExists()
        Espresso.pressBack()
        compose.onNodeWithText("My profile").assertExists()
        assertEquals(owner,compose.activity.supabase.auth.currentUserOrNull()!!.id)
        compose.onNodeWithText("Change password").assertDoesNotExist()
    }
    @Test fun savedSignedInChallengeReturnsToProfileAfterColdViewModel() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("allowPasswordNavigationAcceptance")=="true")
        readyProfile()
        val user=requireNotNull(compose.activity.supabase.auth.currentUserOrNull())
        val target=requireNotNull(PasswordChangePolicy.target(user.phone,user.email))
        val store=WorkspaceRecoveryStore(compose.activity,BuildConfig.SUPABASE_URL)
        check(store.read("challenge",RecoveryPolicy.CHALLENGE_AGE)==null){"An existing challenge must not be overwritten by this test."}
        check(store.write("challenge",RecoveryPolicy.challenge("recover_code",target,System.currentTimeMillis(),user.id)))
        try {
            compose.runOnUiThread{compose.activity.viewModelStore.clear()}
            compose.activityRule.scenario.recreate()
            compose.waitUntil(60000){compose.onAllNodesWithText("Change password").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("Verification requested for your account.").assertExists()
            compose.onNodeWithText("Back to Profile").performScrollTo().performClick()
            compose.onNodeWithText("My profile").assertExists()
            assertNull(store.read("challenge",RecoveryPolicy.CHALLENGE_AGE))
            assertEquals(user.id,compose.activity.supabase.auth.currentUserOrNull()!!.id)
        }finally{store.clear("challenge")}
    }
}
