package com.tanaw.app.feature.workspace

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream
import kotlinx.serialization.json.JsonObject

internal object DriverEvidence {
    const val MAX_BYTES=5*1024*1024
    fun readPhoto(context:Context,uri:Uri):ByteArray {
        require(context.contentResolver.getType(uri) in listOf("image/jpeg","image/png","image/webp")) {"Choose a JPG, PNG or WebP photo."}
        val bytes=context.contentResolver.openInputStream(uri)?.use {input->
            val output=ByteArrayOutputStream();val buffer=ByteArray(8192)
            while(true){val count=input.read(buffer);if(count<0)break;require(output.size()+count<=MAX_BYTES){"Choose a photo no larger than 5 MB."};output.write(buffer,0,count)}
            output.toByteArray()
        } ?: error("Unable to read photo.")
        require(bytes.size in 1..MAX_BYTES) {"Choose a photo no larger than 5 MB."}
        return jpeg(bytes)
    }
    fun jpeg(bytes:ByteArray):ByteArray {
        val bitmap=ProofImage.decode(bytes,MAX_BYTES)
        return try {ByteArrayOutputStream().also {bitmap.compress(Bitmap.CompressFormat.JPEG,85,it)}.toByteArray().also {
            require(it.size in 1..MAX_BYTES) {"Prepared photo exceeds 5 MB. Choose a smaller photo."}
        }}finally {bitmap.recycle()}
    }
    fun status(record:JsonObject)=when(record.value("status")) {
        "pending"->"Pending office review";"approved"->"Approved";"rejected"->"Rejected";else->"Review status unavailable"
    }
}

@Composable internal fun DriverEvidenceRecords(shipment:String,details:JsonObject,busy:Boolean,view:((String,String,String)->Unit)?) {
    details.value("evidence_error").takeIf(String::isNotBlank)?.let {Text(it,color=MaterialTheme.colorScheme.error)}
    if(details["expenses"] is kotlinx.serialization.json.JsonArray && details.value("evidence_error").isBlank()) {
        Text("Trip expenses",style=MaterialTheme.typography.titleMedium)
        if(details.rows("expenses").isEmpty())Text("No expenses submitted for this trip.")
        details.rows("expenses").forEach {expense->
            val label=expense.value("description").ifBlank{expense.value("category")}
            Text("$label · ${MobilePresentation.money(expense.value("amount"))}")
            Text(DriverEvidence.status(expense))
            Text("Submitted ${MobilePresentation.time(expense.value("created_at"))}")
            expense.value("reviewed_at").takeIf(String::isNotBlank)?.let {Text("Reviewed ${MobilePresentation.time(it)}")}
            expense.value("review_reason").takeIf(String::isNotBlank)?.let {Text("Office feedback: $it")}
            if(expense.value("receipt_path").isNotBlank() && view!=null)TextButton(onClick={view(shipment,"expense",expense.value("id"))},enabled=!busy){Text("View receipt · $label")}
            else if(expense.value("receipt_path").isBlank())Text("No receipt attached")
            HorizontalDivider()
        }
    } else Text("Expense history unavailable. Refresh trip records to retry.")
    details.rows("incidents").forEach {incident->
        Text("${incident.value("category")} · ${incident.value("description")}")
        Text(if(incident.value("resolved_at").isBlank())"Incident awaiting office resolution" else "Incident resolved")
        details.rows("incident_evidence").filter {it.value("incident_id")==incident.value("id")}.forEach {photo->
            if(view!=null)TextButton(onClick={view(shipment,"incident",photo.value("id"))},enabled=!busy){Text("View incident photo ${photo.value("slot")}")}
        }
    }
}

@Composable internal fun IncidentPhotoPicker(photos:List<ByteArray>,busy:Boolean,onChange:(List<ByteArray>)->Unit) {
    val context=LocalContext.current
    var error by remember {mutableStateOf<String?>(null)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {uri->
        if(uri!=null)try {if(photos.size<3)onChange(photos+listOf(DriverEvidence.readPhoto(context,uri)));error=null}
        catch(failure:Exception){error=failure.message?:"Unable to prepare photo. Choose another image."}
    }
    Text("Private incident photos · ${photos.size}/3 · up to 5 MB each")
    DriverAsset(com.tanaw.app.R.drawable.figma_driver_f0840,20,18)
    OutlinedButton(onClick={picker.launch("image/*")},enabled=!busy && photos.size<3){Text("Add incident photo")}
    photos.forEachIndexed {index,bytes->
        val preview=remember(bytes){ProofImage.decode(bytes,DriverEvidence.MAX_BYTES)}
        DisposableEffect(preview){onDispose {preview.recycle()}}
        Image(preview.asImageBitmap(),"Incident photo ${index+1}",Modifier.fillMaxWidth().height(140.dp))
        TextButton(onClick={onChange(photos.filterIndexed {position,_->position!=index})},enabled=!busy){Text("Remove incident photo ${index+1}")}
    }
    error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
}
