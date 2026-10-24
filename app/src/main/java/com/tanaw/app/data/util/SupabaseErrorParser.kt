package com.tanaw.app.data.util

import com.tanaw.app.domain.model.BookingError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object SupabaseErrorParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parseBookingError(throwable: Throwable): BookingError {
        val rawMessage = throwable.message ?: ""
        
        return when {
            rawMessage.contains("OUTSIDE_OPERATING_HOURS") -> {
                val nextWindow = extractDetailField(rawMessage, "next_operating_window_start")
                BookingError.OutsideOperatingHours(nextWindow)
            }
            rawMessage.contains("CUSTOMER_BLOCKED") -> {
                val blockedUntil = extractDetailField(rawMessage, "blocked_until")
                BookingError.CustomerBlocked(blockedUntil)
            }
            rawMessage.contains("CUSTOMER_SUSPENDED") -> {
                val suspendedAt = extractDetailField(rawMessage, "suspended_at")
                BookingError.CustomerSuspended(suspendedAt)
            }
            rawMessage.contains("NO_PRICING_RATE") -> BookingError.NoPricingRate()
            rawMessage.contains("OUTSIDE_SERVICE_AREA") -> BookingError.OutsideServiceArea()
            rawMessage.contains("MISSING_COORDINATES") -> BookingError.MissingCoordinates()
            rawMessage.contains("NO_CUSTOMER_PROFILE") -> BookingError.NoCustomerProfile()
            rawMessage.contains("CUSTOMER_INACTIVE") -> BookingError.AccountInactive()
            rawMessage.contains("INVALID_VEHICLE_TYPE") -> BookingError.InvalidVehicleType()
            rawMessage.contains("INVALID_WEIGHT") -> BookingError.InvalidWeight()
            rawMessage.contains("INVALID_SCHEDULE") -> BookingError.InvalidSchedule()
            else -> BookingError.UnknownError(rawMessage.ifBlank { "An unexpected error occurred." })
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
