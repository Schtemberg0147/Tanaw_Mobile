package com.tanaw.app
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.tanaw.app.feature.auth.CreateAccountScreen
import com.tanaw.app.feature.auth.LoginScreen
import com.tanaw.app.ui.theme.TanawTheme
import org.junit.Rule
import org.junit.Test

class PhoneSignupUiTest {
 @get:Rule val compose=createComposeRule()
 @Test fun phoneSignupRecordsContactDetailsAndExplainsSms() {
  compose.setContent {TanawTheme {CreateAccountScreen(onVerifyAndContinue={_,_,_,_,_,_->},onSignIn={},phoneSignup=true)}}
  compose.onNodeWithText("EMAIL ADDRESS").assertExists()
  compose.onNodeWithText("LOCATION ADDRESS").assertExists()
  compose.onNodeWithText("CONTACT NUMBER").assertExists()
  compose.onNodeWithText("We will text a verification code to your contact number.").assertExists()
 }
 @Test fun existingEmailAndNewPhoneAccountsShareLogin() {
  compose.setContent {TanawTheme {LoginScreen(onLoginClick={_,_->},onForgotPassword={},onCreateAccount={},allowPhone=true)}}
  compose.onNodeWithText("PHONE NUMBER").assertExists()
  compose.onNodeWithText("EMAIL ADDRESS").assertDoesNotExist()
  compose.onNodeWithText("Use email address instead").performClick()
  compose.onNodeWithText("EMAIL ADDRESS").assertExists()
  compose.onNodeWithText("Use phone number instead").performClick()
  compose.onNodeWithText("PHONE NUMBER").assertExists()
 }
}
