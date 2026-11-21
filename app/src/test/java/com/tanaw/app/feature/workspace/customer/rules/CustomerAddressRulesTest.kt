package com.tanaw.app.feature.workspace.customer.rules

import org.junit.Assert.*
import org.junit.Test

class CustomerAddressRulesTest {

    @Test
    fun formatForRpcCombinesBarangayAndProvinceCleanly() {
        val (pAddress, pCity) = CustomerAddressRules.formatForRpc(
            street = "123 Maharlika Highway",
            barangay = "Valdefuente",
            city = "Cabanatuan City",
            province = "Nueva Ecija"
        )
        assertEquals("123 Maharlika Highway, Brgy. Valdefuente", pAddress)
        assertEquals("Cabanatuan City, Nueva Ecija", pCity)
    }

    @Test
    fun formatForRpcCleansDuplicateBarangayPrefix() {
        val (pAddress1, _) = CustomerAddressRules.formatForRpc(
            street = "Block 4 Lot 10",
            barangay = "Brgy. San Josef Sur",
            city = "Cabanatuan City",
            province = "Nueva Ecija"
        )
        assertEquals("Block 4 Lot 10, Brgy. San Josef Sur", pAddress1)

        val (pAddress2, _) = CustomerAddressRules.formatForRpc(
            street = "Purok 2",
            barangay = "Barangay Imelda",
            city = "Cabanatuan City",
            province = "Nueva Ecija"
        )
        assertEquals("Purok 2, Brgy. Imelda", pAddress2)
    }

    @Test
    fun formatForRpcHandlesEmptyBarangayOrProvince() {
        val (pAddress, pCity) = CustomerAddressRules.formatForRpc(
            street = "789 Burgos St",
            barangay = "",
            city = "Gapan City",
            province = ""
        )
        assertEquals("789 Burgos St", pAddress)
        assertEquals("Gapan City", pCity)
    }

    @Test
    fun formatForRpcEnforcesDatabaseLengthLimits() {
        val longStreet = "A".repeat(450)
        val longBrgy = "B".repeat(80)
        val longCity = "C".repeat(80)
        val longProv = "D".repeat(40)

        val (pAddress, pCity) = CustomerAddressRules.formatForRpc(
            street = longStreet,
            barangay = longBrgy,
            city = longCity,
            province = longProv
        )

        assertTrue(pAddress.length <= CustomerAddressRules.MAX_RPC_ADDRESS_LENGTH)
        assertTrue(pCity.length <= CustomerAddressRules.MAX_RPC_CITY_LENGTH)
        assertEquals(500, CustomerAddressRules.MAX_RPC_ADDRESS_LENGTH)
        assertEquals(100, CustomerAddressRules.MAX_RPC_CITY_LENGTH)
    }

    @Test
    fun parseFromSavedRoundTripsFormattedAddress() {
        val (pAddress, pCity) = CustomerAddressRules.formatForRpc(
            street = "Purok 3, Mabini Street",
            barangay = "San Juan Accfa",
            city = "Cabanatuan City",
            province = "Nueva Ecija"
        )

        val parsed = CustomerAddressRules.parseFromSaved(
            rawAddress = pAddress,
            rawCity = pCity
        )

        assertEquals("Purok 3, Mabini Street", parsed.streetAddress)
        assertEquals("San Juan Accfa", parsed.barangay)
        assertEquals("Cabanatuan City", parsed.city)
        assertEquals("Nueva Ecija", parsed.province)
    }

    @Test
    fun parseFromSavedSupportsLegacyAddressesWithoutBarangayOrProvince() {
        val legacy = CustomerAddressRules.parseFromSaved(
            rawAddress = "Km 115 Maharlika Highway",
            rawCity = "Cabanatuan"
        )
        assertEquals("Km 115 Maharlika Highway", legacy.streetAddress)
        assertEquals("", legacy.barangay)
        assertEquals("Cabanatuan", legacy.city)
        assertEquals("", legacy.province)
    }

    @Test
    fun parseFromSavedPrefersExplicitBackendKeysWhenAvailable() {
        val parsed = CustomerAddressRules.parseFromSaved(
            rawAddress = "Purok 1, Mabini St, Brgy. Zulueta",
            rawCity = "Cabanatuan City, Nueva Ecija",
            explicitBarangay = "Zulueta",
            explicitProvince = "Nueva Ecija"
        )
        assertEquals("Purok 1, Mabini St", parsed.streetAddress)
        assertEquals("Zulueta", parsed.barangay)
        assertEquals("Cabanatuan City", parsed.city)
        assertEquals("Nueva Ecija", parsed.province)
    }

