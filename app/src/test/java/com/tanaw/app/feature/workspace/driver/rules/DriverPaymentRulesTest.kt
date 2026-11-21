package com.tanaw.app.feature.workspace.driver.rules

import com.tanaw.app.core.error.MobileErrorClassifier
import com.tanaw.app.core.sync.WorkspaceSyncRules
import com.tanaw.app.feature.workspace.viewmodel.rows
import com.tanaw.app.feature.workspace.viewmodel.value
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.util.UUID

class DriverPaymentRulesTest {

    private val shipmentId = "11111111-1111-1111-1111-111111111111"
    private val deliveryId = "22222222-2222-2222-2222-222222222222"
    private val requestId = "33333333-3333-3333-3333-333333333333"

    @Test
    fun missingFinalAmountBlocksSubmissionAndDeparture() {
        val shipment = buildJsonObject { put("id", shipmentId) }
        val summary = DriverPaymentRules.evaluate(shipment)
        assertEquals(DriverPaymentGateStatus.FINAL_AMOUNT_MISSING, summary.gateStatus)
        assertFalse(summary.canDepart)
        assertFalse(summary.canSubmitPayment)
    }

    @Test
    fun unpaidShipmentAllowsSubmissionAndBlocksDeparture() {
        val shipment = buildJsonObject {
            put("id", shipmentId)
            put("final_amount", "2500.00")
        }
        val summary = DriverPaymentRules.evaluate(shipment)
        assertEquals(DriverPaymentGateStatus.UNPAID, summary.gateStatus)
        assertEquals(0, BigDecimal("2500.00").compareTo(summary.balance))
        assertFalse(summary.canDepart)
        assertTrue(summary.canSubmitPayment)
    }

    @Test
    fun pendingPaymentBlocksBothDuplicateSubmissionAndDeparture() {
        val shipment = buildJsonObject {
            put("id", shipmentId)
            put("final_amount", "2500.00")
        }
        val workspace = buildJsonObject {
            put(
                "driver_payments",
                JsonArray(
                    listOf(
                        buildJsonObject {
                            put("id", "pay-1")
                            put("shipment_id", shipmentId)
                            put("amount", "2500.00")
                            put("method", "cash")
                            put("status", "pending")
                            put("proof_path", "$deliveryId/payment-$requestId.jpg")
                            put("created_at", "2026-11-20T02:00:00Z")
                        }
                    )
                )
            )
        }
        val summary = DriverPaymentRules.evaluate(shipment, workspaceData = workspace)
        assertEquals(DriverPaymentGateStatus.PENDING_REVIEW, summary.gateStatus)
        assertEquals("pay-1", summary.latestPaymentId)
        assertEquals("cash", summary.latestPaymentMethod)
        assertFalse(summary.canDepart)
        assertFalse(summary.canSubmitPayment)
    }

    @Test
    fun rejectedPaymentShowsReasonAllowsResubmissionAndKeepsTransitLocked() {
        val shipment = buildJsonObject {
            put("id", shipmentId)
            put("final_amount", "2500.00")
        }
        val workspace = buildJsonObject {
            put(
                "driver_payments",
                JsonArray(
                    listOf(
                        buildJsonObject {
                            put("id", "pay-2")
                            put("shipment_id", shipmentId)
                            put("amount", "2500.00")
                            put("method", "gcash")
                            put("status", "rejected")
                            put("rejection_reason", "GCash reference number did not match statement")
                            put("created_at", "2026-11-20T02:10:00Z")
                        }
                    )
                )
            )
        }
        val summary = DriverPaymentRules.evaluate(shipment, workspaceData = workspace)
        assertEquals(DriverPaymentGateStatus.REJECTED, summary.gateStatus)
        assertEquals("GCash reference number did not match statement", summary.rejectionReason)
        assertFalse(summary.canDepart)
        assertTrue(summary.canSubmitPayment)
    }

