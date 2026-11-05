package com.tanaw.app.feature.workspace

import android.content.Context
import android.util.Base64
import com.tanaw.app.BuildConfig
import kotlinx.serialization.json.*
import java.util.UUID

internal data class IncidentDraft(
    val request: String = UUID.randomUUID().toString(),
    val category: String = "traffic",
    val description: String = "",
    val minutes: Int = 0,
    val photos: List<ByteArray> = emptyList()
) {
    fun isEmpty(): Boolean =
        category == "traffic" && description.isBlank() && minutes == 0 && photos.isEmpty()

    override fun equals(other: Any?): Boolean = this === other || (other is IncidentDraft &&
        request == other.request && category == other.category && description == other.description &&
        minutes == other.minutes && photos.size == other.photos.size &&
        photos.indices.all { photos[it].contentEquals(other.photos[it]) })

    override fun hashCode(): Int {
        var result = request.hashCode()
        result = 31 * result + category.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + minutes.hashCode()
        for (photo in photos) {
            result = 31 * result + photo.contentHashCode()
        }
        return result
    }
}

/** Encrypted, atomic, backup-excluded incident draft scoped by backend/account/delivery. */
internal class IncidentDraftStore(context: Context, project: String = BuildConfig.SUPABASE_URL) {
    private val store = WorkspaceRecoveryStore(context, "$project/incident-drafts-v1", 24_000_000, preserveUnreadable = true)
    private fun scope(owner: String, delivery: String) = "${owner}_incident_${delivery}"

    fun exists(owner: String, delivery: String): Boolean =
        owner.isNotBlank() && delivery.isNotBlank() && store.contains(scope(owner, delivery))

    fun save(owner: String, delivery: String, draft: IncidentDraft): Boolean {
        require(owner.isNotBlank() && delivery.isNotBlank())
        val deliveries = store.read("index/$owner", Long.MAX_VALUE)?.get("deliveries")?.jsonArray.orEmpty()
            .map { it.jsonPrimitive.content }.toSet() + delivery
        return store.write("index/$owner", buildJsonObject {
            put("created", System.currentTimeMillis())
            put("deliveries", JsonArray(deliveries.map(::JsonPrimitive)))
        }) && store.write(scope(owner, delivery), buildJsonObject {
            put("created", System.currentTimeMillis())
            put("owner", owner)
            put("delivery", delivery)
            put("request", draft.request)
            put("category", draft.category)
            put("description", draft.description)
            put("minutes", draft.minutes)
            put("photos", buildJsonArray {
                draft.photos.forEach {
                    add(JsonPrimitive(Base64.encodeToString(it, Base64.NO_WRAP)))
                }
            })
        })
    }

    fun read(owner: String, delivery: String): IncidentDraft? {
        if (owner.isBlank() || delivery.isBlank()) return null
        val row = store.read(scope(owner, delivery), Long.MAX_VALUE) ?: run {
            check(!exists(owner, delivery)) { "The saved incident draft cannot be read." }
            return null
        }
        return try {
            require(row.value("owner") == owner && row.value("delivery") == delivery)
            val request = row.value("request")
            UUID.fromString(request)
            val category = row.value("category").takeIf { it in IncidentRequest.categories } ?: "traffic"
            val description = row.value("description")
            val minutes = row.value("minutes").toIntOrNull()?.coerceIn(0, 10080) ?: 0
            val photos = row["photos"]?.jsonArray?.map { element ->
                Base64.decode(element.jsonPrimitive.content, Base64.NO_WRAP).also { bytes ->
                    require(bytes.size in 1..DriverEvidence.MAX_BYTES)
                }
            }?.take(3) ?: emptyList()
            IncidentDraft(
                request = request,
                category = category,
                description = description,
                minutes = minutes,
                photos = photos
            )
        } catch (failure: Exception) {
            throw IllegalStateException("The saved incident draft cannot be read.", failure)
        }
    }

