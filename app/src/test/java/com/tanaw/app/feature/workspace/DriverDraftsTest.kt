package com.tanaw.app.feature.workspace

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class DriverDraftsTest {

    @Test
    fun incidentDraftDefaultValuesAndEmptyState() {
        val draft = IncidentDraft()
        assertEquals("traffic", draft.category)
        assertEquals("", draft.description)
        assertEquals(0, draft.minutes)
        assertTrue(draft.photos.isEmpty())
        assertTrue(draft.isEmpty())
        assertNotNull(UUID.fromString(draft.request))
    }

    @Test
    fun incidentDraftEqualsAndHashCodeWithMatchingAndDifferingByteArrays() {
        val photo1 = byteArrayOf(10, 20, 30)
        val photo2 = byteArrayOf(40, 50)
        val req = UUID.randomUUID().toString()

        val draft1 = IncidentDraft(
            request = req,
            category = "accident",
            description = "Tire blowout on highway",
            minutes = 45,
            photos = listOf(photo1, photo2)
        )
        val draft2 = IncidentDraft(
            request = req,
            category = "accident",
            description = "Tire blowout on highway",
            minutes = 45,
            photos = listOf(photo1.copyOf(), photo2.copyOf())
        )
        val draftDifferentPhoto = IncidentDraft(
            request = req,
            category = "accident",
            description = "Tire blowout on highway",
            minutes = 45,
            photos = listOf(photo1, byteArrayOf(99))
        )
        val draftDifferentCategory = draft1.copy(category = "breakdown")

        assertEquals(draft1, draft2)
        assertEquals(draft1.hashCode(), draft2.hashCode())
        assertFalse(draft1.isEmpty())
        assertNotEquals(draft1, draftDifferentPhoto)
        assertNotEquals(draft1, draftDifferentCategory)
    }

    @Test
    fun expenseDraftDefaultValuesAndEmptyState() {
        val draft = ExpenseDraft()
        assertEquals("", draft.category)
        assertEquals("", draft.amount)
        assertNull(draft.receipt)
        assertTrue(draft.isEmpty())
    }

    @Test
    fun expenseDraftEqualsAndHashCodeWithReceiptByteArrays() {
        val receipt = byteArrayOf(1, 2, 3, 4, 5)
        val draft1 = ExpenseDraft(category = "Toll", amount = "150.00", receipt = receipt)
        val draft2 = ExpenseDraft(category = "Toll", amount = "150.00", receipt = receipt.copyOf())
        val draftDifferentReceipt = ExpenseDraft(category = "Toll", amount = "150.00", receipt = byteArrayOf(9, 9))
        val draftNoReceipt = ExpenseDraft(category = "Toll", amount = "150.00", receipt = null)

        assertEquals(draft1, draft2)
        assertEquals(draft1.hashCode(), draft2.hashCode())
        assertFalse(draft1.isEmpty())
        assertNotEquals(draft1, draftDifferentReceipt)
        assertNotEquals(draft1, draftNoReceipt)
    }
}
