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
import androidx.compose.ui.platform.testTag
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
    DriverIncidentForm(id,state,close) {request,category,description,minutes,photos->vm.reportIncident(id,request,category,description,minutes,photos,close)}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun DriverIncidentForm(id:String,state:MobileState,close:()->Unit,submit:(String,String,String,Int,List<ByteArray>)->Unit) {
    var category by rememberSaveable(id){mutableStateOf("traffic")}
    var description by rememberSaveable(id){mutableStateOf("")}
    var minutes by rememberSaveable(id){mutableStateOf("0")}
    var request by rememberSaveable(id){mutableStateOf(UUID.randomUUID().toString())}
    var photos by remember(id){mutableStateOf(emptyList<ByteArray>())}
    DriverTransaction("Report an Issue",state.busy,close,"Submit Report",
        !state.offline && description.trim().length in 5..2000 && minutes.toIntOrNull() in 0..10080,
        {submit(request,category,description,minutes.toInt(),photos)}) {
        Text("SELECT ISSUE TYPE",fontSize=12.sp,color=Color(0xFF536581),fontWeight=FontWeight.SemiBold)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("traffic" to "Traffic/Delay","breakdown" to "Vehicle Issue","accident" to "Safety Concern","weather" to "Weather","other" to "Other").forEach {(value,label)->
                FilterChip(selected=category==value,onClick={if(category!=value){category=value;request=UUID.randomUUID().toString()}},enabled=!state.busy,label={Text(label)},shape=RoundedCornerShape(2.dp),
                    colors=FilterChipDefaults.filterChipColors(selectedContainerColor=DriverNavy,selectedLabelColor=Color.White))
            }
        }
        Text("DESCRIPTION",fontSize=12.sp,color=Color(0xFF536581),fontWeight=FontWeight.SemiBold)
        OutlinedTextField(description,{description=it;request=UUID.randomUUID().toString()},placeholder={Text("Describe the issue in detail…")},enabled=!state.busy,
            modifier=Modifier.fillMaxWidth().heightIn(min=160.dp),shape=RoundedCornerShape(2.dp))
        OutlinedTextField(minutes,{minutes=it;request=UUID.randomUUID().toString()},label={Text("Expected delay in minutes")},enabled=!state.busy,singleLine=true,
            modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(2.dp),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Number))
        Text("ATTACH PHOTOS (Optional)",fontSize=12.sp,color=Color(0xFF536581))
        IncidentPhotoPicker(photos,state.busy||state.offline){photos=it;request=UUID.randomUUID().toString()}
        if(state.offline)Text("Reconnect and refresh before submitting an incident. Your current draft is retained.")
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
    val store=remember(owner,id){ProofDraftStore(context)}
    val restored=remember(owner,id){runCatching{store.read(owner,id)}}
    var draft by remember(owner,id){mutableStateOf(restored.getOrNull()?:ProofDraft())}
    var filePath by rememberSaveable(owner,id){mutableStateOf<String?>(null)}
    var current by remember(owner,id){mutableStateOf(emptyList<Offset>())}
    var canvasSize by remember {mutableStateOf(IntSize(1,1))}
    var error by remember {mutableStateOf<String?>(null)}
    var submitted by rememberSaveable(owner,id){mutableStateOf(false)}
    var confirmDiscard by remember {mutableStateOf(false)}
    val editable=!state.busy && !draft.frozen && restored.isSuccess
    fun persist(next:ProofDraft):Boolean {
        if(!store.save(owner,id,next)){error="Unable to save your private draft. Free device storage and retry before leaving.";return false}
        draft=next;error=null;return true
    }
    val preview=remember(draft.photo){draft.photo?.let{runCatching{ProofImage.decode(it)}.getOrNull()}}
    DisposableEffect(preview) {onDispose {preview?.recycle()}}
    val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) {success->
        val file=filePath?.let(::File)
        try {
            if(success && file!=null) {
                require(file.length() in 1..20L*1024*1024)
                val bitmap=ProofImage.decode(file.readBytes(),20*1024*1024)
                val bytes=try{ByteArrayOutputStream().also{check(bitmap.compress(Bitmap.CompressFormat.JPEG,85,it))}.toByteArray()}finally{bitmap.recycle()}
                require(bytes.size in 1..5*1024*1024)
                persist(draft.changed().copy(photo=bytes))
            }else error="Photo capture cancelled. Your previous photo and signature are retained."
        }catch(_:Exception){error="Unable to read the photo. Your existing draft is retained; retry capture."}
        finally {file?.delete();filePath=null}
    }
    fun takePhoto() {
        try {
            val directory=File(context.cacheDir,"proof/$owner").apply{mkdirs()}
            val file=File(directory,"${UUID.randomUUID()}.jpg")
            filePath?.let {File(it).delete()};filePath=file.absolutePath
            camera.launch(FileProvider.getUriForFile(context,context.packageName+".proof",file))
        }catch(_:Exception){error="No camera is available. Retry on a camera-equipped device."}
    }
    if(confirmDiscard)AlertDialog(onDismissRequest={confirmDiscard=false},title={Text("Discard saved proof draft?")},
        text={Text("This removes this device's photo, recipient and signature. Uploaded evidence is retained for reconciliation.")},
        confirmButton={TextButton(onClick={store.clear(owner,id);confirmDiscard=false;close()}){Text("Discard draft")}},
        dismissButton={TextButton(onClick={confirmDiscard=false}){Text("Keep draft")}})
    if(submitted) {
        DriverTransaction("Delivery proof submitted",false,close,"BACK TO HOME",true,{close();onBackHome()}) {
            Surface(color=DriverNavy,shape=RoundedCornerShape(4.dp)) {Column(Modifier.fillMaxWidth().padding(32.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
                DriverAsset(R.drawable.figma_driver_966f6,33,24)
                Text("Proof Submitted!",color=Color.White,fontSize=28.sp,fontWeight=FontWeight.Bold)
                Text(reference,color=Color.White)
            }}
            Text("Your delivery photo and recipient signature are saved. Refresh your trip for the current office review or correction status.")
            Text("Final completion and allowance processing are confirmed by the office.",fontSize=12.sp,color=DriverMuted)
        }
        return
    }
    DriverTransaction("Submit Proof of Delivery",state.busy,close,if(draft.frozen)"RETRY / CHECK SAVED PROOF" else "COMPLETE DELIVERY",
        !state.offline && restored.isSuccess && draft.recipient.trim().length in 2..100 && preview!=null && draft.strokes.isNotEmpty(),{
            try {
                var ready=draft
                if(!ready.frozen) {
                val signature=Bitmap.createBitmap(1000,400,Bitmap.Config.ARGB_8888)
                val canvas=android.graphics.Canvas(signature);canvas.drawColor(android.graphics.Color.WHITE)
                val paint=android.graphics.Paint().apply{color=android.graphics.Color.BLACK;strokeWidth=4f;isAntiAlias=true}
                ready.strokes.forEach {line->line.zipWithNext().forEach {(a,b)->canvas.drawLine(a.x*1000,a.y*400,b.x*1000,b.y*400,paint)}}
                val bytes=ByteArrayOutputStream().also{signature.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray();signature.recycle()
                ready=ready.copy(signature=bytes,frozen=true)
                }
                if(persist(ready))submit(ready.recipient,requireNotNull(ready.photo),requireNotNull(ready.signature),ready.attempt){store.clear(owner,id);submitted=true}
            }catch(_:Exception){error="Unable to prepare proof. Your saved draft is retained; retry or contact the office."}
        }) {
        Surface(color=Color.White,shape=RoundedCornerShape(4.dp)) {Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("SHIPMENT REFERENCE",fontSize=10.sp,color=Color(0xFF536581));Text(reference,fontSize=18.sp,fontWeight=FontWeight.Bold,color=DriverNavy)
            Text("Pending Delivery Confirmation",fontSize=14.sp,color=DriverMuted)
        }}
        Text(if(draft.frozen)"This attempt is saved and locked while its result is uncertain. Retry checks existing proof before sending again." else "Your draft is saved privately on this device. Back keeps it for this account and delivery.",fontSize=12.sp,color=DriverMuted)
        if(state.offline)Text("Reconnect and refresh before submitting. Your saved draft is retained.")
        if(restored.isFailure)Text("The saved proof draft cannot be read. Contact the office before submitting another attempt.",color=MaterialTheme.colorScheme.error)
        Text("1. PHOTO OF CARGO",fontSize=12.sp,color=DriverNavy)
        Surface(onClick=::takePhoto,enabled=editable,color=Color(0xFFF0F3FF),border=BorderStroke(1.dp,Color(0xFFC3C6CF))) {
            Column(Modifier.fillMaxWidth().heightIn(min=180.dp).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                if(preview!=null)Image(preview.asImageBitmap(),"Captured delivery photo",Modifier.fillMaxWidth().height(140.dp))
                else DriverAsset(R.drawable.figma_driver_ceb65,48,46)
                Spacer(Modifier.height(12.dp));Text(if(preview!=null)"RETAKE PHOTO" else "TAKE PHOTO",color=DriverNavy,fontSize=12.sp)
                Text("Capture a clear view of items & labels",fontSize=10.sp,color=DriverMuted)
            }
        }
        Text("2. RECIPIENT NAME",fontSize=12.sp,color=DriverNavy)
        OutlinedTextField(draft.recipient,{persist(draft.changed().copy(recipient=it.take(100)))},placeholder={Text("Enter full name")},enabled=editable,modifier=Modifier.fillMaxWidth(),singleLine=true,shape=RoundedCornerShape(4.dp))
        Text("3. DIGITAL SIGNATURE",fontSize=12.sp,color=DriverNavy)
        Box(Modifier.fillMaxWidth().height(160.dp).background(Color.White).border(1.dp,Color(0xFFC3C6CF),RoundedCornerShape(4.dp))) {
            if(draft.strokes.isEmpty() && current.isEmpty())Text("Tap and draw to sign",Modifier.align(Alignment.Center),fontSize=14.sp,color=Color(0xFF73777F))
            Canvas(Modifier.fillMaxSize().testTag("proof_signature_canvas").onSizeChanged{canvasSize=it}.pointerInput(editable) {
                if(editable)detectDragGestures(onDragStart={current=listOf(it)},
                    onDragEnd={if(current.size>1)persist(draft.changed().copy(strokes=draft.strokes+listOf(current.map {SignaturePoint((it.x/canvasSize.width.coerceAtLeast(1)).coerceIn(0f,1f),(it.y/canvasSize.height.coerceAtLeast(1)).coerceIn(0f,1f))})));current=emptyList()},onDragCancel={current=emptyList()}) {change,_->change.consume();if(current.size<2000)current=current+change.position}
            }) {(draft.strokes.map{line->line.map{Offset(it.x*size.width,it.y*size.height)}}+listOf(current)).forEach {line->line.zipWithNext().forEach {(a,b)->drawLine(Color.Black,a,b,4f)}}}
        }
        TextButton(onClick={persist(draft.changed().copy(strokes=emptyList()));current=emptyList()},enabled=editable){Text("Clear signature")}
        TextButton(onClick={confirmDiscard=true},enabled=editable){Text("Discard saved draft")}
        (error?:state.error)?.let {Text(it,color=MaterialTheme.colorScheme.error)}
    }
}
