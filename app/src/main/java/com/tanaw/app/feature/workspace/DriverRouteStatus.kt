package com.tanaw.app.feature.workspace

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.json.*
import java.time.Instant

internal object DriverRouteStatus {
    fun extractGeoJson(element: JsonElement?): String? {
        if (element == null || element is JsonNull) return null
        val obj = when (element) {
            is JsonObject -> element
            is JsonPrimitive -> {
                val raw = element.contentOrNull?.trim() ?: return null
                if (!raw.startsWith("{") || !raw.endsWith("}")) return null
                runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return null
            }
            else -> return null
        }
        val type = (obj["type"] as? JsonPrimitive)?.contentOrNull?.trim()
        if (type.isNullOrBlank()) return null
        return if (element is JsonPrimitive) element.content.trim() else obj.toString()
    }

    fun resolvedGeometry(shipment: JsonObject, routing: JsonObject? = null): String? {
        extractGeoJson(shipment["route_geometry"])?.let { return it }
        if (routing != null && routing["route_verified"] == JsonPrimitive(true)) {
            return extractGeoJson(routing.obj("plan")["geometry"])
        }
        return null
    }

    fun forAssignment(routing:JsonObject,shipment:JsonObject,delivery:JsonObject):JsonObject {
        if(routing["loading"]==JsonPrimitive(true))return routing
        if(routing["route_verified"]!=JsonPrimitive(true))return routing
        val plan=routing.obj("plan")
        val sGeomElement = shipment["route_geometry"]?.takeUnless { it is JsonNull }
        val pGeomElement = plan["geometry"]?.takeUnless { it is JsonNull }
        val sParsed = sGeomElement?.let { runCatching {
            if (it is JsonPrimitive) Json.parseToJsonElement(it.content) else it
        }.getOrNull() }
        val pParsed = pGeomElement?.let { runCatching {
            if (it is JsonPrimitive) Json.parseToJsonElement(it.content) else it
        }.getOrNull() }
        val geomMatches = sGeomElement == null || (sParsed != null && sParsed == pParsed)
        val matches=plan.value("truck_id").isNotBlank() && plan.value("truck_id")==delivery.value("truck_id") && geomMatches &&
            listOf("pickup_lat","pickup_lng","dropoff_lat","dropoff_lng").all {key->val a=plan.value(key).toBigDecimalOrNull();val b=shipment.value(key).toBigDecimalOrNull();a!=null && b!=null && a.compareTo(b)==0} &&
            plan.value("cargo_weight_kg").toBigDecimalOrNull()?.let{a->shipment.value("weight_kg").toBigDecimalOrNull()?.let{a.compareTo(it)==0}}==true
        return if(matches)routing else buildJsonObject{put("reason","Assignment or confirmed route changed. Refresh to recalculate.")}
    }
    fun eta(data:JsonObject,offline:Boolean,now:Instant=Instant.now()):String {
        if(offline)return "Live ETA unavailable while offline"
        if(data["loading"]==JsonPrimitive(true))return "Loading live route status and ETA…"
        data.value("reason").takeIf(String::isNotBlank)?.let{return "ETA unavailable: $it"}
        if(data["route_verified"]!=JsonPrimitive(true))return "ETA unavailable: office-confirmed truck route required"
        val expiry=runCatching{Instant.parse(data.value("expires_at"))}.getOrNull()?:return "ETA unavailable — refresh to retry"
        val calculated=runCatching{Instant.parse(data.value("calculated_at"))}.getOrNull()?:return "ETA unavailable — calculation time missing"
        val position=runCatching{Instant.parse(data.value("position_at"))}.getOrNull()?:return "ETA unavailable — GPS time missing"
        if(!now.isBefore(expiry) || calculated.isBefore(now.minusSeconds(120)) || expiry.isAfter(calculated.plusSeconds(120)) || expiry.isAfter(position.plusSeconds(120)) || calculated.isAfter(now.plusSeconds(5)) || position.isAfter(now.plusSeconds(5)) || position.isBefore(now.minusSeconds(120)))return "ETA expired — waiting for fresh GPS and route calculation"
        val arrival=runCatching{Instant.parse(data.value("eta_at"))}.getOrNull()?:return "ETA unavailable — arrival time missing"
        if(arrival.isBefore(calculated) || data.value("leg") !in listOf("pickup","destination"))return "ETA unavailable — invalid estimate"
        return "ETA to ${data.value("leg")}: ${MobilePresentation.time(arrival.toString())}"
    }
}

@Composable internal fun DriverRouteStatusCard(routing:JsonObject,offline:Boolean) {
    var now by remember {mutableStateOf(Instant.now())}
    LaunchedEffect(Unit){while(true){delay(15.seconds);now=Instant.now()}}
    val isLoading = routing["loading"] == JsonPrimitive(true)
    val isVerified = routing["route_verified"] == JsonPrimitive(true)
    Text(
        when {
            isLoading -> "Checking office-confirmed truck route…"
            isVerified -> "Office-confirmed truck route"
            else -> "Truck route awaiting office confirmation"
        },
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
    Text(DriverRouteStatus.eta(routing,offline,now),fontSize=14.sp,color=MaterialTheme.colorScheme.onSurface)
    routing.value("calculated_at").takeIf(String::isNotBlank)?.let{Text("Calculated ${MobilePresentation.time(it)} · GPS ${MobilePresentation.time(routing.value("position_at"))}",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    if(isVerified)Text("OpenRouteService HGV · vehicle limits applied · no live traffic",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    Text("Google Maps may recalculate a different route and does not enforce this truck's dimensions or weight. Obey road signs and contact the office if a route is unsuitable.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun DriverTruckRestrictionCard(
    spec: TruckSpecSummary,
    ban: TruckBanAdvisory
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "Vehicle & Route Restrictions",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                TruckRestrictionRules.formatSpecLine(spec),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (spec.isOverCapacity) {
                Text(
                    "WARNING: Cargo weight exceeds vehicle rated capacity!",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }
            for (warning in ban.warnings) {
                Text(
                    warning,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

