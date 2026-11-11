package com.tanaw.app.feature.workspace

import android.content.Context
import com.tanaw.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.util.UUID

internal data class BookingDraft(
    val requestId: String = UUID.randomUUID().toString(),
    val stepName: String = "HOME",
    val pickup: String = "",
    val pickupLat: Double? = null,
    val pickupLng: Double? = null,
    val dropoff: String = "",
    val dropoffLat: Double? = null,
    val dropoffLng: Double? = null,
    val isPickupNow: Boolean = true,
    val scheduledDeparture: String = "",
    val selectedVehicleId: String = "",
    val contact: String = "",
    val cargo: String = "",
    val weight: String = "",
    val handling: String = "None",
    val notes: String = "",
    val submitted: Boolean = false,
    val owner: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun hasMeaningfulContent(): Boolean =
        pickup.isNotBlank() || dropoff.isNotBlank() || cargo.isNotBlank() ||
        notes.isNotBlank() || weight.isNotBlank() ||
        (!isPickupNow && scheduledDeparture.isNotBlank()) ||
        (handling.isNotBlank() && handling != "None") ||
        selectedVehicleId.isNotBlank() ||
        (pickupLat != null && pickupLng != null) ||
        (dropoffLat != null && dropoffLng != null) ||
        (stepName != "HOME" && stepName != "SUBMITTED") || submitted

    fun isEmpty(): Boolean = !hasMeaningfulContent()

    fun toJson(): JsonObject = buildJsonObject {
        put("created", createdAt)
        put("request_id", requestId)
        put("step_name", stepName)
        put("pickup", pickup)
        pickupLat?.let { put("pickup_lat", it) }
        pickupLng?.let { put("pickup_lng", it) }
        put("dropoff", dropoff)
        dropoffLat?.let { put("dropoff_lat", it) }
        dropoffLng?.let { put("dropoff_lng", it) }
        put("is_pickup_now", isPickupNow)
        put("scheduled_departure", scheduledDeparture)
        put("selected_vehicle_id", selectedVehicleId)
        put("contact", contact)
        put("cargo", cargo)
        put("weight", weight)
        put("handling", handling)
        put("notes", notes)
        put("submitted", submitted)
        if (owner.isNotBlank()) put("owner", owner)
    }

    companion object {
        fun fromJson(json: JsonObject): BookingDraft {
            val created = json.value("created").toLongOrNull() ?: System.currentTimeMillis()
            val requestId = json.value("request_id").ifBlank { UUID.randomUUID().toString() }
            val stepName = json.value("step_name").ifBlank { "HOME" }
            val pickup = json.value("pickup")
            val pickupLat = (json["pickup_lat"] as? JsonPrimitive)?.doubleOrNull
            val pickupLng = (json["pickup_lng"] as? JsonPrimitive)?.doubleOrNull
            val dropoff = json.value("dropoff")
            val dropoffLat = (json["dropoff_lat"] as? JsonPrimitive)?.doubleOrNull
            val dropoffLng = (json["dropoff_lng"] as? JsonPrimitive)?.doubleOrNull
            val isPickupNow = json.value("is_pickup_now") != "false"
            val scheduledDeparture = json.value("scheduled_departure")
            val selectedVehicleId = json.value("selected_vehicle_id")
            val contact = json.value("contact")
            val cargo = json.value("cargo")
            val weight = json.value("weight")
            val handling = json.value("handling").ifBlank { "None" }
            val notes = json.value("notes")
            val submitted = json.value("submitted") == "true"
            val owner = json.value("owner")
            return BookingDraft(
                requestId = requestId,
                stepName = stepName,
                pickup = pickup,
                pickupLat = pickupLat,
                pickupLng = pickupLng,
                dropoff = dropoff,
                dropoffLat = dropoffLat,
                dropoffLng = dropoffLng,
                isPickupNow = isPickupNow,
                scheduledDeparture = scheduledDeparture,
                selectedVehicleId = selectedVehicleId,
                contact = contact,
                cargo = cargo,
                weight = weight,
                handling = handling,
                notes = notes,
                submitted = submitted,
                owner = owner,
                createdAt = created
            )
        }
    }
}

internal sealed interface BookingReconciliationResult {
    data class Confirmed(val shipmentId: String, val trackingNumber: String, val draft: BookingDraft) : BookingReconciliationResult
    data class RestoredForRetry(val draft: BookingDraft) : BookingReconciliationResult
    data object NoDraft : BookingReconciliationResult
    data object NotSubmitted : BookingReconciliationResult
}

