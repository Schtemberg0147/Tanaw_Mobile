package com.tanaw.app.feature.workspace.customer.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tanaw.app.R
import com.tanaw.app.feature.workspace.customer.components.AddressQuickAssistBar
import com.tanaw.app.feature.workspace.customer.components.CitySuggestionStrip
import com.tanaw.app.feature.workspace.customer.rules.CustomerAddressRules
import com.tanaw.app.ui.theme.TanawCustomerTheme
import com.tanaw.app.ui.theme.TanawDisplayFontFamily
import com.tanaw.app.ui.theme.TanawMonoFontFamily

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
        errorBorderColor = MaterialTheme.colorScheme.error,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.background,
        title = {
            Text(
                text = title,
                fontFamily = TanawDisplayFontFamily,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        },
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
                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = TanawMonoFontFamily),
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
                        val addressSummary = CustomerAddressRules.StructuredCustomerAddress(
                            streetAddress = fieldValue,
                            barangay = barangayValue,
                            city = cityValue,
                            province = provinceValue,
                        ).fullDisplayAddress()
                        AddressQuickAssistBar(
                            currentAddressSummary = addressSummary,
                            enabled = !isBusy,
                            showInlineSearch = false,
                            onAddressAutofilled = { resolved ->
                                if (resolved.streetAddress.isNotBlank()) onFieldValueChange(resolved.streetAddress)
                                if (resolved.barangay.isNotBlank()) onBarangayChange(resolved.barangay)
                                if (resolved.city.isNotBlank()) onCityChange(resolved.city)
                                if (resolved.province.isNotBlank()) onProvinceChange(resolved.province)
                            },
                        )

                        val streetOverLimit = fieldValue.isNotBlank() && !CustomerAddressRules.validateStreet(fieldValue)
                        val streetError = streetOverLimit || (editError != null && fieldValue.isBlank())
                        OutlinedTextField(
                            value = fieldValue,
                            onValueChange = onFieldValueChange,
                            label = { Text(stringResource(R.string.house_street_label)) },
                            isError = streetError,
                            supportingText = if (streetOverLimit) {
                                { Text(stringResource(R.string.street_length_error)) }
                            } else null,
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = textFieldColors,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        val brgyOverLimit = barangayValue.isNotBlank() && !CustomerAddressRules.validateBarangay(barangayValue)
                        OutlinedTextField(
                            value = barangayValue,
                            onValueChange = onBarangayChange,
                            label = { Text(stringResource(R.string.barangay_label)) },
                            isError = brgyOverLimit,
                            supportingText = if (brgyOverLimit) {
                                { Text(stringResource(R.string.barangay_length_error)) }
                            } else null,
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = textFieldColors,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        val cityOverLimit = cityValue.isNotBlank() && !CustomerAddressRules.validateCity(cityValue)
                        val cityError = cityOverLimit || (editError != null && cityValue.isBlank())
                        OutlinedTextField(
                            value = cityValue,
                            onValueChange = onCityChange,
                            label = { Text(stringResource(R.string.city_municipality_label)) },
                            isError = cityError,
                            supportingText = if (cityOverLimit) {
                                { Text(stringResource(R.string.city_length_error)) }
                            } else null,
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = textFieldColors,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        CitySuggestionStrip(
                            cityQuery = cityValue,
                            enabled = !isBusy,
                            onSelectCity = { suggestion ->
                                onCityChange(suggestion.city)
                                onProvinceChange(suggestion.province)
                            },
                        )

                        val provOverLimit = provinceValue.isNotBlank() && !CustomerAddressRules.validateProvince(provinceValue)
                        OutlinedTextField(
                            value = provinceValue,
                            onValueChange = onProvinceChange,
                            label = { Text(stringResource(R.string.province_label)) },
                            isError = provOverLimit,
                            supportingText = if (provOverLimit) {
                                { Text(stringResource(R.string.province_length_error)) }
                            } else null,
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
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.save), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isBusy,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}

@Preview(showBackground = true, name = "Edit Address Dialog")
@Composable
private fun CustomerFieldEditAddressDialogPreview() {
    TanawCustomerTheme {
        CustomerFieldEditDialog(
            fieldKey = "address",
            fieldValue = "123 Maharlika Highway",
            barangayValue = "Valdefuente",
            cityValue = "Cabanatuan City",
            provinceValue = "Nueva Ecija",
            editError = null,
            isBusy = false,
            focusManager = LocalFocusManager.current,
            onFieldValueChange = {},
            onBarangayChange = {},
            onCityChange = {},
            onProvinceChange = {},
            onDismiss = {},
            onSave = {},
        )
    }
}
