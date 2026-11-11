package com.tanaw.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.espresso.Espresso
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.annotations.SupabaseInternal

/** Signed-out device smoke: no account creation, recovery emails or credentials. */
class PublicNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @OptIn(SupabaseInternal::class)
    @Before fun signedOutNavigationFixture() {
        // These tests cover navigation, not SDK disk-session initialization.
        // No tokens/accounts are imported or changed; only the in-memory status is set.
        val client=compose.activity.supabase
        client.auth.setSessionStatus(SessionStatus.NotAuthenticated(false))
    }
    private fun loginReady() {
        compose.waitUntil(30000) { compose.onAllNodesWithText("Create Account",substring=true).fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun signupIsReachableAndBackReturnsToLogin() {
        loginReady()
        compose.onAllNodesWithText("Create Account",substring=true).onFirst().performClick()
        compose.onNodeWithText("FIRST NAME").assertExists()
        Espresso.pressBack()
        compose.onAllNodesWithText("Create Account",substring=true).onFirst().assertExists()
    }
    @Test fun recoveryHasARealScreenAndDoesNotNavigateToMissingRoute() {
        loginReady()
        compose.onNodeWithText("Forgot Password?").performClick()
        compose.onNodeWithText("Reset password").assertExists()
        compose.onNodeWithText("Request recovery code").assertIsNotEnabled()
        compose.onNodeWithText("Back to sign in").performClick()
        compose.onAllNodesWithText("Create Account",substring=true).onFirst().assertExists()
    }
    @Test fun deepLinkRecoveryOpensPasswordResetDirectlyWithToken() {
        loginReady()
        val token = "abcdef1234567890abcdef1234567890"
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("tanaw://auth/callback?token_hash=$token&type=recovery"))
        compose.activityRule.scenario.onActivity { activity ->
            activity.onNewIntent(intent)
        }
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Recovery link opened", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Recovery link opened", substring = true).assertExists()
        compose.onNodeWithText("Reset password").assertExists()
        compose.onNodeWithText(token).assertExists()
        compose.onNodeWithText("Back to sign in").performClick()
        compose.onAllNodesWithText("Create Account", substring = true).onFirst().assertExists()
    }
}
