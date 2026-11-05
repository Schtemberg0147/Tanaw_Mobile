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
}
