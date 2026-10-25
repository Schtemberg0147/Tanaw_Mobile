package com.tanaw.app.feature.workspace

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.*
import android.os.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@SuppressLint("MissingPermission")
suspend fun freshDriverLocation(context:Context):Location = withTimeout(20000) {
 val manager=context.getSystemService(LocationManager::class.java)
 val provider=if(manager.isProviderEnabled(LocationManager.GPS_PROVIDER))LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
 val location=suspendCancellableCoroutine<Location> { continuation ->
  if(Build.VERSION.SDK_INT>=30) {
   val signal=CancellationSignal();continuation.invokeOnCancellation {signal.cancel()}
   manager.getCurrentLocation(provider,signal,context.mainExecutor) { location ->
    if(continuation.isActive) {if(location!=null)continuation.resume(location)else continuation.resumeWithException(IllegalStateException("No fresh location fix. Try outdoors."))}
   }
  } else {
   val listener=object:LocationListener {
    override fun onLocationChanged(location:Location) {manager.removeUpdates(this);if(continuation.isActive)continuation.resume(location)}
   }
   continuation.invokeOnCancellation {manager.removeUpdates(listener)}
   manager.requestSingleUpdate(provider,listener,Looper.getMainLooper())
  }
 }
 require(location.hasAccuracy() && location.accuracy<=100 && System.currentTimeMillis()-location.time in 0..60000) {"A fresh location accurate within 100 metres is required. Try outdoors."}
 location
}

@Composable fun DriverTripActions(delivery:JsonObject,state:MobileState,data:JsonObject,vm:MobileWorkspaceViewModel) {
 val context=LocalContext.current;val scope=rememberCoroutineScope();val tracking by TripTrackingStatus.state.collectAsState()
 val id=delivery.value("id");val owner=data.obj("profile").value("id");val status=delivery.value("status")
 var action by remember {mutableStateOf<String?>(null)};var error by remember {mutableStateOf<String?>(null)};var locating by remember {mutableStateOf(false)}
 var proof by rememberSaveable(id){mutableStateOf(false)};var incident by rememberSaveable(id){mutableStateOf(false)}
 fun runLocationAction() {
  if(ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){error="Allow precise location to record pickup arrival and share this trip.";return}
  when(action) {
   "start"->vm.startTrip(id){try{TripTrackingService.start(context,owner,id)}catch(_:Exception){error="Route started. Tap Resume location sharing while the app is open."}}
   "resume"->try{TripTrackingService.start(context,owner,id)}catch(_:Exception){error="Unable to start trip tracking. Keep the app open and retry."}
   "arrive"->scope.launch {locating=true;try{val position=freshDriverLocation(context);vm.arrive(id,position.latitude,position.longitude)}catch(cancelled:CancellationException){throw cancelled}catch(failure:Exception){error=failure.message ?: "Could not obtain a fresh location."}finally{locating=false}}
  }
 }
 val notifications=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){runLocationAction()}
 fun withNotificationPermission() {
  if(action in listOf("start","resume") && Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
   notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
  else runLocationAction()
 }
 val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){withNotificationPermission()}
 fun request(next:String) {
  action=next;error=null
  if(ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)withNotificationPermission()
  else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))
 }
 val busy=state.busy||locating
 SupportingText("Trip actions")
 error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
 if(status=="assigned" && delivery.value("route_started_at").isBlank()) Button(onClick={request("start")},enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("Start route and share location")}
 if(status in listOf("assigned","loading","in_transit") && delivery.value("route_started_at").isNotBlank()) {
  SupportingText(if(tracking.delivery==id)"${tracking.message} · ${tracking.buffered} queued" else "Location sharing stopped. Resume it while this trip is active.")
  if(tracking.delivery!=id)OutlinedButton(onClick={request("resume")},enabled=!busy,modifier=Modifier.heightIn(min=48.dp)){Text("Resume location sharing")}
  else TextButton(onClick={TripTrackingService.stop(context)}){Text("Stop location sharing")}
 }
 if(status=="assigned" && delivery.value("route_started_at").isNotBlank() && delivery.value("pickup_arrived_at").isBlank()) Button(onClick={request("arrive")},enabled=!busy,modifier=Modifier.heightIn(min=48.dp)){Text(if(locating)"Getting fresh location…" else "Record pickup arrival")}
 if(status=="assigned" && delivery.value("pickup_arrived_at").isNotBlank()) {
  SupportingText("Grace ends ${MobilePresentation.time(delivery.value("grace_ends_at"))}. Office review threshold ${MobilePresentation.time(delivery.value("auto_cancel_at"))}. Cancellation requires an office decision.")
  Button(onClick={vm.driver(id,"showed")},enabled=!busy,modifier=Modifier.heightIn(min=48.dp)){Text("Confirm customer present")}
  var now by remember {mutableStateOf(Instant.now())};LaunchedEffect(id){while(true){delay(1000);now=Instant.now()}}
  val due=runCatching{!Instant.parse(delivery.value("auto_cancel_at")).isAfter(now)}.getOrDefault(false)
  OutlinedButton(onClick={vm.driver(id,"no_show")},enabled=!busy && due,modifier=Modifier.heightIn(min=48.dp)){Text("Report no-show for office review")}
 }
 if(status=="loading")Button(onClick={vm.driver(id,"loaded")},enabled=!busy,modifier=Modifier.heightIn(min=48.dp)){Text("Confirm cargo loaded")}
 if(status in listOf("assigned","loading","in_transit"))OutlinedButton(onClick={incident=true},enabled=!busy,modifier=Modifier.heightIn(min=48.dp)){Text("Report an incident")}
 if(status=="in_transit" || (status=="delivered" && delivery.value("proof_review_status")=="correction_requested")) {
  delivery.value("proof_review_reason").takeIf(String::isNotBlank)?.let {Text("Office correction request: $it")}
  Button(onClick={proof=true},enabled=!busy,modifier=Modifier.heightIn(min=48.dp)){Text(if(status=="delivered")"Submit corrected proof" else "Capture delivery proof")}
 }
 if(status=="delivered")StatusBadge(delivery.value("proof_review_status"))
 if(incident)IncidentDialog(id,state,vm){incident=false}
 if(proof)ProofCaptureDialog(id,owner,state,vm){proof=false}
}

