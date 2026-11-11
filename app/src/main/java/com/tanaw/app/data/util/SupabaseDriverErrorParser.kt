package com.tanaw.app.data.util

import com.tanaw.app.domain.model.DriverError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object SupabaseDriverErrorParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parseDriverError(throwable: Throwable): DriverError {
        val rawMessage = throwable.message ?: ""

        return when {
            rawMessage.contains("OUTSIDE_OPERATING_HOURS") -> {
                val nextWindow = extractDetailField(rawMessage, "next_operating_window_start")
                DriverError.OutsideOperatingHours(nextWindow)
            }
            rawMessage.contains("ARRIVAL_TOO_FAR") -> {
                val dist = extractDetailField(rawMessage, "distance_meters")?.toDoubleOrNull()
                val maxRad = extractDetailField(rawMessage, "max_radius_meters")?.toDoubleOrNull()
                DriverError.ArrivalTooFar(dist, maxRad)
            }
            rawMessage.contains("INVALID_POD_FILE") -> {
                val reason = extractDetailField(rawMessage, "reason")
                DriverError.InvalidPodFile(reason)
            }
            rawMessage.contains("DELIVERY_NOT_FOUND") -> DriverError.DeliveryNotFound()
            rawMessage.contains("INVALID_TRANSITION") -> {
                val currStatus = extractDetailField(rawMessage, "current_status")
                DriverError.InvalidTransition(currStatus)
            }
            else -> DriverError.UnknownError(rawMessage.ifBlank { "An unexpected error occurred." })
        }
    }

    private fun extractDetailField(rawMessage: String, key: String): String? {
        return try {
            val jsonStartIndex = rawMessage.indexOf("{")
            val jsonEndIndex = rawMessage.lastIndexOf("}")
            if ((jsonStartIndex != -1) && (jsonEndIndex > jsonStartIndex)) {
                val jsonString = rawMessage.substring(jsonStartIndex, jsonEndIndex + 1)
                val jsonElement = json.parseToJsonElement(jsonString)
                jsonElement.jsonObject[key]?.jsonPrimitive?.content
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
