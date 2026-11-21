package com.tanaw.app.feature.auth.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tanaw.app.R
import com.tanaw.app.core.validation.MobileValidation
import com.tanaw.app.feature.auth.rules.PasswordRecoveryRules
import com.tanaw.app.feature.workspace.state.MobileState
import com.tanaw.app.feature.workspace.viewmodel.MobileWorkspaceViewModel
import com.tanaw.app.ui.components.SupportingText
import com.tanaw.app.ui.theme.FormFactorPreviews
import com.tanaw.app.ui.theme.LocalTanawWindowSize
import com.tanaw.app.ui.theme.TanawTheme

@Composable
private fun RecoveryNotices(
    busy: Boolean,
    error: String?,
    message: String?
) {
    if (busy) {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            color = MaterialTheme.colorScheme.primary
        )
    }
    error?.let {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
    message?.let {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Text(
                text = it,
                modifier = Modifier.padding(14.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun recoveryTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
    errorContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    errorBorderColor = MaterialTheme.colorScheme.error,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    errorTextColor = MaterialTheme.colorScheme.onSurface,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    cursorColor = MaterialTheme.colorScheme.primary
)

/**
 * Responsive 6-digit OTP entry row using weight(1f) cells (height = 54.dp, 12.dp radius)
 * so cells never clip horizontally on narrow 320dp viewports.
 */
@Composable
fun OtpCodeInputRow(
    code: String,
    onCodeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    otpLength: Int = 6,
    onDone: () -> Unit = {}
) {
    val otpValues = remember(code, otpLength) {
        val digits = code.filter { it.isDigit() }
        List(otpLength) { idx -> digits.getOrNull(idx)?.toString().orEmpty() }
    }
    val focusRequesters = remember(otpLength) { List(otpLength) { FocusRequester() } }
    var focusedIndex by remember { mutableIntStateOf(-1) }
    val focusManager = LocalFocusManager.current
    val windowSize = LocalTanawWindowSize.current

    Row(
        horizontalArrangement = Arrangement.spacedBy(if (windowSize.isNarrowPhone) 5.dp else 8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        repeat(otpLength) { index ->
            val digit = otpValues[index]
            val isFocusedSlot = focusedIndex == index || (focusedIndex == -1 && digit.isEmpty() && (index == 0 || otpValues[index - 1].isNotEmpty()))
            val borderColor = when {
                isError -> MaterialTheme.colorScheme.error
                isFocusedSlot || digit.isNotEmpty() -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outline
            }

            BasicTextField(
                value = digit,
                enabled = enabled,
                onValueChange = { newVal ->
                    val filteredDigits = newVal.filter { it.isDigit() }
                    if (filteredDigits.length > 1) {
                        val pasted = filteredDigits.take(otpLength)
                        onCodeChange(pasted)
                        if (pasted.length >= otpLength) {
                            focusManager.clearFocus()
                        } else {
                            focusRequesters[pasted.length.coerceAtMost(otpLength - 1)].requestFocus()
                        }
                    } else {
                        val inputDigit = filteredDigits.takeLast(1)
                        val mutableDigits = otpValues.toMutableList()
                        mutableDigits[index] = inputDigit
                        onCodeChange(mutableDigits.joinToString(""))
                        if (inputDigit.isNotEmpty() && index < otpLength - 1) {
                            focusRequesters[index + 1].requestFocus()
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = if (isFocusedSlot || digit.isNotEmpty()) 2.dp else 1.5.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .background(
                        if (digit.isNotEmpty()) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    )
                    .focusRequester(focusRequesters[index])
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            focusedIndex = index
                        } else if (focusedIndex == index) {
                            focusedIndex = -1
                        }
                    }
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown &&
                            keyEvent.key == Key.Backspace &&
                            digit.isEmpty() &&
                            index > 0
                        ) {
                            val mutableDigits = otpValues.toMutableList()
                            mutableDigits[index - 1] = ""
                            onCodeChange(mutableDigits.joinToString(""))
                            focusRequesters[index - 1].requestFocus()
                            true
                        } else {
                            false
                        }
                    },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = if (index == otpLength - 1) ImeAction.Done else ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        onDone()
                    }
                ),
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                textStyle = MaterialTheme.typography.titleLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                ),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.Center) {
                        if (digit.isEmpty() && !isFocusedSlot) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }
    }
}

@Composable
private fun RecoveryStepHeader(
    step: Int,
    totalSteps: Int,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Step $step of $totalSteps",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(totalSteps) { index ->
                val active = index < step
                val current = index == step - 1
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            when {
                                current -> MaterialTheme.colorScheme.secondary
                                active -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.outlineVariant
                            }
                        )
                )
            }
        }
    }
}

