package com.tanaw.app.feature.workspace

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Dialog prompting for the 6-digit SMS OTP when a customer requests a change to their login/contact phone number.
 * Once verified, updates Supabase Auth phone and public customer/user profile in a single atomic flow.
 */
@Composable
internal fun PhoneChangeDialog(
    state: MobileState,
    onVerify: (String) -> Unit,
    onResend: () -> Unit,
    onCancel: () -> Unit
) {
    var code by rememberSaveable(state.phoneChangeTarget) { mutableStateOf("") }
    val targetPhone = state.phoneChangeTarget.orEmpty()
    val isValidCode = code.length in 6..8 && code.all { it.isDigit() }
    val canSubmit = !state.busy && !state.offline && isValidCode

    AlertDialog(
        onDismissRequest = { if (!state.busy) onCancel() },
        title = {
            Text(
                text = "Verify New Phone Number",
                fontWeight = FontWeight.Bold,
                color = DriverNavy
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Enter the 6-digit SMS code sent to $targetPhone. Your login and SMS alert number will update once verified.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DriverNavy
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }.take(8)
                        code = digits
                    },
                    label = { Text("6-digit verification code") },
                    placeholder = { Text("123456") },
                    singleLine = true,
                    enabled = !state.busy,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { if (canSubmit) onVerify(code) }
                    ),
                    isError = code.isNotEmpty() && !isValidCode,
                    supportingText = if (code.isNotEmpty() && !isValidCode) {
                        { Text("Enter the 6-digit numeric code received via SMS.") }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )

                if (state.resendSeconds > 0) {
                    Text(
                        text = "Resend code in ${state.resendSeconds}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = DriverMuted
                    )
                } else {
                    TextButton(
                        onClick = onResend,
                        enabled = !state.busy && !state.offline,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Resend SMS code", color = DriverNavy, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (state.offline) {
                    Text(
                        text = "Network connection unavailable. Reconnect to verify.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                state.error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (state.busy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onVerify(code) },
                enabled = canSubmit,
                colors = ButtonDefaults.buttonColors(containerColor = DriverNavy),
                shape = MaterialTheme.shapes.small
            ) {
                Text("Verify & Save Number", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                enabled = !state.busy
            ) {
                Text("Cancel", color = DriverMuted)
            }
        }
    )
}
