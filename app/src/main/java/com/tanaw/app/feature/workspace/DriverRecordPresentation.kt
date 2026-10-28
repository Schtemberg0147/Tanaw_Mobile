package com.tanaw.app.feature.workspace

import java.math.BigDecimal
import java.time.Instant
import kotlinx.serialization.json.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

internal data class DriverTimelineEntry(val label:String,val timestamp:String)

internal object DriverRecordPresentation {
    fun section(data:JsonObject,key:String):String = data.obj("driver_sections").value(key).ifBlank {
        if(data[key] is JsonArray)if(data.rows(key).isEmpty())"empty" else "ready" else "unavailable"
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
        val live=if(data["driver_deliveries"] is JsonArray)data.rows("driver_deliveries") else data.rows("deliveries")
        return (live+history?.rows("driver_deliveries").orEmpty().filter{it.value("shipment_id") !in liveIds && it.value("shipment_id") in terminalIds}).distinctBy{it.value("id")}
    }
}

@Composable internal fun DriverRecordedTimeline(details:JsonObject) {
    Text("Recorded timeline")
    if(details["history"] !is JsonArray || details["events"] !is JsonArray)Text("Some timeline records are unavailable. Refresh to retry.")
    val entries=DriverRecordPresentation.timeline(details)
    if(entries.isEmpty() && details["history"] is JsonArray && details["events"] is JsonArray)Text("No status events have been recorded.")
    entries.forEach {entry->Text("${entry.label}\n${MobilePresentation.time(entry.timestamp)}")}
}
