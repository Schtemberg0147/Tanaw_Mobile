package com.tanaw.app.feature.workspace.driver.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.*
import android.os.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.tanaw.app.feature.workspace.driver.dialogs.*
import com.tanaw.app.feature.workspace.driver.rules.*
import com.tanaw.app.feature.workspace.driver.screens.DriverButton
import com.tanaw.app.feature.workspace.driver.screens.DriverTheme
import com.tanaw.app.feature.workspace.rules.MobilePresentation
import com.tanaw.app.feature.workspace.state.*
import com.tanaw.app.feature.workspace.tracking.*
import com.tanaw.app.feature.workspace.viewmodel.*
import com.tanaw.app.ui.components.StatusBadge
import com.tanaw.app.ui.components.SupportingText
import com.tanaw.app.ui.theme.ExpiredRed
import com.tanaw.app.ui.theme.ExpiredRedBg
import com.tanaw.app.ui.theme.ExpiringOrange
import com.tanaw.app.ui.theme.ExpiringOrangeBg
import com.tanaw.app.ui.theme.SuccessBannerBg
import com.tanaw.app.ui.theme.SuccessGreen
import com.tanaw.app.ui.theme.TanawMonoFontFamily
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.math.BigDecimal
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

@SuppressLint("MissingPermission")
suspend fun freshDriverLocation(context:Context):Location = withTimeout(20.seconds) {
 val manager=context.getSystemService(LocationManager::class.java)
 val providers=TrackingSafety.providers(manager::isProviderEnabled)
 val now=System.currentTimeMillis()
 val cachedCandidates=providers.mapNotNull { p ->
  runCatching { manager.getLastKnownLocation(p) }.getOrNull()?.takeIf { it.hasAccuracy() }
 }.map { loc ->
  FixCandidate(loc,loc.latitude,loc.longitude,loc.accuracy,loc.time)
 }
 val cached=TrackingSafety.chooseBestFix(cachedCandidates,now,maxAgeMs=15_000L)
 if(cached!=null) return@withTimeout cached

 coroutineScope {
  val result=CompletableDeferred<Location>()
  val signals=providers.map { CancellationSignal() }
  var pending=providers.size
  providers.forEachIndexed { index,provider ->
   val signal=signals[index]
   try {
    LocationManagerCompat.getCurrentLocation(manager,provider,signal,ContextCompat.getMainExecutor(context)) { location ->
     if(result.isCompleted) return@getCurrentLocation
     if(location!=null && location.hasAccuracy() && TrackingSafety.valid(location.latitude,location.longitude,location.accuracy,System.currentTimeMillis()-location.time)) {
      result.complete(location)
     } else {
      pending--
      if(pending<=0 && !result.isCompleted) {
       result.completeExceptionally(IllegalStateException("No fresh location fix. Try outdoors."))
      }
     }
    }
   } catch(_:Exception) {
    pending--
    if(pending<=0 && !result.isCompleted) {
     result.completeExceptionally(IllegalStateException("No fresh location fix. Try outdoors."))
    }
   }
  }
  try {
   result.await()
  } finally {
   signals.forEach { runCatching { it.cancel() } }
  }
 }
}

