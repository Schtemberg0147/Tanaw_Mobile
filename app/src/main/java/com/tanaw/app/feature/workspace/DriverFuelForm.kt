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
    val entry=FuelEntry.parse(liters,price,odometer,station)
    DriverTransaction("Record Fuel",state.busy,close,"Save Fuel Record",entry!=null,{entry?.let {submit(request,it,close)}}) {
        Text("Record actual fuel purchased for this delivery. Liters and unit price calculate the actual cost.")
        listOf(Triple("Liters",liters) {s:String->liters=s},Triple("Price per liter (PHP)",price) {s:String->price=s},Triple("Odometer (km)",odometer) {s:String->odometer=s}).forEach {(label,value,changed)->
            OutlinedTextField(value,changed,label={Text(label)},enabled=!state.busy,singleLine=true,modifier=Modifier.fillMaxWidth(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
        }
        OutlinedTextField(station,{station=it.take(200)},label={Text("Station (optional)")},enabled=!state.busy,modifier=Modifier.fillMaxWidth())
        Text("Total: ${entry?.let {MobilePresentation.money(it.total.toPlainString())}?:"Enter valid quantities"}")
        Text("Fuel records are separate from trip expenses. Avoid recording the same fuel cost again as an expense.")
        Text("Receipt attachment is not available for fuel records yet.")
        state.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
    }
}
