package com.tanaw.app.feature.workspace

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import java.time.Instant

internal object DriverRouteStatus {
    fun forAssignment(routing:JsonObject,shipment:JsonObject,delivery:JsonObject):JsonObject {
        if(routing["route_verified"]!=JsonPrimitive(true))return routing
        val plan=routing.obj("plan")
        val matches=plan.value("truck_id").isNotBlank() && plan.value("truck_id")==delivery.value("truck_id") && plan["geometry"]==shipment["route_geometry"] &&
            listOf("pickup_lat","pickup_lng","dropoff_lat","dropoff_lng").all {key->val a=plan.value(key).toBigDecimalOrNull();val b=shipment.value(key).toBigDecimalOrNull();a!=null && b!=null && a.compareTo(b)==0} &&
            plan.value("cargo_weight_kg").toBigDecimalOrNull()?.let{a->shipment.value("weight_kg").toBigDecimalOrNull()?.let{a.compareTo(it)==0}}==true
        return if(matches)routing else buildJsonObject{put("reason","Assignment or confirmed route changed. Refresh to recalculate.")}
    }
    fun eta(data:JsonObject,offline:Boolean,now:Instant=Instant.now()):String {
        if(offline)return "Live ETA unavailable while offline"
        data.value("reason").takeIf(String::isNotBlank)?.let{return "ETA unavailable: $it"}
        if(data["route_verified"]!=JsonPrimitive(true))return "ETA unavailable: office-confirmed truck route required"
        val expiry=runCatching{Instant.parse(data.value("expires_at"))}.getOrNull()?:return "ETA unavailable — refresh to retry"
        val calculated=runCatching{Instant.parse(data.value("calculated_at"))}.getOrNull()?:return "ETA unavailable — calculation time missing"
        val position=runCatching{Instant.parse(data.value("position_at"))}.getOrNull()?:return "ETA unavailable — GPS time missing"
        if(!now.isBefore(expiry) || calculated.isAfter(now.plusSeconds(5)) || position.isAfter(now) || position.isBefore(now.minusSeconds(120)))return "ETA expired — waiting for fresh GPS and route calculation"
        val arrival=runCatching{Instant.parse(data.value("eta_at"))}.getOrNull()?:return "ETA unavailable — arrival time missing"
        if(arrival.isBefore(calculated) || data.value("leg") !in listOf("pickup","destination"))return "ETA unavailable — invalid estimate"
        return "ETA to ${data.value("leg")}: ${MobilePresentation.time(arrival.toString())}"
    }
}

@Composable internal fun DriverRouteStatusCard(routing:JsonObject,offline:Boolean) {
    var now by remember {mutableStateOf(Instant.now())}
    LaunchedEffect(Unit){while(true){delay(15000);now=Instant.now()}}
    Text(if(routing["route_verified"]==JsonPrimitive(true))"Office-confirmed truck route" else "Truck route awaiting office confirmation")
    Text(DriverRouteStatus.eta(routing,offline,now))
    routing.value("calculated_at").takeIf(String::isNotBlank)?.let{Text("Calculated ${MobilePresentation.time(it)} · GPS ${MobilePresentation.time(routing.value("position_at"))}")}
    if(routing["route_verified"]==JsonPrimitive(true))Text("OpenRouteService HGV · vehicle limits applied · no live traffic")
    Text("Google Maps may recalculate a different route and does not enforce this truck's dimensions or weight. Obey road signs and contact the office if a route is unsuitable.")
}
