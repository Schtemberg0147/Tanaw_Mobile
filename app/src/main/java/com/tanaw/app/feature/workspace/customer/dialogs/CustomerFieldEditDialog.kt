package com.tanaw.app.feature.workspace.customer.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tanaw.app.R
import com.tanaw.app.feature.workspace.customer.rules.CustomerAddressRules

@Composable
internal fun CustomerFieldEditDialog(
    fieldKey: String,
    fieldValue: String,
    barangayValue: String,
    cityValue: String,
    provinceValue: String,
    editError: String?,
    isBusy: Boolean,
    focusManager: FocusManager,
    onFieldValueChange: (String) -> Unit,
    onBarangayChange: (String) -> Unit,
    onCityChange: (String) -> Unit,
    onProvinceChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val title = when (fieldKey) {
        "full_name" -> stringResource(R.string.edit_full_name)
        "phone" -> stringResource(R.string.edit_contact_number)
        "address" -> stringResource(R.string.edit_default_address)
        else -> stringResource(R.string.edit_profile)
    }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = { Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (fieldKey) {
                    "full_name" -> {
                        OutlinedTextField(
                            value = fieldValue,
                            onValueChange = onFieldValueChange,
                            label = { Text(stringResource(R.string.full_name_label)) },
                            isError = editError != null,
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = textFieldColors,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { onSave() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    "phone" -> {
                        OutlinedTextField(
                            value = fieldValue,
                            onValueChange = onFieldValueChange,
                            label = { Text(stringResource(R.string.contact_number)) },
                            placeholder = { Text("09XXXXXXXXX") },
                            supportingText = { Text(stringResource(R.string.phone_change_sms_note)) },
                            isError = editError != null,
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = textFieldColors,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { onSave() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    "address" -> {
                        OutlinedTextField(
                            value = fieldValue,
                            onValueChange = onFieldValueChange,
                            label = { Text(stringResource(R.string.house_street_label)) },
                            isError = editError != null && (fieldValue.isBlank() || !CustomerAddressRules.validateStreet(fieldValue)),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = textFieldColors,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = barangayValue,
                            onValueChange = onBarangayChange,
                            label = { Text(stringResource(R.string.barangay_label)) },
                            isError = editError != null && barangayValue.isNotBlank() && !CustomerAddressRules.validateBarangay(barangayValue),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = textFieldColors,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = cityValue,
                            onValueChange = onCityChange,
                            label = { Text(stringResource(R.string.city_municipality_label)) },
                            isError = editError != null && (cityValue.isBlank() || !CustomerAddressRules.validateCity(cityValue)),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = textFieldColors,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = provinceValue,
                            onValueChange = onProvinceChange,
                            label = { Text(stringResource(R.string.province_label)) },
                            isError = editError != null && provinceValue.isNotBlank() && !CustomerAddressRules.validateProvince(provinceValue),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = textFieldColors,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { onSave() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (editError != null) {
                    Text(editError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = !isBusy,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(stringResource(R.string.save), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) {
                Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}
