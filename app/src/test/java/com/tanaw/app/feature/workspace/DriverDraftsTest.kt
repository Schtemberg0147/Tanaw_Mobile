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
    fun incidentDraftIsEmptyEvaluatesEachFieldIndividually() {
        // Request ID change alone still considered empty
        assertTrue(IncidentDraft(request = UUID.randomUUID().toString()).isEmpty())

        // Non-default category triggers non-empty
        assertFalse(IncidentDraft(category = "accident").isEmpty())
        assertFalse(IncidentDraft(category = "breakdown").isEmpty())
        assertFalse(IncidentDraft(category = "weather").isEmpty())
        assertFalse(IncidentDraft(category = "other").isEmpty())
        assertTrue(IncidentDraft(category = "traffic").isEmpty())

        // Description variations
        assertFalse(IncidentDraft(description = "Highway closed").isEmpty())
        assertTrue(IncidentDraft(description = "   ").isEmpty())
        assertTrue(IncidentDraft(description = "\t\n").isEmpty())

        // Minutes variations
        assertFalse(IncidentDraft(minutes = 1).isEmpty())
        assertFalse(IncidentDraft(minutes = 120).isEmpty())
        assertTrue(IncidentDraft(minutes = 0).isEmpty())

        // Photos variations
        assertFalse(IncidentDraft(photos = listOf(byteArrayOf(1))).isEmpty())
        assertFalse(IncidentDraft(photos = listOf(byteArrayOf())).isEmpty())
        assertTrue(IncidentDraft(photos = emptyList()).isEmpty())
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
    fun incidentDraftEqualsAndHashCodeAllFieldsAndEdgeCases() {
        val req1 = UUID.randomUUID().toString()
        val req2 = UUID.randomUUID().toString()
        val photo = byteArrayOf(1, 2, 3)

        val base = IncidentDraft(
            request = req1,
            category = "weather",
            description = "Heavy flooding on avenue",
            minutes = 60,
            photos = listOf(photo)
        )

        // Same instance reference
        assertEquals(base, base)

        // Incompatible types and null
        assertFalse(base.equals(null))
        assertFalse(base.equals("not an incident draft"))

        // Different request ID
        assertNotEquals(base, base.copy(request = req2))

        // Different description
        assertNotEquals(base, base.copy(description = "Different issue"))

        // Different minutes
        assertNotEquals(base, base.copy(minutes = 90))

        // Different photos list size
        assertNotEquals(base, base.copy(photos = listOf(photo, byteArrayOf(4))))
        assertNotEquals(base, base.copy(photos = emptyList()))

        // Verify distinct hashCodes differ
        assertNotEquals(base.hashCode(), base.copy(category = "traffic").hashCode())
        assertNotEquals(base.hashCode(), base.copy(description = "Other").hashCode())
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
    fun expenseDraftIsEmptyEvaluatesEachFieldIndividually() {
        // Category
        assertFalse(ExpenseDraft(category = "Fuel").isEmpty())
        assertTrue(ExpenseDraft(category = "   ").isEmpty())

        // Amount
        assertFalse(ExpenseDraft(amount = "250.00").isEmpty())
        assertTrue(ExpenseDraft(amount = "   ").isEmpty())

        // Receipt
        assertFalse(ExpenseDraft(receipt = byteArrayOf(1, 2, 3)).isEmpty())
        assertFalse(ExpenseDraft(receipt = byteArrayOf()).isEmpty())
        assertTrue(ExpenseDraft(receipt = null).isEmpty())
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

    @Test
    fun expenseDraftEqualsAndHashCodeEdgeCases() {
        val base = ExpenseDraft(category = "Parking", amount = "50.00", receipt = byteArrayOf(10, 20))

        // Same instance reference
        assertEquals(base, base)

        // Incompatible types and null
        assertFalse(base.equals(null))
        assertFalse(base.equals(12345))

        // Different category
        assertNotEquals(base, base.copy(category = "Toll"))

        // Different amount
        assertNotEquals(base, base.copy(amount = "75.00"))

        // Null receipt vs empty byte array
        val nullReceiptDraft = ExpenseDraft(category = "Toll", amount = "10.00", receipt = null)
        val emptyReceiptDraft = ExpenseDraft(category = "Toll", amount = "10.00", receipt = byteArrayOf())
        assertNotEquals(nullReceiptDraft, emptyReceiptDraft)

        // Both null receipts equal
        val nullReceiptDraft2 = ExpenseDraft(category = "Toll", amount = "10.00", receipt = null)
        assertEquals(nullReceiptDraft, nullReceiptDraft2)
        assertEquals(nullReceiptDraft.hashCode(), nullReceiptDraft2.hashCode())

        // Distinct hashCodes
        assertNotEquals(base.hashCode(), base.copy(category = "Repair").hashCode())
        assertNotEquals(base.hashCode(), base.copy(amount = "100.00").hashCode())
    }
}
