package com.tanaw.app.feature.workspace

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.tanaw.app.R
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

@Composable internal fun DriverTransaction(title:String,busy:Boolean,close:()->Unit,
    label:String,enabled:Boolean,submit:()->Unit,content:@Composable ColumnScope.()->Unit) {
    Dialog(onDismissRequest={if(!busy)close()},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        DriverTheme {
            BackHandler {if(!busy)close()}
            Scaffold(containerColor=DriverBackground,topBar={Column(Modifier.statusBarsPadding()) {DriverPageHeading(title){if(!busy)close()}}},
                bottomBar={Surface(color=Color.White,shadowElevation=3.dp) {Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                    DriverButton(if(busy)"Submitting…" else label,enabled && !busy,submit)
                }}}) {padding->
                Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement=Arrangement.spacedBy(16.dp),content=content)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun DriverIncidentDialog(id:String,state:MobileState,vm:MobileWorkspaceViewModel,close:()->Unit) {
    DriverIncidentForm(id,state,close) {request,category,description,minutes->vm.reportIncident(id,request,category,description,minutes,close)}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun DriverIncidentForm(id:String,state:MobileState,close:()->Unit,submit:(String,String,String,Int)->Unit) {
    var category by rememberSaveable(id){mutableStateOf("traffic")}
    var description by rememberSaveable(id){mutableStateOf("")}
    var minutes by rememberSaveable(id){mutableStateOf("0")}
    val request=rememberSaveable(id){UUID.randomUUID().toString()}
    DriverTransaction("Report an Issue",state.busy,close,"Submit Report",
        description.trim().length in 5..2000 && minutes.toIntOrNull() in 0..10080,
        {submit(request,category,description,minutes.toInt())}) {
        Text("SELECT ISSUE TYPE",fontSize=12.sp,color=Color(0xFF536581),fontWeight=FontWeight.SemiBold)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("traffic" to "Traffic/Delay","breakdown" to "Vehicle Issue","accident" to "Safety Concern","weather" to "Weather","other" to "Other").forEach {(value,label)->
                FilterChip(selected=category==value,onClick={category=value},enabled=!state.busy,label={Text(label)},shape=RoundedCornerShape(2.dp),
                    colors=FilterChipDefaults.filterChipColors(selectedContainerColor=DriverNavy,selectedLabelColor=Color.White))
            }
        }
        Text("DESCRIPTION",fontSize=12.sp,color=Color(0xFF536581),fontWeight=FontWeight.SemiBold)
        OutlinedTextField(description,{description=it},placeholder={Text("Describe the issue in detail…")},enabled=!state.busy,
            modifier=Modifier.fillMaxWidth().heightIn(min=160.dp),shape=RoundedCornerShape(2.dp))
        OutlinedTextField(minutes,{minutes=it},label={Text("Expected delay in minutes")},enabled=!state.busy,singleLine=true,
            modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(2.dp),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Number))
        Text("ATTACH PHOTOS (Optional)",fontSize=12.sp,color=Color(0xFF536581))
        Surface(color=Color(0xFFF0F3FF),border=BorderStroke(1.dp,Color(0xFFC3C6CF))) {Column(Modifier.padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            DriverAsset(R.drawable.figma_driver_f0840,20,18)
            Text("Photo attachments are not supported for incident reports yet. Include the details in your description.",fontSize=12.sp,color=DriverMuted)
        }}
        state.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        TextButton(onClick=close,enabled=!state.busy,modifier=Modifier.fillMaxWidth()){Text("Cancel",color=Color(0xFFBA1A1A))}
    }
}

@Composable internal fun DriverProofDialog(id:String,owner:String,reference:String,state:MobileState,vm:MobileWorkspaceViewModel,onBackHome:()->Unit,close:()->Unit) {
    DriverProofForm(id,owner,reference,state,onBackHome,close) {recipient,photo,signature,attempt,saved->vm.submitProof(id,recipient,photo,signature,attempt,saved)}
}

@Composable internal fun DriverProofForm(id:String,owner:String,reference:String,state:MobileState,onBackHome:()->Unit,close:()->Unit,
    submit:(String,ByteArray,ByteArray,String,()->Unit)->Unit) {
    val context=LocalContext.current
    var recipient by rememberSaveable(id){mutableStateOf("")}
    var filePath by rememberSaveable(id){mutableStateOf<String?>(null)}
    var photoReady by rememberSaveable(id){mutableStateOf(false)}
    var attempt by rememberSaveable(id){mutableStateOf(UUID.randomUUID().toString())}
    val lines=remember(id){mutableStateListOf<List<Offset>>()}
    var current by remember(id){mutableStateOf(emptyList<Offset>())}
    var canvasSize by remember {mutableStateOf(IntSize(1,1))}
    var error by remember {mutableStateOf<String?>(null)}
    var submitted by rememberSaveable(id){mutableStateOf(false)}
    val preview=remember(filePath,photoReady){if(photoReady)runCatching {ProofImage.decode(File(filePath!!).readBytes(),20*1024*1024)}.getOrNull()else null}
    DisposableEffect(preview) {onDispose {preview?.recycle()}}
    val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) {success->photoReady=success;if(success)attempt=UUID.randomUUID().toString()}
    fun discard(){filePath?.let{File(it).delete()};close()}
    fun takePhoto() {
        try {
            val directory=File(context.cacheDir,"proof/$owner").apply{mkdirs()}
            val file=File(directory,"${UUID.randomUUID()}.jpg")
            filePath?.let {File(it).delete()};filePath=file.absolutePath;photoReady=false
            camera.launch(FileProvider.getUriForFile(context,context.packageName+".proof",file))
        }catch(_:Exception){error="No camera is available. Retry on a camera-equipped device."}
    }
    if(submitted) {
        DriverTransaction("Delivery proof submitted",false,close,"BACK TO HOME",true,{close();onBackHome()}) {
            Surface(color=DriverNavy,shape=RoundedCornerShape(4.dp)) {Column(Modifier.fillMaxWidth().padding(32.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
                DriverAsset(R.drawable.figma_driver_966f6,33,24)
                Text("Proof Submitted!",color=Color.White,fontSize=28.sp,fontWeight=FontWeight.Bold)
                Text(reference,color=Color.White)
            }}
            Text("Your delivery photo and recipient signature have been submitted. Office review is pending.")
            Text("Final completion and allowance processing are confirmed by the office.",fontSize=12.sp,color=DriverMuted)
        }
        return
    }
    DriverTransaction("Submit Proof of Delivery",state.busy,::discard,"COMPLETE DELIVERY",
        recipient.trim().length in 2..100 && photoReady && lines.isNotEmpty(),{
            try {
                val file=File(filePath?:error("Missing photo"));require(file.length() in 1..(20L*1024*1024))
                val bitmap=ProofImage.decode(file.readBytes(),20*1024*1024)
                val photo=ByteArrayOutputStream().also{bitmap.compress(Bitmap.CompressFormat.JPEG,85,it)}.toByteArray();bitmap.recycle()
                val signature=Bitmap.createBitmap(1000,400,Bitmap.Config.ARGB_8888)
                val canvas=android.graphics.Canvas(signature);canvas.drawColor(android.graphics.Color.WHITE)
                val paint=android.graphics.Paint().apply{color=android.graphics.Color.BLACK;strokeWidth=4f;isAntiAlias=true}
                lines.forEach {line->line.zipWithNext().forEach {(a,b)->canvas.drawLine(a.x/canvasSize.width*1000,a.y/canvasSize.height*400,b.x/canvasSize.width*1000,b.y/canvasSize.height*400,paint)}}
                val bytes=ByteArrayOutputStream().also{signature.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray();signature.recycle()
                submit(recipient,photo,bytes,attempt){file.delete();submitted=true}
            }catch(_:Exception){error="Unable to prepare proof. Retake a smaller photo and sign again."}
        }) {
        Surface(color=Color.White,shape=RoundedCornerShape(4.dp)) {Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("SHIPMENT REFERENCE",fontSize=10.sp,color=Color(0xFF536581));Text(reference,fontSize=18.sp,fontWeight=FontWeight.Bold,color=DriverNavy)
            Text("Pending Delivery Confirmation",fontSize=14.sp,color=DriverMuted)
        }}
        Text("1. PHOTO OF CARGO",fontSize=12.sp,color=DriverNavy)
        Surface(onClick=::takePhoto,enabled=!state.busy,color=Color(0xFFF0F3FF),border=BorderStroke(1.dp,Color(0xFFC3C6CF))) {
            Column(Modifier.fillMaxWidth().heightIn(min=180.dp).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                if(preview!=null)Image(preview.asImageBitmap(),"Captured delivery photo",Modifier.fillMaxWidth().height(140.dp))
                else DriverAsset(R.drawable.figma_driver_ceb65,48,46)
                Spacer(Modifier.height(12.dp));Text(if(photoReady)"RETAKE PHOTO" else "TAKE PHOTO",color=DriverNavy,fontSize=12.sp)
                Text("Capture a clear view of items & labels",fontSize=10.sp,color=DriverMuted)
            }
        }
        Text("2. RECIPIENT NAME",fontSize=12.sp,color=DriverNavy)
        OutlinedTextField(recipient,{recipient=it},placeholder={Text("Enter full name")},enabled=!state.busy,modifier=Modifier.fillMaxWidth(),singleLine=true,shape=RoundedCornerShape(4.dp))
        Text("3. DIGITAL SIGNATURE",fontSize=12.sp,color=DriverNavy)
        Box(Modifier.fillMaxWidth().height(160.dp).background(Color.White).border(1.dp,Color(0xFFC3C6CF),RoundedCornerShape(4.dp))) {
            if(lines.isEmpty() && current.isEmpty())Text("Tap and draw to sign",Modifier.align(Alignment.Center),fontSize=14.sp,color=Color(0xFF73777F))
            Canvas(Modifier.fillMaxSize().onSizeChanged{canvasSize=it}.pointerInput(state.busy) {
                if(!state.busy)detectDragGestures(onDragStart={attempt=UUID.randomUUID().toString();current=listOf(it)},
                    onDragEnd={if(current.size>1)lines.add(current);current=emptyList()},onDragCancel={current=emptyList()}) {change,_->change.consume();current=current+change.position}
            }) {(lines+listOf(current)).forEach {line->line.zipWithNext().forEach {(a,b)->drawLine(Color.Black,a,b,4f)}}}
        }
        TextButton(onClick={lines.clear();current=emptyList()},enabled=!state.busy){Text("Clear signature")}
        (error?:state.error)?.let {Text(it,color=MaterialTheme.colorScheme.error)}
    }
}
