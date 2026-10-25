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

@Composable fun DriverTripActions(delivery:JsonObject,state:MobileState,data:JsonObject,vm:MobileWorkspaceViewModel,reference:String="",onBackHome:()->Unit={}) {
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
 if(status=="assigned" && delivery.value("route_started_at").isBlank()) DriverButton("START ROUTE AND SHARE LOCATION",!busy){request("start")}
 if(status in listOf("assigned","loading","in_transit") && delivery.value("route_started_at").isNotBlank()) {
  SupportingText(if(tracking.delivery==id)"${tracking.message} · ${tracking.buffered} queued" else "Location sharing stopped. Resume it while this trip is active.")
  if(tracking.delivery!=id)OutlinedButton(onClick={request("resume")},enabled=!busy,modifier=Modifier.heightIn(min=48.dp)){Text("Resume location sharing")}
  else TextButton(onClick={TripTrackingService.stop(context)}){Text("Stop location sharing")}
 }
 if(status=="assigned" && delivery.value("route_started_at").isNotBlank() && delivery.value("pickup_arrived_at").isBlank()) DriverButton(if(locating)"Getting fresh location…" else "I'VE ARRIVED AT PICK-UP",!busy){request("arrive")}
 if(status=="assigned" && delivery.value("pickup_arrived_at").isNotBlank()) {
  SupportingText("Grace ends ${MobilePresentation.time(delivery.value("grace_ends_at"))}. Office review threshold ${MobilePresentation.time(delivery.value("auto_cancel_at"))}. Cancellation requires an office decision.")
  DriverButton("CUSTOMER ARRIVED AT PICK-UP",!busy){vm.driver(id,"showed")}
  var now by remember {mutableStateOf(Instant.now())};LaunchedEffect(id){while(true){delay(1000);now=Instant.now()}}
  val due=runCatching{!Instant.parse(delivery.value("auto_cancel_at")).isAfter(now)}.getOrDefault(false)
  OutlinedButton(onClick={vm.driver(id,"no_show")},enabled=!busy && due,modifier=Modifier.heightIn(min=48.dp)){Text("Report no-show for office review")}
 }
 if(status=="loading")DriverButton("CARGO LOADED",!busy){vm.driver(id,"loaded")}
 if(status=="in_transit" || (status=="delivered" && delivery.value("proof_review_status")=="correction_requested")) {
  delivery.value("proof_review_reason").takeIf(String::isNotBlank)?.let {Text("Office correction request: $it")}
  DriverButton(if(status=="delivered")"SUBMIT CORRECTED PROOF" else "MARK AS DELIVERED",!busy){proof=true}
 }
 if(status=="delivered")StatusBadge(delivery.value("proof_review_status"))
 if(status in listOf("assigned","loading","in_transit"))TextButton(onClick={incident=true},enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("Report an Issue")}
 if(incident)DriverIncidentDialog(id,state,vm){incident=false}
 if(proof)DriverProofDialog(id,owner,reference,state,vm,onBackHome=onBackHome){proof=false}
}
