package com.tanaw.app.feature.workspace

import kotlinx.serialization.json.JsonObject
import java.net.URLEncoder

internal object DriverDirections {
    fun target(shipment:JsonObject,destination:Boolean):String {
        val prefix=if(destination)"dropoff" else "pickup"
        val lat=shipment.value("${prefix}_lat").toDoubleOrNull()
        val lng=shipment.value("${prefix}_lng").toDoubleOrNull()
        return if(lat!=null && lng!=null && lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0)
            "$lat,$lng" else shipment.value("${prefix}_address").trim()
    }
    fun url(shipment:JsonObject,destination:Boolean):String? = target(shipment,destination).takeIf(String::isNotBlank)?.let {
        "https://www.google.com/maps/dir/?api=1&destination=${URLEncoder.encode(it,"UTF-8")}&travelmode=driving"
    }
    fun tripUrl(shipment:JsonObject):String? {
        val pickup=target(shipment,false);val destination=target(shipment,true)
        if(pickup.isBlank() || destination.isBlank())return null
        return "https://www.google.com/maps/dir/?api=1&origin=${URLEncoder.encode(pickup,"UTF-8")}&destination=${URLEncoder.encode(destination,"UTF-8")}&travelmode=driving"
    }
}
