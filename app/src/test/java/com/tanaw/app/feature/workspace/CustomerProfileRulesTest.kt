package com.tanaw.app.feature.workspace

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class CustomerProfileRulesTest {

    private fun sampleData(
        profileId: String = "u1",
        fullName: String = "Old Name",
        phone: String = "+639111111111",
        email: String = "customer@example.com",
        role: String = "customer",
        customerId: String = "c1",
        address: String = "Old Address",
        city: String = "Old City"
    ): JsonObject = buildJsonObject {
        put("profile", buildJsonObject {
            put("id", profileId)
            put("full_name", fullName)
            put("phone", phone)
            put("email", email)
            put("role", role)
        })
        put("customer", buildJsonObject {
            put("id", customerId)
            put("name", fullName)
            put("phone", phone)
            put("address", address)
            put("city", city)
        })
        put("shipments", buildJsonArray {
            add(buildJsonObject { put("id", "s1"); put("tracking_number", "TRK-001") })
        })
    }

    @Test
    fun optimisticUpdateUpdatesProfileAndCustomerFields() {
        val initial = sampleData()
        val updated = CustomerProfileRules.optimisticUpdate(
            data = initial,
            name = " Maria Santos ",
            phone = "+639123456789",
            address = " 123 Rizal St ",
            city = " Pasig City "
        )

        assertNotNull(updated)
        val profile = updated!!.obj("profile")
        val customer = updated.obj("customer")

        // Profile fields
        assertEquals("u1", profile.value("id"))
        assertEquals("customer@example.com", profile.value("email"))
        assertEquals("customer", profile.value("role"))
        assertEquals("Maria Santos", profile.value("full_name"))
        assertEquals("+639123456789", profile.value("phone"))

        // Customer fields
        assertEquals("c1", customer.value("id"))
        assertEquals("Maria Santos", customer.value("name"))
        assertEquals("+639123456789", customer.value("phone"))
        assertEquals("123 Rizal St", customer.value("address"))
        assertEquals("Pasig City", customer.value("city"))

        // Unrelated workspace arrays preserved
        assertEquals(1, updated.rows("shipments").size)
        assertEquals("TRK-001", updated.rows("shipments").first().value("tracking_number"))
    }

    @Test
    fun optimisticUpdateReturnsNullForNullData() {
        assertNull(
            CustomerProfileRules.optimisticUpdate(
                data = null,
                name = "Test",
                phone = "+639123456789",
                address = "Address",
                city = "City"
            )
        )
    }

    @Test
    fun optimisticUpdateHandlesMissingCustomerSection() {
        val driverData = buildJsonObject {
            put("profile", buildJsonObject {
                put("id", "d1")
                put("full_name", "Driver One")
                put("phone", "+639111111111")
                put("role", "driver")
            })
        }
        val updated = CustomerProfileRules.optimisticUpdate(
            data = driverData,
            name = "Driver Renamed",
            phone = "+639999999999",
            address = "",
            city = ""
        )

        assertNotNull(updated)
        assertEquals("Driver Renamed", updated!!.obj("profile").value("full_name"))
        assertEquals("+639999999999", updated.obj("profile").value("phone"))
        assertFalse(updated.containsKey("customer"))
    }

    @Test
    fun optimisticUpdatePreservesJsonNullCustomerSectionWithoutSynthesizingCustomerObject() {
        val driverWithNullCustomer = buildJsonObject {
            put("profile", buildJsonObject {
                put("id", "d1")
                put("full_name", "Driver One")
                put("phone", "+639111111111")
                put("role", "driver")
            })
            put("customer", JsonNull)
        }
        val updated = CustomerProfileRules.optimisticUpdate(
            data = driverWithNullCustomer,
            name = "Driver Renamed",
            phone = "+639999999999",
            address = "New Address",
            city = "New City"
        )

        assertNotNull(updated)
        assertEquals("Driver Renamed", updated!!.obj("profile").value("full_name"))
        assertEquals("+639999999999", updated.obj("profile").value("phone"))
        assertTrue(updated.containsKey("customer"))
        assertEquals(JsonNull, updated["customer"])
    }

    @Test
    fun saveProfileAcknowledgesBeforeFailedRefreshWithoutRepeatingMutation() = runTest {
        val events = mutableListOf<String>()
        var message: String? = null
        var error: String? = null

        CustomerProfileRules.saveProfile(
            mutate = { events.add("mutate") },
            current = { true },
            onOptimisticUpdate = {
                events.add("optimisticUpdate")
                message = CustomerProfileRules.MSG_PROFILE_SAVED
                error = null
            },
            refresh = {
                events.add("refresh")
                error("simulated network blip during load()")
            },
            onRefreshFailed = {
                events.add("onRefreshFailed")
                message = CustomerProfileRules.MSG_PROFILE_SAVED_REFRESH_FAILED
                error = null
            }
        )

        assertEquals(listOf("mutate", "optimisticUpdate", "refresh", "onRefreshFailed"), events)
        assertEquals(CustomerProfileRules.MSG_PROFILE_SAVED_REFRESH_FAILED, message)
        assertNull("Error banner must remain null when write succeeded", error)
    }

    @Test
    fun saveProfileKeepsSuccessMessageWhenRefreshSucceeds() = runTest {
        val events = mutableListOf<String>()
        var message: String? = null
        var error: String? = null

        CustomerProfileRules.saveProfile(
            mutate = { events.add("mutate") },
            current = { true },
            onOptimisticUpdate = {
                events.add("optimisticUpdate")
                message = CustomerProfileRules.MSG_PROFILE_SAVED
                error = null
            },
            refresh = {
                events.add("refresh")
                message = CustomerProfileRules.MSG_PROFILE_SAVED
                error = null
            },
            onRefreshFailed = {
                fail("onRefreshFailed should not be called on successful refresh")
            }
        )

        assertEquals(listOf("mutate", "optimisticUpdate", "refresh"), events)
        assertEquals(CustomerProfileRules.MSG_PROFILE_SAVED, message)
        assertNull(error)
    }

    @Test
    fun saveProfileRejectsAcknowledgeWhenMutateFails() = runTest {
        var acknowledged = false
        var refreshed = false
        var refreshFailed = false

        val result = runCatching {
            CustomerProfileRules.saveProfile(
                mutate = { error("RPC_REJECTED") },
                current = { true },
                onOptimisticUpdate = { acknowledged = true },
                refresh = { refreshed = true },
                onRefreshFailed = { refreshFailed = true }
            )
        }

        assertTrue(result.isFailure)
        assertEquals("RPC_REJECTED", result.exceptionOrNull()?.message)
        assertFalse(acknowledged)
        assertFalse(refreshed)
        assertFalse(refreshFailed)
    }

    @Test
    fun saveProfilePropagatesCancellationExceptionFromRefresh() = runTest {
        var refreshedFailedCalled = false
        val result = runCatching {
            CustomerProfileRules.saveProfile(
                mutate = { },
                current = { true },
                onOptimisticUpdate = { },
                refresh = { throw CancellationException("coroutine cancelled") },
                onRefreshFailed = { refreshedFailedCalled = true }
            )
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CancellationException)
        assertFalse(refreshedFailedCalled)
    }

    @Test
    fun saveProfileAbortsIfSessionSwitchedBeforeAcknowledge() = runTest {
        var acknowledged = false
        var refreshed = false

        CustomerProfileRules.saveProfile(
            mutate = { },
            current = { false }, // session switched or logged out
            onOptimisticUpdate = { acknowledged = true },
            refresh = { refreshed = true },
            onRefreshFailed = { fail("refreshFailed must not run if session switched") }
        )

        assertFalse(acknowledged)
        assertFalse(refreshed)
    }

    @Test
    fun saveProfilePropagatesCancellationExceptionFromMutate() = runTest {
        var acknowledged = false
        var refreshed = false
        var refreshFailed = false

        val result = runCatching {
            CustomerProfileRules.saveProfile(
                mutate = { throw CancellationException("mutation cancelled") },
                current = { true },
                onOptimisticUpdate = { acknowledged = true },
                refresh = { refreshed = true },
                onRefreshFailed = { refreshFailed = true }
            )
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CancellationException)
        assertFalse(acknowledged)
        assertFalse(refreshed)
        assertFalse(refreshFailed)
    }

    @Test
    fun saveProfileAbortsRefreshFailedIfSessionSwitchedDuringRefresh() = runTest {
        var isCurrent = true
        var acknowledged = false
        var refreshFailedCalled = false

        CustomerProfileRules.saveProfile(
            mutate = { },
            current = { isCurrent },
            onOptimisticUpdate = { acknowledged = true },
            refresh = {
                // Session switched / logged out during refresh
                isCurrent = false
                error("simulated network error during refresh")
            },
            onRefreshFailed = {
                refreshFailedCalled = true
            }
        )

        assertTrue(acknowledged)
        assertFalse("refreshFailed must not run if session switched during refresh", refreshFailedCalled)
    }

    @Test
    fun resolveRefreshMessagePreservesLoadWarningsAndReconciliationNotices() {
        // Default / null case -> MSG_PROFILE_SAVED
        assertEquals(
            CustomerProfileRules.MSG_PROFILE_SAVED,
            CustomerProfileRules.resolveRefreshMessage(null)
        )
        // Already profile saved -> MSG_PROFILE_SAVED
        assertEquals(
            CustomerProfileRules.MSG_PROFILE_SAVED,
            CustomerProfileRules.resolveRefreshMessage(CustomerProfileRules.MSG_PROFILE_SAVED)
        )
        // Offline cache warning preserved
        assertEquals(
            "Offline copy could not be saved on this device.",
            CustomerProfileRules.resolveRefreshMessage("Offline copy could not be saved on this device.")
        )
        // Booking reconciliation notice preserved
        val reconciliationNotice = "Request TRK-001 confirmed. Office review and quotation are pending."
        assertEquals(
            reconciliationNotice,
            CustomerProfileRules.resolveRefreshMessage(reconciliationNotice)
        )
    }

    @Test
    fun validationRulesValidateLengthsAndBounds() {
        // Name: 2..200 chars trimmed
        assertFalse(CustomerProfileRules.validateName(""))
        assertFalse(CustomerProfileRules.validateName("   "))
        assertFalse(CustomerProfileRules.validateName("A"))
        assertTrue(CustomerProfileRules.validateName("Al"))
        assertTrue(CustomerProfileRules.validateName(" Juan dela Cruz "))
        assertTrue(CustomerProfileRules.validateName("A".repeat(200)))
        assertFalse(CustomerProfileRules.validateName("A".repeat(201)))

        // Address: <= 500 chars
        assertTrue(CustomerProfileRules.validateAddress(""))
        assertTrue(CustomerProfileRules.validateAddress("123 Rizal St."))
        assertTrue(CustomerProfileRules.validateAddress("A".repeat(500)))
        assertFalse(CustomerProfileRules.validateAddress("A".repeat(501)))

        // City: <= 100 chars
        assertTrue(CustomerProfileRules.validateCity(""))
        assertTrue(CustomerProfileRules.validateCity("Pasig City"))
        assertTrue(CustomerProfileRules.validateCity("A".repeat(100)))
        assertFalse(CustomerProfileRules.validateCity("A".repeat(101)))
    }

    @Test
    fun optimisticUpdatePopulatesStructuredBarangayAndProvince() {
        val initial = JsonObject(mapOf(
            "profile" to JsonObject(mapOf(
                "full_name" to JsonPrimitive("Old Name"),
                "phone" to JsonPrimitive("09123456789")
            )),
            "customer" to JsonObject(mapOf(
                "name" to JsonPrimitive("Old Name"),
                "phone" to JsonPrimitive("09123456789"),
                "address" to JsonPrimitive("Old Street"),
                "city" to JsonPrimitive("Old City")
            ))
        ))

        val updated = CustomerProfileRules.optimisticUpdate(
            data = initial,
            name = "Maria Santos",
            phone = "09987654321",
            address = "123 Maharlika Highway, Brgy. San Juan",
            city = "Cabanatuan City, Nueva Ecija",
            barangay = "San Juan",
            province = "Nueva Ecija"
        )

        assertNotNull(updated)
        val customer = updated?.get("customer") as? JsonObject
        assertNotNull(customer)
        assertEquals("Maria Santos", customer?.get("name")?.let { (it as JsonPrimitive).content })
        assertEquals("09987654321", customer?.get("phone")?.let { (it as JsonPrimitive).content })
        assertEquals("123 Maharlika Highway, Brgy. San Juan", customer?.get("address")?.let { (it as JsonPrimitive).content })
        assertEquals("Cabanatuan City, Nueva Ecija", customer?.get("city")?.let { (it as JsonPrimitive).content })
        assertEquals("San Juan", customer?.get("barangay")?.let { (it as JsonPrimitive).content })
        assertEquals("Nueva Ecija", customer?.get("province")?.let { (it as JsonPrimitive).content })
    }
}
