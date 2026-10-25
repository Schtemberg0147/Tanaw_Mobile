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
        compose.onNodeWithText("Request recovery email").assertIsNotEnabled()
        compose.onNodeWithText("Back to sign in").performClick()
        compose.onAllNodesWithText("Create Account",substring=true).onFirst().assertExists()
    }
}