    fun clear(owner: String, delivery: String): Boolean {
        if (owner.isBlank() || delivery.isBlank()) return false
        store.clear(scope(owner, delivery))
        return true
    }

    fun clearOwner(owner: String) {
        if (owner.isBlank()) return
        store.read("index/$owner", Long.MAX_VALUE)?.get("deliveries")?.jsonArray?.forEach {
            clear(owner, it.jsonPrimitive.content)
        }
        store.clear("index/$owner")
    }
}

internal data class ExpenseDraft(
    val category: String = "",
    val amount: String = "",
    val receipt: ByteArray? = null
) {
    fun isEmpty(): Boolean = category.isBlank() && amount.isBlank() && receipt == null

    override fun equals(other: Any?): Boolean = this === other || (other is ExpenseDraft &&
        category == other.category && amount == other.amount && receipt.contentEquals(other.receipt))

    override fun hashCode(): Int {
        var result = category.hashCode()
        result = 31 * result + amount.hashCode()
        result = 31 * result + (receipt?.contentHashCode() ?: 0)
        return result
    }
}

/** Encrypted, atomic, backup-excluded expense draft scoped by backend/account/delivery. */
internal class ExpenseDraftStore(context: Context, project: String = BuildConfig.SUPABASE_URL) {
    private val store = WorkspaceRecoveryStore(context, "$project/expense-drafts-v1", 10_000_000, preserveUnreadable = true)
    private fun scope(owner: String, delivery: String) = "${owner}_expense_${delivery}"

    fun exists(owner: String, delivery: String): Boolean =
        owner.isNotBlank() && delivery.isNotBlank() && store.contains(scope(owner, delivery))

    fun save(owner: String, delivery: String, draft: ExpenseDraft): Boolean {
        require(owner.isNotBlank() && delivery.isNotBlank())
        val deliveries = store.read("index/$owner", Long.MAX_VALUE)?.get("deliveries")?.jsonArray.orEmpty()
            .map { it.jsonPrimitive.content }.toSet() + delivery
        return store.write("index/$owner", buildJsonObject {
            put("created", System.currentTimeMillis())
            put("deliveries", JsonArray(deliveries.map(::JsonPrimitive)))
        }) && store.write(scope(owner, delivery), buildJsonObject {
            put("created", System.currentTimeMillis())
            put("owner", owner)
            put("delivery", delivery)
            put("category", draft.category)
            put("amount", draft.amount)
            draft.receipt?.let { put("receipt", Base64.encodeToString(it, Base64.NO_WRAP)) }
        })
    }

    fun read(owner: String, delivery: String): ExpenseDraft? {
        if (owner.isBlank() || delivery.isBlank()) return null
        val row = store.read(scope(owner, delivery), Long.MAX_VALUE) ?: run {
            check(!exists(owner, delivery)) { "The saved expense draft cannot be read." }
            return null
        }
        return try {
            require(row.value("owner") == owner && row.value("delivery") == delivery)
            val category = row.value("category")
            val amount = row.value("amount")
            val receipt = row.value("receipt").takeIf { it.isNotBlank() }?.let {
                Base64.decode(it, Base64.NO_WRAP).also { bytes ->
                    require(bytes.size in 1..DriverEvidence.MAX_BYTES)
                }
            }
            ExpenseDraft(category = category, amount = amount, receipt = receipt)
        } catch (failure: Exception) {
            throw IllegalStateException("The saved expense draft cannot be read.", failure)
        }
    }

    fun clear(owner: String, delivery: String): Boolean {
        if (owner.isBlank() || delivery.isBlank()) return false
        store.clear(scope(owner, delivery))
        return true
    }

    fun clearOwner(owner: String) {
        if (owner.isBlank()) return
        store.read("index/$owner", Long.MAX_VALUE)?.get("deliveries")?.jsonArray?.forEach {
            clear(owner, it.jsonPrimitive.content)
        }
        store.clear("index/$owner")
    }
}
