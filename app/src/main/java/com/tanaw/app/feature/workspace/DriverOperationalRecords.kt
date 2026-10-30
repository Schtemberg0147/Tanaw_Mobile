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
    fun allowance(data:JsonObject)=if(data.value("allowance_missing").toIntOrNull()!=0)"Not fully recorded" else MobilePresentation.money(data.value("allowance_total"))
    fun onTime(data:JsonObject):String {
        val known=data.value("on_time_known").toLongOrNull()?:0
        val onTime=data.value("on_time_count").toLongOrNull()?:0
        return if(known==0L)"Not recorded" else "${onTime*100/known}% ($known recorded)"
    }
}