@Composable fun DriverTripActions(
 delivery:JsonObject,
 state:MobileState,
 data:JsonObject,
 vm:MobileWorkspaceViewModel,
 reference:String="",
 onBackHome:()->Unit={},
 shipment:JsonObject=buildJsonObject {}
) {
 val context=LocalContext.current;val scope=rememberCoroutineScope();val tracking by TripTrackingStatus.state.collectAsState()
 val id=delivery.value("id");val owner=data.obj("profile").value("id");val status=delivery.value("status")
 val resolvedShipment = remember(shipment, data, delivery) {
  shipment.takeIf { it.isNotEmpty() }
   ?: data.rows("shipments").firstOrNull { it.value("id") == delivery.value("shipment_id") }
   ?: buildJsonObject {}
 }
 var action by remember {mutableStateOf<String?>(null)};var error by remember {mutableStateOf<String?>(null)};var locating by remember {mutableStateOf(false)}
 var proof by rememberSaveable(id){mutableStateOf(false)};var incident by rememberSaveable(id){mutableStateOf(false)}
 var paymentOpen by rememberSaveable(id){mutableStateOf(false)}
 val paymentSummary = remember(resolvedShipment, data, state.details) {
  DriverPaymentRules.evaluate(resolvedShipment, data, state.details)
 }
 fun runLocationAction() {
  if(ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){error="Allow precise location to verify arrival within 50m and share this trip.";return}
  when(action) {
   "start"->vm.startTrip(id){try{TripTrackingService.start(context,owner,id)}catch(_:Exception){error="Route started. Tap Resume location sharing while the app is open."}}
   "resume"->try{TripTrackingService.start(context,owner,id)}catch(_:Exception){error="Unable to start trip tracking. Keep the app open and retry."}
   "arrive","deliver"-> {
    val isDestination = action == "deliver"
    val target = DriverGeofenceRules.extractTarget(resolvedShipment, state.details, isDestination)
    if (target == null) {
     error = DriverGeofenceRules.message(
      if (state.detailLoading) GeofenceCheckResult.LoadingCoordinates else GeofenceCheckResult.MissingTargetCoordinates,
      isDestination
     )
     return
    }
    scope.launch {
     locating = true
     try {
      val position = freshDriverLocation(context)
      val check = DriverGeofenceRules.checkArrival(
       driverLat = position.latitude,
       driverLng = position.longitude,
       shipment = resolvedShipment,
       details = state.details,
       isDestination = isDestination,
       detailLoading = state.detailLoading
      )
      if (check is GeofenceCheckResult.Allowed) {
       if (isDestination) {
        proof = true
       } else {
        vm.arrive(id, position.latitude, position.longitude)
       }
      } else {
       error = DriverGeofenceRules.message(check, isDestination)
      }
     } catch(cancelled:CancellationException) {
      throw cancelled
     } catch(failure:Exception) {
      error = failure.message ?: "Could not obtain a fresh location."
     } finally {
      locating = false
     }
    }
   }
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
  if(next in listOf("arrive","deliver")) {
   val isDestination = next == "deliver"
   if(DriverGeofenceRules.extractTarget(resolvedShipment, state.details, isDestination) == null) {
    error = DriverGeofenceRules.message(
     if (state.detailLoading) GeofenceCheckResult.LoadingCoordinates else GeofenceCheckResult.MissingTargetCoordinates,
     isDestination
    )
    return
   }
  }
  if(ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)withNotificationPermission()
  else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))
 }
 var now by remember {mutableStateOf(Instant.now())}
 LaunchedEffect(id, status, delivery.value("pickup_arrived_at")) {
  if(status == "assigned" && delivery.value("pickup_arrived_at").isNotBlank()) {
   while(true) { delay(1.seconds); now = Instant.now() }
  }
 }
 val noShowDue = runCatching { !Instant.parse(delivery.value("auto_cancel_at")).isAfter(now) }.getOrDefault(false)
 val hasProofDraft = remember(owner, id, status, delivery.value("proof_review_status")) {
  status == "delivered" && delivery.value("proof_review_status") != "correction_requested" && vm.hasProofDraft(owner, id)
 }
 DriverTripActionsContent(
  delivery = delivery,
  shipment = resolvedShipment,
  state = state,
  tracking = tracking,
  locating = locating,
  activeAction = action,
  error = error,
  noShowDue = noShowDue,
  hasProofDraft = hasProofDraft,
  paymentSummary = paymentSummary,
  onRequestLocationAction = ::request,
  onStopTracking = { TripTrackingService.stop(context) },
  onDriverAction = { vm.driver(id, it) },
  onOpenProof = { proof = true },
  onOpenIncident = { incident = true },
  onOpenPayment = { paymentOpen = true },
  onViewPaymentProof = {
   val shipmentId = resolvedShipment.value("id").ifBlank { delivery.value("shipment_id") }
   if (shipmentId.isNotBlank()) {
    vm.viewPaymentProof(shipmentId, paymentSummary.latestPaymentId, paymentSummary.latestProofPath)
   }
  },
  onRefreshTrip = {
   error = null
   vm.refresh()
   val shipmentId = resolvedShipment.value("id").ifBlank { delivery.value("shipment_id") }
   if (shipmentId.isNotBlank()) vm.loadDetails(shipmentId)
  }
 )
 if(incident)DriverIncidentDialog(id,owner,state,vm){incident=false}
 if(proof)DriverProofDialog(id,owner,reference,state,vm,onBackHome=onBackHome){proof=false}
 if(paymentOpen && status == "loading" && paymentSummary.canSubmitPayment && paymentSummary.balance > BigDecimal.ZERO) {
  val shipmentId = resolvedShipment.value("id").ifBlank { delivery.value("shipment_id") }
  DriverPaymentSheet(
   deliveryId = id,
   trackingNumber = reference.ifBlank { resolvedShipment.value("tracking_number") },
   amountDue = paymentSummary.balance,
   isResubmission = paymentSummary.gateStatus == DriverPaymentGateStatus.REJECTED,
   rejectionReason = paymentSummary.rejectionReason,
   isSubmitting = state.busy,
   errorMessage = state.error,
   offline = state.offline,
   owner = owner,
   onDismiss = { paymentOpen = false },
   onSubmit = { requestId, method, refNum, receiptBytes, notes ->
    vm.submitDriverPayment(
     deliveryId = id,
     shipmentId = shipmentId,
     requestId = requestId,
     method = method,
     referenceNumber = refNum,
     receiptBytes = receiptBytes,
     notes = notes,
     amountDue = paymentSummary.balance,
     onSaved = { paymentOpen = false }
    )
   }
  )
 }
}

