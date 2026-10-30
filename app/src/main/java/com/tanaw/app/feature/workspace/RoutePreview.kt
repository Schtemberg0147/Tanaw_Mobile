package com.tanaw.app.feature.workspace

import kotlinx.serialization.json.*
data class RoutePreview(val geometry:String,val distanceKm:Double,val minutes:Int) {
 companion object {
  fun parse(data:JsonObject):RoutePreview {
   require(data["roadOnlyVerified"]?.jsonPrimitive?.booleanOrNull==true)
   val distance=data["distanceKm"]?.jsonPrimitive?.doubleOrNull
   val minutes=data["estimatedMinutes"]?.jsonPrimitive?.intOrNull
   require(distance!=null && distance.isFinite() && distance>=0 && minutes!=null && minutes>=0)
   val geometry=data["geojson"]?.jsonObject ?: error("No route geometry")
   val lines=geometry["features"]?.jsonArray.orEmpty().filter {it.jsonObject["geometry"]?.jsonObject?.get("type")?.jsonPrimitive?.content=="LineString"}
   require(lines.isNotEmpty())
   lines.forEach {line ->
    val coordinates=line.jsonObject["geometry"]!!.jsonObject["coordinates"]!!.jsonArray
    require(coordinates.size>=2)
    coordinates.forEach {point ->val values=point.jsonArray;require(values.size>=2);val lng=values[0].jsonPrimitive.double;val lat=values[1].jsonPrimitive.double;require(lng.isFinite() && lat.isFinite() && lng in -180.0..180.0 && lat in -90.0..90.0)}
   }
   return RoutePreview(geometry.toString(),distance,minutes)
  }
 }
}
