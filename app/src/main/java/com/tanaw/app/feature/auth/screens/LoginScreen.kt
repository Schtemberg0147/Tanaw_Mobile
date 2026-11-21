package com.tanaw.app.feature.auth.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.unit.dp
import com.tanaw.app.R
import com.tanaw.app.core.validation.MobileValidation
import com.tanaw.app.feature.auth.components.GoogleSignInButton
import com.tanaw.app.ui.components.*
import com.tanaw.app.ui.theme.FormFactorPreviews
import com.tanaw.app.ui.theme.LocalTanawWindowSize
import com.tanaw.app.ui.theme.TanawTheme
import com.tanaw.app.ui.theme.WindowWidthClass

// ─── Login Screen ─────────────────────────────────────────────────────────────
/**
 * LoginScreen
 *
 * State that changes during a login attempt (isLoading, errorMessage) is owned
 * by the parent (TanawNavHost / MobileWorkspaceApp) and passed in as parameters
 * so the composable itself stays stateless and easy to preview/test.
 *
 * @param onLoginClick        Called with (email, password) when the user taps Login.
 *                            The parent is responsible for validation + navigation.
 * @param onForgotPassword    Open working recovery; omitted when the host has no recovery flow.
 * @param onCreateAccount     Navigate to CreateAccountScreen
 * @param onTermsClick        Open Terms of Service
 * @param onPrivacyClick      Open Privacy Policy
 * @param successMessage      Non-null shows the success banner (e.g. after registration)
 * @param isLoading           Shows a spinner on the Login button and disables it
 * @param errorMessage        Non-null shows an inline error notice below the form
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
    var email           by rememberSaveable { mutableStateOf("") }
    var emailTouched    by rememberSaveable { mutableStateOf(false) }
    var useEmail        by rememberSaveable(allowPhone) { mutableStateOf(!allowPhone) }
    var password        by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var showBanner      by rememberSaveable { mutableStateOf(successMessage != null) }
    val focusManager = LocalFocusManager.current
    val windowSize = LocalTanawWindowSize.current
    val horizontalPad = if (windowSize.isNarrowPhone) 16.dp else 24.dp

    val isEmailInvalid = emailTouched && email.isNotBlank() && !MobileValidation.email(email)
    val isPhoneInvalid = emailTouched && email.isNotBlank() && MobileValidation.phone(email) == null
    val isInputError = if (useEmail) isEmailInvalid else isPhoneInvalid
    val inputSupportingText: @Composable (() -> Unit)? = if (isInputError) {
        {
            Text(
                text  = if (useEmail) "Enter a valid email address." else "Enter a Philippine mobile number, e.g. 0912 345 6789.",
                style = MaterialTheme.typography.bodySmall
            )
        }
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontalPad),
            contentAlignment = if (windowSize.widthClass == WindowWidthClass.COMPACT && !windowSize.isCompactLandscape) {
                Alignment.TopCenter
            } else {
                Alignment.Center
            }
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = windowSize.authMaxWidth)
                    .fillMaxWidth()
                    .padding(vertical = if (windowSize.widthClass != WindowWidthClass.COMPACT) 24.dp else 0.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
            if (showBanner && successMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                SuccessBanner(
                    message   = successMessage,
                    onDismiss = { showBanner = false },
                    modifier  = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(20.dp))
            } else {
                Spacer(modifier = Modifier.height(40.dp))
            }

            // ── TANAW Logo ───────────────────────────────────────────────
            Image(
                painter            = painterResource(id = R.drawable.ic_tanaw_logo),
                contentDescription = "TANAW logo",
                modifier           = Modifier.size(56.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text  = "TANAW",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text  = "Mabutol Trucking Operations",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ── Google Sign In (Optional) ────────────────────────────────
            onGoogleSignIn?.let { GoogleSignInButton(!isLoading, it) }

            // ── Identifier field ─────────────────────────────────────────
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
                    Icon(
                        imageVector        = if (useEmail) Icons.Outlined.Email else Icons.Outlined.Phone,
                        contentDescription = null,
                        modifier           = Modifier.size(20.dp)
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (useEmail) KeyboardType.Email else KeyboardType.Phone,
                    imeAction    = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                isError        = isInputError,
                supportingText = inputSupportingText,
            )
            if (allowPhone) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    TanawTextLink(
                        text     = if (useEmail) "Use phone number instead" else "Use email address instead",
                        onClick  = {
                            if (!isLoading) {
                                useEmail = !useEmail
                                email = ""
                                emailTouched = false
                            }
                        },
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .wrapContentHeight(Alignment.CenterVertically)
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (allowPhone) {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ── Password field ───────────────────────────────────────────
            TanawInputLabel(text = "PASSWORD")
            Spacer(modifier = Modifier.height(6.dp))
            TanawTextField(
                value         = password,
                onValueChange = { password = it },
                placeholder   = "••••••••",
                leadingIcon   = {
                    Icon(
                        imageVector        = Icons.Outlined.Lock,
                        contentDescription = null,
                        modifier           = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector        = if (passwordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            modifier           = Modifier.size(20.dp)
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
            onForgotPassword?.let { openRecovery ->
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TanawTextLink(
                        text       = "Forgot Password?",
                        onClick    = openRecovery,
                        fontWeight = FontWeight.SemiBold,
                        modifier   = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .wrapContentHeight(Alignment.CenterVertically)
                    )
                }
            }

            // ── Inline error ─────────────────────────────────────────────
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color  = MaterialTheme.colorScheme.errorContainer,
                    shape  = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier              = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector        = Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint               = MaterialTheme.colorScheme.onErrorContainer,
                            modifier           = Modifier.size(20.dp)
                        )
                        Text(
                            text  = errorMessage,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Login button ─────────────────────────────────────────────
            TanawPrimaryButton(
                text      = "Login  →",
                onClick   = {
                    focusManager.clearFocus()
                    onLoginClick(email, password)
                },
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
@FormFactorPreviews
@Composable
fun LoginScreenLightPreview() {
    TanawTheme {
        LoginScreen(
            onLoginClick     = { _, _ -> },
            onForgotPassword = {},
            onCreateAccount  = {}
        )
    }
}
