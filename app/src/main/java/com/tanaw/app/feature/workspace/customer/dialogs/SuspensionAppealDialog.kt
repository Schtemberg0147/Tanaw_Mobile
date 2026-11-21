package com.tanaw.app.feature.workspace.customer.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tanaw.app.core.validation.MobileValidation
import com.tanaw.app.feature.workspace.customer.rules.CustomerNoShowRules
import com.tanaw.app.ui.theme.BorderGray
import com.tanaw.app.ui.theme.ExpiredRed
import com.tanaw.app.ui.theme.ExpiredRedBg
import com.tanaw.app.ui.theme.HintGray
import com.tanaw.app.ui.theme.InputGray
import com.tanaw.app.ui.theme.NavyPrimary
import com.tanaw.app.ui.theme.OutlineGray
import com.tanaw.app.ui.theme.TanawCustomerTheme
import com.tanaw.app.ui.theme.TanawDisplayFontFamily
import com.tanaw.app.ui.theme.TextDark

private val APPEAL_REASON_STARTERS = listOf(
    "Traffic or road delay at pickup",
    "Warehouse loading gate delay",
    "Missed driver call — updated contact",
    "Schedule mix-up with consignee"
)

/**
 * In-app suspension appeal form submitted by a suspended customer directly to office review.
 */
@Composable
internal fun SuspensionAppealDialog(
    initialPhone: String,
    isSubmitting: Boolean,
    offline: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSubmit: (reason: String, contactNumber: String?) -> Unit
) {
    val focusManager = LocalFocusManager.current
    var reason by rememberSaveable { mutableStateOf("") }
    var contactNumber by rememberSaveable(initialPhone) { mutableStateOf(initialPhone) }

    val trimmedLength = reason.trim().length
    val isReasonValid = CustomerNoShowRules.validateAppealReason(reason)
    val normalizedContact = contactNumber.trim().takeIf { it.isNotBlank() }?.let { MobileValidation.phone(it) }
    val isContactError = contactNumber.trim().isNotBlank() && normalizedContact == null
    val canSubmit = isReasonValid && !isContactError && !isSubmitting && !offline

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        shape = RoundedCornerShape(16.dp),
        containerColor = Color.White,
        title = {
            Text(
                text = "Submit Suspension Appeal",
                style = MaterialTheme.typography.titleLarge,
                fontFamily = TanawDisplayFontFamily,
                fontWeight = FontWeight.Bold,
                color = NavyPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Explain what happened during the recorded no-show incidents. Our administration team reviews appeals within 3–5 business days.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HintGray
                )

                if (!errorMessage.isNullOrBlank()) {
                    Surface(
                        color = ExpiredRedBg,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ExpiredRed.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = ExpiredRed,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    APPEAL_REASON_STARTERS.forEach { starter ->
                        SuggestionChip(
                            onClick = {
                                val trimmed = reason.trim()
                                val updated = when {
                                    trimmed.isEmpty() -> starter
                                    trimmed.contains(starter, ignoreCase = true) -> reason
                                    trimmed.endsWith(".") -> "$trimmed $starter"
                                    else -> "$trimmed. $starter"
                                }
                                if (updated.length <= CustomerNoShowRules.MAX_APPEAL_REASON_LENGTH) {
                                    reason = updated
                                }
                            },
                            enabled = !isSubmitting,
                            label = { Text(starter) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = reason,
                    onValueChange = { input ->
                        if (input.length <= CustomerNoShowRules.MAX_APPEAL_REASON_LENGTH) {
                            reason = input
                        }
                    },
                    label = { Text("Reason for appeal") },
                    placeholder = { Text("Share details, warehouse delays, or contact issues...") },
                    minLines = 4,
                    maxLines = 6,
                    enabled = !isSubmitting,
                    isError = reason.isNotEmpty() && !isReasonValid,
                    supportingText = {
                        if (reason.isNotEmpty() && trimmedLength < CustomerNoShowRules.MIN_APPEAL_REASON_LENGTH) {
                            Text("Enter at least ${CustomerNoShowRules.MIN_APPEAL_REASON_LENGTH} characters.")
                        } else {
                            Text("$trimmedLength/${CustomerNoShowRules.MAX_APPEAL_REASON_LENGTH} characters")
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = InputGray,
                        unfocusedContainerColor = InputGray,
                        focusedBorderColor = NavyPrimary,
                        unfocusedBorderColor = OutlineGray,
                        focusedTextColor = TextDark,
                        unfocusedTextColor = TextDark
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("AppealReasonInput")
                )

                OutlinedTextField(
                    value = contactNumber,
                    onValueChange = { contactNumber = it },
                    label = { Text("Best callback number (optional)") },
                    placeholder = { Text("0917 123 4567") },
                    singleLine = true,
                    enabled = !isSubmitting,
                    isError = isContactError,
                    supportingText = {
                        if (isContactError) {
                            Text("Enter a valid Philippine mobile number (e.g. 0917 123 4567).")
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    ),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = InputGray,
                        unfocusedContainerColor = InputGray,
                        focusedBorderColor = NavyPrimary,
                        unfocusedBorderColor = OutlineGray,
                        focusedTextColor = TextDark,
                        unfocusedTextColor = TextDark
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("AppealContactInput")
                )

                if (offline) {
                    Text(
                        text = "Network connection unavailable. Reconnect to submit your appeal.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ExpiredRed
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(reason.trim(), normalizedContact) },
                enabled = canSubmit,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NavyPrimary,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("SubmitAppealConfirmButton")
            ) {
                Text(
                    text = if (isSubmitting) "Submitting…" else "Submit Appeal",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isSubmitting,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, BorderGray),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancel", color = HintGray, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

@Preview(name = "Suspension Appeal Dialog", showBackground = true)
@Composable
private fun SuspensionAppealDialogPreview() {
    TanawCustomerTheme {
        SuspensionAppealDialog(
            initialPhone = "+639171234567",
            isSubmitting = false,
            offline = false,
            errorMessage = null,
            onDismiss = {},
            onSubmit = { _, _ -> }
        )
    }
}