/**
 * Reusable verification code screen supporting both 6-digit OTP entry and token/link paste,
 * with resend cooldown and a clear Back to sign in action.
 */
@Composable
fun VerificationCodeScreen(
    title: String,
    headline: String,
    subtitle: String,
    targetLabel: String,
    code: String,
    onCodeChange: (String) -> Unit,
    onVerify: () -> Unit,
    onResend: () -> Unit,
    onBackToSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    verifyButtonLabel: String = "Verify code",
    resendButtonLabel: String = "Resend code",
    codeFieldLabel: String = "Verification code or confirmation link",
    allowTokenPaste: Boolean = true,
    canVerify: Boolean = false,
    isCodeError: Boolean = false,
    codeErrorText: String = "Enter a valid verification code.",
    busy: Boolean = false,
    offline: Boolean = false,
    resendSeconds: Int = 0,
    errorMessage: String? = null,
    statusMessage: String? = null,
) {
    val focusManager = LocalFocusManager.current
    val windowSize = LocalTanawWindowSize.current
    val horizontalPad = if (windowSize.isNarrowPhone) 16.dp else 24.dp
    var showTokenField by rememberSaveable { mutableStateOf(code.length > 6) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
            .imePadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            IconButton(
                onClick = onBackToSignIn,
                enabled = !busy,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Column(
            modifier = Modifier
                .widthIn(max = windowSize.authMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = horizontalPad)
                .padding(top = 12.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RecoveryNotices(busy = busy, error = errorMessage, message = statusMessage)
            if (offline) {
                SupportingText("Offline or reconnecting · check your connection.")
                Spacer(modifier = Modifier.height(10.dp))
            }

            Box(
                contentAlignment = Alignment.BottomEnd,
                modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(22.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_mail_check),
                        contentDescription = null,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .offset(x = 4.dp, y = 4.dp)
                        .background(MaterialTheme.colorScheme.secondary, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_shield_lock),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSecondary),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Text(
                text = headline,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (targetLabel.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = targetLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (!showTokenField && code.length <= 6) {
                OtpCodeInputRow(
                    code = code,
                    onCodeChange = onCodeChange,
                    enabled = !busy,
                    isError = isCodeError
                )
                if (isCodeError) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = codeErrorText,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (allowTokenPaste) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { showTokenField = true },
                        enabled = !busy,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(
                            text = "Paste confirmation link instead",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            } else {
                OutlinedTextField(
                    value = code,
                    onValueChange = { onCodeChange(it.trim()) },
                    label = { Text(codeFieldLabel) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = recoveryTextFieldColors(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            if (canVerify && !busy) onVerify()
                        }
                    ),
                    isError = isCodeError,
                    supportingText = if (isCodeError) {
                        { Text(codeErrorText) }
                    } else null
                )
                if (allowTokenPaste && code.length <= 6) {
                    TextButton(
                        onClick = { showTokenField = false },
                        enabled = !busy,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(
                            text = "Enter 6-digit code instead",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    focusManager.clearFocus()
                    onVerify()
                },
                enabled = canVerify && !busy,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.70f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = verifyButtonLabel,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(
                onClick = onResend,
                enabled = !busy && !offline && resendSeconds == 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text(
                    text = if (resendSeconds > 0) "$resendButtonLabel in ${resendSeconds}s" else resendButtonLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (!busy && !offline && resendSeconds == 0) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onBackToSignIn,
                enabled = !busy,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
            ) {
                Text(
                    text = "Back to sign in",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

/**
 * Screen 1: Forgot Password
 * Elevated card with a bottom accent line, hero badge with a question mark,
 * soft input field, primary Navy button, and secondary outlined Back to Sign In button.
 */
@Composable
fun ForgotPasswordCardScreen(
    state: MobileState,
    vm: MobileWorkspaceViewModel,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val windowSize = LocalTanawWindowSize.current
    val horizontalPad = if (windowSize.isNarrowPhone) 16.dp else 24.dp
    var email by rememberSaveable(state.email) { mutableStateOf(state.email) }
    val recoveryValid = MobileValidation.email(email) || MobileValidation.phone(email) != null
    val isRecoveryError = email.isNotBlank() && !recoveryValid

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = horizontalPad, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = windowSize.authMaxWidth)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RecoveryNotices(busy = state.busy, error = state.error, message = state.message)
            if (state.offline) {
                SupportingText("Offline or reconnecting · check your network connection.")
                Spacer(modifier = Modifier.height(12.dp))
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 6.dp,
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            contentAlignment = Alignment.TopEnd,
                            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                        shape = RoundedCornerShape(20.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_forgotpass),
                                    contentDescription = null,
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .offset(x = 6.dp, y = (-6).dp)
                                    .background(MaterialTheme.colorScheme.secondary, shape = CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "?",
                                    color = MaterialTheme.colorScheme.onSecondary,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        // Eyebrow / Test Identifier (satisfies compose.onNodeWithText("Reset password"))
                        Text(
                            text = "Reset password",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Forgot Password?",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Enter your email address or mobile number and we'll send you a code to reset your password.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Account phone number or email") },
                            placeholder = { Text("your@gmail.com or 0912 345 6789") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Email,
                                    contentDescription = null,
                                    tint = if (isRecoveryError) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = recoveryTextFieldColors(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (!state.busy && state.resendSeconds == 0 && recoveryValid) {
                                        focusManager.clearFocus()
                                        vm.resetEmail(email)
                                    }
                                }
                            ),
                            isError = isRecoveryError,
                            supportingText = if (isRecoveryError) {
                                { Text("Enter a valid email address or Philippine mobile number.") }
                            } else null
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                vm.resetEmail(email)
                            },
                            enabled = !state.busy && state.resendSeconds == 0 && recoveryValid,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                                disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.70f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                        ) {
                            Text(
                                text = "Request recovery code",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        if (state.resendSeconds > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Request another code in ${state.resendSeconds}s",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedButton(
                            onClick = { vm.page("login") },
                            enabled = !state.busy,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 50.dp)
                        ) {
                            Text(
                                text = "Back to sign in",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(MaterialTheme.colorScheme.secondary)
                    )
                }
            }
        }
    }
}

/**
 * Screen 2 (Step 2 of 3): Verify Code
 * Verifies the OTP or recovery token with the server BEFORE revealing password reset inputs.
 */
@Composable
fun VerifyRecoveryCodeScreen(
    state: MobileState,
    vm: MobileWorkspaceViewModel,
    modifier: Modifier = Modifier
) {
    var code by rememberSaveable(state.recoveryCode) { mutableStateOf(state.recoveryCode.orEmpty()) }
    val codeValid = vm.canVerifyRecoveryCode(code)

    VerifyRecoveryCodeContent(
        target = state.email,
        isPhoneTarget = PasswordRecoveryRules.isPhoneTarget(state.email),
        code = code,
        onCodeChange = { code = it },
        codeValid = codeValid,
        busy = state.busy,
        offline = state.offline,
        resendSeconds = state.resendSeconds,
        errorMessage = state.error,
        statusMessage = state.message,
        onVerifyCode = { vm.verifyRecoveryCode(code) },
        onResendCode = { vm.resetEmail(state.email) },
        onBackToRequest = { vm.page(PasswordRecoveryRules.PAGE_REQUEST) },
        onBackToSignIn = { vm.cancelRecovery() },
        modifier = modifier
    )
}

@Composable
internal fun VerifyRecoveryCodeContent(
    target: String,
    isPhoneTarget: Boolean,
    code: String,
    onCodeChange: (String) -> Unit,
    codeValid: Boolean,
    busy: Boolean,
    offline: Boolean,
    resendSeconds: Int,
    errorMessage: String?,
    statusMessage: String?,
    onVerifyCode: () -> Unit,
    onResendCode: () -> Unit,
    onBackToRequest: () -> Unit,
    onBackToSignIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val windowSize = LocalTanawWindowSize.current
    val horizontalPad = if (windowSize.isNarrowPhone) 16.dp else 24.dp
    var showTokenField by rememberSaveable(code.length > 6) { mutableStateOf(code.length > 6) }
    var verifyAttempted by rememberSaveable { mutableStateOf(false) }
    val isCodeError = verifyAttempted && !codeValid
    val canVerify = !busy && !offline && codeValid

    val submitVerification = {
        focusManager.clearFocus()
        if (codeValid) {
            verifyAttempted = false
            onVerifyCode()
        } else {
            verifyAttempted = true
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
            .imePadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            IconButton(
                onClick = onBackToRequest,
                enabled = !busy,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = "Verify Code",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Column(
            modifier = Modifier
                .widthIn(max = windowSize.authMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = horizontalPad)
                .padding(top = 8.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RecoveryStepHeader(
                step = 2,
                totalSteps = 3,
                label = "Verify code"
            )

            RecoveryNotices(busy = busy, error = errorMessage, message = statusMessage)
            if (offline) {
                SupportingText("Offline or reconnecting · check your connection.")
                Spacer(modifier = Modifier.height(10.dp))
            }

            Box(
                contentAlignment = Alignment.BottomEnd,
                modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(22.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isPhoneTarget) {
                        Icon(
                            imageVector = Icons.Outlined.Sms,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.ic_mail_check),
                            contentDescription = null,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .offset(x = 4.dp, y = 4.dp)
                        .background(MaterialTheme.colorScheme.secondary, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_shield_lock),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSecondary),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Text(
                text = if (isPhoneTarget) "Verify your number" else "Check your email",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isPhoneTarget) {
                    "Enter the 6-digit SMS code to unlock password reset."
                } else {
                    "Enter the 6-digit code or confirmation link to unlock password reset."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (target.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(
                        text = "Recovery requested for $target",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (!showTokenField && code.length <= 6) {
                OtpCodeInputRow(
                    code = code,
                    onCodeChange = {
                        verifyAttempted = false
                        onCodeChange(it)
                    },
                    enabled = !busy,
                    isError = isCodeError,
                    onDone = submitVerification
                )
                if (isCodeError) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isPhoneTarget) {
                            "Enter the 6-digit SMS verification code."
                        } else {
                            "Enter a valid 6-digit verification code or link."
                        },
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (!isPhoneTarget) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { showTokenField = true },
                        enabled = !busy,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(
                            text = "Paste confirmation link instead",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            } else {
                OutlinedTextField(
                    value = code,
                    onValueChange = {
                        verifyAttempted = false
                        onCodeChange(it.trim())
                    },
                    label = { Text("Recovery code (or original email link)") },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = recoveryTextFieldColors(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { submitVerification() }
                    ),
                    isError = isCodeError,
                    supportingText = if (isCodeError) {
                        { Text("Enter a valid recovery code or confirmation link.") }
                    } else null
                )
                if (!isPhoneTarget && code.length <= 6) {
                    TextButton(
                        onClick = { showTokenField = false },
                        enabled = !busy,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(
                            text = "Enter 6-digit code instead",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = submitVerification,
                enabled = canVerify,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.70f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Verify code",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Didn't receive the code? ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = onResendCode,
                    enabled = resendSeconds == 0 && !busy && !offline,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("request_recovery_code")
                ) {
                    Text(
                        text = if (resendSeconds > 0) {
                            "Resend in %02d:%02d".format(resendSeconds / 60, resendSeconds % 60)
                        } else {
                            "Resend code"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (resendSeconds == 0 && !busy && !offline) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            TextButton(
                onClick = onBackToSignIn,
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text(
                    text = "Back to sign in",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Screen 3 (Step 3 of 3): Set New Password
 * Unlocked only after the recovery code or deep-link session is verified by the server.
 */
@Composable
fun ResetPasswordScreen(
    state: MobileState,
    vm: MobileWorkspaceViewModel,
    modifier: Modifier = Modifier
) {
    ResetPasswordContent(
        target = state.email,
        busy = state.busy,
        offline = state.offline,
        errorMessage = state.error,
        statusMessage = state.message,
        onUpdatePassword = { password, confirm -> vm.completePasswordRecovery(password, confirm) },
        onBackToSignIn = { vm.cancelRecovery() },
        modifier = modifier
    )
}

@Composable
internal fun ResetPasswordContent(
    target: String,
    busy: Boolean,
    offline: Boolean,
    errorMessage: String?,
    statusMessage: String?,
    onUpdatePassword: (password: String, confirm: String) -> Unit,
    onBackToSignIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val windowSize = LocalTanawWindowSize.current
    val horizontalPad = if (windowSize.isNarrowPhone) 16.dp else 24.dp
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var confirmVisible by rememberSaveable { mutableStateOf(false) }

    val passwordTooShort = password.isNotEmpty() && password.length < 8
    val passwordMismatch = confirm.isNotEmpty() && password != confirm
    val canUpdatePassword = !busy && !offline && MobileValidation.password(password, confirm)
    val extendedColors = TanawTheme.extendedColors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
            .imePadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            IconButton(
                onClick = onBackToSignIn,
                enabled = !busy,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = "New Password",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Column(
            modifier = Modifier
                .widthIn(max = windowSize.authMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = horizontalPad)
                .padding(top = 8.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RecoveryStepHeader(
                step = 3,
                totalSteps = 3,
                label = "Set new password"
            )

            RecoveryNotices(busy = busy, error = errorMessage, message = statusMessage)
            if (offline) {
                SupportingText("Offline or reconnecting · reconnect before updating your password.")
                Spacer(modifier = Modifier.height(10.dp))
            }

            Box(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 18.dp)
                    .size(76.dp)
                    .background(
                        color = extendedColors.successBannerBackground,
                        shape = RoundedCornerShape(20.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.VerifiedUser,
                    contentDescription = null,
                    tint = extendedColors.onSuccessBannerText,
                    modifier = Modifier.size(38.dp)
                )
            }

            Text(
                text = "Create a new password",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (target.isNotBlank()) {
                Surface(
                    color = extendedColors.successBannerBackground,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = extendedColors.onSuccessBannerText,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Verified for $target",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = extendedColors.onSuccessBannerText
                        )
                    }
                }
            } else {
                Text(
                    text = "Your recovery code is verified. Choose a strong password to secure your account.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                enabled = !busy,
                label = { Text("New password (8+ characters)") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            painter = painterResource(
                                id = if (passwordVisible) R.drawable.ic_visibility else R.drawable.ic_visibility_off
                            ),
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = recoveryTextFieldColors(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                isError = passwordTooShort,
                supportingText = if (passwordTooShort) {
                    { Text("Password must be at least 8 characters.") }
                } else null
            )

            PasswordStrengthChecklist(password = password)

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = confirm,
                onValueChange = { confirm = it },
                enabled = !busy,
                label = { Text("Confirm new password") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { confirmVisible = !confirmVisible }) {
                        Icon(
                            painter = painterResource(
                                id = if (confirmVisible) R.drawable.ic_visibility else R.drawable.ic_visibility_off
                            ),
                            contentDescription = if (confirmVisible) "Hide password" else "Show password",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                visualTransformation = if (confirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = recoveryTextFieldColors(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (canUpdatePassword) {
                            focusManager.clearFocus()
                            onUpdatePassword(password, confirm)
                        }
                    }
                ),
                isError = passwordMismatch,
                supportingText = if (passwordMismatch) {
                    { Text("Passwords do not match.") }
                } else null
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    focusManager.clearFocus()
                    onUpdatePassword(password, confirm)
                },
                enabled = canUpdatePassword,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.70f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Update password",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = onBackToSignIn,
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text(
                    text = "Back to sign in",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@FormFactorPreviews
@Composable
private fun VerificationCodeScreenLightPreview() {
    TanawTheme {
        VerificationCodeScreen(
            title = "Verify Phone",
            headline = "Verify your number",
            subtitle = "Enter the 6-digit SMS code sent to",
            targetLabel = "+63 912 345 6789",
            code = "123",
            onCodeChange = {},
            onVerify = {},
            onResend = {},
            onBackToSignIn = {}
        )
    }
}

@FormFactorPreviews
@Composable
private fun VerifyRecoveryCodePhonePreview() {
    TanawTheme {
        VerifyRecoveryCodeContent(
            target = "09995130476",
            isPhoneTarget = true,
            code = "12",
            onCodeChange = {},
            codeValid = false,
            busy = false,
            offline = false,
            resendSeconds = 50,
            errorMessage = null,
            statusMessage = "If the account exists, a recovery SMS has been requested.",
            onVerifyCode = {},
            onResendCode = {},
            onBackToRequest = {},
            onBackToSignIn = {}
        )
    }
}

@FormFactorPreviews
@Composable
private fun VerifyRecoveryCodeEmailPreview() {
    TanawTheme {
        VerifyRecoveryCodeContent(
            target = "driver@example.com",
            isPhoneTarget = false,
            code = "",
            onCodeChange = {},
            codeValid = false,
            busy = false,
            offline = false,
            resendSeconds = 0,
            errorMessage = null,
            statusMessage = "If the account exists, a recovery email has been requested.",
            onVerifyCode = {},
            onResendCode = {},
            onBackToRequest = {},
            onBackToSignIn = {}
        )
    }
}

@FormFactorPreviews
@Composable
private fun ResetPasswordScreenLightPreview() {
    TanawTheme {
        ResetPasswordContent(
            target = "09995130476",
            busy = false,
            offline = false,
            errorMessage = null,
            statusMessage = "Code verified. Create a new password for your account.",
            onUpdatePassword = { _, _ -> },
            onBackToSignIn = {}
        )
    }
}
