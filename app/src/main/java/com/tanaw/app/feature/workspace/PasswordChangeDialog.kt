package com.tanaw.app.feature.workspace

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.tanaw.app.ui.theme.*

/** Kept above the mounted workspace so cancel returns to the exact Profile entry screen. */
@Composable internal fun PasswordChangeDialog(
    state: MobileState,
    cancel: () -> Unit,
    request: () -> Unit,
    canVerify: (String) -> Boolean,
    update: (String, String, String) -> Unit
) {
    var code by rememberSaveable(state.passwordChangeOwner, state.recoveryCode) { mutableStateOf(state.recoveryCode.orEmpty()) }
    var password by rememberSaveable(state.passwordChangeOwner) { mutableStateOf("") }
    var confirm by rememberSaveable(state.passwordChangeOwner) { mutableStateOf("") }
    val sent = state.passwordChangeCodeSent
    val focusManager = LocalFocusManager.current
    val canSubmit = !state.offline && if (sent) canVerify(code) && MobileValidation.password(password, confirm) else state.resendSeconds == 0

    DriverTransaction(
        "Change password",
        state.busy,
        cancel,
        if (sent) "UPDATE PASSWORD" else "REQUEST VERIFICATION CODE",
        canSubmit,
        { if (sent) update(code, password, confirm) else request() }
    ) {
        Text(
            text = "Verify your account with a code before changing your password. After a successful update, sign in again with your new password.",
            style = MaterialTheme.typography.bodySmall,
            color = HintGray
        )
        OutlinedTextField(
            value = state.email,
            onValueChange = {},
            readOnly = true,
            label = { Text("Account phone number or email") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = InputGray,
                unfocusedContainerColor = InputGray,
                focusedBorderColor = NavyPrimary,
                unfocusedBorderColor = BorderGray
            )
        )
        if (sent) {
            Text(
                text = "Verification requested for your account.",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = NavyPrimary
            )
            val isCodeError = code.isNotBlank() && !canVerify(code)
            val passwordTooShort = password.isNotEmpty() && password.length < 8
            val passwordMismatch = confirm.isNotEmpty() && password != confirm

            OutlinedTextField(
                value = code,
                onValueChange = { code = it.trim() },
                enabled = !state.busy,
                label = { Text("Recovery code (or original email link)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = InputGray,
                    unfocusedContainerColor = InputGray,
                    focusedBorderColor = NavyPrimary,
                    unfocusedBorderColor = BorderGray
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                isError = isCodeError,
                supportingText = if (isCodeError) { { Text("Enter a valid recovery code or confirmation link.") } } else null
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                enabled = !state.busy,
                label = { Text("New password (8+ characters)") },
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = InputGray,
                    unfocusedContainerColor = InputGray,
                    focusedBorderColor = NavyPrimary,
                    unfocusedBorderColor = BorderGray
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth(),
                isError = passwordTooShort,
                supportingText = if (passwordTooShort) { { Text("Password must be at least 8 characters.") } } else null
            )
            OutlinedTextField(
                value = confirm,
                onValueChange = { confirm = it },
                enabled = !state.busy,
                label = { Text("Confirm new password") },
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = InputGray,
                    unfocusedContainerColor = InputGray,
                    focusedBorderColor = NavyPrimary,
                    unfocusedBorderColor = BorderGray
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (canSubmit) update(code, password, confirm) }),
                modifier = Modifier.fillMaxWidth(),
                isError = passwordMismatch,
                supportingText = if (passwordMismatch) { { Text("Passwords do not match.") } } else null
            )
            TextButton(onClick = request, enabled = !state.busy && !state.offline && state.resendSeconds == 0) {
                Text(
                    text = if (state.resendSeconds > 0) "Request another code in ${state.resendSeconds}s" else "Request another verification code",
                    color = if (!state.busy && !state.offline && state.resendSeconds == 0) OrangeAccent else HintGray,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        if (state.offline) {
            Text("Reconnect and refresh before requesting a code or updating your password.", color = MaterialTheme.colorScheme.error)
        }
        state.message?.let { Text(it, color = NavyPrimary, fontWeight = FontWeight.SemiBold) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = cancel, enabled = !state.busy) {
            Text("Back to Profile", color = NavyPrimary, fontWeight = FontWeight.Bold)
        }
    }
}