@Composable
internal fun DriverTripActionsContent(
 delivery: JsonObject,
 shipment: JsonObject,
 state: MobileState,
 tracking: TrackingStatus,
 locating: Boolean,
 activeAction: String?,
 error: String?,
 noShowDue: Boolean,
 hasProofDraft: Boolean,
 paymentSummary: DriverPaymentSummary = DriverPaymentRules.evaluate(shipment, state.data, state.details),
 onRequestLocationAction: (String) -> Unit,
 onStopTracking: () -> Unit,
 onDriverAction: (String) -> Unit,
 onOpenProof: () -> Unit,
 onOpenIncident: () -> Unit,
 onOpenPayment: () -> Unit = {},
 onViewPaymentProof: () -> Unit = {},
 onRefreshTrip: () -> Unit,
 modifier: Modifier = Modifier
) {
 val id = delivery.value("id")
 val status = delivery.value("status")
 val busy = state.busy || locating || state.offline
 Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
  SupportingText("Trip actions")
  error?.let {
   Surface(
    color = MaterialTheme.colorScheme.errorContainer,
    shape = RoundedCornerShape(10.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f))
   ) {
    Text(
     text = it,
     style = MaterialTheme.typography.bodySmall,
     color = MaterialTheme.colorScheme.onErrorContainer,
     modifier = Modifier.fillMaxWidth().padding(12.dp)
    )
   }
  }
  if (status == "assigned" && delivery.value("route_started_at").isBlank()) {
   DriverButton("START ROUTE AND SHARE LOCATION", !busy) { onRequestLocationAction("start") }
  }
  if (status in listOf("assigned", "loading", "in_transit") && delivery.value("route_started_at").isNotBlank()) {
   Surface(
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    shape = RoundedCornerShape(10.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
   ) {
    Column(
     modifier = Modifier.fillMaxWidth().padding(12.dp),
     verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
     SupportingText(if (tracking.delivery == id) "${tracking.message} · ${tracking.buffered} queued" else "${tracking.message} Resume location sharing while this trip is active.")
     if (tracking.delivery != id) {
      OutlinedButton(
       onClick = { onRequestLocationAction("resume") },
       enabled = !busy,
       shape = RoundedCornerShape(10.dp),
       modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
      ) {
       Text("Resume location sharing")
      }
     } else {
      TextButton(
       onClick = onStopTracking,
       modifier = Modifier.heightIn(min = 48.dp)
      ) { Text("Stop location sharing") }
     }
    }
   }
  }
  if (status == "assigned" && delivery.value("route_started_at").isNotBlank() && delivery.value("pickup_arrived_at").isBlank()) {
   val pickupTarget = DriverGeofenceRules.extractTarget(shipment, state.details, isDestination = false)
   SupportingText(
    when {
     pickupTarget != null -> "Must be within 50m of the pick-up location to confirm arrival."
     state.detailLoading -> "Loading pick-up coordinates…"
     else -> "Pick-up coordinates are missing for this trip. Refresh trip details or contact the office."
    }
   )
   if (pickupTarget == null && !state.detailLoading) {
    TextButton(onClick = onRefreshTrip, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
     Text("Refresh trip coordinates")
    }
   }
   DriverButton(
    if (locating && activeAction == "arrive") "Checking pick-up location…" else "I'VE ARRIVED AT PICK-UP",
    !busy
   ) { onRequestLocationAction("arrive") }
  }
  if (status == "assigned" && delivery.value("pickup_arrived_at").isNotBlank()) {
   SupportingText("Grace ends ${MobilePresentation.time(delivery.value("grace_ends_at"))}. Office review threshold ${MobilePresentation.time(delivery.value("auto_cancel_at"))}. Cancellation requires an office decision.")
   DriverButton("CUSTOMER ARRIVED AT PICK-UP", !busy) { onDriverAction("showed") }
   OutlinedButton(
    onClick = { onDriverAction("no_show") },
    enabled = !busy && noShowDue,
    shape = RoundedCornerShape(10.dp),
    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
   ) {
    Text("Report no-show for office review")
   }
  }
  if (status == "loading") {
   DriverPaymentGateCard(
    summary = paymentSummary,
    busy = busy,
    onOpenPayment = onOpenPayment,
    onRefreshTrip = onRefreshTrip,
    onViewPaymentProof = onViewPaymentProof
   )
   DriverButton(
    label = if (paymentSummary.canDepart) "CARGO LOADED — START TRANSIT" else "START TRANSIT (AWAITING VERIFICATION)",
    enabled = !busy && paymentSummary.canDepart
   ) { onDriverAction("loaded") }
  }
  if (status == "in_transit" || (status == "delivered" && delivery.value("proof_review_status") == "correction_requested")) {
   delivery.value("proof_review_reason").takeIf(String::isNotBlank)?.let {
    Surface(
     color = MaterialTheme.colorScheme.errorContainer,
     shape = RoundedCornerShape(10.dp)
    ) {
     Text(
      "Office correction request: $it",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onErrorContainer,
      modifier = Modifier.fillMaxWidth().padding(12.dp)
     )
    }
   }
   if (status == "in_transit") {
    val destinationTarget = DriverGeofenceRules.extractTarget(shipment, state.details, isDestination = true)
    SupportingText(
     when {
      destinationTarget != null -> "Must be within 50m of the destination to mark as delivered."
      state.detailLoading -> "Loading destination coordinates…"
      else -> "Destination coordinates are missing for this trip. Refresh trip details or contact the office."
     }
    )
    if (destinationTarget == null && !state.detailLoading) {
     TextButton(onClick = onRefreshTrip, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
      Text("Refresh trip coordinates")
     }
    }
   }
   DriverButton(
    if (status == "delivered") "SUBMIT CORRECTED PROOF"
    else if (locating && activeAction == "deliver") "Checking destination location…"
    else "MARK AS DELIVERED",
    !busy
   ) {
    if (status == "delivered") onOpenProof() else onRequestLocationAction("deliver")
   }
  }
  if (status == "delivered") StatusBadge(delivery.value("proof_review_status"))
  if (hasProofDraft) {
   DriverButton("RECOVER SAVED PROOF", !busy) { onOpenProof() }
  }
  if (status in listOf("assigned", "loading", "in_transit")) {
   OutlinedButton(
    onClick = onOpenIncident,
    enabled = !busy,
    shape = RoundedCornerShape(10.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
   ) {
    Text("Report an Issue")
   }
  }
 }
}

