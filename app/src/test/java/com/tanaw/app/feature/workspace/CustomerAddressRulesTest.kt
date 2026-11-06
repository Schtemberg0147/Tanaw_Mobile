package com.tanaw.app.feature.workspace

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
}