internal object BookingRecoveryRules {
    fun reconcile(draft: BookingDraft?, matchingShipment: JsonObject?): BookingReconciliationResult {
        if (draft == null) return BookingReconciliationResult.NoDraft
        if (!draft.submitted) return BookingReconciliationResult.NotSubmitted
        val id = matchingShipment?.value("id").orEmpty().trim()
        val tracking = matchingShipment?.value("tracking_number").orEmpty().trim()
        return if (id.isNotBlank() && tracking.isNotBlank()) {
            BookingReconciliationResult.Confirmed(id, tracking, draft)
        } else {
            BookingReconciliationResult.RestoredForRetry(draft.copy(submitted = false))
        }
    }
}

/** Encrypted, atomic booking draft scoped by customer ID. */
internal class BookingDraftStore(context: Context, project: String = BuildConfig.SUPABASE_URL) {
    private val store = WorkspaceRecoveryStore(context, "$project/booking-drafts-v1", 2_000_000, preserveUnreadable = true)
    private fun scope(customerId: String): String = "${customerId}_booking_draft"

    fun exists(customerId: String): Boolean =
        customerId.isNotBlank() && store.contains(scope(customerId))

    fun save(customerId: String, draft: BookingDraft): Boolean {
        if (customerId.isBlank()) return false
        if (!draft.hasMeaningfulContent()) {
            clear(customerId)
            return true
        }
        val customers = store.read("index/customers", Long.MAX_VALUE)?.get("customers")?.jsonArray.orEmpty()
            .map { it.jsonPrimitive.content }.toSet() + customerId
        return store.write("index/customers", buildJsonObject {
            put("created", System.currentTimeMillis())
            put("customers", JsonArray(customers.map(::JsonPrimitive)))
        }) && store.write(scope(customerId), draft.toJson())
    }

    fun read(customerId: String, maxAge: Long = RecoveryPolicy.CACHE_AGE): BookingDraft? {
        if (customerId.isBlank()) return null
        val row = store.read(scope(customerId), maxAge) ?: return null
        return try {
            BookingDraft.fromJson(row)
        } catch (_: Exception) {
            null
        }
    }

    fun markSubmitted(customerId: String, requestId: String): Boolean {
        if (customerId.isBlank()) return false
        val existing = read(customerId) ?: BookingDraft(requestId = requestId)
        return save(customerId, existing.copy(requestId = requestId, submitted = true))
    }

    fun clear(customerId: String): Boolean {
        if (customerId.isBlank()) return false
        store.clear(scope(customerId))
        val remaining = store.read("index/customers", Long.MAX_VALUE)?.get("customers")?.jsonArray.orEmpty()
            .map { it.jsonPrimitive.content }
            .filter { it != customerId }
        if (remaining.isEmpty()) {
            store.clear("index/customers")
        } else {
            store.write("index/customers", buildJsonObject {
                put("created", System.currentTimeMillis())
                put("customers", JsonArray(remaining.map(::JsonPrimitive)))
            })
        }
        return true
    }

    fun clearOwner(owner: String) {
        if (owner.isBlank()) return
        clear(owner)
        val customers = store.read("index/customers", Long.MAX_VALUE)?.get("customers")?.jsonArray.orEmpty()
            .map { it.jsonPrimitive.content }
        customers.forEach { cid ->
            val draft = read(cid)
            if (cid == owner || draft?.owner == owner) {
                clear(cid)
            }
        }
    }

    suspend fun saveAsync(customerId: String, draft: BookingDraft): Boolean = withContext(Dispatchers.IO) { save(customerId, draft) }

    suspend fun readAsync(customerId: String, maxAge: Long = RecoveryPolicy.CACHE_AGE): BookingDraft? = withContext(Dispatchers.IO) { read(customerId, maxAge) }

    suspend fun markSubmittedAsync(customerId: String, requestId: String): Boolean = withContext(Dispatchers.IO) { markSubmitted(customerId, requestId) }

    suspend fun clearAsync(customerId: String): Boolean = withContext(Dispatchers.IO) { clear(customerId) }

    suspend fun clearOwnerAsync(owner: String): Unit = withContext(Dispatchers.IO) { clearOwner(owner) }
}
