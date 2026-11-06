package com.tanaw.app.feature.workspace

/**
 * Pure Kotlin rules for structured customer addresses (Street, Barangay, City, Province).
 * Formats structured address components into existing PostgreSQL/RPC address & city fields
 * without requiring backend schema changes, and safely parses legacy or combined address strings.
 */
object CustomerAddressRules {

    const val MAX_STREET_LENGTH = 380
    const val MAX_BARANGAY_LENGTH = 100
    const val MAX_CITY_LENGTH = 60
    const val MAX_PROVINCE_LENGTH = 35
    const val MAX_RPC_ADDRESS_LENGTH = 500
    const val MAX_RPC_CITY_LENGTH = 100
    const val DEFAULT_PROVINCE_SUGGESTION = "Nueva Ecija"

    data class StructuredCustomerAddress(
        val streetAddress: String = "",
        val barangay: String = "",
        val city: String = "",
        val province: String = ""
    ) {
        fun toRpcPair(): Pair<String, String> = formatForRpc(
            street = streetAddress,
            barangay = barangay,
            city = city,
            province = province
        )

        fun fullDisplayAddress(): String {
            val parts = mutableListOf<String>()
            val cleanStreet = streetAddress.trim()
            if (cleanStreet.isNotBlank()) parts.add(cleanStreet)

            val cleanBrgy = cleanBarangayName(barangay)
            if (cleanBrgy.isNotBlank()) parts.add("Brgy. $cleanBrgy")

            val cleanCity = city.trim()
            if (cleanCity.isNotBlank()) parts.add(cleanCity)

            val cleanProv = province.trim()
            if (cleanProv.isNotBlank()) parts.add(cleanProv)

            return parts.joinToString(", ")
        }
    }

    fun cleanBarangayName(barangay: String): String {
        var cleaned = barangay.trim()
        while (cleaned.startsWith("Brgy.", ignoreCase = true) || cleaned.startsWith("Barangay", ignoreCase = true)) {
            cleaned = if (cleaned.startsWith("Brgy.", ignoreCase = true)) {
                cleaned.substring(5).trim()
            } else {
                cleaned.substring(8).trim()
            }
        }
        return cleaned.trim()
    }

    /**
     * Formats structured address components into (p_address, p_city) expected by
     * mobile_save_profile and public.customers columns.
     */
    fun formatForRpc(
        street: String,
        barangay: String,
        city: String,
        province: String
    ): Pair<String, String> {
        val cleanStreet = street.trim()
        val cleanBrgy = cleanBarangayName(barangay)
        val cleanCity = city.trim()
        val cleanProvince = province.trim()

        val rawAddress = if (cleanBrgy.isNotBlank()) {
            if (cleanStreet.isNotBlank()) "$cleanStreet, Brgy. $cleanBrgy" else "Brgy. $cleanBrgy"
        } else {
            cleanStreet
        }

        val rawCity = if (cleanProvince.isNotBlank()) {
            if (cleanCity.isNotBlank()) "$cleanCity, $cleanProvince" else cleanProvince
        } else {
            cleanCity
        }

        val pAddress = if (rawAddress.length > MAX_RPC_ADDRESS_LENGTH) {
            rawAddress.take(MAX_RPC_ADDRESS_LENGTH).trimEnd()
        } else {
            rawAddress
        }

        val pCity = if (rawCity.length > MAX_RPC_CITY_LENGTH) {
            rawCity.take(MAX_RPC_CITY_LENGTH).trimEnd()
        } else {
            rawCity
        }

        return Pair(pAddress, pCity)
    }

    /**
     * Parses stored address and city strings (which may be legacy free-form or combined)
     * back into structured fields. Prefers explicit barangay/province if supplied.
     */
    fun parseFromSaved(
        rawAddress: String,
        rawCity: String,
        explicitBarangay: String = "",
        explicitProvince: String = ""
    ): StructuredCustomerAddress {
        val trimmedAddress = rawAddress.trim()
        val trimmedCity = rawCity.trim()

        // 1. Resolve Barangay and Street
        val (street, barangay) = when {
            explicitBarangay.isNotBlank() -> {
                val cleanExp = cleanBarangayName(explicitBarangay)
                val stripped = stripBarangayFromAddress(trimmedAddress, cleanExp)
                Pair(stripped, cleanExp)
            }
            else -> {
                extractBarangayFromAddress(trimmedAddress)
            }
        }

        // 2. Resolve City and Province
        val (city, province) = when {
            explicitProvince.isNotBlank() -> {
                val cleanProv = explicitProvince.trim()
                val stripped = if (trimmedCity.endsWith(", $cleanProv", ignoreCase = true)) {
                    trimmedCity.substring(0, trimmedCity.length - (cleanProv.length + 2)).trim()
                } else if (trimmedCity.equals(cleanProv, ignoreCase = true)) {
                    ""
                } else {
                    trimmedCity
                }
                Pair(stripped, cleanProv)
            }
            trimmedCity.contains(",") -> {
                val lastComma = trimmedCity.lastIndexOf(',')
                val possibleCity = trimmedCity.substring(0, lastComma).trim()
                val possibleProvince = trimmedCity.substring(lastComma + 1).trim()
                Pair(possibleCity, possibleProvince)
            }
            else -> {
                Pair(trimmedCity, "")
            }
        }

        return StructuredCustomerAddress(
            streetAddress = street,
            barangay = barangay,
            city = city,
            province = province
        )
    }

    private fun extractBarangayFromAddress(address: String): Pair<String, String> {
        val brgyRegex = Regex("(?i),\\s*(?:Brgy\\.?|Barangay)\\s+([^,]+)$")
        val match = brgyRegex.find(address)
        return if (match != null) {
            val street = address.substring(0, match.range.first).trim()
            val brgy = cleanBarangayName(match.groupValues[1])
            Pair(street, brgy)
        } else {
            Pair(address, "")
        }
    }

    private fun stripBarangayFromAddress(address: String, barangayName: String): String {
        val brgyPattern = Regex("(?i),\\s*(?:Brgy\\.?|Barangay)\\s+${Regex.escape(barangayName)}$")
        return address.replace(brgyPattern, "").trim()
    }

    fun validateStreet(street: String): Boolean = street.trim().length in 1..MAX_STREET_LENGTH
    fun validateBarangay(barangay: String): Boolean = barangay.trim().length in 1..MAX_BARANGAY_LENGTH
    fun validateCity(city: String): Boolean = city.trim().length in 1..MAX_CITY_LENGTH
    fun validateProvince(province: String): Boolean = province.trim().length in 1..MAX_PROVINCE_LENGTH

    fun isValidAddress(
        street: String,
        barangay: String,
        city: String,
        province: String
    ): Boolean {
        val (formattedAddress, formattedCity) = formatForRpc(street, barangay, city, province)
        return validateStreet(street) &&
                (barangay.isBlank() || validateBarangay(barangay)) &&
                validateCity(city) &&
                (province.isBlank() || validateProvince(province)) &&
                formattedAddress.length in 1..MAX_RPC_ADDRESS_LENGTH &&
                formattedCity.length in 1..MAX_RPC_CITY_LENGTH
    }
}
