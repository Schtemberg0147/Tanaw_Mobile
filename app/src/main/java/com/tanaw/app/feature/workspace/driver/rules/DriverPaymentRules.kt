package com.tanaw.app.feature.workspace.driver.rules

import com.tanaw.app.feature.workspace.driver.components.DriverEvidence
import com.tanaw.app.feature.workspace.viewmodel.rows
import com.tanaw.app.feature.workspace.viewmodel.value
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

internal enum class DriverPaymentMethod(
    val value: String,
    val label: String,
    val requiresReference: Boolean
) {
    CASH("cash", "Cash", false),
    GCASH("gcash", "GCash", true);

    companion object {
        fun fromValue(raw: String): DriverPaymentMethod =
            entries.firstOrNull { it.value.equals(raw.trim(), ignoreCase = true) } ?: CASH

        fun isValid(raw: String): Boolean =
            entries.any { it.value.equals(raw.trim(), ignoreCase = true) }
    }
}

internal enum class DriverPaymentGateStatus {
    FINAL_AMOUNT_MISSING,
    UNPAID,
    PENDING_REVIEW,
    REJECTED,
    VERIFIED
}

internal data class DriverPaymentSummary(
    val finalAmount: BigDecimal?,
    val verifiedPaid: BigDecimal,
    val balance: BigDecimal,
    val gateStatus: DriverPaymentGateStatus,
    val latestPaymentId: String = "",
    val latestPaymentMethod: String = "",
    val latestReferenceNumber: String = "",
    val latestProofPath: String = "",
    val latestPaidAt: String = "",
    val rejectionReason: String = ""
) {
    val canDepart: Boolean
        get() = gateStatus == DriverPaymentGateStatus.VERIFIED

    val canSubmitPayment: Boolean
        get() = gateStatus == DriverPaymentGateStatus.UNPAID || gateStatus == DriverPaymentGateStatus.REJECTED
}

/**
 * Enforces the payment-first gate after cargo loading:
 * full office-finalized payment + signed receipt photo + manual office verification before departure.
 */
internal object DriverPaymentRules {
    const val MAX_REFERENCE_LENGTH = 120
    const val MAX_NOTES_LENGTH = 500

    fun evaluate(
        shipment: JsonObject,
        workspaceData: JsonObject? = null,
        details: JsonObject? = null
    ): DriverPaymentSummary {
        val shipmentId = shipment.value("id")
        val finalAmount = shipment.value("final_amount").toBigDecimalOrNull()
        if (finalAmount == null || finalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return DriverPaymentSummary(
                finalAmount = finalAmount,
                verifiedPaid = BigDecimal.ZERO,
                balance = BigDecimal.ZERO,
                gateStatus = DriverPaymentGateStatus.FINAL_AMOUNT_MISSING
            )
        }

        val combinedRows = buildList {
            if (details != null && details.value("shipment_id") == shipmentId) {
                addAll(details.rows("payments"))
            }
            if (workspaceData != null) {
                addAll(workspaceData.rows("driver_payments"))
                addAll(workspaceData.rows("payments"))
            }
        }
            .filter { row ->
                val rowShipment = row.value("shipment_id")
                shipmentId.isNotBlank() && rowShipment == shipmentId
            }
            .distinctBy { row ->
                row.value("id").ifBlank { row.value("client_request_id").ifBlank { row.toString() } }
            }
            .sortedWith(compareByDescending<JsonObject> { sortTimestamp(it) }.thenByDescending { it.value("id") })

        val rowVerifiedPaid = combinedRows
            .filter { it.value("status").equals("verified", ignoreCase = true) }
            .fold(BigDecimal.ZERO) { acc, row ->
                acc + (row.value("amount").toBigDecimalOrNull() ?: BigDecimal.ZERO)
            }

        val shipmentReportedPaid = shipment.value("amount_paid").toBigDecimalOrNull() ?: BigDecimal.ZERO
        val verifiedPaid = if (combinedRows.isNotEmpty()) rowVerifiedPaid else shipmentReportedPaid
        val balance = (finalAmount - verifiedPaid).max(BigDecimal.ZERO)

        val paymentState = shipment.value("payment_state").trim().lowercase()
        if (balance.compareTo(BigDecimal.ZERO) == 0 || paymentState == "paid") {
            val verifiedRow = combinedRows.firstOrNull { it.value("status").equals("verified", ignoreCase = true) }
                ?: combinedRows.firstOrNull()
            return DriverPaymentSummary(
                finalAmount = finalAmount,
                verifiedPaid = verifiedPaid.max(finalAmount),
                balance = BigDecimal.ZERO,
                gateStatus = DriverPaymentGateStatus.VERIFIED,
                latestPaymentId = verifiedRow?.value("id").orEmpty(),
                latestPaymentMethod = verifiedRow?.value("method").orEmpty(),
                latestReferenceNumber = verifiedRow?.value("reference_number").orEmpty(),
                latestProofPath = verifiedRow?.value("proof_path").orEmpty(),
                latestPaidAt = verifiedRow?.value("verified_at").orEmpty()
                    .ifBlank { verifiedRow?.value("paid_at").orEmpty() }
                    .ifBlank { verifiedRow?.value("created_at").orEmpty() }
            )
        }

        val pendingRow = combinedRows.firstOrNull { it.value("status").equals("pending", ignoreCase = true) }
        val latestRow = combinedRows.firstOrNull()
        val fallbackLatestStatus = shipment.value("latest_payment_status").trim().lowercase()

        if (pendingRow != null || (combinedRows.isEmpty() && fallbackLatestStatus == "pending")) {
            return DriverPaymentSummary(
                finalAmount = finalAmount,
                verifiedPaid = verifiedPaid,
                balance = balance,
                gateStatus = DriverPaymentGateStatus.PENDING_REVIEW,
                latestPaymentId = pendingRow?.value("id").orEmpty(),
                latestPaymentMethod = pendingRow?.value("method").orEmpty()
                    .ifBlank { shipment.value("latest_payment_method") },
                latestReferenceNumber = pendingRow?.value("reference_number").orEmpty(),
                latestProofPath = pendingRow?.value("proof_path").orEmpty()
                    .ifBlank { shipment.value("latest_payment_proof_path") },
                latestPaidAt = pendingRow?.value("paid_at").orEmpty()
                    .ifBlank { pendingRow?.value("created_at").orEmpty() }
                    .ifBlank { shipment.value("latest_payment_created_at") }
            )
        }

        if (latestRow?.value("status").equals("rejected", ignoreCase = true) == true ||
            (combinedRows.isEmpty() && fallbackLatestStatus == "rejected")
        ) {
            val reason = latestRow?.value("rejection_reason").orEmpty()
                .ifBlank { shipment.value("latest_payment_rejection_reason") }
                .ifBlank { latestRow?.value("notes").orEmpty() }
            return DriverPaymentSummary(
                finalAmount = finalAmount,
                verifiedPaid = verifiedPaid,
                balance = balance,
                gateStatus = DriverPaymentGateStatus.REJECTED,
                latestPaymentId = latestRow?.value("id").orEmpty(),
                latestPaymentMethod = latestRow?.value("method").orEmpty()
                    .ifBlank { shipment.value("latest_payment_method") },
                latestReferenceNumber = latestRow?.value("reference_number").orEmpty(),
                latestProofPath = latestRow?.value("proof_path").orEmpty()
                    .ifBlank { shipment.value("latest_payment_proof_path") },
                latestPaidAt = latestRow?.value("paid_at").orEmpty()
                    .ifBlank { latestRow?.value("created_at").orEmpty() }
                    .ifBlank { shipment.value("latest_payment_created_at") },
                rejectionReason = reason
            )
        }

        return DriverPaymentSummary(
            finalAmount = finalAmount,
            verifiedPaid = verifiedPaid,
            balance = balance,
            gateStatus = DriverPaymentGateStatus.UNPAID
        )
    }

