package com.tanaw.app.feature.workspace

import java.math.BigDecimal
import java.time.Instant
import kotlinx.serialization.json.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class DriverTimelineEntry(val label:String,val timestamp:String)

internal object DriverRecordPresentation {
    fun section(data:JsonObject,key:String):String = data.obj("driver_sections").value(key).ifBlank {
        val rows=if(key=="driver_deliveries" && data[key] !is JsonArray)data["deliveries"] else data[key]
        if(rows is JsonArray)if(rows.isEmpty())"empty" else "ready" else "unavailable"
    }
    fun vehicleLabel(data:JsonObject,truck:JsonObject?):String = truck?.let {"${it.value("model")} · ${it.value("plate_number")}"}
        ?:if(section(data,"driver_trucks") in listOf("error","unavailable"))"Assigned vehicle unavailable" else "No assigned vehicle"
    fun timeline(details:JsonObject):List<DriverTimelineEntry> {
        val history=details.rows("history").map {row->
            val target=row.value("to_status").takeIf(String::isNotBlank)?.let(MobilePresentation::status)?:"Status unavailable"
            val from=row.value("from_status").takeIf(String::isNotBlank)?.let {"${MobilePresentation.status(it)} → "}.orEmpty()
            val note=row.value("note").takeIf(String::isNotBlank)?.let {" · $it"}.orEmpty()
            DriverTimelineEntry(from+target+note,row.value("created_at"))
        }
        val events=details.rows("events").map {row->
            val label=row.value("event_type").takeIf(String::isNotBlank)?.let(MobilePresentation::status)?:"Event type unavailable"
            val reason=row.obj("meta").value("reason").takeIf(String::isNotBlank)?.let {" · $it"}.orEmpty()
            DriverTimelineEntry(label+reason,row.value("occurred_at"))
        }
        return (history+events).sortedWith(compareBy<DriverTimelineEntry>{runCatching{Instant.parse(it.timestamp)}.getOrNull()?:Instant.MAX}.thenBy{it.label})
    }
    // Only complete, trip-scoped detail reads can establish a total (workspace expenses are capped).
    fun expenseTotal(details:JsonObject?,shipment:String):String {
        if(details==null || details.value("shipment_id")!=shipment || details.value("evidence_error").isNotBlank() || details["expenses"] !is JsonArray)return "Unavailable — refresh trip records"
        val rows=details["expenses"] as JsonArray
        var total=BigDecimal.ZERO
        for(element in rows) {
            val amount=(element as? JsonObject)?.value("amount")?.toBigDecimalOrNull()
            if(amount==null || amount.signum()<=0)return "Unavailable — expense amounts are incomplete"
            total+=amount
        }
        return MobilePresentation.money(total.toPlainString())
    }
    fun shipments(data:JsonObject,history:JsonObject?):List<JsonObject> =
        (data.rows("shipments")+history?.rows("shipments").orEmpty().filter {it.value("status") in listOf("completed","cancelled")}).distinctBy{it.value("id")}
    fun deliveries(data:JsonObject,history:JsonObject?):List<JsonObject> {
        val liveIds=data.rows("shipments").map{it.value("id")}.toSet()
        val terminalIds=history?.rows("shipments").orEmpty().filter{it.value("status") in listOf("completed","cancelled")}.map{it.value("id")}.toSet()
        val live=DriverUiRules.deliveryRows(data)
        return (live+history?.rows("driver_deliveries").orEmpty().filter{it.value("shipment_id") !in liveIds && it.value("shipment_id") in terminalIds}).distinctBy{it.value("id")}
    }
}

@Composable internal fun DriverRecordedTimeline(details:JsonObject) {
    var expanded by rememberSaveable(details.value("shipment_id")){mutableStateOf(false)}
    Text("Recorded timeline",fontSize=16.sp,fontWeight=FontWeight.Bold,color=DriverNavy)
    val entries=DriverRecordPresentation.timeline(details)
    val complete=details["history"] is JsonArray && details["events"] is JsonArray
    TextButton(onClick={expanded=!expanded}){Text(if(expanded)"Hide timeline" else if(complete)"Show timeline (${entries.size} updates)" else "Show timeline (some updates unavailable)")}
    if(!expanded)return
    if(details["history"] !is JsonArray || details["events"] !is JsonArray)Text("Some timeline records are unavailable. Refresh to retry.")
    if(entries.isEmpty() && details["history"] is JsonArray && details["events"] is JsonArray)Text("No status events have been recorded.")
    entries.forEach {entry->Surface(color=DriverBackground,shape=RoundedCornerShape(10.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(entry.label,fontSize=14.sp,fontWeight=FontWeight.SemiBold)
            Text(if(runCatching{Instant.parse(entry.timestamp)}.isSuccess)MobilePresentation.time(entry.timestamp)else "Date unavailable",fontSize=12.sp,color=DriverMuted)
        }
    }}
}
