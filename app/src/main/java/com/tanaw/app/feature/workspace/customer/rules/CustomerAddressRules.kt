package com.tanaw.app.feature.workspace.customer.rules

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

    data class CityProvinceSuggestion(
        val city: String,
        val province: String
    )

    val COMMON_CITIES: List<CityProvinceSuggestion> = listOf(
        CityProvinceSuggestion("Cabanatuan City", "Nueva Ecija"),
        CityProvinceSuggestion("Palayan City", "Nueva Ecija"),
        CityProvinceSuggestion("Gapan City", "Nueva Ecija"),
        CityProvinceSuggestion("San Jose City", "Nueva Ecija"),
        CityProvinceSuggestion("Science City of Muñoz", "Nueva Ecija"),
        CityProvinceSuggestion("Talavera", "Nueva Ecija"),
        CityProvinceSuggestion("Santa Rosa", "Nueva Ecija"),
        CityProvinceSuggestion("Guimba", "Nueva Ecija"),
        CityProvinceSuggestion("San Leonardo", "Nueva Ecija"),
        CityProvinceSuggestion("Cabiao", "Nueva Ecija"),
        CityProvinceSuggestion("San Antonio", "Nueva Ecija"),
        CityProvinceSuggestion("Jaen", "Nueva Ecija"),
        CityProvinceSuggestion("Zaragoza", "Nueva Ecija"),
        CityProvinceSuggestion("Aliaga", "Nueva Ecija"),
        CityProvinceSuggestion("Bongabon", "Nueva Ecija"),
        CityProvinceSuggestion("Manila", "Metro Manila"),
        CityProvinceSuggestion("Quezon City", "Metro Manila"),
        CityProvinceSuggestion("Valenzuela City", "Metro Manila"),
        CityProvinceSuggestion("Caloocan City", "Metro Manila"),
        CityProvinceSuggestion("Pasig City", "Metro Manila"),
        CityProvinceSuggestion("Taguig City", "Metro Manila"),
        CityProvinceSuggestion("Makati City", "Metro Manila"),
        CityProvinceSuggestion("Malolos City", "Bulacan"),
        CityProvinceSuggestion("Meycauayan City", "Bulacan"),
        CityProvinceSuggestion("Balagtas", "Bulacan"),
        CityProvinceSuggestion("San Fernando City", "Pampanga"),
        CityProvinceSuggestion("Angeles City", "Pampanga"),
        CityProvinceSuggestion("Tarlac City", "Tarlac"),
        CityProvinceSuggestion("Dagupan City", "Pangasinan"),
        CityProvinceSuggestion("Baguio City", "Benguet")
    )

    private val plusCodePrefixRegex =
        Regex("^[23456789CFGHJMPQRVWX]{2,8}\\+[23456789CFGHJMPQRVWX]{2,3}(?:\\s+(.*))?$", RegexOption.IGNORE_CASE)
    private val leadingPostalCodeRegex = Regex("^\\d{4}\\s+")
    private val trailingPostalCodeRegex = Regex("\\s+\\d{4}$")
    private val streetHintRegex =
        Regex("(?i)(?:^\\d|^#|\\b(?:st\\.?|street|ave\\.?|avenue|rd\\.?|road|hwy\\.?|highway|blvd\\.?|boulevard|dr\\.?|drive|ln\\.?|lane|purok|sitio|subd\\.?|subdivision|lot|block|blk|phase|unit|floor|flr|bldg\\.?|building|market|harbor|complex|zone|terminal)\\b)")
    private val barangaySegmentRegex =
        Regex("(?i)^(?:brgy\\.?|barangay)\\s+(.+)$")

    fun suggestCities(query: String, limit: Int = 5): List<CityProvinceSuggestion> {
        if (limit <= 0) return emptyList()
        val trimmed = query.trim()
        if (trimmed.isBlank()) return COMMON_CITIES.take(limit)

        val startsWithMatches = COMMON_CITIES.filter {
            it.city.startsWith(trimmed, ignoreCase = true) ||
                it.province.startsWith(trimmed, ignoreCase = true)
        }
        val containsMatches = COMMON_CITIES.filter {
            it !in startsWithMatches && (
                it.city.contains(trimmed, ignoreCase = true) ||
                    it.province.contains(trimmed, ignoreCase = true)
                )
        }
        return (startsWithMatches + containsMatches).take(limit)
    }

    fun lookupProvinceForCity(city: String): String? {
        val clean = city.trim().removeSuffix(" City").trim()
        if (clean.isBlank()) return null
        return COMMON_CITIES.firstOrNull { suggestion ->
            suggestion.city.equals(city.trim(), ignoreCase = true) ||
                suggestion.city.removeSuffix(" City").equals(clean, ignoreCase = true)
        }?.province
    }

    /**
     * Parses a geocoded address label (from MapTiler search, reverse geocoding, or Location Picker)
     * into structured street, barangay, city, and province fields bounded to RPC column limits.
     */
    fun parseFromGeocodedLabel(
        label: String,
        defaultProvince: String = DEFAULT_PROVINCE_SUGGESTION
    ): StructuredCustomerAddress {
        val rawSegments = label
            .split(",")
            .mapNotNull { raw ->
                var seg = raw.trim()
                val plusMatch = plusCodePrefixRegex.matchEntire(seg)
                if (plusMatch != null) {
                    seg = plusMatch.groupValues[1].trim()
                }
                seg = seg
                    .replace(leadingPostalCodeRegex, "")
                    .replace(trailingPostalCodeRegex, "")
                    .trim()
                seg.takeIf {
                    it.isNotBlank() &&
                        !it.equals("Philippines", ignoreCase = true) &&
                        !it.equals("PH", ignoreCase = true)
                }
            }

        if (rawSegments.isEmpty()) {
            return StructuredCustomerAddress(province = defaultProvince)
        }

        var extractedBarangay = ""
        val remaining = mutableListOf<String>()
        for (seg in rawSegments) {
            val brgyMatch = barangaySegmentRegex.matchEntire(seg)
            if (brgyMatch != null && extractedBarangay.isBlank()) {
                extractedBarangay = cleanBarangayName(brgyMatch.groupValues[1])
            } else {
                remaining.add(seg)
            }
        }

        if (remaining.isEmpty()) {
            return StructuredCustomerAddress(
                barangay = extractedBarangay.take(MAX_BARANGAY_LENGTH),
                province = defaultProvince
            )
        }

        val knownProvinces = COMMON_CITIES.map { it.province }.toSet()
        fun isKnownProvince(value: String): Boolean =
            knownProvinces.any { it.equals(value, ignoreCase = true) }

        val street: String
        val city: String
        val province: String

        when (remaining.size) {
            1 -> {
                val single = remaining[0]
                val matchedProv = lookupProvinceForCity(single)
                if (matchedProv != null) {
                    street = ""
                    city = single
                    province = matchedProv
                } else {
                    street = single
                    city = ""
                    province = defaultProvince
                }
            }
            2 -> {
                val first = remaining[0]
                val second = remaining[1]
                val secondIsProvince = isKnownProvince(second)
                val firstHasStreetHint = streetHintRegex.containsMatchIn(first)
                when {
                    secondIsProvince && !firstHasStreetHint -> {
                        street = ""
                        city = first
                        province = second
                    }
                    else -> {
                        street = first
                        city = second
                        province = lookupProvinceForCity(second) ?: defaultProvince
                    }
                }
            }
            else -> {
                val last = remaining.last()
                val secondLast = remaining[remaining.size - 2]
                val lastIsProvince = isKnownProvince(last) ||
                    (!streetHintRegex.containsMatchIn(last) && lookupProvinceForCity(secondLast) != null)
                if (lastIsProvince || remaining.size >= 3) {
                    province = last
                    city = secondLast
                    street = remaining.dropLast(2).joinToString(", ")
                } else {
                    province = lookupProvinceForCity(last) ?: defaultProvince
                    city = last
                    street = remaining.dropLast(1).joinToString(", ")
                }
            }
        }

        val resolvedProvince = province.ifBlank {
            lookupProvinceForCity(city) ?: defaultProvince
        }

        return StructuredCustomerAddress(
            streetAddress = street.trim().take(MAX_STREET_LENGTH),
            barangay = extractedBarangay.trim().take(MAX_BARANGAY_LENGTH),
            city = city.trim().take(MAX_CITY_LENGTH),
            province = resolvedProvince.trim().take(MAX_PROVINCE_LENGTH)
        )
    }

    /**
     * Converts Android Geocoder address fields into structured customer address components,
     * falling back to [parseFromGeocodedLabel] for any missing values.
     */
    fun fromAndroidAddressComponents(
        featureName: String?,
        thoroughfare: String?,
        subThoroughfare: String?,
        subLocality: String?,
        locality: String?,
        subAdminArea: String?,
        adminArea: String?,
        addressLine: String?
    ): StructuredCustomerAddress {
        val fallback = addressLine?.takeIf { it.isNotBlank() }?.let { parseFromGeocodedLabel(it) }
            ?: StructuredCustomerAddress()

        val cleanSubThoroughfare = subThoroughfare?.trim().orEmpty()
        val cleanThoroughfare = thoroughfare?.trim().orEmpty()
        val cleanFeature = featureName?.trim().orEmpty().takeIf { feat ->
            feat.isNotBlank() &&
                !feat.equals(cleanSubThoroughfare, ignoreCase = true) &&
                !feat.equals(cleanThoroughfare, ignoreCase = true) &&
                !plusCodePrefixRegex.matches(feat) &&
                !feat.all { it.isDigit() }
        }.orEmpty()

        val combinedStreet = listOfNotNull(
            listOf(cleanSubThoroughfare, cleanThoroughfare)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .takeIf { it.isNotBlank() },
            cleanFeature.takeIf { it.isNotBlank() }
        ).distinct().joinToString(", ")

        val street = combinedStreet.ifBlank { fallback.streetAddress }
        val barangay = cleanBarangayName(subLocality.orEmpty()).ifBlank { fallback.barangay }
        val city = locality?.trim().orEmpty().ifBlank { fallback.city }

        val rawAdmin = adminArea?.trim().orEmpty()
        val normalizedAdmin = when {
            rawAdmin.equals("National Capital Region", ignoreCase = true) ||
                rawAdmin.equals("Metro Manila", ignoreCase = true) ||
                rawAdmin.equals("NCR", ignoreCase = true) -> "Metro Manila"
            rawAdmin.contains("Region", ignoreCase = true) ||
                rawAdmin.equals("Central Luzon", ignoreCase = true) ||
                rawAdmin.equals("Calabarzon", ignoreCase = true) ||
                rawAdmin.equals("Ilocos Region", ignoreCase = true) -> ""
            else -> rawAdmin
        }

        val province = subAdminArea?.trim().orEmpty()
            .ifBlank { normalizedAdmin }
            .ifBlank { lookupProvinceForCity(city).orEmpty() }
            .ifBlank { fallback.province }

        return StructuredCustomerAddress(
            streetAddress = street.take(MAX_STREET_LENGTH),
            barangay = barangay.take(MAX_BARANGAY_LENGTH),
            city = city.take(MAX_CITY_LENGTH),
            province = province.take(MAX_PROVINCE_LENGTH)
        )
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