@Composable private fun IncidentDialog(id:String,state:MobileState,vm:MobileWorkspaceViewModel,close:()->Unit) {
 var category by rememberSaveable{mutableStateOf("traffic")};var description by rememberSaveable{mutableStateOf("")};var minutes by rememberSaveable{mutableStateOf("")};val request=rememberSaveable{UUID.randomUUID().toString()}
 AlertDialog(onDismissRequest={if(!state.busy)close()},title={Text("Report delivery incident")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
  listOf("traffic","weather","late_loading","breakdown","accident","road_closure","other").forEach {c->FilterChip(selected=category==c,onClick={category=c},label={Text(MobilePresentation.status(c))})}
  OutlinedTextField(description,{description=it},label={Text("What happened?")},enabled=!state.busy)
  OutlinedTextField(minutes,{minutes=it},label={Text("Expected delay in minutes")},enabled=!state.busy)
  state.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
 }},confirmButton={TextButton(enabled=!state.busy && description.trim().length in 5..2000 && minutes.toIntOrNull() in 0..10080,onClick={vm.reportIncident(id,request,category,description,minutes.toInt(),close)}){Text("Send incident")}},dismissButton={TextButton(enabled=!state.busy,onClick=close){Text("Back")}})
}

@Composable private fun ProofCaptureDialog(id:String,owner:String,state:MobileState,vm:MobileWorkspaceViewModel,close:()->Unit) {
 val context=LocalContext.current
 var recipient by rememberSaveable(id){mutableStateOf("")};var filePath by rememberSaveable(id){mutableStateOf<String?>(null)}
 var photoReady by rememberSaveable(id){mutableStateOf(false)};var attempt by rememberSaveable(id){mutableStateOf(UUID.randomUUID().toString())}
 val lines=remember {mutableStateListOf<List<Offset>>()};var current by remember{mutableStateOf(emptyList<Offset>())}
 var error by remember {mutableStateOf<String?>(null)}
 val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()){success->photoReady=success;if(success)attempt=UUID.randomUUID().toString()}
 fun discard(){filePath?.let{File(it).delete()};close()}
 AlertDialog(onDismissRequest={if(!state.busy)discard()},title={Text("Delivery proof")},text={Column(Modifier.heightIn(max=560.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
  OutlinedTextField(recipient,{recipient=it},label={Text("Recipient name")},enabled=!state.busy)
  OutlinedButton(enabled=!state.busy,onClick={try{
   val directory=File(context.cacheDir,"proof/$owner").apply{mkdirs()};val file=File(directory,"$attempt.jpg");filePath=file.absolutePath;photoReady=false
   camera.launch(FileProvider.getUriForFile(context,context.packageName+".proof",file))
  }catch(_:Exception){error="No camera is available. Retry on a camera-equipped device."}}){Text(if(photoReady)"Retake delivery photo" else "Take delivery photo")}
  if(photoReady)SupportingText("Delivery photo captured on this device.")
  SupportingText("Recipient signature · draw inside the box")
  Canvas(Modifier.fillMaxWidth().height(150.dp).background(Color.White).border(1.dp,Color.Gray).pointerInput(state.busy) {
   if(!state.busy)detectDragGestures(onDragStart={attempt=UUID.randomUUID().toString();current=listOf(it)},onDragEnd={if(current.size>1)lines.add(current);current=emptyList()},onDragCancel={current=emptyList()}) {change,_->change.consume();current=current+change.position}
  }) { (lines+listOf(current)).forEach{line->line.zipWithNext().forEach{(a,b)->drawLine(Color.Black,a,b,4f)}} }
  TextButton(enabled=!state.busy,onClick={lines.clear();current=emptyList()}){Text("Clear signature")}
  (error?:state.error)?.let{Text(it,color=MaterialTheme.colorScheme.error)}
 }},confirmButton={TextButton(enabled=!state.busy && recipient.trim().length in 2..100 && photoReady && lines.isNotEmpty(),onClick={
  try {
   val file=File(filePath?:error("Missing photo"));require(file.length() in 1..(20L*1024*1024))
   val bitmap=ProofImage.decode(file.readBytes(),20*1024*1024)
   val photo=ByteArrayOutputStream().also{bitmap.compress(Bitmap.CompressFormat.JPEG,85,it)}.toByteArray()
   val signature=Bitmap.createBitmap(1000,400,Bitmap.Config.ARGB_8888)
   val canvas=android.graphics.Canvas(signature);canvas.drawColor(android.graphics.Color.WHITE)
   val paint=android.graphics.Paint().apply{color=android.graphics.Color.BLACK;strokeWidth=4f;isAntiAlias=true}
   val maxX=lines.flatten().maxOf{it.x}.coerceAtLeast(1f);val maxY=lines.flatten().maxOf{it.y}.coerceAtLeast(1f)
   lines.forEach{line->line.zipWithNext().forEach{(a,b)->canvas.drawLine(20+a.x/maxX*960,20+a.y/maxY*360,20+b.x/maxX*960,20+b.y/maxY*360,paint)}}
   val bytes=ByteArrayOutputStream().also{signature.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray();signature.recycle()
   vm.submitProof(id,recipient,photo,bytes,attempt){file.delete();close()}
  }catch(_:Exception){error="Unable to prepare proof. Retake a smaller photo and sign again."}
 }){Text(if(state.busy)"Uploading…" else "Submit private proof")}},dismissButton={TextButton(enabled=!state.busy,onClick=::discard){Text("Back")}})
}
