package com.tanaw.app.feature.auth.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tanaw.app.core.validation.MobileValidation
import com.tanaw.app.feature.auth.screens.PasswordStrengthChecklist
import com.tanaw.app.feature.workspace.driver.dialogs.DriverTransaction
import com.tanaw.app.feature.workspace.state.MobileState
import com.tanaw.app.ui.theme.TanawTheme

/** Kept above the mounted workspace so cancel returns to the exact Profile entry screen. */
@Composable
internal fun PasswordChangeDialog(
    state: MobileState,
    cancel: () -> Unit,
    request: () -> Unit,
    canVerify: (String) -> Boolean,
    update: (String, String, String) -> Unit,
    onEmailChange: ((String) -> Unit)? = null
) {
    var code by rememberSaveable(state.passwordChangeOwner, state.recoveryCode) {
        mutableStateOf(state.recoveryCode.orEmpty())
    }
    var password by rememberSaveable(state.passwordChangeOwner) { mutableStateOf("") }
    var confirm by rememberSaveable(state.passwordChangeOwner) { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var confirmVisible by rememberSaveable { mutableStateOf(false) }

    val sent = state.passwordChangeCodeSent
    val canEditEmail = onEmailChange != null && !sent
    val focusManager = LocalFocusManager.current
    val hasValidTarget = MobileValidation.email(state.email) || MobileValidation.phone(state.email) != null
    val canSubmit = !state.offline && if (sent) {
        canVerify(code) && MobileValidation.password(password, confirm)
    } else {
        state.resendSeconds == 0 && (!canEditEmail || hasValidTarget)
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
        errorContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        errorBorderColor = MaterialTheme.colorScheme.error,
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        errorTextColor = MaterialTheme.colorScheme.onSurface,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        cursorColor = MaterialTheme.colorScheme.primary
    )

    DriverTransaction(
        title = "Change password",
        busy = state.busy,
        close = cancel,
        label = if (sent) "UPDATE PASSWORD" else "REQUEST VERIFICATION CODE",
        enabled = canSubmit,
        submit = { if (sent) update(code, password, confirm) else request() }
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Verify your account with a code before changing your password. After a successful update, sign in again with your new password.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(14.dp)
            )
        }

        OutlinedTextField(
            value = state.email,
            onValueChange = { value -> if (canEditEmail && !state.busy) onEmailChange(value) },
            readOnly = !canEditEmail,
            enabled = !state.busy,
            label = { Text("Account phone number or email") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = fieldColors
        )

        if (sent) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Verification requested for your account.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(14.dp)
                )
            }

            val isCodeError = code.isNotBlank() && !canVerify(code)
            val passwordTooShort = password.isNotEmpty() && password.length < 8
            val passwordMismatch = confirm.isNotEmpty() && password != confirm

            OutlinedTextField(
                value = code,
                onValueChange = { code = it.trim() },
                enabled = !state.busy,
                label = { Text("Recovery code (or original email link)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                isError = isCodeError,
                supportingText = if (isCodeError) {
                    { Text("Enter a valid recovery code or confirmation link.") }
                } else null
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                enabled = !state.busy,
                label = { Text("New password (8+ characters)") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                modifier = Modifier.fillMaxWidth(),
                isError = passwordTooShort,
                supportingText = if (passwordTooShort) {
                    { Text("Password must be at least 8 characters.") }
                } else null
            )

            PasswordStrengthChecklist(password = password)

            OutlinedTextField(
                value = confirm,
                onValueChange = { confirm = it },
                enabled = !state.busy,
                label = { Text("Confirm new password") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { confirmVisible = !confirmVisible }) {
                        Icon(
                            imageVector = if (confirmVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                            contentDescription = if (confirmVisible) "Hide password" else "Show password",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                visualTransformation = if (confirmVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        if (canSubmit) update(code, password, confirm)
                    }
                ),
                modifier = Modifier.fillMaxWidth(),
                isError = passwordMismatch,
                supportingText = if (passwordMismatch) {
                    { Text("Passwords do not match.") }
                } else null
            )

            TextButton(
                onClick = request,
                enabled = !state.busy && !state.offline && state.resendSeconds == 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text(
                    text = if (state.resendSeconds > 0) {
                        "Request another code in ${state.resendSeconds}s"
                    } else {
                        "Request another verification code"
                    },
                    color = if (!state.busy && !state.offline && state.resendSeconds == 0) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (state.offline) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
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
                        text = "Reconnect and refresh before requesting a code or updating your password.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        state.message?.let { message ->
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        state.error?.let { error ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
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
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        TextButton(
            onClick = cancel,
            enabled = !state.busy,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Text(
                text = "Back to Profile",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Preview(showBackground = true, name = "Password Change")
@Composable
private fun PasswordChangeDialogLightPreview() {
    TanawTheme {
        PasswordChangeDialog(
            state = MobileState(
                email = "09123456789",
                passwordChangeOwner = "user-1",
                passwordChangeCodeSent = true
            ),
            cancel = {},
            request = {},
            canVerify = { it.length == 6 },
            update = { _, _, _ -> }
        )
    }
}