@Preview(showBackground = true)
@Composable
private fun DriverTripActionsPickupGeofencePreview() {
 DriverTheme {
  DriverTripActionsContent(
   delivery = buildJsonObject {
    put("id", "del-1")
    put("shipment_id", "shp-1")
    put("status", "assigned")
    put("route_started_at", "2026-11-15T01:00:00Z")
   },
   shipment = buildJsonObject {
    put("id", "shp-1")
    put("pickup_lat", 15.4865)
    put("pickup_lng", 120.9679)
   },
   state = MobileState(),
   tracking = TrackingStatus(delivery = "del-1", message = "Sharing live location", buffered = 0),
   locating = false,
   activeAction = null,
   error = "You are 142m from the pick-up location. Move within 50m to confirm pick-up arrival.",
   noShowDue = false,
   hasProofDraft = false,
   onRequestLocationAction = {},
   onStopTracking = {},
   onDriverAction = {},
   onOpenProof = {},
   onOpenIncident = {},
   onRefreshTrip = {},
   modifier = Modifier.padding(16.dp)
  )
 }
}

@Preview(showBackground = true)
@Composable
private fun DriverTripActionsDestinationMissingPinsPreview() {
 DriverTheme {
  DriverTripActionsContent(
   delivery = buildJsonObject {
    put("id", "del-1")
    put("shipment_id", "shp-1")
    put("status", "in_transit")
    put("route_started_at", "2026-11-15T01:00:00Z")
   },
   shipment = buildJsonObject {
    put("id", "shp-1")
   },
   state = MobileState(),
   tracking = TrackingStatus(delivery = "del-1", message = "Sharing live location", buffered = 1),
   locating = false,
   activeAction = null,
   error = null,
   noShowDue = false,
   hasProofDraft = false,
   onRequestLocationAction = {},
   onStopTracking = {},
   onDriverAction = {},
   onOpenProof = {},
   onOpenIncident = {},
   onRefreshTrip = {},
   modifier = Modifier.padding(16.dp)
  )
 }
}

