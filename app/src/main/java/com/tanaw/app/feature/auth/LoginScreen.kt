package com.tanaw.app.feature.auth

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tanaw.app.R
import com.tanaw.app.feature.workspace.MobileValidation
import com.tanaw.app.ui.components.*
import com.tanaw.app.ui.theme.TanawTheme

// ─── Login Screen ─────────────────────────────────────────────────────────────
/**
 * LoginScreen
 *
 * State that changes during a login attempt (isLoading, errorMessage) is owned
 * by the parent (TanawNavHost) and passed in as parameters so the composable
 * itself stays stateless and easy to preview/test.
 *
 * @param onLoginClick        Called with (email, password) when the user taps Login.
 *                            The parent is responsible for validation + navigation.
 * @param onForgotPassword    Open working recovery; omitted when the host has no recovery flow.
 * @param onCreateAccount     Navigate to CreateAccountScreen
 * @param onTermsClick        Open Terms of Service
 * @param onPrivacyClick      Open Privacy Policy
 * @param successMessage      Non-null shows the success banner (e.g. after registration)
 * @param isLoading           Shows a spinner on the Login button and disables it
 * @param errorMessage        Non-null shows a red inline error below the form
 */
@Composable
fun LoginScreen(
    onLoginClick: (email: String, password: String) -> Unit,
    onForgotPassword: (() -> Unit)? = null,
    onCreateAccount: () -> Unit,
    onTermsClick: () -> Unit = {},
    onPrivacyClick: () -> Unit = {},
    successMessage: String? = null,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onGoogleSignIn: (() -> Unit)? = null,
    allowPhone: Boolean = false,
) {
    // Local UI-only state
    var email           by remember { mutableStateOf("") }
    var emailTouched    by remember { mutableStateOf(false) }
    var useEmail        by remember(allowPhone) { mutableStateOf(!allowPhone) }
    var password        by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showBanner      by remember { mutableStateOf(successMessage != null) }
    val focusManager = LocalFocusManager.current

    val isEmailInvalid = emailTouched && email.isNotBlank() && !MobileValidation.email(email)
    val isPhoneInvalid = emailTouched && email.isNotBlank() && MobileValidation.phone(email) == null
    val isInputError = if (useEmail) isEmailInvalid else isPhoneInvalid
    val inputSupportingText: @Composable (() -> Unit)? = if (isInputError) {
        { Text(if (useEmail) "Enter a valid email address." else "Enter a Philippine mobile number, e.g. 0912 345 6789.") }
    } else null

    val isInputValid = if (useEmail) MobileValidation.email(email) else (MobileValidation.phone(email) != null)
    val canSubmit = isInputValid && password.isNotBlank() && !isLoading

    LaunchedEffect(successMessage) {
        showBanner = successMessage != null
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color    = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // ── Success banner ───────────────────────────────────────────────
            if (showBanner && successMessage != null) {
                SuccessBanner(
                    message   = successMessage,
                    onDismiss = { showBanner = false },
                    modifier  = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            // ── Main content ─────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {

                Spacer(modifier = Modifier.height(if (showBanner) 96.dp else 64.dp))

                // ── TANAW Logo ───────────────────────────────────────────────
                Image(
                    painter            = painterResource(id = if (androidx.compose.foundation.isSystemInDarkTheme()) R.drawable.ic_tanaw_logo_dark else R.drawable.ic_tanaw_logo),
                    contentDescription = "TANAW logo",
                    modifier           = Modifier.size(56.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text  = "TANAW",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(36.dp))

                // ── Email field ──────────────────────────────────────────────
                onGoogleSignIn?.let { GoogleSignInButton(!isLoading, it) }
                TanawInputLabel(text = if (useEmail) "EMAIL ADDRESS" else "PHONE NUMBER")
                Spacer(modifier = Modifier.height(6.dp))
                TanawTextField(
                    value         = email,
                    onValueChange = {
                        email = it
                        emailTouched = true
                    },
                    placeholder   = if (useEmail) "email@gmail.com" else "0912 345 6789",
                    leadingIcon   = {
                        if (!useEmail) Icon(Icons.Outlined.Phone, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        else
                        Icon(
                            painter            = painterResource(id = R.drawable.ic_mail_check),
                            contentDescription = null,
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier           = Modifier.size(18.dp)
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (useEmail) KeyboardType.Email else KeyboardType.Phone,
                        imeAction    = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    isError = isInputError,
                    supportingText = inputSupportingText,
                )
                if (allowPhone) {
                    TextButton(enabled = !isLoading, onClick = { useEmail = !useEmail; email = ""; emailTouched = false }) {
                        Text(if (useEmail) "Use phone number instead" else "Use email address instead")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Password field ───────────────────────────────────────────
                TanawInputLabel(text = "PASSWORD")
                Spacer(modifier = Modifier.height(6.dp))
                TanawTextField(
                    value         = password,
                    onValueChange = { password = it },
                    placeholder   = "••••••••",
                    leadingIcon   = {
                        Icon(
                            painter            = painterResource(id = R.drawable.ic_shield_lock),
                            contentDescription = null,
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier           = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                painter = painterResource(
                                    id = if (passwordVisible) R.drawable.ic_visibility else R.drawable.ic_visibility_off
                                ),
                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible)
                        VisualTransformation.None
                    else
                        PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction    = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            if (canSubmit) onLoginClick(email, password)
                        }
                    ),
                )

                // ── Forgot password ──────────────────────────────────────────
                onForgotPassword?.let { openRecovery -> Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text       = "Forgot Password?",
                        color      = MaterialTheme.colorScheme.secondary,
                        style      = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier   = Modifier
                            .padding(top = 10.dp)
                            .clickable(onClick = openRecovery)
                    )
                } }

                // ── Inline error ─────────────────────────────────────────────
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text     = errorMessage,
                        color    = MaterialTheme.colorScheme.error,
                        style    = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // ── Login button ─────────────────────────────────────────────
                TanawPrimaryButton(
                    text      = "Login  →",
                    onClick   = { onLoginClick(email, password) },
                    isLoading = isLoading,
                    enabled   = canSubmit
                )

                Spacer(modifier = Modifier.height(20.dp))

                // ── OR divider ───────────────────────────────────────────────
                OrDivider()

                Spacer(modifier = Modifier.height(20.dp))

                // ── Create Account button ────────────────────────────────────
                TanawOutlinedButton(
                    text    = "Create Account",
                    onClick = onCreateAccount
                )

                Spacer(modifier = Modifier.height(24.dp))

                // ── Terms footer ─────────────────────────────────────────────
                TermsFooter(
                    onTermsClick   = onTermsClick,
                    onPrivacyClick = onPrivacyClick
                )

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

// ─── Previews ─────────────────────────────────────────────────────────────────
@Preview(showBackground = true, name = "Light Mode")
@Composable
fun LoginScreenLightPreview() {
    TanawTheme(darkTheme = false) {
        LoginScreen(
            onLoginClick     = { _, _ -> },
            onForgotPassword = {},
            onCreateAccount  = {}
        )
    }
}

@Preview(showBackground = true, name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun LoginScreenDarkPreview() {
    TanawTheme(darkTheme = true) {
        LoginScreen(
            onLoginClick     = { _, _ -> },
            onForgotPassword = {},
            onCreateAccount  = {}
        )
    }
}
