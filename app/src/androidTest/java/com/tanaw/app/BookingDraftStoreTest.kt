package com.tanaw.app

import androidx.test.platform.app.InstrumentationRegistry
import com.tanaw.app.feature.workspace.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class BookingDraftStoreTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun bookingDraftStoreSavesRestoresAndSeparatesAccount() {
        val customerId = "cust-${UUID.randomUUID()}"
        val otherCustomerId = "cust-${UUID.randomUUID()}"
        val store = BookingDraftStore(context)
        val original = BookingDraft(
            requestId = UUID.randomUUID().toString(),
            stepName = "ADD_DETAILS",
            pickup = "Warehouse Cabanatuan",
            dropoff = "Tondo Pier 4",
            cargo = "Hardware supplies",
            weight = "3200",
            notes = "Ring gate upon arrival",
            handling = "Tailgate Required",
            selectedVehicleId = "v-6-wheeler",
            isPickupNow = false,
            scheduledDeparture = "2026-11-15 09:00 AM",
            contact = "+639171234567",
            owner = "user-auth-uuid",
            submitted = false
        )

        try {
            assertTrue(store.save(customerId, original))
            assertTrue(store.exists(customerId))

            val restored = BookingDraftStore(context).read(customerId)
            assertNotNull(restored)
            assertEquals(original.requestId, restored!!.requestId)
            assertEquals(original.stepName, restored.stepName)
            assertEquals(original.pickup, restored.pickup)
            assertEquals(original.dropoff, restored.dropoff)
            assertEquals(original.cargo, restored.cargo)
            assertEquals(original.weight, restored.weight)
            assertEquals(original.notes, restored.notes)
            assertEquals(original.handling, restored.handling)
            assertEquals(original.selectedVehicleId, restored.selectedVehicleId)
            assertEquals(original.isPickupNow, restored.isPickupNow)
            assertEquals(original.scheduledDeparture, restored.scheduledDeparture)
            assertEquals(original.contact, restored.contact)
            assertEquals(original.owner, restored.owner)
            assertFalse(restored.submitted)

            // Isolation between customers
            assertNull(store.read(otherCustomerId))
            assertNull(BookingDraftStore(context, "https://other-supabase.co").read(customerId))

            // Clear
            assertTrue(store.clear(customerId))
            assertFalse(store.exists(customerId))
            assertNull(store.read(customerId))
        } finally {
            store.clear(customerId)
            store.clear(otherCustomerId)
        }
    }

    @Test
    fun bookingDraftStoreClearOwnerRemovesCustomerDraftMatchingOwner() {
        val customerId = "cust-${UUID.randomUUID()}"
        val owner = "user-auth-${UUID.randomUUID()}"
        val store = BookingDraftStore(context)
        val draft = BookingDraft(
            pickup = "Manila North",
            dropoff = "San Jose City",
            cargo = "Dry Food Items",
            owner = owner
        )

        try {
            assertTrue(store.save(customerId, draft))
            assertTrue(store.exists(customerId))

            // Clearing by owner should find and remove draft with matching owner
            store.clearOwner(owner)
            assertFalse(store.exists(customerId))
            assertNull(store.read(customerId))
        } finally {
            store.clear(customerId)
            store.clearOwner(owner)
        }
    }

    @Test
    fun bookingDraftStoreMarkSubmittedPreservesFields() {
        val customerId = "cust-${UUID.randomUUID()}"
        val reqId = "req-${UUID.randomUUID()}"
        val store = BookingDraftStore(context)
        val draft = BookingDraft(
            requestId = reqId,
            pickup = "Warehouse 1",
            dropoff = "Store 2",
            cargo = "Beverages",
            weight = "1500",
            notes = "Handle glass carefully",
            submitted = false
        )

        try {
            assertTrue(store.save(customerId, draft))
            assertTrue(store.markSubmitted(customerId, reqId))

            val submitted = store.read(customerId)
            assertNotNull(submitted)
            assertTrue(submitted!!.submitted)
            assertEquals(reqId, submitted.requestId)
            assertEquals("Warehouse 1", submitted.pickup)
            assertEquals("Store 2", submitted.dropoff)
            assertEquals("Beverages", submitted.cargo)
            assertEquals("1500", submitted.weight)
            assertEquals("Handle glass carefully", submitted.notes)
        } finally {
            store.clear(customerId)
        }
    }

    @Test
    fun bookingDraftStoreEmptyDraftAutomaticallyClears() {
        val customerId = "cust-${UUID.randomUUID()}"
        val store = BookingDraftStore(context)
        val meaningful = BookingDraft(pickup = "Cabanatuan Terminal")

        try {
            assertTrue(store.save(customerId, meaningful))
            assertTrue(store.exists(customerId))

            // Saving empty draft clears existing draft
            assertTrue(store.save(customerId, BookingDraft()))
            assertFalse(store.exists(customerId))
            assertNull(store.read(customerId))
        } finally {
            store.clear(customerId)
        }
    }

    @Test
    fun bookingDraftStoreBlankCustomerGuards() {
        val store = BookingDraftStore(context)
        assertFalse(store.save("", BookingDraft(pickup = "Test")))
        assertFalse(store.save("   ", BookingDraft(pickup = "Test")))
        assertNull(store.read(""))
        assertNull(store.read("   "))
        assertFalse(store.clear(""))
        assertFalse(store.clear("   "))
        assertFalse(store.markSubmitted("", "req-1"))
        assertFalse(store.markSubmitted("   ", "req-1"))
        assertFalse(store.exists(""))
        assertFalse(store.exists("   "))
    }
}
