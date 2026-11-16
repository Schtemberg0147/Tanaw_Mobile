package com.tanaw.app.feature.workspace.customer.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanaw.app.R
import com.tanaw.app.core.validation.MobileValidation
import com.tanaw.app.feature.workspace.customer.components.RouteSummaryCard
import com.tanaw.app.feature.workspace.customer.rules.CustomerBookingRules
import com.tanaw.app.feature.workspace.model.RoutePreview
import com.tanaw.app.feature.workspace.viewmodel.value
import com.tanaw.app.ui.theme.TanawTheme
import kotlinx.serialization.json.JsonObject

// ─── Add More Details Screen (Image 1, Screen 2) ──────────────────────────────
@Composable
fun AddMoreDetailsScreen(
    pickup: String,
    dropoff: String,
    routePreview: RoutePreview?,
    vehicles: List<JsonObject>,
    selectedVehicle: JsonObject?,
    contact: String,
    cargo: String,
    weight: String,
    handling: String,
    notes: String,
    busy: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onEditRoute: () -> Unit,
    onContactChange: (String) -> Unit,
    onCargoChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
    onSelectVehicle: (String) -> Unit,
    onHandlingChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onConfirm: () -> Unit
) {
    val parsedWeight = MobileValidation.weight(weight)
    val normalizedContact = MobileValidation.phone(contact)
    val vehicleCap = selectedVehicle?.value("default_capacity_kg").orEmpty()
    val weightFits = selectedVehicle != null && CustomerBookingRules.weightFitsCapacity(weight, vehicleCap)
    val focusManager = LocalFocusManager.current
    val canConfirm = !busy &&
        pickup.trim().length in 5..500 &&
        dropoff.trim().length in 5..500 &&
        !pickup.trim().equals(dropoff.trim(), ignoreCase = true) &&
        cargo.trim().length in 3..1000 &&
        parsedWeight != null &&
        weightFits &&
        normalizedContact != null &&
        notes.length <= 2000

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, enabled = !busy) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = stringResource(R.string.add_more_details),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                LinearProgressIndicator(
                    progress = { 0.66f },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.total_fee_label),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.awaiting_office_quotation),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Button(
                        onClick = onConfirm,
                        enabled = canConfirm,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("ConfirmDetailsButton")
                    ) {
                        Text(
                            text = stringResource(R.string.confirm_action),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            errorMessage?.let {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = it,
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Route Summary Card
            RouteSummaryCard(
                pickup = pickup,
                dropoff = dropoff,
                routePreview = routePreview,
                onEdit = onEditRoute
            )

            // Cargo Details Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                shadowElevation = 1.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.cargo_details),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // ORDER CONTACT NUMBER
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.order_contact_number),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = contact,
                            onValueChange = onContactChange,
                            placeholder = { Text("0912 345 6789") },
                            singleLine = true,
                            isError = contact.isNotBlank() && normalizedContact == null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("OrderContactInput")
                        )
                        if (contact.isNotBlank() && normalizedContact == null) {
                            Text(
                                text = "Enter a valid Philippine mobile number (e.g. 0912 345 6789).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // CARGO DESCRIPTION
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.cargo_description_label),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${cargo.trim().length}/1000",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (cargo.isNotEmpty() && cargo.trim().length < 3) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                        val isCargoError = cargo.isNotEmpty() && cargo.trim().length < 3
                        OutlinedTextField(
                            value = cargo,
                            onValueChange = onCargoChange,
                            placeholder = { Text(stringResource(R.string.cargo_description_placeholder)) },
                            singleLine = true,
                            isError = isCargoError,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("CargoDescriptionInput")
                        )
                        if (isCargoError) {
                            Text(
                                text = "Cargo description must be at least 3 characters.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // EST. WEIGHT (kg)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.est_weight_label),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val isWeightInvalid = weight.isNotBlank() && (parsedWeight == null || parsedWeight <= 0 || !weightFits)
                        OutlinedTextField(
                            value = weight,
                            onValueChange = onWeightChange,
                            placeholder = { Text("0") },
                            suffix = { Text("kg", fontWeight = FontWeight.SemiBold) },
                            singleLine = true,
                            isError = isWeightInvalid,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("EstWeightInput")
                        )
                        val rawWeight = weight.toDoubleOrNull()
                        if (weight.isNotBlank() && rawWeight != null && rawWeight > 100000.0) {
                            Text(
                                text = "Cargo weight cannot exceed 100,000 kg.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else if (weight.isNotBlank() && (parsedWeight == null || parsedWeight <= 0)) {
                            Text(
                                text = "Enter a valid positive cargo weight in kilograms.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else if (selectedVehicle != null) {
                            val capKgVal = vehicleCap.toDoubleOrNull() ?: 0.0
                            val exceededText = if (parsedWeight != null && parsedWeight > capKgVal) {
                                "Weight exceeds ${selectedVehicle.value("name")}'s max limit of ${CustomerBookingRules.formatCapacity(vehicleCap)}."
                            } else {
                                "Selected vehicle: ${selectedVehicle.value("name")} (${CustomerBookingRules.formatCapacity(vehicleCap)})"
                            }
                            Text(
                                text = exceededText,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (parsedWeight != null && !weightFits) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (parsedWeight != null && !weightFits) {
                            val larger = vehicles.filter {
                                CustomerBookingRules.weightFitsCapacity(weight, it.value("default_capacity_kg"))
                            }
                            if (larger.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    larger.forEach { v ->
                                        AssistChip(
                                            onClick = { onSelectVehicle(v.value("id")) },
                                            label = { Text("Switch to ${v.value("name")}") }
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = "Weight exceeds available vehicle capacities. Contact the office for heavy cargo.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    // SPECIAL HANDLING Chips
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.special_handling_label),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "None",
                                "Fragile",
                                "Keep Dry",
                                "Temperature Sensitive",
                                "Perishable",
                                "Cold Chain (Refrigerated)"
                            ).forEach { option ->
                                FilterChip(
                                    selected = handling == option,
                                    onClick = { onHandlingChange(option) },
                                    label = { Text(option) }
                                )
                            }
                        }
                    }

                    // NOTES TO DRIVER
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.notes_to_driver_label),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${notes.length}/2000",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (notes.length > 2000) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                        val isNotesError = notes.length > 2000
                        OutlinedTextField(
                            value = notes,
                            onValueChange = onNotesChange,
                            placeholder = { Text(stringResource(R.string.notes_to_driver_placeholder)) },
                            minLines = 3,
                            maxLines = 5,
                            isError = isNotesError,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("NotesToDriverInput")
                        )
                        if (isNotesError) {
                            Text(
                                text = "Notes to driver must be 2000 characters or fewer.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Add More Details Preview")
@Composable
private fun AddMoreDetailsPreview() {
    TanawTheme(darkTheme = false) {
        AddMoreDetailsScreen(
            pickup = "Cabanatuan City Public Market",
            dropoff = "Manila North Harbor, Manila",
            routePreview = RoutePreview("{}", 118.4, 145),
            vehicles = previewVehicles,
            selectedVehicle = previewVehicles[1],
            contact = "09171234567",
            cargo = "50 sacks of milled rice",
            weight = "2500",
            handling = "Keep Dry",
            notes = "Gate 3, call upon arrival",
            busy = false,
            errorMessage = null,
            onBack = {},
            onEditRoute = {},
            onContactChange = {},
            onCargoChange = {},
            onWeightChange = {},
            onSelectVehicle = {},
            onHandlingChange = {},
            onNotesChange = {},
            onConfirm = {}
        )
    }
}
