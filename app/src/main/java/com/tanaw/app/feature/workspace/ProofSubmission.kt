package com.tanaw.app.feature.workspace

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import java.util.UUID

/** Identity includes recipient and BOTH immutable objects, including historical correction attempts. */
internal object ProofSubmission {
    fun validate(delivery:String,attempt:String,recipient:String,photo:ByteArray,signature:ByteArray) {
        require(UUID.fromString(delivery).toString()==delivery && UUID.fromString(attempt).toString()==attempt)
        require(recipient.trim().length in 2..100 && photo.size in 1..5*1024*1024 && signature.size in 1..5*1024*1024)
    }
    fun recorded(rows:List<JsonObject>,delivery:String,owner:String,recipient:String,photo:String,signature:String):Boolean {
        val pair=rows.filter {it.value("storage_path") in listOf(photo,signature)}
        if(pair.isEmpty())return false
        check(pair.size==2 && pair.map{it.value("storage_path")}.toSet()==setOf(photo,signature) &&
            pair.all {it.value("delivery_id")==delivery && it.value("uploaded_by")==owner && it.value("recipient_name")==recipient.trim()} &&
            pair.map{it.value("proof_attempt")}.distinct().singleOrNull()?.toIntOrNull()?.let{it>0}==true &&
            pair.single{it.value("storage_path")==photo}.value("kind")=="proof_of_delivery" &&
            pair.single{it.value("storage_path")==signature}.value("kind")=="signature") {
            "The saved proof differs from this attempt. Refresh and contact the office before changing it."
        }
        return true
    }
    suspend fun run(recorded:suspend ()->Boolean,verifyFiles:suspend ()->Unit,upload:suspend ()->Unit,commit:suspend ()->Unit) {
        if(recorded()){verifyFiles();return}
        upload()
        try {commit()}
        catch(cancelled:CancellationException){throw cancelled}
        catch(failure:Exception) {
            val saved=try {recorded()}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){throw failure}
            if(!saved)throw failure
            verifyFiles()
        }
    }
    fun applyConfirmedDelivery(data:JsonObject?,deliveryId:String,recipient:String,arrivedAt:String):JsonObject? {
        if(data==null || deliveryId.isBlank())return data
        val cleanRecipient=recipient.trim()
        val targetDelivery=(data.rows("driver_deliveries")+data.rows("deliveries")).firstOrNull {it.value("id")==deliveryId}
        val shipmentId=targetDelivery?.value("shipment_id").orEmpty()
        val truckId=targetDelivery?.value("truck_id").orEmpty()
        fun updateDeliveryRow(row:JsonObject):JsonObject {
            if(row.value("id")!=deliveryId)return row
            val existingArrived=row.value("arrived_at").ifBlank {arrivedAt}
            val reviewStatus=row.value("proof_review_status").takeIf {it=="approved"}?:"pending_review"
            return JsonObject(row+mapOf(
                "status" to kotlinx.serialization.json.JsonPrimitive("delivered"),
                "recipient_name" to kotlinx.serialization.json.JsonPrimitive(cleanRecipient),
                "arrived_at" to kotlinx.serialization.json.JsonPrimitive(existingArrived),
                "proof_review_status" to kotlinx.serialization.json.JsonPrimitive(reviewStatus),
                "proof_review_reason" to kotlinx.serialization.json.JsonPrimitive("")
            ))
        }
        val updated=data.toMutableMap()
        if(data["driver_deliveries"] is kotlinx.serialization.json.JsonArray) {
            updated["driver_deliveries"]=kotlinx.serialization.json.JsonArray(data.rows("driver_deliveries").map(::updateDeliveryRow))
        }
        if(data["deliveries"] is kotlinx.serialization.json.JsonArray) {
            updated["deliveries"]=kotlinx.serialization.json.JsonArray(data.rows("deliveries").map(::updateDeliveryRow))
        }
        if(shipmentId.isNotBlank() && data["shipments"] is kotlinx.serialization.json.JsonArray) {
            updated["shipments"]=kotlinx.serialization.json.JsonArray(data.rows("shipments").map {row->
                if(row.value("id")==shipmentId && row.value("status") !in listOf("completed","cancelled")) {
                    JsonObject(row+mapOf(
                        "status" to kotlinx.serialization.json.JsonPrimitive("review"),
                        "actual_delivery_at" to kotlinx.serialization.json.JsonPrimitive(row.value("actual_delivery_at").ifBlank {arrivedAt})
                    ))
                } else row
            })
        }
        val provisional=JsonObject(updated)
        val hasRemainingActive=DriverUiRules.deliveryRows(provisional).any {
            it.value("id")!=deliveryId && it.value("status") in listOf("assigned","loading","in_transit")
        }
        if(!hasRemainingActive) {
            val driverObj=data["driver"] as? JsonObject
            if(driverObj!=null && driverObj.value("status") in listOf("on_trip","in_transit","busy","assigned")) {
                updated["driver"]=JsonObject(driverObj+mapOf("status" to kotlinx.serialization.json.JsonPrimitive("available")))
            }
            if(truckId.isNotBlank() && data["driver_trucks"] is kotlinx.serialization.json.JsonArray) {
                updated["driver_trucks"]=kotlinx.serialization.json.JsonArray(data.rows("driver_trucks").map {row->
                    if(row.value("id")==truckId && row.value("status") in listOf("on_trip","in_transit","busy","assigned")) {
                        JsonObject(row+mapOf("status" to kotlinx.serialization.json.JsonPrimitive("available")))
                    } else row
                })
            }
        }
        return JsonObject(updated)
    }
}
