package com.tanaw.app.feature.workspace.driver.store

import android.content.Context
import android.util.Base64
import com.tanaw.app.BuildConfig
import com.tanaw.app.feature.workspace.driver.components.DriverEvidence
import com.tanaw.app.feature.workspace.driver.rules.DriverPaymentMethod
import com.tanaw.app.feature.workspace.store.WorkspaceRecoveryStore
import com.tanaw.app.feature.workspace.viewmodel.value
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.UUID

internal data class PaymentDraft(
    val request: String = UUID.randomUUID().toString(),
    val method: String = DriverPaymentMethod.CASH.value,
    val referenceNumber: String = "",
    val notes: String = "",
    val receipt: ByteArray? = null
) {
    fun changed(): PaymentDraft = copy(request = UUID.randomUUID().toString())

    override fun equals(other: Any?): Boolean = this === other || (other is PaymentDraft &&
        request == other.request &&
        method == other.method &&
        referenceNumber == other.referenceNumber &&
        notes == other.notes &&
        receipt.contentEquals(other.receipt))

    override fun hashCode(): Int {
        var result = request.hashCode()
        result = 31 * result + method.hashCode()
        result = 31 * result + referenceNumber.hashCode()
        result = 31 * result + notes.hashCode()
        result = 31 * result + (receipt?.contentHashCode() ?: 0)
        return result
    }
}

/** Encrypted, atomic, backup-excluded payment draft scoped by backend/account/delivery. */
internal class PaymentDraftStore(context: Context, project: String = BuildConfig.SUPABASE_URL) {
    private val store = WorkspaceRecoveryStore(
        context,
        "$project/payment-drafts-v1",
        10_000_000,
        preserveUnreadable = true
    )

    private fun scope(owner: String, delivery: String) = "${owner}_payment_$delivery"

    fun exists(owner: String, delivery: String): Boolean =
        owner.isNotBlank() && delivery.isNotBlank() && store.contains(scope(owner, delivery))

    internal fun indexedDeliveries(owner: String): Set<String> =
        if (owner.isBlank()) emptySet()
        else store.read("index/$owner", Long.MAX_VALUE)?.get("deliveries")?.jsonArray.orEmpty()
            .map { it.jsonPrimitive.content }.toSet()

    fun save(owner: String, delivery: String, draft: PaymentDraft): Boolean {
        require(owner.isNotBlank() && delivery.isNotBlank())
        val written = store.write(scope(owner, delivery), buildJsonObject {
            put("created", System.currentTimeMillis())
            put("owner", owner)
            put("delivery", delivery)
            put("request", draft.request)
            put("method", draft.method)
            put("reference_number", draft.referenceNumber)
            put("notes", draft.notes)
            draft.receipt?.let { put("receipt", Base64.encodeToString(it, Base64.NO_WRAP)) }
        })
        if (!written) return false
        val deliveries = indexedDeliveries(owner) + delivery
        val indexWritten = store.write("index/$owner", buildJsonObject {
            put("created", System.currentTimeMillis())
            put("deliveries", JsonArray(deliveries.map(::JsonPrimitive)))
        })
        if (!indexWritten) {
            store.clear(scope(owner, delivery))
            return false
        }
        return true
    }

    fun read(owner: String, delivery: String): PaymentDraft? {
        if (owner.isBlank() || delivery.isBlank()) return null
        val row = store.read(scope(owner, delivery), Long.MAX_VALUE) ?: run {
            check(!exists(owner, delivery)) { "The saved payment draft cannot be read." }
            return null
        }
        return try {
            require(row.value("owner") == owner && row.value("delivery") == delivery)
            val request = row.value("request")
            UUID.fromString(request)
            val method = row.value("method").takeIf { DriverPaymentMethod.isValid(it) }
                ?: DriverPaymentMethod.CASH.value
            val referenceNumber = row.value("reference_number")
            val notes = row.value("notes")
            val receipt = row.value("receipt").takeIf { it.isNotBlank() }?.let {
                Base64.decode(it, Base64.NO_WRAP).also { bytes ->
                    require(bytes.size in 1..DriverEvidence.MAX_BYTES)
                }
            }
            PaymentDraft(
                request = request,
                method = method,
                referenceNumber = referenceNumber,
                notes = notes,
                receipt = receipt
            )
        } catch (failure: Exception) {
            throw IllegalStateException("The saved payment draft cannot be read.", failure)
        }
    }

    fun clear(owner: String, delivery: String): Boolean {
        if (owner.isBlank() || delivery.isBlank()) return false
        store.clear(scope(owner, delivery))
        val existing = indexedDeliveries(owner)
        if (delivery in existing) {
            val remaining = existing - delivery
            if (remaining.isEmpty()) {
                store.clear("index/$owner")
            } else {
                store.write("index/$owner", buildJsonObject {
                    put("created", System.currentTimeMillis())
                    put("deliveries", JsonArray(remaining.map(::JsonPrimitive)))
                })
            }
        }
        return true
    }

    fun clearOwner(owner: String) {
        if (owner.isBlank()) return
        indexedDeliveries(owner).forEach {
            store.clear(scope(owner, it))
        }
        store.clear("index/$owner")
    }
}