    fun validateSubmission(
        deliveryId: String,
        requestId: String,
        method: String,
        referenceNumber: String,
        receiptBytes: ByteArray?,
        notes: String
    ) {
        require(runCatching { UUID.fromString(deliveryId).toString() == deliveryId }.getOrDefault(false)) {
            "Invalid delivery reference."
        }
        require(runCatching { UUID.fromString(requestId).toString() == requestId }.getOrDefault(false)) {
            "Invalid payment request identifier."
        }
        require(DriverPaymentMethod.isValid(method)) {
            "Select a valid payment method."
        }
        val parsedMethod = DriverPaymentMethod.fromValue(method)
        val trimmedRef = referenceNumber.trim()
        if (parsedMethod.requiresReference) {
            require(trimmedRef.length in 2..MAX_REFERENCE_LENGTH) {
                "Enter the reference or transaction number for ${parsedMethod.label}."
            }
        } else {
            require(trimmedRef.length <= MAX_REFERENCE_LENGTH) {
                "Reference number must be $MAX_REFERENCE_LENGTH characters or fewer."
            }
        }
        require(receiptBytes != null && receiptBytes.size in 1..DriverEvidence.MAX_BYTES) {
            "Attach a photo of the customer-signed receipt (up to 5 MB)."
        }
        require(notes.trim().length <= MAX_NOTES_LENGTH) {
            "Notes must be $MAX_NOTES_LENGTH characters or fewer."
        }
    }

    fun storagePath(deliveryId: String, requestId: String): String =
        "$deliveryId/payment-$requestId.jpg"

    fun applySubmittedPayment(
        data: JsonObject?,
        shipmentId: String,
        paymentId: String,
        requestId: String,
        amount: BigDecimal,
        method: String,
        referenceNumber: String,
        proofPath: String,
        notes: String,
        paidAt: String
    ): JsonObject? {
        if (data == null || shipmentId.isBlank()) return data
        val newRow = buildJsonObject {
            put("id", paymentId.ifBlank { requestId })
            put("shipment_id", shipmentId)
            put("client_request_id", requestId)
            put("amount", amount.stripTrailingZeros().toPlainString())
            put("method", method.trim().lowercase())
            put("reference_number", referenceNumber.trim())
            put("proof_path", proofPath)
            put("status", "pending")
            put("paid_at", paidAt)
            put("created_at", paidAt)
            put("notes", notes.trim())
        }
        val existing = data.rows("driver_payments").filterNot { row ->
            val sameId = paymentId.isNotBlank() && row.value("id") == paymentId
            val sameRequest = requestId.isNotBlank() && row.value("client_request_id") == requestId
            sameId || sameRequest
        }
        val updated = data.toMutableMap()
        updated["driver_payments"] = JsonArray(listOf(newRow) + existing)
        return JsonObject(updated)
    }

    private fun sortTimestamp(row: JsonObject): Long {
        val raw = row.value("created_at").ifBlank { row.value("paid_at") }.ifBlank { row.value("updated_at") }
        return runCatching { Instant.parse(raw).toEpochMilli() }.getOrDefault(0L)
    }
}