@Composable
internal fun DriverPaymentGateCard(
 summary: DriverPaymentSummary,
 busy: Boolean,
 onOpenPayment: () -> Unit,
 onRefreshTrip: () -> Unit,
 onViewPaymentProof: () -> Unit,
 modifier: Modifier = Modifier
) {
 val (containerColor, contentColor, borderColor, title) = when (summary.gateStatus) {
  DriverPaymentGateStatus.VERIFIED -> Quad(
   SuccessBannerBg,
   SuccessGreen,
   SuccessGreen.copy(alpha = 0.35f),
   "Full payment verified by office"
  )
  DriverPaymentGateStatus.PENDING_REVIEW -> Quad(
   ExpiringOrangeBg,
   ExpiringOrange,
   ExpiringOrange.copy(alpha = 0.35f),
   "Awaiting office payment verification"
  )
  DriverPaymentGateStatus.REJECTED -> Quad(
   ExpiredRedBg,
   ExpiredRed,
   ExpiredRed.copy(alpha = 0.35f),
   "Payment proof rejected by office"
  )
  DriverPaymentGateStatus.FINAL_AMOUNT_MISSING -> Quad(
   ExpiringOrangeBg,
   ExpiringOrange,
   ExpiringOrange.copy(alpha = 0.35f),
   "Final charge awaiting office setup"
  )
  DriverPaymentGateStatus.UNPAID -> Quad(
   MaterialTheme.colorScheme.surfaceContainerHigh,
   MaterialTheme.colorScheme.onSurface,
   MaterialTheme.colorScheme.outlineVariant,
   "Full payment required before transit"
  )
 }

 Surface(
  color = containerColor,
  shape = RoundedCornerShape(12.dp),
  border = BorderStroke(1.dp, borderColor),
  modifier = modifier.fillMaxWidth()
 ) {
  Column(
   modifier = Modifier.fillMaxWidth().padding(14.dp),
   verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
   Text(
    text = title,
    style = MaterialTheme.typography.titleSmall,
    fontWeight = FontWeight.SemiBold,
    color = contentColor
   )

   when (summary.gateStatus) {
    DriverPaymentGateStatus.UNPAID -> {
     Text(
      text = "Amount due: ${MobilePresentation.money(summary.balance.toPlainString())}",
      style = MaterialTheme.typography.titleMedium.copy(
       fontFamily = TanawMonoFontFamily,
       fontWeight = FontWeight.Bold
      ),
      color = MaterialTheme.colorScheme.primary
     )
     Text(
      text = "After loading cargo, bill the customer in full, have them sign the receipt, and submit payment proof for office verification.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
     )
     DriverButton("RECORD PAYMENT RECEIVED", !busy, onOpenPayment)
    }
    DriverPaymentGateStatus.PENDING_REVIEW -> {
     val methodLabel = DriverPaymentMethod.fromValue(summary.latestPaymentMethod).label
     val submittedLine = listOfNotNull(
      MobilePresentation.money(summary.balance.toPlainString()),
      methodLabel.takeIf { summary.latestPaymentMethod.isNotBlank() },
      summary.latestPaidAt.takeIf { it.isNotBlank() }?.let { MobilePresentation.time(it) }
     ).joinToString(" · ")
     Text(
      text = submittedLine,
      style = MaterialTheme.typography.bodyMedium.copy(
       fontFamily = TanawMonoFontFamily,
       fontWeight = FontWeight.Medium
      ),
      color = contentColor
     )
     Text(
      text = "Wait at pick-up until the office verifies the payment. Start Transit unlocks automatically once verified.",
      style = MaterialTheme.typography.bodySmall,
      color = contentColor
     )
     Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
     ) {
      OutlinedButton(
       onClick = onRefreshTrip,
       enabled = !busy,
       shape = RoundedCornerShape(10.dp),
       modifier = Modifier.weight(1f).heightIn(min = 48.dp)
      ) {
       Text("Refresh Status")
      }
      if (summary.latestProofPath.isNotBlank() || summary.latestPaymentId.isNotBlank()) {
       OutlinedButton(
        onClick = onViewPaymentProof,
        enabled = !busy,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
       ) {
        Text("View Receipt")
       }
      }
     }
    }
    DriverPaymentGateStatus.REJECTED -> {
     Text(
      text = "Amount due: ${MobilePresentation.money(summary.balance.toPlainString())}",
      style = MaterialTheme.typography.titleSmall.copy(
       fontFamily = TanawMonoFontFamily,
       fontWeight = FontWeight.Bold
      ),
      color = contentColor
     )
     Text(
      text = summary.rejectionReason.ifBlank { "Office could not verify the previous submission. Confirm payment with the customer and resubmit." },
      style = MaterialTheme.typography.bodySmall,
      color = contentColor
     )
     DriverButton("RESUBMIT PAYMENT PROOF", !busy, onOpenPayment)
    }
    DriverPaymentGateStatus.VERIFIED -> {
     val amountText = summary.finalAmount?.let { MobilePresentation.money(it.toPlainString()) }.orEmpty()
     Text(
      text = if (amountText.isNotBlank()) "$amountText verified. You may now depart and start transit."
      else "Payment verified. You may now depart and start transit.",
      style = MaterialTheme.typography.bodySmall,
      color = contentColor
     )
    }
    DriverPaymentGateStatus.FINAL_AMOUNT_MISSING -> {
     Text(
      text = "The office has not finalized the shipment charge yet. Contact dispatch or refresh before collecting payment.",
      style = MaterialTheme.typography.bodySmall,
      color = contentColor
     )
     OutlinedButton(
      onClick = onRefreshTrip,
      enabled = !busy,
      shape = RoundedCornerShape(10.dp),
      modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
     ) {
      Text("Refresh Trip Details")
     }
    }
   }
  }
 }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Preview(showBackground = true, name = "Loading — Payment Unpaid")
