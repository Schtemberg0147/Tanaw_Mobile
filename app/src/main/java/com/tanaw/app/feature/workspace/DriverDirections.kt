package com.tanaw.app.feature.workspace

import kotlinx.serialization.json.*
import java.net.URLEncoder

internal object DriverDirections {
    fun target(shipment: JsonObject, destination: Boolean): String {
        val prefix = if (destination) "dropoff" else "pickup"
        val lat = shipment.value("${prefix}_lat").toDoubleOrNull()
        val lng = shipment.value("${prefix}_lng").toDoubleOrNull()
        val isValidCoord = lat != null && lng != null &&
            lat.isFinite() && lng.isFinite() &&
            lat in -90.0..90.0 && lng in -180.0..180.0 &&
            !(lat == 0.0 && lng == 0.0)
        return if (isValidCoord) "$lat,$lng" else shipment.value("${prefix}_address").trim()
    }

    fun extractWaypoints(routeGeoJson: String?, maxWaypoints: Int = 6): List<Pair<Double, Double>> {
        if (routeGeoJson.isNullOrBlank() || maxWaypoints <= 0) return emptyList()
        val clampedMax = maxWaypoints.coerceIn(1, 9)
        return runCatching {
            var element = Json.parseToJsonElement(routeGeoJson)
            while (element is JsonPrimitive && element.isString) {
                val next = runCatching { Json.parseToJsonElement(element.content) }.getOrNull() ?: break
                element = next
            }
            val rawCoords = mutableListOf<Pair<Double, Double>>()

            fun addCoord(coord: JsonElement?) {
                val arr = coord as? JsonArray ?: return
                val lng = arr.getOrNull(0)?.jsonPrimitive?.doubleOrNull
                val lat = arr.getOrNull(1)?.jsonPrimitive?.doubleOrNull
                if (lat != null && lng != null && lat.isFinite() && lng.isFinite() &&
                    lat in -90.0..90.0 && lng in -180.0..180.0 &&
                    !(lat == 0.0 && lng == 0.0)
                ) {
                    rawCoords.add(Pair(lat, lng))
                }
            }

            fun parseGeometry(geom: JsonObject) {
                val type = geom["type"]?.jsonPrimitive?.contentOrNull
                val coords = geom["coordinates"]?.jsonArray ?: return
                if (type.equals("LineString", ignoreCase = true)) {
                    for (coord in coords) {
                        addCoord(coord)
                    }
                } else if (type.equals("MultiLineString", ignoreCase = true)) {
                    for (line in coords) {
                        val lineCoords = line as? JsonArray ?: continue
                        for (coord in lineCoords) {
                            addCoord(coord)
                        }
                    }
                } else if (geom.containsKey("coordinates")) {
                    val first = coords.firstOrNull() as? JsonArray
                    if (first?.firstOrNull() is JsonArray) {
                        for (line in coords) {
                            val lineCoords = line as? JsonArray ?: continue
                            for (coord in lineCoords) {
                                addCoord(coord)
                            }
                        }
                    } else {
                        for (coord in coords) {
                            addCoord(coord)
                        }
                    }
                }
            }

            if (element is JsonObject) {
                val type = element["type"]?.jsonPrimitive?.contentOrNull
                if (type.equals("FeatureCollection", ignoreCase = true) || element.containsKey("features")) {
                    element["features"]?.jsonArray?.forEach { f ->
                        (f as? JsonObject)?.get("geometry")?.let { geom ->
                            if (geom is JsonObject) parseGeometry(geom)
                        }
                    }
                } else if (type.equals("Feature", ignoreCase = true) || element.containsKey("geometry")) {
                    element["geometry"]?.let { geom ->
                        if (geom is JsonObject) parseGeometry(geom)
                    }
                } else if (type.equals("LineString", ignoreCase = true) || type.equals("MultiLineString", ignoreCase = true) || element.containsKey("coordinates")) {
                    parseGeometry(element)
                }
            }

            if (rawCoords.size < 3) return@runCatching emptyList()

            val first = rawCoords.first()
            val last = rawCoords.last()
            val candidates = rawCoords.subList(1, rawCoords.size - 1).filter { pt ->
                kotlin.math.hypot(pt.first - first.first, pt.second - first.second) > 0.002 &&
                kotlin.math.hypot(pt.first - last.first, pt.second - last.second) > 0.002
            }

            if (candidates.isEmpty()) return@runCatching emptyList()

            if (candidates.size <= clampedMax) {
                candidates
            } else {
                (1..clampedMax).map { i -> candidates[(i * candidates.size) / (clampedMax + 1)] }
            }
        }.getOrDefault(emptyList())
    }

    fun corridorWaypointsParam(routeGeoJson: String?, maxWaypoints: Int = 9): String? {
        val waypoints = extractWaypoints(routeGeoJson, maxWaypoints)
        if (waypoints.isEmpty()) return null
        return waypoints.joinToString("|") { (lat, lng) ->
            String.format(java.util.Locale.US, "%.5f,%.5f", lat, lng)
        }
    }

    fun url(shipment: JsonObject, destination: Boolean, confirmedGeoJson: String? = null): String? =
        target(shipment, destination).takeIf(String::isNotBlank)?.let {
            val base = "https://www.google.com/maps/dir/?api=1&destination=${URLEncoder.encode(it, "UTF-8")}&travelmode=driving"
            if (destination) {
                corridorWaypointsParam(confirmedGeoJson)?.let { wp ->
                    "$base&waypoints=${URLEncoder.encode(wp, "UTF-8")}"
                } ?: base
            } else {
                base
            }
        }

    fun tripUrl(shipment: JsonObject, confirmedGeoJson: String? = null): String? {
        val pickup = target(shipment, false)
        val destination = target(shipment, true)
        if (pickup.isBlank() || destination.isBlank()) return null
        val base = "https://www.google.com/maps/dir/?api=1&origin=${URLEncoder.encode(pickup, "UTF-8")}&destination=${URLEncoder.encode(destination, "UTF-8")}&travelmode=driving"
        return corridorWaypointsParam(confirmedGeoJson)?.let { wp ->
            "$base&waypoints=${URLEncoder.encode(wp, "UTF-8")}"
        } ?: base
    }
}
