package com.tanaw.app

import androidx.test.platform.app.InstrumentationRegistry
import com.tanaw.app.feature.workspace.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class DriverDraftStoreTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun incidentDraftStoreSavesRestoresAndSeparatesAccountAndDelivery() {
        val owner = UUID.randomUUID().toString()
        val delivery = UUID.randomUUID().toString()
        val store = IncidentDraftStore(context)
        val photo1 = byteArrayOf(1, 2, 3, 4)
        val photo2 = byteArrayOf(5, 6, 7)
        val original = IncidentDraft(
            request = UUID.randomUUID().toString(),
            category = "accident",
            description = "Engine overheating on highway",
            minutes = 30,
            photos = listOf(photo1, photo2)
        )

        try {
            assertTrue(store.save(owner, delivery, original))
            assertTrue(store.exists(owner, delivery))

            val restored = IncidentDraftStore(context).read(owner, delivery)
            assertNotNull(restored)
            assertEquals(original.request, restored!!.request)
            assertEquals(original.category, restored.category)
            assertEquals(original.description, restored.description)
            assertEquals(original.minutes, restored.minutes)
            assertEquals(2, restored.photos.size)
            assertArrayEquals(photo1, restored.photos[0])
            assertArrayEquals(photo2, restored.photos[1])

            // Scope separation
            assertNull(store.read("different-owner", delivery))
            assertNull(store.read(owner, "different-delivery"))
            assertNull(IncidentDraftStore(context, "other-backend-url").read(owner, delivery))

            // Clear single delivery
            assertTrue(store.clear(owner, delivery))
            assertFalse(store.exists(owner, delivery))
            assertNull(store.read(owner, delivery))
        } finally {
            store.clearOwner(owner)
        }
    }

    @Test
    fun incidentDraftStoreClearOwnerRemovesAllDeliveriesForOwner() {
        val owner = UUID.randomUUID().toString()
        val delivery1 = UUID.randomUUID().toString()
        val delivery2 = UUID.randomUUID().toString()
        val store = IncidentDraftStore(context)

        try {
            store.save(owner, delivery1, IncidentDraft(description = "Issue 1"))
            store.save(owner, delivery2, IncidentDraft(description = "Issue 2"))
            assertTrue(store.exists(owner, delivery1))
            assertTrue(store.exists(owner, delivery2))

            store.clearOwner(owner)
            assertFalse(store.exists(owner, delivery1))
            assertFalse(store.exists(owner, delivery2))
            assertNull(store.read(owner, delivery1))
            assertNull(store.read(owner, delivery2))
        } finally {
            store.clearOwner(owner)
        }
    }

    @Test
    fun incidentDraftStorePrunesIndexOnClear() {
        val owner = UUID.randomUUID().toString()
        val delivery1 = UUID.randomUUID().toString()
        val delivery2 = UUID.randomUUID().toString()
        val store = IncidentDraftStore(context)

        try {
            store.save(owner, delivery1, IncidentDraft(description = "Issue 1"))
            store.save(owner, delivery2, IncidentDraft(description = "Issue 2"))
            assertEquals(setOf(delivery1, delivery2), store.indexedDeliveries(owner))

            // Clearing one delivery should leave the remaining delivery in index
            assertTrue(store.clear(owner, delivery1))
            assertEquals(setOf(delivery2), store.indexedDeliveries(owner))
            assertFalse(store.exists(owner, delivery1))
            assertTrue(store.exists(owner, delivery2))

            // Clearing the last delivery should empty the index
            assertTrue(store.clear(owner, delivery2))
            assertTrue(store.indexedDeliveries(owner).isEmpty())
            assertFalse(store.exists(owner, delivery2))
        } finally {
            store.clearOwner(owner)
        }
    }

    @Test
    fun incidentDraftStoreBoundaryAndOverwriteScenarios() {
        val owner = UUID.randomUUID().toString()
        val delivery = UUID.randomUUID().toString()
        val store = IncidentDraftStore(context)

        try {
            // Empty photos round-trip
            val emptyPhotos = IncidentDraft(
                category = "breakdown",
                description = "Flat tire",
                minutes = 15,
                photos = emptyList()
            )
            assertTrue(store.save(owner, delivery, emptyPhotos))
            val restoredEmpty = store.read(owner, delivery)
            assertNotNull(restoredEmpty)
            assertTrue(restoredEmpty!!.photos.isEmpty())

            // Overwrite: saving again updates the draft
            val threePhotos = listOf(byteArrayOf(1), byteArrayOf(2), byteArrayOf(3))
            val updated = IncidentDraft(
                category = "weather",
                description = "Severe thunderstorm",
                minutes = 120,
                photos = threePhotos
            )
            assertTrue(store.save(owner, delivery, updated))
            val restoredUpdated = store.read(owner, delivery)
            assertNotNull(restoredUpdated)
            assertEquals("weather", restoredUpdated!!.category)
            assertEquals("Severe thunderstorm", restoredUpdated.description)
            assertEquals(120, restoredUpdated.minutes)
            assertEquals(3, restoredUpdated.photos.size)
            assertArrayEquals(threePhotos[0], restoredUpdated.photos[0])
            assertArrayEquals(threePhotos[1], restoredUpdated.photos[1])
            assertArrayEquals(threePhotos[2], restoredUpdated.photos[2])
        } finally {
            store.clearOwner(owner)
        }
    }

    @Test
    fun incidentDraftStoreBlankInputsAndGuardClauses() {
        val store = IncidentDraftStore(context)
        val validDraft = IncidentDraft()

        // Exists with blank
        assertFalse(store.exists("", "delivery-1"))
        assertFalse(store.exists("owner-1", ""))
        assertFalse(store.exists("  ", "  "))

        // Read with blank
        assertNull(store.read("", "delivery-1"))
        assertNull(store.read("owner-1", ""))

        // Clear with blank
        assertFalse(store.clear("", "delivery-1"))
        assertFalse(store.clear("owner-1", ""))

        // Clear non-existent delivery succeeds silently
        assertTrue(store.clear("owner-1", UUID.randomUUID().toString()))

        // ClearOwner with blank is a safe no-op
        store.clearOwner("")
        assertTrue(store.indexedDeliveries("").isEmpty())

        // Save requires non-blank owner and delivery
        assertThrows(IllegalArgumentException::class.java) {
            store.save("", "delivery-1", validDraft)
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.save("owner-1", "", validDraft)
        }
    }

    @Test
    fun expenseDraftStoreSavesRestoresAndSeparatesAccountAndDelivery() {
        val owner = UUID.randomUUID().toString()
        val delivery = UUID.randomUUID().toString()
        val store = ExpenseDraftStore(context)
        val receiptBytes = byteArrayOf(10, 20, 30, 40, 50)
        val original = ExpenseDraft(
            category = "Toll fee",
            amount = "250.50",
            receipt = receiptBytes
        )

        try {
            assertTrue(store.save(owner, delivery, original))
            assertTrue(store.exists(owner, delivery))

            val restored = ExpenseDraftStore(context).read(owner, delivery)
            assertNotNull(restored)
            assertEquals(original.category, restored!!.category)
            assertEquals(original.amount, restored.amount)
            assertNotNull(restored.receipt)
            assertArrayEquals(receiptBytes, restored.receipt)

            // Scope separation
            assertNull(store.read("different-owner", delivery))
            assertNull(store.read(owner, "different-delivery"))
            assertNull(ExpenseDraftStore(context, "other-backend-url").read(owner, delivery))

            // Clear single delivery
            assertTrue(store.clear(owner, delivery))
            assertFalse(store.exists(owner, delivery))
            assertNull(store.read(owner, delivery))
        } finally {
            store.clearOwner(owner)
        }
    }

    @Test
    fun expenseDraftStoreClearOwnerRemovesAllDeliveriesForOwner() {
        val owner = UUID.randomUUID().toString()
        val delivery1 = UUID.randomUUID().toString()
        val delivery2 = UUID.randomUUID().toString()
        val store = ExpenseDraftStore(context)

        try {
            store.save(owner, delivery1, ExpenseDraft(category = "Parking", amount = "50.00"))
            store.save(owner, delivery2, ExpenseDraft(category = "Fuel", amount = "1200.00"))
            assertTrue(store.exists(owner, delivery1))
            assertTrue(store.exists(owner, delivery2))

            store.clearOwner(owner)
            assertFalse(store.exists(owner, delivery1))
            assertFalse(store.exists(owner, delivery2))
            assertNull(store.read(owner, delivery1))
            assertNull(store.read(owner, delivery2))
        } finally {
            store.clearOwner(owner)
        }
    }

    @Test
    fun expenseDraftStorePrunesIndexOnClear() {
        val owner = UUID.randomUUID().toString()
        val delivery1 = UUID.randomUUID().toString()
        val delivery2 = UUID.randomUUID().toString()
        val store = ExpenseDraftStore(context)

        try {
            store.save(owner, delivery1, ExpenseDraft(category = "Parking", amount = "50.00"))
            store.save(owner, delivery2, ExpenseDraft(category = "Fuel", amount = "1200.00"))
            assertEquals(setOf(delivery1, delivery2), store.indexedDeliveries(owner))

            // Clearing one delivery should leave the remaining delivery in index
            assertTrue(store.clear(owner, delivery1))
            assertEquals(setOf(delivery2), store.indexedDeliveries(owner))
            assertFalse(store.exists(owner, delivery1))
            assertTrue(store.exists(owner, delivery2))

            // Clearing the last delivery should empty the index
            assertTrue(store.clear(owner, delivery2))
            assertTrue(store.indexedDeliveries(owner).isEmpty())
            assertFalse(store.exists(owner, delivery2))
        } finally {
            store.clearOwner(owner)
        }
    }

    @Test
    fun expenseDraftStoreNullReceiptAndOverwriteScenarios() {
        val owner = UUID.randomUUID().toString()
        val delivery = UUID.randomUUID().toString()
        val store = ExpenseDraftStore(context)

        try {
            // Null receipt round-trip
            val nullReceipt = ExpenseDraft(
                category = "Meal allowance",
                amount = "300.00",
                receipt = null
            )
            assertTrue(store.save(owner, delivery, nullReceipt))
            val restoredNull = store.read(owner, delivery)
            assertNotNull(restoredNull)
            assertEquals("Meal allowance", restoredNull!!.category)
            assertEquals("300.00", restoredNull.amount)
            assertNull(restoredNull.receipt)

            // Overwrite: saving with receipt replaces null receipt draft
            val receiptBytes = byteArrayOf(11, 22, 33)
            val updated = ExpenseDraft(
                category = "Fuel top-up",
                amount = "1500.00",
                receipt = receiptBytes
            )
            assertTrue(store.save(owner, delivery, updated))
            val restoredUpdated = store.read(owner, delivery)
            assertNotNull(restoredUpdated)
            assertEquals("Fuel top-up", restoredUpdated!!.category)
            assertEquals("1500.00", restoredUpdated.amount)
            assertNotNull(restoredUpdated.receipt)
            assertArrayEquals(receiptBytes, restoredUpdated.receipt)
        } finally {
            store.clearOwner(owner)
        }
    }

    @Test
    fun expenseDraftStoreBlankInputsAndGuardClauses() {
        val store = ExpenseDraftStore(context)
        val validDraft = ExpenseDraft(category = "Toll", amount = "50.00")

        // Exists with blank
        assertFalse(store.exists("", "delivery-1"))
        assertFalse(store.exists("owner-1", ""))
        assertFalse(store.exists("  ", "  "))

        // Read with blank
        assertNull(store.read("", "delivery-1"))
        assertNull(store.read("owner-1", ""))

        // Clear with blank
        assertFalse(store.clear("", "delivery-1"))
        assertFalse(store.clear("owner-1", ""))

        // Clear non-existent delivery succeeds silently
        assertTrue(store.clear("owner-1", UUID.randomUUID().toString()))

        // ClearOwner with blank is a safe no-op
        store.clearOwner("")
        assertTrue(store.indexedDeliveries("").isEmpty())

        // Save requires non-blank owner and delivery
        assertThrows(IllegalArgumentException::class.java) {
            store.save("", "delivery-1", validDraft)
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.save("owner-1", "", validDraft)
        }
    }
}
