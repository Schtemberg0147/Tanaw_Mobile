package com.tanaw.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.espresso.Espresso
import com.tanaw.app.feature.workspace.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.annotations.SupabaseInternal
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Synthetic persisted challenges: no code requests, credential changes or messages. */
class DriverLoginAcceptanceTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val store get()=WorkspaceRecoveryStore(compose.activity,BuildConfig.SUPABASE_URL)
    @OptIn(SupabaseInternal::class)
    @Before fun signedOut() {
        store.clear("challenge")
        compose.activity.supabase.auth.setSessionStatus(SessionStatus.NotAuthenticated(false))
    }
    @After fun cleanUp() {store.clear("challenge")}
    private fun ready(text:String) {
        compose.waitUntil(30000) {compose.onAllNodesWithText(text,substring=true).fetchSemanticsNodes().isNotEmpty()}
    }
    private fun restore(target:String,age:Long=0) {
        check(store.write("challenge",RecoveryPolicy.challenge("recover_code",target,System.currentTimeMillis()-age)))
        // Activity recreation normally retains its ViewModel. Clear it to exercise cold reconstruction.
        compose.runOnUiThread {compose.activity.viewModelStore.clear()}
        compose.activityRule.scenario.recreate()
    }
    @Test fun legalFooterOpensBothHonestPublicationStatesAndCloses() {
        ready("Terms of Service")
        compose.onNodeWithText("Terms of Service").performScrollTo().performClick()
        compose.onNodeWithText("The office has not published",substring=true).assertExists()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Privacy Policy").performScrollTo().performClick()
        compose.onNodeWithText("The office has not published",substring=true).assertExists()
        Espresso.pressBack()
        compose.onNodeWithText("Privacy Policy").assertExists()
    }
    @Test fun phoneChallengeRestoresCooldownAndBackClearsPendingContext() {
        restore("+639000000000")
        ready("Recovery requested for")
        compose.onNodeWithText("Recovery requested for +639000000000").assertExists()
        compose.onNodeWithText("Update password").assertIsNotEnabled()
        compose.onNodeWithText("Request recovery code").assertIsNotEnabled()
        Espresso.pressBack()
        ready("Forgot Password?")
        check(store.read("challenge",RecoveryPolicy.CHALLENGE_AGE)==null)
        compose.onNodeWithText("Forgot Password?").performScrollTo().performClick()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextReplacement("driver@example.invalid")
        compose.onNodeWithText("Request recovery code").assertIsEnabled()
    }
    @Test fun emailChallengeSurvivesActivityRecreationWithoutCodeOrPassword() {
        restore("driver@example.invalid",65000)
        ready("Recovery requested for")
        compose.onNodeWithText("Recovery requested for driver@example.invalid").assertExists()
        compose.onNodeWithText("Request recovery code").assertIsEnabled()
        compose.onNodeWithText("Update password").assertIsNotEnabled()
        compose.activityRule.scenario.recreate()
        ready("Recovery requested for")
        compose.onNodeWithText("Update password").assertIsNotEnabled()
    }
    @Test fun expiredChallengeReturnsToLogin() {
        restore("driver@example.invalid",RecoveryPolicy.CHALLENGE_AGE+1000)
        ready("Forgot Password?")
        compose.onNodeWithText("Forgot Password?").assertExists()
        check(store.read("challenge",RecoveryPolicy.CHALLENGE_AGE)==null)
    }
}
