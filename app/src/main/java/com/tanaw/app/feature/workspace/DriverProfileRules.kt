package com.tanaw.app.feature.workspace

import kotlinx.serialization.json.*
import java.net.URI
import java.time.LocalDate
import java.time.ZoneId

internal data class DriverWarningRule(val first:Int,val critical:Int?)

/** Same persisted policy as the office; suggestions are never activated by mobile. */
internal object DriverProfileRules {
    val types=setOf("drivers_license","medical_certificate","nbi_clearance","lto_registration","insurance","franchise_permit","emission_test","maintenance")
    private fun setting(rows:JsonArray,key:String)=rows.filterIsInstance<JsonObject>().firstOrNull {it.value("key")==key}?.get("value")
    fun rule(rows:JsonArray,type:String):DriverWarningRule {
        val legacy=setting(rows,"compliance_threshold_days")?.let {(it as? JsonPrimitive)?.content?.toIntOrNull()?:error("Invalid legacy threshold")}?:30
        require(legacy in 0..365)
        val configured=setting(rows,"compliance_thresholds")?:return DriverWarningRule(legacy,null)
        val config=configured as? JsonObject?:error("Invalid thresholds")
        require(config.keys==types)
        val rules=config.mapValues {(_,value)->
            val item=value as? JsonObject?:error("Invalid threshold")
            val first=(item["first"] as? JsonPrimitive)?.takeUnless {it.isString}?.intOrNull?:error("Invalid first")
            val critical=(item["critical"] as? JsonPrimitive)?.takeUnless {it.isString}?.intOrNull?:error("Invalid critical")
            require(critical>=0 && first in 1..365 && first>critical)
            DriverWarningRule(first,critical)
        }
        return rules[type]?:DriverWarningRule(legacy,null)
    }
    fun settings(data:JsonObject):JsonArray? = if(DriverRecordPresentation.section(data,"driver_settings") in listOf("ready","empty"))data["driver_settings"] as? JsonArray else null
    fun policyLabel(data:JsonObject):String {
        val rows=settings(data)?:return "Warning thresholds unavailable. Refresh to retry."
        return runCatching {
            rule(rows,"drivers_license")
            if(setting(rows,"compliance_thresholds")!=null) "Uses the office’s saved per-document warning thresholds."
            else "Uses the office’s ${rule(rows,"unknown").first}-day warning window; no per-document policy is saved."
        }.getOrDefault("Warning thresholds invalid. Ask the administrator to correct the settings.")
    }
    fun compliance(doc:JsonObject,rows:JsonArray?,today:LocalDate=LocalDate.now(ZoneId.of("Asia/Manila"))):String {
        if(doc.value("review_status")!="approved")return doc.value("review_status").ifBlank {"Not reviewed"}.replace('_',' ').uppercase()
        val expiry=runCatching {LocalDate.parse(doc.value("expiry_date"))}.getOrNull()?:return "EXPIRY UNKNOWN"
        if(expiry.isBefore(today))return "EXPIRED"
        val warning=rows?.let {runCatching {rule(it,doc.value("doc_type"))}.getOrNull()}?:return "WARNING STATUS UNKNOWN"
        val days=java.time.temporal.ChronoUnit.DAYS.between(today,expiry)
        return when {warning.critical!=null && days<=warning.critical->"CRITICAL";days<=warning.first->"EXPIRING SOON";else->"VALID"}
    }
    fun content(data:JsonObject):JsonObject? = settings(data)?.let {setting(it,"driver_app_content") as? JsonObject}
    fun https(value:String):String?=runCatching {
        val uri=URI(value)
        require(uri.scheme=="https" && !uri.host.isNullOrBlank() && uri.userInfo==null)
        value
    }.getOrNull()
    fun email(value:String):String?=value.takeIf {it.matches(Regex("[A-Za-z0-9._+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"))}
    fun phone(value:String):String?=value.takeIf {it.matches(Regex("\\+?[0-9 ()-]{7,24}"))}?.filter {it.isDigit()||it=='+'}
    fun rosterState(data:JsonObject):String=data.obj("driver_sections").value("driver_roster").ifBlank {if(data.obj("driver").value("id").isNotBlank())"ready" else "unavailable"}
}
