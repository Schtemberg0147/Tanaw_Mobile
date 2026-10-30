package com.tanaw.app.feature.workspace

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

/** Each optional driver section can fail without hiding the other permitted records. */
internal object DriverDatabaseData {
    suspend fun load(readSettings:suspend ()->JsonArray = {JsonArray(emptyList())},read: suspend (String, String?) -> JsonArray): JsonObject {
        val fields=mutableMapOf<String,JsonElement>()
        val failed=mutableListOf<String>()
        val sections=mutableMapOf<String,JsonElement>()
        try {
            val settings=readSettings()
            fields["driver_settings"]=settings
            sections["driver_settings"]=JsonPrimitive("ready")
        } catch(cancelled:CancellationException) {throw cancelled}
        catch(_:Exception) {sections["driver_settings"]=JsonPrimitive("error");failed.add("profile settings")}
        suspend fun section(table:String,key:String,label:String,driver:String?=null):JsonArray? = try {
            read(table,driver).also {fields[key]=it;sections[key]=JsonPrimitive(if(it.isEmpty())"empty" else "ready")}
        } catch(cancelled:CancellationException) {throw cancelled}
        catch(_:Exception) {failed.add(label);sections[key]=JsonPrimitive("error");null}
        val roster=section("drivers","driver_roster","driver profile")
        val driver=roster?.firstOrNull() as? JsonObject
        fields.remove("driver_roster")
        fields["driver"]=driver?:buildJsonObject{}
        val id=(driver?.get("id") as? JsonPrimitive)?.contentOrNull
        if(!id.isNullOrBlank()) {
            section("trucks","driver_trucks","assigned vehicle",id)
            section("compliance_documents","compliance","compliance",id)
            section("deliveries","driver_deliveries","trip details",id)
            section("delivery_expenses","expenses","expenses",id)
        } else {
            listOf("driver_trucks","compliance","driver_deliveries","expenses").forEach {sections[it]=JsonPrimitive("unavailable")}
            if(roster!=null) failed.add("driver roster link (contact the office)")
        }
        fields["driver_sections"]=JsonObject(sections)
        if(failed.isNotEmpty()) fields["driver_read_error"]=JsonPrimitive("Could not load ${failed.joinToString(", ")}. Refresh to retry.")
        return JsonObject(fields)
    }
}