@Composable
private fun DriverTripActionsPaymentUnpaidPreview() {
 DriverTheme {
  DriverTripActionsContent(
   delivery = buildJsonObject {
    put("id", "del-1")
    put("shipment_id", "shp-1")
    put("status", "loading")
    put("route_started_at", "2026-11-20T01:00:00Z")
   },
   shipment = buildJsonObject {
    put("id", "shp-1")
    put("final_amount", "2500.00")
   },
   state = MobileState(),
   tracking = TrackingStatus(delivery = "del-1", message = "Sharing live location", buffered = 0),
   locating = false,
   activeAction = null,
   error = null,
   noShowDue = false,
   hasProofDraft = false,
   paymentSummary = DriverPaymentSummary(
    finalAmount = BigDecimal("2500.00"),
    verifiedPaid = BigDecimal.ZERO,
    balance = BigDecimal("2500.00"),
    gateStatus = DriverPaymentGateStatus.UNPAID
   ),
   onRequestLocationAction = {},
   onStopTracking = {},
   onDriverAction = {},
   onOpenProof = {},
   onOpenIncident = {},
   onRefreshTrip = {},
   modifier = Modifier.padding(16.dp)
  )
 }
}

@Preview(showBackground = true, name = "Loading — Payment Pending Review")
@Composable
private fun DriverTripActionsPaymentPendingPreview() {
 DriverTheme {
  DriverTripActionsContent(
   delivery = buildJsonObject {
    put("id", "del-1")
    put("shipment_id", "shp-1")
    put("status", "loading")
    put("route_started_at", "2026-11-20T01:00:00Z")
   },
   shipment = buildJsonObject {
    put("id", "shp-1")
    put("final_amount", "2500.00")
   },
   state = MobileState(),
   tracking = TrackingStatus(delivery = "del-1", message = "Sharing live location", buffered = 0),
   locating = false,
   activeAction = null,
   error = null,
   noShowDue = false,
   hasProofDraft = false,
   paymentSummary = DriverPaymentSummary(
    finalAmount = BigDecimal("2500.00"),
    verifiedPaid = BigDecimal.ZERO,
    balance = BigDecimal("2500.00"),
    gateStatus = DriverPaymentGateStatus.PENDING_REVIEW,
    latestPaymentId = "pay-1",
    latestPaymentMethod = "cash",
    latestProofPath = "del-1/payment-req-1.jpg",
    latestPaidAt = "2026-11-20T02:15:00Z"
   ),
   onRequestLocationAction = {},
   onStopTracking = {},
   onDriverAction = {},
   onOpenProof = {},
   onOpenIncident = {},
   onRefreshTrip = {},
   modifier = Modifier.padding(16.dp)
  )
 }
}

@Preview(showBackground = true, name = "Loading — Payment Verified")
@Composable
private fun DriverTripActionsPaymentVerifiedPreview() {
 DriverTheme {
  DriverTripActionsContent(
   delivery = buildJsonObject {
    put("id", "del-1")
    put("shipment_id", "shp-1")
    put("status", "loading")
    put("route_started_at", "2026-11-20T01:00:00Z")
   },
   shipment = buildJsonObject {
    put("id", "shp-1")
    put("final_amount", "2500.00")
   },
   state = MobileState(),
   tracking = TrackingStatus(delivery = "del-1", message = "Sharing live location", buffered = 0),
   locating = false,
   activeAction = null,
   error = null,
   noShowDue = false,
   hasProofDraft = false,
   paymentSummary = DriverPaymentSummary(
    finalAmount = BigDecimal("2500.00"),
    verifiedPaid = BigDecimal("2500.00"),
    balance = BigDecimal.ZERO,
    gateStatus = DriverPaymentGateStatus.VERIFIED
   ),
   onRequestLocationAction = {},
   onStopTracking = {},
   onDriverAction = {},
   onOpenProof = {},
   onOpenIncident = {},
   onRefreshTrip = {},
   modifier = Modifier.padding(16.dp)
  )
 }
}

