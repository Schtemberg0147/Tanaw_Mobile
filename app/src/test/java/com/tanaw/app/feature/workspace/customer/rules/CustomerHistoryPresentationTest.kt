package com.tanaw.app.feature.workspace.customer.rules

import com.tanaw.app.feature.workspace.rules.MobilePresentation
import com.tanaw.app.feature.workspace.viewmodel.value
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomerHistoryPresentationTest {

    @Test
    fun statusHelpersClassifyCompletedDeliveredAndCancelled() {
        assertTrue(CustomerHistoryPresentation.isCompletedStatus("completed"))
        assertTrue(CustomerHistoryPresentation.isCompletedStatus(" Delivered "))
        assertFalse(CustomerHistoryPresentation.isCompletedStatus("cancelled"))
        assertFalse(CustomerHistoryPresentation.isCompletedStatus("in_transit"))

        assertTrue(CustomerHistoryPresentation.isCancelledStatus("cancelled"))
        assertTrue(CustomerHistoryPresentation.isCancelledStatus(" CANCELLED "))
        assertFalse(CustomerHistoryPresentation.isCancelledStatus("completed"))
    }

    @Test
    fun cleanAddressStripsGooglePlusCodesAndHandlesBlank() {
        assertEquals(
            "Maharlika Hwy, Cabanatuan City, Nueva Ecija",
            CustomerHistoryPresentation.cleanAddress("VW6H+F53, Maharlika Hwy, Cabanatuan City, Nueva Ecija")
        )
        assertEquals(
            "Brgy. Poblacion, Makati City",
            CustomerHistoryPresentation.cleanAddress("C6V9+M4J Brgy. Poblacion, Makati City")
        )
        assertEquals(
            "Not provided",
            CustomerHistoryPresentation.cleanAddress("   ")
        )
    }

    @Test
    fun footerTimestampUsesExpectedPrecedenceByStatus() {
        val deliveredAt = "2026-11-18T06:45:00Z"
        val deliveryArrivedAt = "2026-11-18T06:30:00Z"
        val cancelledAt = "2026-11-10T02:20:00Z"
        val createdAt = "2026-11-09T01:00:00Z"

        val completedShipment = buildJsonObject {
            put("id", "shp-1")
            put("status", "completed")
            put("actual_delivery_at", deliveredAt)
            put("created_at", createdAt)
        }
        assertEquals(
            "Delivered · ${MobilePresentation.time(deliveredAt)}",
            CustomerHistoryPresentation.footerTimestamp(completedShipment)
        )

        val deliveryFallbackShipment = buildJsonObject {
            put("id", "shp-2")
            put("status", "delivered")
            put("created_at", createdAt)
        }
        val deliveries = listOf(
            buildJsonObject {
                put("shipment_id", "shp-2")
                put("arrived_at", deliveryArrivedAt)
            }
        )
        assertEquals(
            "Delivered · ${MobilePresentation.time(deliveryArrivedAt)}",
            CustomerHistoryPresentation.footerTimestamp(deliveryFallbackShipment, deliveries)
        )

        val cancelledShipment = buildJsonObject {
            put("id", "shp-3")
            put("status", "cancelled")
            put("cancelled_at", cancelledAt)
            put("created_at", createdAt)
        }
        assertEquals(
            "Cancelled · ${MobilePresentation.time(cancelledAt)}",
            CustomerHistoryPresentation.footerTimestamp(cancelledShipment)
        )

        val activeShipment = buildJsonObject {
            put("id", "shp-4")
            put("status", "in_transit")
            put("created_at", createdAt)
        }
        assertEquals(
            "Booked · ${MobilePresentation.time(createdAt)}",
            CustomerHistoryPresentation.footerTimestamp(activeShipment)
        )
    }

    @Test
    fun formattedAmountPrefersFinalAmountAndHandlesMissingAmounts() {
        val withFinal = buildJsonObject {
            put("status", "completed")
            put("quoted_amount", "2000.00")
            put("final_amount", "2500.00")
        }
        assertEquals(MobilePresentation.money("2500.00"), CustomerHistoryPresentation.formattedAmount(withFinal))

        val withQuotedOnly = buildJsonObject {
            put("status", "completed")
            put("quoted_amount", "1800.00")
        }
        assertEquals(MobilePresentation.money("1800.00"), CustomerHistoryPresentation.formattedAmount(withQuotedOnly))

        val cancelledNoQuote = buildJsonObject {
            put("status", "cancelled")
        }
        assertEquals("No quote", CustomerHistoryPresentation.formattedAmount(cancelledNoQuote))

        val activeNoQuote = buildJsonObject {
            put("status", "pending")
        }
        assertEquals("Awaiting quotation", CustomerHistoryPresentation.formattedAmount(activeNoQuote))
    }

    @Test
    fun resolveProofDocumentsMatchesOnlyPodAndSignatureForShipment() {
        val docs = listOf(
            buildJsonObject {
                put("id", "doc-other")
                put("shipment_id", "shp-2")
                put("kind", "proof_of_delivery")
            },
            buildJsonObject {
                put("id", "doc-expense")
                put("shipment_id", "shp-1")
                put("kind", "expense_receipt")
            },
            buildJsonObject {
                put("id", "doc-pod")
                put("shipment_id", "shp-1")
                put("kind", "proof_of_delivery")
            },
            buildJsonObject {
                put("id", "doc-sig")
                put("shipment_id", "shp-1")
                put("kind", "signature")
            }
        )

        val resolved = CustomerHistoryPresentation.resolveProofDocuments("shp-1", docs)
        assertEquals("doc-pod", resolved.podDocument?.value("id"))
        assertEquals("doc-sig", resolved.signatureDocument?.value("id"))

        val partial = CustomerHistoryPresentation.resolveProofDocuments("shp-2", docs)
        assertEquals("doc-other", partial.podDocument?.value("id"))
        assertNull(partial.signatureDocument)

        val missing = CustomerHistoryPresentation.resolveProofDocuments("shp-missing", docs)
        assertNull(missing.podDocument)
        assertNull(missing.signatureDocument)
    }

    @Test
    fun isRedundantOfflineErrorDeduplicatesOnlyOfflineConnectionErrors() {
        assertTrue(
            CustomerHistoryPresentation.isRedundantOfflineError(
                offline = true,
                error = "Connection issue. Check your network connection and tap Refresh to retry."
            )
        )
        assertTrue(
            CustomerHistoryPresentation.isRedundantOfflineError(
                offline = true,
                error = "You appear to be offline right now."
            )
        )
        assertFalse(
            CustomerHistoryPresentation.isRedundantOfflineError(
                offline = false,
                error = "Connection issue. Check your network connection and tap Refresh to retry."
            )
        )
        assertFalse(
            CustomerHistoryPresentation.isRedundantOfflineError(
                offline = true,
                error = "Unable to open proof document."
            )
        )
        assertFalse(CustomerHistoryPresentation.isRedundantOfflineError(offline = true, error = null))
    }
}
