package com.tanaw.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.tanaw.app.feature.auth.CreateAccountScreen
import com.tanaw.app.feature.auth.LoginScreen
import com.tanaw.app.ui.theme.TanawTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** UI-only callbacks: never opens an account picker or sends an Auth request. */
class GoogleAuthUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun loginGoogleActionDoesNotRequirePasswordAndKeepsEmailLogin() {
        var clicks = 0
        compose.setContent { TanawTheme {
            LoginScreen(onLoginClick={_,_->}, onForgotPassword={}, onCreateAccount={}, onGoogleSignIn={clicks++})
        } }
        compose.onNodeWithText("Continue with Google").assertIsEnabled().performClick()
        assertEquals(1, clicks)
        compose.onNodeWithText("EMAIL ADDRESS").assertExists()
        compose.onNodeWithText("PASSWORD").assertExists()
    }

    @Test fun signupGoogleActionWorksWithoutAnEmailPasswordDraft() {
        var clicks = 0
        compose.setContent { TanawTheme {
            CreateAccountScreen(onVerifyAndContinue={_,_,_,_,_,_->}, onSignIn={}, onGoogleSignIn={clicks++})
        } }
        compose.onNodeWithText("Continue with Google").assertIsEnabled().performClick()
        assertEquals(1, clicks)
        compose.onNodeWithText("FIRST NAME").assertExists()
    }

    @Test fun busyLoginPreventsRepeatedGoogleRequests() {
        compose.setContent { TanawTheme {
            LoginScreen(onLoginClick={_,_->}, onForgotPassword={}, onCreateAccount={},
                onGoogleSignIn={}, isLoading=true)
        } }
        compose.onNodeWithText("Continue with Google").assertIsNotEnabled()
    }
}