    @Test
    fun structuredCustomerAddressGeneratesFullDisplayAddress() {
        val addr = CustomerAddressRules.StructuredCustomerAddress(
            streetAddress = "Unit 5, Golden Building",
            barangay = "Kapitan Pepe",
            city = "Cabanatuan City",
            province = "Nueva Ecija"
        )
        assertEquals(
            "Unit 5, Golden Building, Brgy. Kapitan Pepe, Cabanatuan City, Nueva Ecija",
            addr.fullDisplayAddress()
        )
    }

    @Test
    fun validationHelpersEnforceLengthConstraints() {
        assertTrue(CustomerAddressRules.validateStreet("123 Main St"))
        assertFalse(CustomerAddressRules.validateStreet(""))
        assertFalse(CustomerAddressRules.validateStreet("A".repeat(381)))

        assertTrue(CustomerAddressRules.validateBarangay("San Josef"))
        assertFalse(CustomerAddressRules.validateBarangay(""))
        assertFalse(CustomerAddressRules.validateBarangay("B".repeat(101)))

        assertTrue(CustomerAddressRules.validateCity("Cabanatuan City"))
        assertFalse(CustomerAddressRules.validateCity(""))
        assertFalse(CustomerAddressRules.validateCity("C".repeat(61)))

        assertTrue(CustomerAddressRules.validateProvince("Nueva Ecija"))
        assertFalse(CustomerAddressRules.validateProvince(""))
        assertFalse(CustomerAddressRules.validateProvince("P".repeat(36)))

        assertTrue(
            CustomerAddressRules.isValidAddress(
                street = "123 Main St",
                barangay = "San Josef",
                city = "Cabanatuan City",
                province = "Nueva Ecija"
            )
        )
        assertTrue(
            CustomerAddressRules.isValidAddress(
                street = "123 Main St",
                barangay = "",
                city = "Cabanatuan City",
                province = ""
            )
        )
    }

    @Test
    fun maxValidInputsProduceRpcOutputsWithinDatabaseLimitsWithoutTruncation() {
        val maxStreet = "S".repeat(380)
        val maxBrgy = "B".repeat(100)
        val maxCity = "C".repeat(60)
        val maxProv = "P".repeat(35)

        assertTrue(CustomerAddressRules.validateStreet(maxStreet))
        assertTrue(CustomerAddressRules.validateBarangay(maxBrgy))
        assertTrue(CustomerAddressRules.validateCity(maxCity))
        assertTrue(CustomerAddressRules.validateProvince(maxProv))

        val (pAddress, pCity) = CustomerAddressRules.formatForRpc(
            street = maxStreet,
            barangay = maxBrgy,
            city = maxCity,
            province = maxProv
        )

        // 380 + ", Brgy. " (8) + 100 = 488 <= 500
        assertEquals(488, pAddress.length)
        assertTrue(pAddress.length <= CustomerAddressRules.MAX_RPC_ADDRESS_LENGTH)

        // 60 + ", " (2) + 35 = 97 <= 100
        assertEquals(97, pCity.length)
        assertTrue(pCity.length <= CustomerAddressRules.MAX_RPC_CITY_LENGTH)
    }

    @Test
    fun addressValidationRejectsWhitespaceOnlyAndHandlesUnicode() {
        assertFalse("Whitespace-only street must fail", CustomerAddressRules.validateStreet("   "))
        assertFalse("Whitespace-only barangay must fail", CustomerAddressRules.validateBarangay("   "))
        assertFalse("Whitespace-only city must fail", CustomerAddressRules.validateCity("   "))
        assertFalse("Whitespace-only province must fail", CustomerAddressRules.validateProvince("   "))

        // Unicode addresses with accents and ñ
        val unicodeStreet = "Calle España, Piso 3"
        val unicodeBrgy = "Santo Niño"
        val unicodeCity = "Valenzuela City"
        val unicodeProv = "Metro Manila"

        assertTrue(CustomerAddressRules.validateStreet(unicodeStreet))
        assertTrue(CustomerAddressRules.validateBarangay(unicodeBrgy))
        assertTrue(CustomerAddressRules.validateCity(unicodeCity))
        assertTrue(CustomerAddressRules.validateProvince(unicodeProv))

        val (pAddress, pCity) = CustomerAddressRules.formatForRpc(
            street = unicodeStreet,
            barangay = unicodeBrgy,
            city = unicodeCity,
            province = unicodeProv
        )
        assertEquals("Calle España, Piso 3, Brgy. Santo Niño", pAddress)
        assertEquals("Valenzuela City, Metro Manila", pCity)
    }

