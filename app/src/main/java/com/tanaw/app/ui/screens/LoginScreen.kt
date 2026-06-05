package com.tanaw.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import com.tanaw.app.R
import com.tanaw.app.ui.theme.*

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
 * @param onForgotPassword    Navigate to ForgotPasswordScreen
 * @param onCreateAccount     Navigate to CreateAccountScreen
 * @param onTermsClick        Open Terms of Service
 * @param onPrivacyClick      Open Privacy Policy
 * @param successMessage      Non-null shows the green success banner (e.g. after registration)
 * @param isLoading           Shows a spinner on the Login button and disables it
 * @param errorMessage        Non-null shows a red inline error below the form
 */
@Composable
fun LoginScreen(
    onLoginClick: (email: String, password: String) -> Unit,
    onForgotPassword: () -> Unit,
    onCreateAccount: () -> Unit,
    onTermsClick: () -> Unit = {},
    onPrivacyClick: () -> Unit = {},
    successMessage: String? = null,
    isLoading: Boolean = false,
    errorMessage: String? = null,
) {
    // Local UI-only state (stays here — not needed by the parent)
    var email           by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showBanner      by remember { mutableStateOf(successMessage != null) }

    LaunchedEffect(successMessage) {
        showBanner = successMessage != null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {

        // ── Success banner ───────────────────────────────────────────────────
        if (showBanner && successMessage != null) {
            SuccessBanner(
                message  = successMessage,
                onDismiss = { showBanner = false },
                modifier  = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }

        // ── Main content ─────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

            Spacer(modifier = Modifier.height(if (showBanner) 96.dp else 64.dp))

            // ── TANAW Logo ───────────────────────────────────────────────────
            Image(
                painter            = painterResource(id = R.drawable.ic_tanaw_logo),
                contentDescription = "TANAW logo",
                modifier           = Modifier.size(56.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text          = "TANAW",
                fontSize      = 28.sp,
                fontWeight    = FontWeight.ExtraBold,
                color         = NavyPrimary,
                letterSpacing = 3.sp
            )

            Spacer(modifier = Modifier.height(36.dp))

            // ── Email field ──────────────────────────────────────────────────
            TanawInputLabel(text = "EMAIL ADDRESS")
            Spacer(modifier = Modifier.height(6.dp))
            TanawTextField(
                value       = email,
                onValueChange = { email = it },
                placeholder = "email@gmail.com",
                leadingIcon = {
                    Icon(
                        imageVector        = Icons.Outlined.Email,
                        contentDescription = null,
                        tint               = HintGray,
                        modifier           = Modifier.size(18.dp)
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Password field ───────────────────────────────────────────────
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
                        tint               = HintGray,
                        modifier           = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible)
                                Icons.Outlined.Visibility
                            else
                                Icons.Outlined.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint     = HintGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                visualTransformation = if (passwordVisible)
                    VisualTransformation.None
                else
                    PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )

            // ── Forgot password ──────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text       = "Forgot Password?",
                    color      = OrangeAccent,
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier   = Modifier
                        .padding(top = 10.dp)
                        .clickable { onForgotPassword() }
                )
            }

            // ── Inline error ─────────────────────────────────────────────────
            // Shown for: empty fields, wrong credentials
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text     = errorMessage,
                    color    = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Login button ─────────────────────────────────────────────────
            Button(
                onClick  = { onLoginClick(email, password) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape   = RoundedCornerShape(28.dp),
                colors  = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                enabled = !isLoading   // disabled while the fake network call runs
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color       = Color.White,
                        modifier    = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text       = "Login  →",
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── OR divider ───────────────────────────────────────────────────
            OrDivider()

            Spacer(modifier = Modifier.height(20.dp))

            // ── Create Account outline button ────────────────────────────────
            OutlinedButton(
                onClick  = onCreateAccount,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape  = RoundedCornerShape(28.dp),
                border = BorderStroke(1.5.dp, NavyPrimary),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NavyPrimary)
            ) {
                Text(
                    text       = "Create Account",
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Terms footer ─────────────────────────────────────────────────
            TermsFooter(
                onTermsClick   = onTermsClick,
                onPrivacyClick = onPrivacyClick
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ─── Shared Components ────────────────────────────────────────────────────────
// NOTE: Move these to ui/components/TanawComponents.kt once you have more screens.

@Composable
fun TanawInputLabel(text: String) {
    Text(
        text          = text,
        fontSize      = 11.sp,
        fontWeight    = FontWeight.Bold,
        color         = TextDark,
        letterSpacing = 0.8.sp,
        modifier      = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TanawTextField(
    value                : String,
    onValueChange        : (String) -> Unit,
    placeholder          : String,
    leadingIcon          : @Composable (() -> Unit)? = null,
    trailingIcon         : @Composable (() -> Unit)? = null,
    visualTransformation : VisualTransformation = VisualTransformation.None,
    keyboardOptions      : KeyboardOptions = KeyboardOptions.Default,
) {
    OutlinedTextField(
        value           = value,
        onValueChange   = onValueChange,
        modifier        = Modifier.fillMaxWidth(),
        shape           = RoundedCornerShape(12.dp),
        placeholder     = {
            Text(text = placeholder, color = HintGray, fontSize = 14.sp)
        },
        leadingIcon          = leadingIcon,
        trailingIcon         = trailingIcon,
        visualTransformation = visualTransformation,
        keyboardOptions      = keyboardOptions,
        singleLine           = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor    = NavyPrimary,
            unfocusedBorderColor  = BorderGray,
            focusedContainerColor = InputGray,
            unfocusedContainerColor = InputGray,
            cursorColor           = NavyPrimary,
            focusedTextColor      = TextDark,
            unfocusedTextColor    = TextDark,
        )
    )
}

@Composable
fun OrDivider(modifier: Modifier = Modifier) {
    Row(
        modifier              = modifier.fillMaxWidth(),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = BorderGray, thickness = 1.dp)
        Text(
            text       = "  OR  ",
            color      = HintGray,
            fontSize   = 12.sp,
            fontWeight = FontWeight.Medium
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = BorderGray, thickness = 1.dp)
    }
}

@Composable
fun SuccessBanner(
    message   : String,
    onDismiss : () -> Unit,
    modifier  : Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape    = RoundedCornerShape(10.dp),
        colors   = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter            = painterResource(id = R.drawable.ic_check),
                contentDescription = null,
                modifier           = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text       = message,
                color      = SuccessGreen,
                fontSize   = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier   = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Image(
                painter            = painterResource(id = R.drawable.ic_close),
                contentDescription = "Dismiss",
                modifier           = Modifier
                    .size(16.dp)
                    .clickable { onDismiss() }
            )
        }
    }
}

@Composable
fun TermsFooter(
    onTermsClick   : () -> Unit,
    onPrivacyClick : () -> Unit
) {
    val annotated = buildAnnotatedString {
        withStyle(SpanStyle(color = HintGray, fontSize = 11.sp)) {
            append("By signing in you agree to our\n")
        }
        withStyle(SpanStyle(color = OrangeAccent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)) {
            append("Terms of Service")
        }
        withStyle(SpanStyle(color = HintGray, fontSize = 11.sp)) {
            append(" and ")
        }
        withStyle(SpanStyle(color = OrangeAccent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)) {
            append("Privacy Policy")
        }
    }

    Text(
        text       = annotated,
        textAlign  = TextAlign.Center,
        lineHeight = 18.sp
    )
}