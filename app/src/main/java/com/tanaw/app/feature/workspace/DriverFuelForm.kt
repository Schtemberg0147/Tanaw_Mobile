package com.tanaw.app.feature.workspace

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
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
    fun requestClose(){if(unconfirmed)confirmClose=true else close()}
    DriverTransaction("Record Fuel",state.busy,::requestClose,if(unconfirmed)"Retry Fuel Record" else "Save Fuel Record",entry!=null && !state.offline,{
        entry?.let {record->
            unconfirmed=true
            submit(request,record){unconfirmed=false;close()}
        }
    }) {
        Text("Record actual fuel purchased for this delivery. Liters and unit price calculate the actual cost.")
        listOf(Triple("Liters",liters) {s:String->liters=s},Triple("Price per liter (PHP)",price) {s:String->price=s},Triple("Odometer (km)",odometer) {s:String->odometer=s}).forEach {(label,value,changed)->
            OutlinedTextField(value,changed,label={Text(label)},enabled=editable,singleLine=true,modifier=Modifier.fillMaxWidth(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
        }
        OutlinedTextField(station,{station=it.take(200)},label={Text("Station (optional)")},enabled=editable,modifier=Modifier.fillMaxWidth())
        Text("Total: ${entry?.let {MobilePresentation.money(it.total.toPlainString())}?:"Enter valid quantities"}")
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
