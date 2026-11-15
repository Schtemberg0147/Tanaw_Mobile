package com.tanaw.app.feature.workspace

import kotlinx.serialization.json.JsonObject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

internal data class GeofenceTarget(
    val latitude: Double,
    val longitude: Double,
)

internal sealed class GeofenceCheckResult {
    data class Allowed(val distanceMeters: Int) : GeofenceCheckResult()
    data class TooFar(val distanceMeters: Int, val maxRadiusMeters: Int) : GeofenceCheckResult()
    data object LoadingCoordinates : GeofenceCheckResult()
    data object MissingTargetCoordinates : GeofenceCheckResult()
    data object InvalidDriverCoordinates : GeofenceCheckResult()
}

internal object DriverGeofenceRules {
    const val MAX_ARRIVAL_RADIUS_METERS: Double = 50.0
    private const val EARTH_RADIUS_METERS: Double = 6_371_000.0

    fun isValidCoordinate(lat: Double?, lng: Double?): Boolean =
        lat != null && lng != null &&
            lat.isFinite() && lng.isFinite() &&
            lat in -90.0..90.0 && lng in -180.0..180.0 &&
            !(lat == 0.0 && lng == 0.0)

    fun extractTarget(
        shipment: JsonObject,
        details: JsonObject?,
        isDestination: Boolean
    ): GeofenceTarget? {
        val prefix = if (isDestination) "dropoff" else "pickup"
        val sLat = shipment.value("${prefix}_lat").toDoubleOrNull()
        val sLng = shipment.value("${prefix}_lng").toDoubleOrNull()
        if (isValidCoordinate(sLat, sLng)) {
            return GeofenceTarget(requireNotNull(sLat), requireNotNull(sLng))
        }
        val shipmentId = shipment.value("id")
        val matchingDetails = details?.takeIf {
            shipmentId.isBlank() || it.value("shipment_id").isBlank() || it.value("shipment_id") == shipmentId
        }
        val plan = matchingDetails?.obj("routing")?.obj("plan")
        if (plan != null) {
            val pLat = plan.value("${prefix}_lat").toDoubleOrNull()
            val pLng = plan.value("${prefix}_lng").toDoubleOrNull()
            if (isValidCoordinate(pLat, pLng)) {
                return GeofenceTarget(requireNotNull(pLat), requireNotNull(pLng))
            }
        }
        return null
    }

    fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaPhi = Math.toRadians(lat2 - lat1)
        val deltaLambda = Math.toRadians(lng2 - lng1)
        val a = sin(deltaPhi / 2).pow(2) +
            cos(phi1) * cos(phi2) * sin(deltaLambda / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    fun checkArrival(
        driverLat: Double,
        driverLng: Double,
        shipment: JsonObject,
        details: JsonObject?,
        isDestination: Boolean,
        detailLoading: Boolean = false,
        maxRadiusMeters: Double = MAX_ARRIVAL_RADIUS_METERS
    ): GeofenceCheckResult {
        if (!isValidCoordinate(driverLat, driverLng)) {
            return GeofenceCheckResult.InvalidDriverCoordinates
        }
        val target = extractTarget(shipment, details, isDestination)
            ?: return if (detailLoading) {
                GeofenceCheckResult.LoadingCoordinates
            } else {
                GeofenceCheckResult.MissingTargetCoordinates
            }
        val distance = distanceMeters(driverLat, driverLng, target.latitude, target.longitude)
        val roundedMeters = distance.roundToInt()
        return if (distance <= maxRadiusMeters) {
            GeofenceCheckResult.Allowed(roundedMeters)
        } else {
            GeofenceCheckResult.TooFar(roundedMeters, maxRadiusMeters.roundToInt())
        }
    }

    fun message(result: GeofenceCheckResult, isDestination: Boolean): String {
        val place = if (isDestination) "destination" else "pick-up location"
        val action = if (isDestination) "mark as delivered" else "confirm pick-up arrival"
        return when (result) {
            is GeofenceCheckResult.Allowed -> ""
            is GeofenceCheckResult.TooFar ->
                "You are ${result.distanceMeters}m from the $place. Move within ${result.maxRadiusMeters}m to $action."
            GeofenceCheckResult.LoadingCoordinates ->
                "Loading $place coordinates. Wait a moment and try again."
            GeofenceCheckResult.MissingTargetCoordinates ->
                "${place.replaceFirstChar { it.uppercase() }} coordinates are missing for this trip. Refresh the trip or contact the office to pin the $place."
            GeofenceCheckResult.InvalidDriverCoordinates ->
                "Could not obtain a valid GPS location. Move outdoors and try again."
        }
    }
}
