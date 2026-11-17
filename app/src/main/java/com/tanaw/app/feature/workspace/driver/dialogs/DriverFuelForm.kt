package com.tanaw.app.feature.workspace.driver.dialogs

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tanaw.app.feature.workspace.driver.rules.FuelEntry
import com.tanaw.app.feature.workspace.driver.screens.DriverTheme
import com.tanaw.app.feature.workspace.rules.MobilePresentation
import com.tanaw.app.feature.workspace.state.MobileState
import java.math.BigDecimal
import java.util.UUID

@Composable
internal fun DriverFuelForm(
    delivery: String,
    state: MobileState,
    close: () -> Unit,
    submit: (String, FuelEntry, () -> Unit) -> Unit
) {
    var liters by rememberSaveable(delivery) { mutableStateOf("") }
    var price by rememberSaveable(delivery) { mutableStateOf("") }
    var odometer by rememberSaveable(delivery) { mutableStateOf("") }
    var station by rememberSaveable(delivery) { mutableStateOf("") }
    val request = rememberSaveable(delivery) { UUID.randomUUID().toString() }
    var unconfirmed by rememberSaveable(delivery) { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    val entry = FuelEntry.parse(liters, price, odometer, station)
    val editable = !state.busy && !unconfirmed

    val litersDec = liters.trim().takeIf { it.matches(Regex("[0-9]{1,8}(\\.[0-9]{1,3})?")) }?.toBigDecimalOrNull()
    val isLitersError = liters.isNotBlank() && (litersDec == null || litersDec.scale() > 2 || litersDec <= BigDecimal.ZERO || litersDec > BigDecimal("10000"))

    val priceDec = price.trim().takeIf { it.matches(Regex("[0-9]{1,8}(\\.[0-9]{1,3})?")) }?.toBigDecimalOrNull()
    val isPriceError = price.isNotBlank() && (priceDec == null || priceDec.scale() > 2 || priceDec <= BigDecimal.ZERO || priceDec > BigDecimal("10000"))

    val odoDec = odometer.trim().takeIf { it.matches(Regex("[0-9]{1,8}(\\.[0-9]{1,3})?")) }?.toBigDecimalOrNull()
    val isOdoError = odometer.isNotBlank() && (odoDec == null || odoDec.scale() > 1 || odoDec < BigDecimal.ZERO || odoDec > BigDecimal("10000000"))

    val isTotalOverLimit = entry != null && entry.total > BigDecimal("1000000")

    fun requestClose() {
        if (unconfirmed) confirmClose = true else close()
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        errorBorderColor = MaterialTheme.colorScheme.error
    )

    DriverTransaction(
        title = "Record Fuel",
        busy = state.busy,
        close = ::requestClose,
        label = if (unconfirmed) "Retry Fuel Record" else "Save Fuel Record",
        enabled = entry != null && !state.offline,
        submit = {
            entry?.let { record ->
                unconfirmed = true
                submit(request, record) {
                    unconfirmed = false
                    close()
                }
            }
        }
    ) {
        Text(
            text = "Record actual fuel purchased for this delivery. Liters and unit price calculate the actual cost.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = liters,
                    onValueChange = { liters = it },
                    label = { Text("Liters") },
                    placeholder = { Text("e.g. 45.50") },
                    enabled = editable,
                    singleLine = true,
                    isError = isLitersError,
                    supportingText = if (isLitersError) {
                        { Text("Enter valid liters between 0.01 and 10,000.00.") }
                    } else null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Price per liter (PHP)") },
                    placeholder = { Text("e.g. 62.50") },
                    enabled = editable,
                    singleLine = true,
                    isError = isPriceError,
                    supportingText = if (isPriceError) {
                        { Text("Enter valid unit price between ₱0.01 and ₱10,000.00.") }
                    } else null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = odometer,
                    onValueChange = { odometer = it },
                    label = { Text("Odometer (km)") },
                    placeholder = { Text("e.g. 125430.5") },
                    enabled = editable,
                    singleLine = true,
                    isError = isOdoError,
                    supportingText = if (isOdoError) {
                        { Text("Enter odometer reading between 0 and 10,000,000.0 km.") }
                    } else null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = station,
                    onValueChange = { station = it.take(200) },
                    label = { Text("Station (optional)") },
                    placeholder = { Text("e.g. Shell Maharlika") },
                    enabled = editable,
                    singleLine = true,
                    supportingText = { Text("${station.length}/200 characters") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done)
                )
            }
        }

        // Total Cost Summary Banner
        val summaryBg = if (isTotalOverLimit) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        }
        val summaryFg = if (isTotalOverLimit) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        }
        Surface(
            color = summaryBg,
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "TOTAL COST",
                        style = MaterialTheme.typography.labelSmall,
                        color = summaryFg.copy(alpha = 0.85f)
                    )
                    Text(
                        text = "Total: ${entry?.let { MobilePresentation.money(it.total.toPlainString()) } ?: "Enter valid quantities"}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = summaryFg
                    )
                }
            }
        }

        if (isTotalOverLimit) {
            Text(
                text = "Total fuel cost cannot exceed ₱1,000,000.00.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Fuel records are separate from trip expenses. Avoid recording the same fuel cost again as an expense.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Receipt attachment is not available for fuel records yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (unconfirmed) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "Save unconfirmed. Retry the same quantities and request to confirm the record without a duplicate.",
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        if (state.offline) {
            Text(
                text = "Offline. Reconnect before saving or retrying this fuel record.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        state.error?.let {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = it,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }

    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            shape = RoundedCornerShape(16.dp),
            title = { Text("Close unconfirmed fuel record?", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    text = "This record may already be saved. Closing discards its retry information. Refresh the trip's Fuel records before creating another entry for this purchase.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClose = false
                        close()
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(
                        text = "Close and check records",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmClose = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Keep retrying", style = MaterialTheme.typography.labelLarge)
                }
            }
        )
    }
}

@Preview(showBackground = true, name = "Driver Fuel Form Light")
@Composable
private fun DriverFuelFormLightPreview() {
    DriverTheme(darkTheme = false) {
        DriverFuelForm("fixture", MobileState(), {}) { _, _, _ -> }
    }
}

@Preview(showBackground = true, name = "Driver Fuel Form Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DriverFuelFormDarkPreview() {
    DriverTheme(darkTheme = true) {
        DriverFuelForm("fixture", MobileState(), {}) { _, _, _ -> }
    }
}
