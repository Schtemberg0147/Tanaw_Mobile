package com.tanaw.app.feature.workspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonObject

/**
 * Renders the customer self-service cancellation section inside `ShipmentDetails`.
 * - If the shipment is eligible (`pending`, `booked`, or `assigned` before the driver starts the route),
 *   renders a "Cancel Shipment" button that opens `CustomerCancellationDialog`.
 * - If the driver is already on the way or the trip is in progress, renders an explanatory notice.
 */
@Composable
internal fun CustomerCancellationSection(
    shipment: JsonObject,
    deliveries: List<JsonObject>,
    state: MobileState,
    onCancelShipment: (shipmentId: String, reason: String, onCancelled: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    onClearError: () -> Unit = {}
) {
    val shipmentId = shipment.value("id")
    val trackingNumber = shipment.value("tracking_number")
    val canCancel = CustomerCancellationRules.canCancelShipment(shipment, deliveries)
    val lockReason = CustomerCancellationRules.ineligibilityReason(shipment, deliveries)
    var showDialog by rememberSaveable(shipmentId) { mutableStateOf(false) }

    if (canCancel && shipmentId.isNotBlank()) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = { showDialog = true },
                enabled = !state.busy && !state.offline,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(
                    1.dp,
                    if (!state.busy && !state.offline) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.error.copy(alpha = 0.38f)
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text("Cancel Shipment", fontWeight = FontWeight.SemiBold)
            }
            if (state.offline) {
                SupportingText("Reconnect to the internet to cancel this shipment.")
            } else {
                SupportingText("You can cancel this shipment before the assigned driver starts heading to the pickup location.")
            }
        }
    } else if (lockReason != null) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            shape = RoundedCornerShape(10.dp),
            modifier = modifier.fillMaxWidth()
        ) {
            Text(
                text = lockReason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp)
            )
        }
    }

    if (showDialog && canCancel && shipmentId.isNotBlank()) {
        CustomerCancellationDialog(
            shipmentId = shipmentId,
            trackingNumber = trackingNumber,
            state = state,
            onDismiss = {
                if (!state.busy) {
                    showDialog = false
                    onClearError()
                }
            },
            onConfirm = { reason ->
                onCancelShipment(shipmentId, reason) {
                    showDialog = false
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CustomerCancellationDialog(
    shipmentId: String,
    trackingNumber: String,
    state: MobileState,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    var selectedPreset by rememberSaveable(shipmentId) { mutableStateOf("") }
    var reasonText by rememberSaveable(shipmentId) { mutableStateOf("") }

    val isReasonValid = CustomerCancellationRules.validateReason(reasonText)
    val canSubmit = !state.busy && !state.offline && isReasonValid

    AlertDialog(
        onDismissRequest = {
            if (!state.busy) {
                focusManager.clearFocus()
                onDismiss()
            }
        },
        title = {
            Text(
                text = if (trackingNumber.isNotBlank()) "Cancel Shipment $trackingNumber?" else "Cancel Shipment?",
                fontWeight = FontWeight.Bold
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
                    text = "This action cannot be undone. Select a reason below or enter your own reason for cancelling this shipment.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = "Quick-select reason",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CustomerCancellationRules.PRESET_REASONS.forEach { preset ->
                        val isSelected = selectedPreset == preset && reasonText.trim() == preset
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (!state.busy) {
                                    selectedPreset = preset
                                    reasonText = preset
                                }
                            },
                            enabled = !state.busy,
                            label = { Text(preset) }
                        )
                    }
                    val isCustomSelected = selectedPreset == "Other"
                    FilterChip(
                        selected = isCustomSelected,
                        onClick = {
                            if (!state.busy) {
                                selectedPreset = "Other"
                                if (reasonText.trim() in CustomerCancellationRules.PRESET_REASONS) {
                                    reasonText = ""
                                }
                            }
                        },
                        enabled = !state.busy,
                        label = { Text("Other") }
                    )
                }

                OutlinedTextField(
                    value = reasonText,
                    onValueChange = { input ->
                        if (input.length <= CustomerCancellationRules.MAX_REASON_LENGTH) {
                            reasonText = input
                            val trimmed = input.trim()
                            selectedPreset = if (trimmed in CustomerCancellationRules.PRESET_REASONS) {
                                trimmed
                            } else if (trimmed.isNotEmpty()) {
                                "Other"
                            } else {
                                selectedPreset
                            }
                        }
                    },
                    label = { Text("Cancellation reason") },
                    placeholder = { Text("Enter reason for cancelling...") },
                    enabled = !state.busy,
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(8.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (canSubmit) {
                                focusManager.clearFocus()
                                onConfirm(CustomerCancellationRules.normalizeReason(reasonText))
                            }
                        }
                    ),
                    isError = reasonText.isNotEmpty() && !isReasonValid,
                    supportingText = {
                        val trimmedLength = reasonText.trim().length
                        if (reasonText.isNotEmpty() && trimmedLength < CustomerCancellationRules.MIN_REASON_LENGTH) {
                            Text("Please enter at least ${CustomerCancellationRules.MIN_REASON_LENGTH} characters.")
                        } else {
                            Text("$trimmedLength/${CustomerCancellationRules.MAX_REASON_LENGTH} characters")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (state.offline) {
                    Text(
                        text = "Network connection unavailable. Reconnect to cancel this shipment.",
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
                onClick = {
                    focusManager.clearFocus()
                    onConfirm(CustomerCancellationRules.normalizeReason(reasonText))
                },
                enabled = canSubmit,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Confirm Cancellation", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    focusManager.clearFocus()
                    onDismiss()
                },
                enabled = !state.busy
            ) {
                Text("Keep Shipment")
            }
        }
    )
}
