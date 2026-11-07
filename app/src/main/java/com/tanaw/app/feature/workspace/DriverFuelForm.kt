package com.tanaw.app.feature.workspace

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import java.math.BigDecimal
import java.util.UUID

@Composable internal fun DriverFuelForm(delivery:String,state:MobileState,close:()->Unit,submit:(String,FuelEntry,()->Unit)->Unit) {
    var liters by rememberSaveable(delivery){mutableStateOf("")}
    var price by rememberSaveable(delivery){mutableStateOf("")}
    var odometer by rememberSaveable(delivery){mutableStateOf("")}
    var station by rememberSaveable(delivery){mutableStateOf("")}
    val request=rememberSaveable(delivery){UUID.randomUUID().toString()}
    var unconfirmed by rememberSaveable(delivery){mutableStateOf(false)}
    var confirmClose by remember {mutableStateOf(false)}
    val entry=FuelEntry.parse(liters,price,odometer,station)
    val editable=!state.busy && !unconfirmed

    val litersDec = liters.trim().takeIf { it.matches(Regex("[0-9]{1,8}(\\.[0-9]{1,3})?")) }?.toBigDecimalOrNull()
    val isLitersError = liters.isNotBlank() && (litersDec == null || litersDec.scale() > 2 || litersDec <= BigDecimal.ZERO || litersDec > BigDecimal("10000"))

    val priceDec = price.trim().takeIf { it.matches(Regex("[0-9]{1,8}(\\.[0-9]{1,3})?")) }?.toBigDecimalOrNull()
    val isPriceError = price.isNotBlank() && (priceDec == null || priceDec.scale() > 2 || priceDec <= BigDecimal.ZERO || priceDec > BigDecimal("10000"))

    val odoDec = odometer.trim().takeIf { it.matches(Regex("[0-9]{1,8}(\\.[0-9]{1,3})?")) }?.toBigDecimalOrNull()
    val isOdoError = odometer.isNotBlank() && (odoDec == null || odoDec.scale() > 1 || odoDec < BigDecimal.ZERO || odoDec > BigDecimal("10000000"))

    val isTotalOverLimit = entry != null && entry.total > BigDecimal("1000000")

    fun requestClose(){if(unconfirmed)confirmClose=true else close()}
    DriverTransaction("Record Fuel",state.busy,::requestClose,if(unconfirmed)"Retry Fuel Record" else "Save Fuel Record",entry!=null && !state.offline,{
        entry?.let {record->
            unconfirmed=true
            submit(request,record){unconfirmed=false;close()}
        }
    }) {
        Text("Record actual fuel purchased for this delivery. Liters and unit price calculate the actual cost.")
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
            modifier = Modifier.fillMaxWidth(),
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
            modifier = Modifier.fillMaxWidth(),
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
            modifier = Modifier.fillMaxWidth(),
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
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done)
        )
        Text(
            text = "Total: ${entry?.let { MobilePresentation.money(it.total.toPlainString()) } ?: "Enter valid quantities"}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (isTotalOverLimit) MaterialTheme.colorScheme.error else DriverNavy
        )
        if (isTotalOverLimit) {
            Text("Total fuel cost cannot exceed ₱1,000,000.00.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Text("Fuel records are separate from trip expenses. Avoid recording the same fuel cost again as an expense.")
        Text("Receipt attachment is not available for fuel records yet.")
        if(unconfirmed)Text("Save unconfirmed. Retry the same quantities and request to confirm the record without a duplicate.")
        if(state.offline)Text("Offline. Reconnect before saving or retrying this fuel record.")
        state.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
    }
    if(confirmClose)AlertDialog(onDismissRequest={confirmClose=false},title={Text("Close unconfirmed fuel record?")},
        text={Text("This record may already be saved. Closing discards its retry information. Refresh the trip's Fuel records before creating another entry for this purchase.")},
        confirmButton={TextButton(onClick={confirmClose=false;close()}){Text("Close and check records")}},
        dismissButton={TextButton(onClick={confirmClose=false}){Text("Keep retrying")}})
}
