package com.tanaw.app.feature.workspace

import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.serialization.json.*

internal data class FuelEntry(val liters:BigDecimal,val price:BigDecimal,val odometer:BigDecimal,val station:String) {
    val total:BigDecimal get()=liters.multiply(price).setScale(2,RoundingMode.HALF_UP)
    companion object {
        fun parse(liters:String,price:String,odometer:String,station:String):FuelEntry? {
            fun decimal(s:String)=s.takeIf {it.matches(Regex("[0-9]{1,8}(\\.[0-9]{1,3})?"))}?.toBigDecimalOrNull()
            val l=decimal(liters)?:return null;val p=decimal(price)?:return null;val o=decimal(odometer)?:return null
            if(l.scale()>2 || p.scale()>2 || o.scale()>1 || l<=BigDecimal.ZERO || l>BigDecimal("10000") || p<=BigDecimal.ZERO || p>BigDecimal("10000") || o>BigDecimal("10000000") || station.trim().length>200)return null
            return FuelEntry(l,p,o,station.trim()).takeIf {it.total<=BigDecimal("1000000")}
        }
    }
}

internal object DriverHistory {
    fun sameRevision(previous:JsonObject?,page:JsonObject)=previous==null || previous.value("revision")==page.value("revision")
    fun merge(previous:JsonObject?,page:JsonObject):JsonObject {
        if(page.value("offset")=="0" || previous?.value("period")!=page.value("period"))return page
        return JsonObject(page+listOf("shipments","driver_deliveries").associateWith {key->
            JsonArray((previous.rows(key)+page.rows(key)).distinctBy {it.value("id")})
        })
    }
    fun reconcileWithLive(
        data:JsonObject,
        history:JsonObject?,
        period:String,
        today:java.time.LocalDate=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Manila"))
    ):JsonObject? {
        if(history==null || history.value("period")!=period)return null
        val historyShipments=history.rows("shipments")
        val historyTotal=history.value("total").toIntOrNull()?:historyShipments.size
        val liveShipments=data.rows("shipments")
        val liveById=liveShipments.associateBy {it.value("id")}
        val historyById=historyShipments.associateBy {it.value("id")}
        val updatedExisting=historyShipments.map {liveById[it.value("id")]?:it}
        val prependNew=if(historyShipments.isNotEmpty() || historyTotal==0) {
            liveShipments.filter {it.value("id") !in historyById && DriverUiRules.inPeriod(it,period,today)}
        } else emptyList()
        val reconciledShipments=(prependNew+updatedExisting).distinctBy {it.value("id")}
        val liveDeliveries=DriverUiRules.deliveryRows(data)
        val liveDeliveriesById=liveDeliveries.associateBy {it.value("id")}
        val historyDeliveries=history.rows("driver_deliveries").map {liveDeliveriesById[it.value("id")]?:it}
        val reconciledShipmentIds=reconciledShipments.map {it.value("id")}.toSet()
        val reconciledDeliveries=(liveDeliveries.filter {it.value("shipment_id") in reconciledShipmentIds}+historyDeliveries)
            .distinctBy {it.value("id")}
        val deliveryLookup=buildJsonObject {put("driver_deliveries",JsonArray(reconciledDeliveries))}
        val oldDeliveryLookup=buildJsonObject {put("driver_deliveries",JsonArray(history.rows("driver_deliveries")))}
        var completedDelta=0
        var allowanceDelta=BigDecimal.ZERO
        var allowanceMissingDelta=0
        var onTimeKnownDelta=0L
        var onTimeCountDelta=0L
        for(s in reconciledShipments) {
            val id=s.value("id")
            val oldShipment=historyById[id]
            val wasServerCompleted=oldShipment?.value("status")=="completed"
            val currentDelivery=DriverUiRules.delivery(deliveryLookup,id)
            val nowCompleted=DriverUiRules.isDriverCompleted(s,currentDelivery)
            if(nowCompleted && !wasServerCompleted) {
                completedDelta++
                val amount=(currentDelivery.value("driver_allowance").ifBlank{s.value("driver_allowance_php")}).toBigDecimalOrNull()
                if(amount!=null)allowanceDelta=allowanceDelta.add(amount) else allowanceMissingDelta++
                val arrived=currentDelivery.value("arrived_at").ifBlank{s.value("actual_delivery_at")}
                val scheduled=s.value("scheduled_arrival").ifBlank{s.value("estimated_arrival")}
                val isOnTime=runCatching {
                    !java.time.Instant.parse(arrived).isAfter(java.time.Instant.parse(scheduled))
                }.getOrNull()
                if(isOnTime!=null) {
                    onTimeKnownDelta++
                    if(isOnTime)onTimeCountDelta++
                }
            } else if(!nowCompleted && wasServerCompleted) {
                completedDelta--
                val oldDelivery=DriverUiRules.delivery(oldDeliveryLookup,id)
                val oldAmount=(oldDelivery.value("driver_allowance").ifBlank{oldShipment.value("driver_allowance_php")}).toBigDecimalOrNull()
                if(oldAmount!=null)allowanceDelta=allowanceDelta.subtract(oldAmount) else allowanceMissingDelta--
                val wasArrived=oldDelivery.value("arrived_at").ifBlank{oldShipment.value("actual_delivery_at")}
                val wasScheduled=oldShipment.value("scheduled_arrival").ifBlank{oldShipment.value("estimated_arrival")}
                val wasOnTime=runCatching {
                    !java.time.Instant.parse(wasArrived).isAfter(java.time.Instant.parse(wasScheduled))
                }.getOrNull()
                if(wasOnTime!=null) {
                    onTimeKnownDelta--
                    if(wasOnTime)onTimeCountDelta--
                }
            }
        }
        val addedTotal=prependNew.size
        if(addedTotal==0 && completedDelta==0 && reconciledShipments==historyShipments && reconciledDeliveries==history.rows("driver_deliveries")) {
            return history
        }
        val nextTotal=(historyTotal+addedTotal).coerceAtLeast(reconciledShipments.size)
        val nextCompleted=((history.value("completed").toIntOrNull()?:0)+completedDelta).coerceAtLeast(0)
        val nextAllowanceMissing=((history.value("allowance_missing").toIntOrNull()?:0)+allowanceMissingDelta).coerceAtLeast(0)
        val nextAllowanceTotal=((history.value("allowance_total").toBigDecimalOrNull()?:BigDecimal.ZERO).add(allowanceDelta)).max(BigDecimal.ZERO)
        val nextOnTimeKnown=((history.value("on_time_known").toLongOrNull()?:0L)+onTimeKnownDelta).coerceAtLeast(0L)
        val nextOnTimeCount=((history.value("on_time_count").toLongOrNull()?:0L)+onTimeCountDelta).coerceAtLeast(0L)
        return JsonObject(history+mapOf(
            "shipments" to JsonArray(reconciledShipments),
            "driver_deliveries" to JsonArray(reconciledDeliveries),
            "total" to JsonPrimitive(nextTotal),
            "completed" to JsonPrimitive(nextCompleted),
            "allowance_missing" to JsonPrimitive(nextAllowanceMissing),
            "allowance_total" to JsonPrimitive(nextAllowanceTotal.toPlainString()),
            "on_time_known" to JsonPrimitive(nextOnTimeKnown),
            "on_time_count" to JsonPrimitive(nextOnTimeCount)
        ))
    }
    fun allowance(data:JsonObject)=if(data.value("allowance_missing").toIntOrNull()!=0)"Not fully recorded" else MobilePresentation.money(data.value("allowance_total"))
    fun onTime(data:JsonObject):String {
        val known=data.value("on_time_known").toLongOrNull()?:0
        val onTime=data.value("on_time_count").toLongOrNull()?:0
        return if(known==0L)"Not recorded" else "${onTime*100/known}% ($known recorded)"
    }
}
