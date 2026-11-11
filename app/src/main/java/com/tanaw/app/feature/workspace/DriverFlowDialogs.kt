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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.tanaw.app.R
import java.io.File
import java.util.UUID
import com.tanaw.app.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable internal fun DriverTransaction(title:String,busy:Boolean,close:()->Unit,
    label:String,enabled:Boolean,submit:()->Unit,content:@Composable ColumnScope.()->Unit) {
    Dialog(onDismissRequest={if(!busy)close()},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        DriverTheme {
            BackHandler {if(!busy)close()}
            Scaffold(containerColor=MaterialTheme.colorScheme.background,topBar={Column(Modifier.statusBarsPadding()) {DriverPageHeading(title){if(!busy)close()}}},
                bottomBar={Surface(color=MaterialTheme.colorScheme.surface,shadowElevation=3.dp) {Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                    DriverButton(if(busy)"Submitting…" else label,enabled && !busy,submit)
                }}}) {padding->
                Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement=Arrangement.spacedBy(16.dp),content=content)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun DriverIncidentDialog(id:String,state:MobileState,vm:MobileWorkspaceViewModel,owner:String="",close:()->Unit) {
    DriverIncidentForm(id,state,close,owner) {request,category,description,minutes,photos->vm.reportIncident(id,request,category,description,minutes,photos,close)}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun DriverIncidentDialog(id:String,owner:String,state:MobileState,vm:MobileWorkspaceViewModel,close:()->Unit) {
    DriverIncidentForm(id,state,close,owner) {request,category,description,minutes,photos->vm.reportIncident(id,request,category,description,minutes,photos,close)}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun DriverIncidentForm(
    id:String,
    state:MobileState,
    close:()->Unit,
    owner:String="",
    submit:(String,String,String,Int,List<ByteArray>)->Unit
) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val store=remember(owner,id){if(owner.isNotBlank())IncidentDraftStore(context) else null}
    val restored=remember(owner,id){if(store!=null)runCatching{store.read(owner,id)}.getOrNull() else null}
    var category by rememberSaveable(id){mutableStateOf(restored?.category ?: "traffic")}
    var description by rememberSaveable(id){mutableStateOf(restored?.description ?: "")}
    var minutes by rememberSaveable(id){mutableStateOf(restored?.minutes?.toString() ?: "0")}
    var request by rememberSaveable(id){mutableStateOf(restored?.request ?: UUID.randomUUID().toString())}
    var photos by remember(id){mutableStateOf(restored?.photos ?: emptyList<ByteArray>())}
    var preparingPhotos by remember {mutableStateOf(false)}

    fun persist(c: String = category, d: String = description, m: String = minutes, p: List<ByteArray> = photos, r: String = request) {
        if(store!=null) {
            scope.launch(Dispatchers.IO) {
                store.save(owner,id,IncidentDraft(request=r,category=c,description=d,minutes=m.toIntOrNull()?.coerceIn(0,10080)?:0,photos=p))
            }
        }
    }

    val isDescError = description.isNotEmpty() && (description.trim().length < 5 || description.trim().length > 2000)
    val delayVal = minutes.toIntOrNull()
    val isMinutesError = minutes.isNotEmpty() && (delayVal == null || delayVal !in 0..1440)

    DriverTransaction("Report an Issue",state.busy || preparingPhotos,close,if(preparingPhotos)"PREPARING PHOTO…" else "Submit Report",
        !preparingPhotos && !state.offline && description.trim().length in 5..2000 && minutes.toIntOrNull() in 0..1440,
        {if(!preparingPhotos)submit(request,category,description,minutes.toInt(),photos)}) {
        Text("SELECT ISSUE TYPE",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,fontWeight=FontWeight.SemiBold)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("traffic" to "Traffic/Delay","breakdown" to "Vehicle Issue","accident" to "Safety Concern","weather" to "Weather","other" to "Other").forEach {(value,label)->
                FilterChip(selected=category==value,onClick={
                    if(category!=value){
                        category=value
                        val nextReq=UUID.randomUUID().toString()
                        request=nextReq
                        persist(c=value,r=nextReq)
                    }
                },enabled=!state.busy && !preparingPhotos,label={Text(label)},shape=RoundedCornerShape(2.dp),
                    colors=FilterChipDefaults.filterChipColors(selectedContainerColor=MaterialTheme.colorScheme.primary,selectedLabelColor=MaterialTheme.colorScheme.onPrimary))
            }
        }
        Text("DESCRIPTION",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,fontWeight=FontWeight.SemiBold)
        OutlinedTextField(
            value = description,
            onValueChange = {
                description = it
                val nextReq = UUID.randomUUID().toString()
                request = nextReq
                persist(d = it, r = nextReq)
            },
            placeholder = { Text("Describe the issue in detail…") },
            enabled = !state.busy && !preparingPhotos,
            isError = isDescError,
            supportingText = {
                if (isDescError) {
                    Text(if (description.trim().length < 5) "Description must be at least 5 characters." else "Description cannot exceed 2000 characters.")
                } else {
                    Text("${description.trim().length}/2000 characters")
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
            shape = RoundedCornerShape(2.dp)
        )
        OutlinedTextField(
            value = minutes,
            onValueChange = {
                minutes = it
                val nextReq = UUID.randomUUID().toString()
                request = nextReq
                persist(m = it, r = nextReq)
            },
            label = { Text("Expected delay in minutes") },
            enabled = !state.busy && !preparingPhotos,
            singleLine = true,
            isError = isMinutesError,
            supportingText = if (isMinutesError) {
                { Text("Enter a delay between 0 and 1440 minutes (up to 24 hours).") }
            } else null,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(2.dp)
        )
        Text("ATTACH PHOTOS (Optional)",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        IncidentPhotoPicker(photos,state.busy||state.offline,onPreparingChange={preparingPhotos=it}){
            photos=it
            val nextReq=UUID.randomUUID().toString()
            request=nextReq
            persist(p=it,r=nextReq)
        }
        if(state.offline)Text("Reconnect and refresh before submitting an incident. Your current draft is retained.")
        state.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        TextButton(onClick=close,enabled=!state.busy && !preparingPhotos,modifier=Modifier.fillMaxWidth()){Text("Cancel",color=MaterialTheme.colorScheme.error)}
    }
}

@Composable internal fun DriverProofDialog(id:String,owner:String,reference:String,state:MobileState,vm:MobileWorkspaceViewModel,onBackHome:()->Unit,close:()->Unit) {
    DriverProofForm(id,owner,reference,state,onBackHome,close) {recipient,photo,signature,attempt,saved->vm.submitProof(id,recipient,photo,signature,attempt,saved)}
}

@Composable internal fun DriverProofForm(id:String,owner:String,reference:String,state:MobileState,onBackHome:()->Unit,close:()->Unit,
    submit:(String,ByteArray,ByteArray,String,()->Unit)->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val store=remember(owner,id){ProofDraftStore(context)}
    val restored=remember(owner,id){runCatching{store.read(owner,id)}}
    var draft by remember(owner,id){mutableStateOf(restored.getOrNull()?:ProofDraft())}
    var filePath by rememberSaveable(owner,id){mutableStateOf<String?>(null)}
    var current by remember(owner,id){mutableStateOf(emptyList<Offset>())}
    var canvasSize by remember {mutableStateOf(IntSize(1,1))}
    var error by remember {mutableStateOf<String?>(null)}
    var submitted by rememberSaveable(owner,id){mutableStateOf(false)}
    var confirmDiscard by remember {mutableStateOf(false)}
    var processing by remember {mutableStateOf(false)}
    val editable=!state.busy && !processing && !draft.frozen && restored.isSuccess
    fun persist(next:ProofDraft):Boolean {
        if(!store.save(owner,id,next)){error="Unable to save your private draft. Free device storage and retry before leaving.";return false}
        draft=next;error=null;return true
    }
    var preview by remember(draft.photo){mutableStateOf<Bitmap?>(null)}
    LaunchedEffect(draft.photo) {
        val bytes=draft.photo
        if(bytes==null) {
            preview?.recycle()
            preview=null
        } else {
            val bitmap=withContext(Dispatchers.Default){
                runCatching{ProofImage.decode(bytes)}.getOrNull()
            }
            if(isActive) {
                preview?.recycle()
                preview=bitmap
            } else {
                bitmap?.recycle()
            }
        }
    }
    DisposableEffect(draft.photo) {
        onDispose {
            preview?.recycle()
            preview=null
        }
    }
    val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) {success->
        val file=filePath?.let(::File)
        if(success && file!=null) {
            processing=true
            error=null
            scope.launch {
                try {
                    val bytes=ProofImage.processCameraPhoto(file)
                    persist(draft.changed().copy(photo=bytes))
                    file.delete()
                    filePath=null
                }catch(cancelled:CancellationException){
                    throw cancelled
                }catch(_:Exception){
                    file.delete()
                    filePath=null
                    error="Unable to read the photo. Your existing draft is retained; retry capture."
                }finally {
                    processing=false
                }
            }
        } else {
            file?.delete()
            filePath=null
            error="Photo capture cancelled. Your previous photo and signature are retained."
        }
    }
    fun takePhoto() {
        if(processing)return
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
    val isDark = isSystemInDarkTheme()
    if(submitted) {
        val exitSubmitted={close();onBackHome()}
        DriverTransaction("Delivery proof submitted",false,exitSubmitted,"BACK TO HOME",true,exitSubmitted) {
            Surface(
                color=if(isDark)MaterialTheme.colorScheme.surfaceContainerHigh else DriverNavy,
                shape=RoundedCornerShape(4.dp),
                border=if(isDark)BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant) else null
            ) {
                Column(Modifier.fillMaxWidth().padding(32.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    DriverAsset(R.drawable.figma_driver_966f6,33,24)
                    Text("Proof Submitted!",color=if(isDark)MaterialTheme.colorScheme.onSurface else Color.White,fontSize=28.sp,fontWeight=FontWeight.Bold)
                    Text(reference,color=if(isDark)MaterialTheme.colorScheme.onSurfaceVariant else Color.White)
                }
            }
            Text("Your delivery photo and recipient signature are saved. Refresh your trip for the current office review or correction status.")
            Text("Final completion and allowance processing are confirmed by the office.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    DriverTransaction("Submit Proof of Delivery",state.busy || processing,close,
        if(processing)"PREPARING PROOF…" else if(draft.frozen)"RETRY / CHECK SAVED PROOF" else "COMPLETE DELIVERY",
        !processing && !state.offline && restored.isSuccess && draft.recipient.trim().length in 2..100 && preview!=null && draft.strokes.isNotEmpty(),{
            if(processing)return@DriverTransaction
            processing=true
            scope.launch {
                try {
                    var ready=draft
                    if(!ready.frozen) {
                        val bytes=ProofImage.renderSignaturePng(ready.strokes)
                        ready=ready.copy(signature=bytes,frozen=true)
                    }
                    if(persist(ready))submit(ready.recipient,requireNotNull(ready.photo),requireNotNull(ready.signature),ready.attempt){store.clear(owner,id);submitted=true}
                }catch(cancelled:CancellationException){
                    throw cancelled
                }catch(_:Exception){
                    error="Unable to prepare proof. Your saved draft is retained; retry or contact the office."
                }finally {
                    processing=false
                }
            }
        }) {
        if(processing)LinearProgressIndicator(Modifier.fillMaxWidth())
        Surface(color=MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(4.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("SHIPMENT REFERENCE",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(reference,fontSize=18.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurface)
                Text("Pending Delivery Confirmation",fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(if(draft.frozen)"This attempt is saved and locked while its result is uncertain. Retry checks existing proof before sending again." else "Your draft is saved privately on this device. Back keeps it for this account and delivery.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(state.offline)Text("Reconnect and refresh before submitting. Your saved draft is retained.")
        if(restored.isFailure)Text("The saved proof draft cannot be read. Contact the office before submitting another attempt.",color=MaterialTheme.colorScheme.error)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("1. PHOTO OF CARGO", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            val photoBg = if(preview!=null) (if(isDark) StatusGreenBgDark else Color(0xFFD6F5E3)) else (if(isDark) MaterialTheme.colorScheme.errorContainer else Color(0xFFFFEDEA))
            val photoFg = if(preview!=null) (if(isDark) StatusGreenDark else Color(0xFF006C4C)) else (if(isDark) MaterialTheme.colorScheme.onErrorContainer else Color(0xFFBA1A1A))
            Surface(
                color = photoBg,
                shape = RoundedCornerShape(2.dp)
            ) {
                Text(
                    text = if (preview != null) "PHOTO ADDED" else "REQUIRED",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = photoFg,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Surface(onClick=::takePhoto,enabled=editable,color=MaterialTheme.colorScheme.surfaceContainerHigh,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.fillMaxWidth().heightIn(min=180.dp).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                if(preview!=null)Image(preview!!.asImageBitmap(),"Captured delivery photo",Modifier.fillMaxWidth().height(140.dp))
                else DriverAsset(R.drawable.figma_driver_ceb65,48,46)
                Spacer(Modifier.height(12.dp));Text(if(preview!=null)"RETAKE PHOTO" else "TAKE PHOTO",color=MaterialTheme.colorScheme.primary,fontSize=12.sp)
                Text("Capture a clear view of items & labels",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val isRecipientError = draft.recipient.isNotBlank() && draft.recipient.trim().length < 2
        Text("2. RECIPIENT NAME", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = draft.recipient,
            onValueChange = { persist(draft.changed().copy(recipient = it.take(100))) },
            placeholder = { Text("Enter full name") },
            enabled = editable,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = isRecipientError,
            supportingText = if (isRecipientError) {
                { Text("Recipient name must be at least 2 characters.") }
            } else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            shape = RoundedCornerShape(4.dp)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("3. DIGITAL SIGNATURE", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            val sigBg = if(draft.strokes.isNotEmpty()) (if(isDark) StatusGreenBgDark else Color(0xFFD6F5E3)) else (if(isDark) MaterialTheme.colorScheme.errorContainer else Color(0xFFFFEDEA))
            val sigFg = if(draft.strokes.isNotEmpty()) (if(isDark) StatusGreenDark else Color(0xFF006C4C)) else (if(isDark) MaterialTheme.colorScheme.onErrorContainer else Color(0xFFBA1A1A))
            Surface(
                color = sigBg,
                shape = RoundedCornerShape(2.dp)
            ) {
                Text(
                    text = if (draft.strokes.isNotEmpty()) "SIGNED" else "REQUIRED",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = sigFg,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(160.dp).background(if(isDark) MaterialTheme.colorScheme.surfaceContainerHigh else Color.White).border(1.dp,MaterialTheme.colorScheme.outlineVariant,RoundedCornerShape(4.dp))) {
            if(draft.strokes.isEmpty() && current.isEmpty())Text("Tap and draw to sign",Modifier.align(Alignment.Center),fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            fun commitStroke(points:List<Offset>) {
                if(points.size>1) {
                    val w=canvasSize.width.coerceAtLeast(1)
                    val h=canvasSize.height.coerceAtLeast(1)
                    persist(draft.changed().copy(strokes=draft.strokes+listOf(points.map {SignaturePoint((it.x/w).coerceIn(0f,1f),(it.y/h).coerceIn(0f,1f))})))
                }
            }
            Canvas(Modifier.fillMaxSize().testTag("proof_signature_canvas").onSizeChanged{canvasSize=it}.pointerInput(editable) {
                if(editable)detectDragGestures(onDragStart={current=listOf(it)},
                    onDragEnd={val pts=current;current=emptyList();commitStroke(pts)},
                    onDragCancel={val pts=current;current=emptyList();commitStroke(pts)}) {change,_->change.consume();if(current.size<2000)current=current+change.position}
            }) {
                val strokeColor = if(isDark) Color.White else Color.Black
                (draft.strokes.map{line->line.map{Offset(it.x*size.width,it.y*size.height)}}+listOf(current)).forEach {line->line.zipWithNext().forEach {(a,b)->drawLine(strokeColor,a,b,4f)}}
            }
        }
        TextButton(onClick={persist(draft.changed().copy(strokes=emptyList()));current=emptyList()},enabled=editable){Text("Clear signature")}
        TextButton(onClick={confirmDiscard=true},enabled=editable){Text("Discard saved draft")}
        (error?:state.error)?.let {Text(it,color=MaterialTheme.colorScheme.error)}
    }
}
