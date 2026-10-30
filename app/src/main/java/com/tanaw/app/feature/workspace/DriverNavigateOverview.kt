package com.tanaw.app.feature.workspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.*
import org.maplibre.android.geometry.LatLng
import java.time.Instant
import kotlinx.coroutines.CancellationException

@Composable internal fun DriverTripSection(title:String,subtitle:String?=null,content:@Composable ColumnScope.()->Unit) {
    Surface(Modifier.fillMaxWidth(),color=Color.White,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,DriverLine)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(title,fontSize=18.sp,fontWeight=FontWeight.Bold,color=DriverNavy)
            subtitle?.let {Text(it,fontSize=12.sp,color=DriverMuted)}
            content()
        }
    }
}

/** Saved stop pins only. A stale/unrelated GPS sample must not zoom this preview out to the world. */
internal fun driverStopPins(s:JsonObject):List<Pair<String,LatLng>> =
    listOf("Pickup" to "pickup","Destination" to "dropoff").mapNotNull {(label,prefix)->
        val lat=s.value("${prefix}_lat").toDoubleOrNull();val lng=s.value("${prefix}_lng").toDoubleOrNull()
        if(lat!=null && lng!=null && lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0)label to LatLng(lat,lng) else null
    }

@Composable internal fun DriverNavigateOverview(s:JsonObject,d:JsonObject,state:MobileState,actions:@Composable (JsonObject,JsonObject)->Unit,expense:()->Unit,fuel:(()->Unit)?=null,routePreview:(suspend (String,LatLng,LatLng)->RoutePreview)?=null) {
    val stage=DriverUiRules.stage(d)
    val headings=listOf("Ready to start route","Heading to Pick-up","Waiting for Customer","Waiting to Load Cargo","Heading to Destination","Delivery submitted")
    val next=listOf("Start your route when you are ready to leave.","Go to the pickup, then confirm your arrival.","Confirm when the customer is present.","Confirm when all cargo has been loaded.","Go to the destination, then submit delivery proof.","The office will review your delivery proof.")
    var mapOpen by rememberSaveable(s.value("id")){mutableStateOf(false)}
    var progressOpen by rememberSaveable(d.value("id")){mutableStateOf(false)}
    val points=driverStopPins(s)
    val pinIdentity=points.map{Triple(it.first,it.second.latitude,it.second.longitude)}
    val savedGeometry=s["route_geometry"]?.takeUnless{it==JsonNull}?.toString()
    var preview by remember(s.value("id"),d.value("id"),d.value("driver_id"),pinIdentity){mutableStateOf<RoutePreview?>(null)}
    var previewError by remember(s.value("id"),d.value("id"),pinIdentity){mutableStateOf<String?>(null)}
    var previewLoading by remember {mutableStateOf(false)}
    var previewRevision by remember {mutableIntStateOf(0)}
    val previewLoader by rememberUpdatedState(routePreview)
    LaunchedEffect(mapOpen,s.value("id"),d.value("id"),d.value("driver_id"),pinIdentity,savedGeometry,state.offline,previewRevision) {
        previewLoading=false
        if(!mapOpen || savedGeometry!=null || preview!=null)return@LaunchedEffect
        if(state.offline){previewError="Road preview unavailable offline. Reconnect and retry.";return@LaunchedEffect}
        if(points.size!=2){previewError="The office must save both pickup and destination pins before a road preview is available.";return@LaunchedEffect}
        val loader=previewLoader
        if(loader==null){previewError="Road preview unavailable. Refresh the trip and retry.";return@LaunchedEffect}
        previewError=null;previewLoading=true
        try {preview=loader(d.value("id"),points[0].second,points[1].second)}
        catch(cancelled:CancellationException){throw cancelled}
        catch(_:Exception){previewError="Road preview unavailable. Check your connection and the assignment pins, then retry."}
        finally {previewLoading=false}
    }
    Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Surface(color=DriverNavy,shape=RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(s.value("tracking_number").ifBlank{"Current trip"},color=Color(0xFFC7DFFF),fontSize=12.sp)
                Text(headings[stage],color=Color.White,fontSize=22.sp,fontWeight=FontWeight.Bold)
                Text(next[stage],color=Color.White,fontSize=14.sp)
                HorizontalDivider(color=Color.White.copy(alpha=.2f))
                Text(if(stage>=4)"DESTINATION" else "PICKUP",color=Color(0xFFC7DFFF),fontSize=11.sp)
                Text(s.value(if(stage>=4)"dropoff_address" else "pickup_address").ifBlank{"Address unavailable — contact the office"},color=Color.White,fontSize=15.sp,fontWeight=FontWeight.SemiBold)
            }
        }
        DriverTripSection("Next step","Update the trip only when the action has actually happened.") {
            if(d.value("id").isBlank())Text("No delivery assignment is available. Refresh or contact the office.") else actions(s,d)
            if(fuel!=null)OutlinedButton(onClick=fuel,enabled=!state.busy && !state.offline,modifier=Modifier.fillMaxWidth()){Text("Record Fuel")}
            if(d.value("status") in listOf("in_transit","delivered"))OutlinedButton(onClick=expense,enabled=!state.busy && !state.offline,modifier=Modifier.fillMaxWidth()){Text("Add Trip Expense")}
        }
        DriverTripSection("Directions & route","Open directions for travel. The saved map is a location preview.") {
            Text("PICKUP",fontSize=11.sp,color=DriverMuted)
            Text(s.value("pickup_address").ifBlank{"Not recorded"},fontWeight=FontWeight.SemiBold)
            Text("DESTINATION",fontSize=11.sp,color=DriverMuted)
            Text(s.value("dropoff_address").ifBlank{"Not recorded"},fontWeight=FontWeight.SemiBold)
            DriverDirectionsButtons(s)
            val routing=state.details?.takeIf{it.value("shipment_id")==s.value("id")}?.obj("routing")?:buildJsonObject{}
            HorizontalDivider(color=DriverLine)
            DriverRouteStatusCard(DriverRouteStatus.forAssignment(routing,s,d),state.offline)
            Text("Office planned arrival: ${MobilePresentation.time(s.value("estimated_arrival"))}",fontSize=12.sp,color=DriverMuted)
            Text("Recorded distance: ${MobilePresentation.number(s.value("distance_km"),"km")} · duration: ${MobilePresentation.number(s.value("route_duration_minutes"),"min")}",fontSize=12.sp,color=DriverMuted)
            if(points.isEmpty())Text("This assignment has no saved map pins. Use address directions and confirm the entrance with the office.",fontSize=12.sp,color=DriverMuted)
            else {
                OutlinedButton(onClick={mapOpen=!mapOpen},modifier=Modifier.fillMaxWidth()){Text(if(mapOpen)"Hide route map" else "Show route map")}
                if(mapOpen) {
                    if(savedGeometry==null) {
                        Text("Unverified road preview",fontWeight=FontWeight.Bold,color=DriverNavy)
                        Text("Not office-confirmed. Truck height, width, loaded weight and access restrictions have not been checked. This preview does not change the confirmed route or live ETA.",fontSize=12.sp,color=DriverMuted)
                        if(previewLoading){LinearProgressIndicator(Modifier.fillMaxWidth());Text("Loading unverified road preview…",fontSize=12.sp)}
                        previewError?.let {Text(it,fontSize=12.sp,color=MaterialTheme.colorScheme.error)}
                        if(previewError!=null)OutlinedButton(onClick={previewRevision++},enabled=!state.offline && !previewLoading){Text("Retry road preview")}
                        preview?.let{Text("Preview road distance: ${it.distanceKm} km",fontSize=12.sp,color=DriverMuted)}
                    }
                    ShipmentMap(points,savedGeometry?:preview?.geometry)
                    Text("Saved pickup and destination locations. Vehicle GPS is not shown on this preview.",fontSize=12.sp,color=DriverMuted)
                    if(savedGeometry==null && preview==null && !previewLoading)Text("Showing saved stops only until a road preview is available.",fontSize=12.sp,color=DriverMuted)
                }
            }
        }
        DriverTripSection("Delivery progress") {
            Text("Current stage: ${headings[stage]}",fontWeight=FontWeight.SemiBold,color=DriverNavy)
            TextButton(onClick={progressOpen=!progressOpen}){Text(if(progressOpen)"Hide delivery steps" else "Show delivery steps")}
            if(progressOpen) {
                val steps=listOf("Assigned" to "assigned_at","Route started" to "route_started_at","Arrived at pickup" to "pickup_arrived_at","Customer present" to "customer_showed_at","Cargo loaded" to "departed_at","Proof submitted" to "arrived_at")
                steps.forEachIndexed {index,(label,field)->
                    val raw=d.value(field)
                    val recorded=runCatching{Instant.parse(raw)}.isSuccess
                    Surface(color=if(recorded)Color(0xFFEFF6FF)else DriverBackground,shape=RoundedCornerShape(8.dp)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text("${index+1}. $label",fontWeight=FontWeight.SemiBold)
                            Text(if(recorded)MobilePresentation.time(raw)else if(index>stage)"Not reached yet" else "Time not available",fontSize=12.sp,color=DriverMuted)
                        }
                    }
                }
            }
        }
    }
}