    @Test
    fun suggestCitiesFiltersAndPrioritizesPrefixMatches() {
        val defaults = CustomerAddressRules.suggestCities("", limit = 5)
        assertEquals(5, defaults.size)
        assertEquals("Cabanatuan City", defaults.first().city)

        val cabanatuan = CustomerAddressRules.suggestCities("Cab", limit = 5)
        assertTrue(cabanatuan.any { it.city == "Cabanatuan City" && it.province == "Nueva Ecija" })
        assertTrue(cabanatuan.any { it.city == "Cabiao" && it.province == "Nueva Ecija" })

        val pampanga = CustomerAddressRules.suggestCities("Pampanga", limit = 5)
        assertTrue(pampanga.any { it.city == "San Fernando City" })
        assertTrue(pampanga.any { it.city == "Angeles City" })

        assertTrue(CustomerAddressRules.suggestCities("Cab", limit = 0).isEmpty())
    }

    @Test
    fun parseFromGeocodedLabelExtractsStreetBarangayCityAndProvince() {
        val full = CustomerAddressRules.parseFromGeocodedLabel(
            "123 Rizal St, Brgy. San Josef Sur, Cabanatuan City, 3100 Nueva Ecija, Philippines"
        )
        assertEquals("123 Rizal St", full.streetAddress)
        assertEquals("San Josef Sur", full.barangay)
        assertEquals("Cabanatuan City", full.city)
        assertEquals("Nueva Ecija", full.province)

        val landmark = CustomerAddressRules.parseFromGeocodedLabel(
            "SM City Cabanatuan, Maharlika Highway, Cabanatuan City, Nueva Ecija"
        )
        assertEquals("SM City Cabanatuan, Maharlika Highway", landmark.streetAddress)
        assertEquals("", landmark.barangay)
        assertEquals("Cabanatuan City", landmark.city)
        assertEquals("Nueva Ecija", landmark.province)

        val plusCode = CustomerAddressRules.parseFromGeocodedLabel(
            "8HVM+7Q Cabanatuan City, Nueva Ecija, Philippines"
        )
        assertEquals("", plusCode.streetAddress)
        assertEquals("Cabanatuan City", plusCode.city)
        assertEquals("Nueva Ecija", plusCode.province)

        val twoPartStreetAndCity = CustomerAddressRules.parseFromGeocodedLabel(
            "Purok 3 Mabini St, Cabanatuan City"
        )
        assertEquals("Purok 3 Mabini St", twoPartStreetAndCity.streetAddress)
        assertEquals("Cabanatuan City", twoPartStreetAndCity.city)
        assertEquals("Nueva Ecija", twoPartStreetAndCity.province)
    }

    @Test
    fun fromAndroidAddressComponentsNormalizesRegionsAndExtractsBarangay() {
        val nuevaEcija = CustomerAddressRules.fromAndroidAddressComponents(
            featureName = "123",
            thoroughfare = "Maharlika Highway",
            subThoroughfare = "123",
            subLocality = "Brgy. Valdefuente",
            locality = "Cabanatuan City",
            subAdminArea = "Nueva Ecija",
            adminArea = "Central Luzon",
            addressLine = "123 Maharlika Highway, Brgy. Valdefuente, Cabanatuan City, Nueva Ecija, Philippines"
        )
        assertEquals("123 Maharlika Highway", nuevaEcija.streetAddress)
        assertEquals("Valdefuente", nuevaEcija.barangay)
        assertEquals("Cabanatuan City", nuevaEcija.city)
        assertEquals("Nueva Ecija", nuevaEcija.province)

        val ncr = CustomerAddressRules.fromAndroidAddressComponents(
            featureName = "North Harbor Pier 4",
            thoroughfare = "Radial Road 10",
            subThoroughfare = null,
            subLocality = "Tondo",
            locality = "Manila",
            subAdminArea = null,
            adminArea = "National Capital Region",
            addressLine = "North Harbor Pier 4, Radial Road 10, Tondo, Manila, Metro Manila"
        )
        assertEquals("Radial Road 10, North Harbor Pier 4", ncr.streetAddress)
        assertEquals("Tondo", ncr.barangay)
        assertEquals("Manila", ncr.city)
        assertEquals("Metro Manila", ncr.province)
    }
}
