package com.tanaw.app

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.espresso.Espresso
import com.tanaw.app.feature.workspace.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Local session/driver fixtures only; no codes requested or passwords changed. */
class PasswordChangeNavigationTest {
    @get:Rule val compose=createComposeRule()
    private fun data()=buildJsonObject {
        put("profile",buildJsonObject {put("id","driver");put("role","driver");put("full_name","Navigation Fixture")})
        put("driver",buildJsonObject{put("id","roster");put("status","available")})
        put("shipments",JsonArray(emptyList()));put("driver_deliveries",JsonArray(emptyList()))
    }
    @Test fun profileRemainsMountedAndBothCancelPathsReturnToProfile() {
        val data=data();var state by mutableStateOf(MobileState(page="workspace",data=data))
        compose.setContent {
            DriverTheme {DriverWorkspaceScreen(state,data,{},{},{state=PasswordChangePolicy.begin(state,"driver","09000000000",null)},
                {},{},{_,_,_->},{_,_->},{_,_,_,_,_,_->})}
            if(state.passwordChangeOwner!=null)PasswordChangeDialog(state,{state=PasswordChangePolicy.cancel(state,"driver")},{fail("unrequested code")},{false},{_,_,_->fail("unrequested change")})
        }
        compose.onNodeWithText("PROFILE").performClick()
        compose.onNodeWithText("Change Password").performScrollTo().performClick()
        compose.onNodeWithText("Change password").assertExists()
        compose.onNodeWithText("Back to sign in").assertDoesNotExist()
        compose.onNodeWithText("Back to Profile").performScrollTo().performClick()
        compose.onNodeWithText("Change password").assertDoesNotExist()
        compose.onNodeWithText("Navigation Fixture").assertExists()
        compose.onNodeWithText("Change Password").performScrollTo().performClick()
        Espresso.pressBack()
        compose.onNodeWithText("Change password").assertDoesNotExist()
        compose.onNodeWithText("Navigation Fixture").assertExists()
        compose.onNodeWithText("My profile").assertExists()
        compose.onNodeWithText("Help & Support").performScrollTo().performClick()
        compose.onNodeWithText("Change Password").performScrollTo().performClick()
        compose.onNodeWithText("Back to Profile").performScrollTo().performClick()
        compose.onNodeWithText("Delivery support").performScrollTo().assertExists()
        compose.onNodeWithText("My profile").assertDoesNotExist()
    }
    @Test fun verificationErrorsStayInChangeAndBusyBlocksCancellationAndDuplicateRequests() {
        var state by mutableStateOf(MobileState(page="workspace",data=data(),passwordChangeOwner="driver",email="+639000000000"))
        var requests=0;var cancelled=0
        compose.setContent {PasswordChangeDialog(state,{cancelled++},{requests++;state=state.copy(busy=true)},{it=="123456"},{_,_,_->fail("not submitted")})}
        compose.onNodeWithText("REQUEST VERIFICATION CODE").performClick()
        compose.onNodeWithText("Back to Profile").performScrollTo().assertIsNotEnabled()
        Espresso.pressBack()
        compose.runOnIdle{assertEquals(1,requests);assertEquals(0,cancelled);state=state.copy(busy=false,passwordChangeCodeSent=true,error="Verification failed. Retry.",resendSeconds=30)}
        compose.onNodeWithText("Verification failed. Retry.").performScrollTo().assertExists()
        compose.onNodeWithText("UPDATE PASSWORD").assertIsNotEnabled()
        compose.onNodeWithText("Request another code in 30s").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Back to Profile").performScrollTo().performClick()
        compose.runOnIdle{assertEquals(1,cancelled)}
    }
    @Test fun updateRequiresValidCodeAndMatchingPasswordAndLocksWhileSubmitting() {
        var state by mutableStateOf(MobileState(page="workspace",data=data(),passwordChangeOwner="driver",email="+639000000000",passwordChangeCodeSent=true))
        var updates=0
        compose.setContent {PasswordChangeDialog(state,{fail("unexpected cancel")},{fail("unexpected resend")},{it=="123456"},{code,password,confirm->
            assertEquals("123456",code);assertEquals("synthetic-password",password);assertEquals(password,confirm)
            updates++;state=state.copy(busy=true)
        })}
        compose.onNodeWithText("UPDATE PASSWORD").assertIsNotEnabled()
        compose.onNodeWithText("Recovery code (or original email link)").performScrollTo().performTextInput("123456")
        compose.onNodeWithText("New password (8+ characters)").performScrollTo().performTextInput("synthetic-password")
        compose.onNodeWithText("Confirm new password").performScrollTo().performTextInput("different-password")
        compose.onNodeWithText("UPDATE PASSWORD").assertIsNotEnabled()
        compose.onNodeWithText("Confirm new password").performTextReplacement("synthetic-password")
        compose.onNodeWithText("UPDATE PASSWORD").performClick()
        compose.onNodeWithText("Submitting…").assertIsNotEnabled()
        compose.runOnIdle{assertEquals(1,updates)}
    }
}