    @Test
    fun verifiedFullPaymentUnlocksDepartureAndSkipsBilling() {
        val shipment = buildJsonObject {
            put("id", shipmentId)
            put("final_amount", "2500.00")
        }
        val workspace = buildJsonObject {
            put(
                "driver_payments",
                JsonArray(
                    listOf(
                        buildJsonObject {
                            put("id", "pay-3")
                            put("shipment_id", shipmentId)
                            put("amount", "2500.00")
                            put("method", "gcash")
                            put("status", "verified")
                            put("verified_at", "2026-11-20T02:20:00Z")
                        }
                    )
                )
            )
        }
        val summary = DriverPaymentRules.evaluate(shipment, workspaceData = workspace)
        assertEquals(DriverPaymentGateStatus.VERIFIED, summary.gateStatus)
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.balance))
        assertTrue(summary.canDepart)
        assertFalse(summary.canSubmitPayment)
    }

    @Test
    fun partialPriorVerifiedPaymentLeavesExactRemainingBalanceDue() {
        val shipment = buildJsonObject {
            put("id", shipmentId)
            put("final_amount", "2500.00")
        }
        val workspace = buildJsonObject {
            put(
                "driver_payments",
                JsonArray(
                    listOf(
                        buildJsonObject {
                            put("id", "pay-deposit")
                            put("shipment_id", shipmentId)
                            put("amount", "1000.00")
                            put("method", "gcash")
                            put("status", "verified")
                            put("created_at", "2026-11-19T08:00:00Z")
                        }
                    )
                )
            )
        }
        val summary = DriverPaymentRules.evaluate(shipment, workspaceData = workspace)
        assertEquals(DriverPaymentGateStatus.UNPAID, summary.gateStatus)
        assertEquals(0, BigDecimal("1500.00").compareTo(summary.balance))
        assertFalse(summary.canDepart)
        assertTrue(summary.canSubmitPayment)
    }

    @Test
    fun validateSubmissionRequiresSignedReceiptAndDigitalReference() {
        val validReceipt = byteArrayOf(1, 2, 3, 4)

        // Cash allows optional reference
        DriverPaymentRules.validateSubmission(
            deliveryId = deliveryId,
            requestId = requestId,
            method = "cash",
            referenceNumber = "",
            receiptBytes = validReceipt,
            notes = "Signed by site receiver"
        )

        // Missing photo fails for cash
        assertTrue(
            runCatching {
                DriverPaymentRules.validateSubmission(deliveryId, requestId, "cash", "", null, "")
            }.isFailure
        )

        // GCash requires reference number
        assertTrue(
            runCatching {
                DriverPaymentRules.validateSubmission(deliveryId, requestId, "gcash", "", validReceipt, "")
            }.isFailure
        )

        // GCash with reference + photo succeeds
        DriverPaymentRules.validateSubmission(
            deliveryId = deliveryId,
            requestId = requestId,
            method = "gcash",
            referenceNumber = "GCASH-901234",
            receiptBytes = validReceipt,
            notes = ""
        )

        // Removed enum values are rejected
        assertEquals(listOf(DriverPaymentMethod.CASH, DriverPaymentMethod.GCASH), DriverPaymentMethod.entries)
        listOf("bank_transfer", "check", "other").forEach { removedMethod ->
            assertFalse(DriverPaymentMethod.isValid(removedMethod))
            assertTrue(
                runCatching {
                    DriverPaymentRules.validateSubmission(
                        deliveryId = deliveryId,
                        requestId = requestId,
                        method = removedMethod,
                        referenceNumber = "REF-123",
                        receiptBytes = validReceipt,
                        notes = ""
                    )
                }.isFailure
            )
        }
    }

    @Test
    fun applySubmittedPaymentImmediatelyTransitionsWorkspaceToPendingReview() {
        val shipment = buildJsonObject {
            put("id", shipmentId)
            put("final_amount", "2500.00")
        }
        val baseWorkspace = buildJsonObject {
            put("driver_payments", JsonArray(emptyList()))
        }
        val updated = DriverPaymentRules.applySubmittedPayment(
            data = baseWorkspace,
            shipmentId = shipmentId,
            paymentId = UUID.randomUUID().toString(),
            requestId = requestId,
            amount = BigDecimal("2500.00"),
            method = "cash",
            referenceNumber = "OR-1002",
            proofPath = DriverPaymentRules.storagePath(deliveryId, requestId),
            notes = "Collected full cash",
            paidAt = "2026-11-20T03:00:00Z"
        )
        assertEquals(1, updated?.rows("driver_payments")?.size)
        assertEquals("pending", updated?.rows("driver_payments")?.first()?.value("status"))

        val summary = DriverPaymentRules.evaluate(shipment, workspaceData = updated)
        assertEquals(DriverPaymentGateStatus.PENDING_REVIEW, summary.gateStatus)
        assertFalse(summary.canDepart)
        assertFalse(summary.canSubmitPayment)
    }

    @Test
    fun paymentsTableTriggersWorkspaceAndDetailRefreshAndMapsErrorsCleanly() {
        assertTrue(WorkspaceSyncRules.shouldReloadWorkspaceForTable("payments", "driver"))
        assertTrue(WorkspaceSyncRules.shouldRefreshDetailsForTable("payments", shipmentId))
        val msg = MobileErrorClassifier.friendlyMessage(IllegalStateException("PAYMENT_VERIFICATION_REQUIRED"))
        assertTrue(msg.contains("verified by the office", ignoreCase = true))
    }
}
