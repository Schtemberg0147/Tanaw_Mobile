package com.tanaw.app.feature.workspace

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.*

/** Each optional driver section can fail without hiding the other permitted records. */
internal object DriverDatabaseData {
    fun retainDetailsForReload(currentDetails: JsonObject?, targetShipmentId: String): JsonObject? {
        if (targetShipmentId.isBlank()) return null
        return currentDetails?.takeIf { it.value("shipment_id") == targetShipmentId }
    }

    suspend fun loadDetails(
        shipmentId: String,
        role: String,
        deliveryId: String? = null,
        previousDetails: JsonObject? = null,
        onFastDetailsReady: ((JsonObject) -> Unit)? = null,
        readShipmentDetails: suspend (String) -> JsonObject,
        readFuelHistory: (suspend (String) -> JsonArray)? = null,
        readEvidence: (suspend (String) -> JsonObject)? = null,
        readRouteStatus: (suspend (String) -> JsonObject)? = null
    ): JsonObject = coroutineScope {
        require(shipmentId.isNotBlank()) { "SHIPMENT_ID_REQUIRED" }
        val detailsDeferred = async { readShipmentDetails(shipmentId) }

        if (role != "driver") {
            val routingDeferred = if (!deliveryId.isNullOrBlank() && readRouteStatus != null) {
                async {
                    try {
                        readRouteStatus(deliveryId)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        buildJsonObject { put("reason", "Live route calculation unavailable") }
                    }
                }
            } else null

            val details = detailsDeferred.await()
            val priorRouting = previousDetails?.takeIf { it.value("shipment_id") == shipmentId }?.get("routing")
            if (routingDeferred != null && onFastDetailsReady != null) {
                val initial = if (priorRouting != null) JsonObject(details + mapOf("routing" to priorRouting)) else details
                onFastDetailsReady(initial)
            }
            val routing = routingDeferred?.await()
            return@coroutineScope if (routing != null) JsonObject(details + mapOf("routing" to routing)) else details
        }

        val extraDeferred = async {
            if (readFuelHistory == null) buildJsonObject {}
            else {
                try {
                    buildJsonObject { put("fuel_logs", readFuelHistory(shipmentId)) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    buildJsonObject { put("fuel_error", "Could not load fuel records. Refresh to retry.") }
                }
            }
        }

        val evidenceDeferred = async {
            if (readEvidence == null) buildJsonObject {}
            else {
                try {
                    readEvidence(shipmentId)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    buildJsonObject { put("evidence_error", "Could not load incident photos or expense reviews. Refresh to retry.") }
                }
            }
        }

        val routingDeferred = async {
            if (deliveryId.isNullOrBlank()) {
                buildJsonObject { put("reason", "Assigned delivery unavailable") }
            } else if (readRouteStatus == null) {
                buildJsonObject { put("reason", "Truck routing provider unavailable. Refresh to retry.") }
            } else {
                try {
                    readRouteStatus(deliveryId)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    buildJsonObject { put("reason", "Truck routing provider unavailable. Refresh to retry.") }
                }
            }
        }

        val details = detailsDeferred.await()
        val extra = extraDeferred.await()
        val evidence = evidenceDeferred.await()

        if (onFastDetailsReady != null) {
            val priorRouting = previousDetails?.takeIf { it.value("shipment_id") == shipmentId }?.get("routing")
            val interim = buildMap<String, JsonElement> {
                putAll(details)
                putAll(extra)
                putAll(evidence)
                if (priorRouting != null) put("routing", priorRouting)
            }
            onFastDetailsReady(JsonObject(interim))
        }

        val routing = routingDeferred.await()
        JsonObject(details + extra + evidence + mapOf("routing" to routing))
    }

    suspend fun load(
        readSettings: suspend () -> JsonArray = { JsonArray(emptyList()) },
        read: suspend (String, String?) -> JsonArray
    ): JsonObject = coroutineScope {
        val fields = mutableMapOf<String, JsonElement>()
        val failed = mutableListOf<String>()
        val sections = mutableMapOf<String, JsonElement>()

        val settingsDeferred = async {
            try {
                Result.success(readSettings())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        suspend fun fetchSection(table: String, label: String, driver: String? = null): Pair<String, Result<JsonArray>> =
            try {
                label to Result.success(read(table, driver))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                label to Result.failure(e)
            }

        val rosterDeferred = async { fetchSection("drivers", "driver profile") }

        val settingsResult = settingsDeferred.await()
        settingsResult.fold(
            onSuccess = {
                fields["driver_settings"] = it
                sections["driver_settings"] = JsonPrimitive("ready")
            },
            onFailure = {
                sections["driver_settings"] = JsonPrimitive("error")
                failed.add("profile settings")
            }
        )

        val (_, rosterResult) = rosterDeferred.await()
        val roster = rosterResult.fold(
            onSuccess = {
                sections["driver_roster"] = JsonPrimitive(if (it.isEmpty()) "empty" else "ready")
                it
            },
            onFailure = {
                failed.add("driver profile")
                sections["driver_roster"] = JsonPrimitive("error")
                null
            }
        )

        val driver = roster?.firstOrNull() as? JsonObject
        fields["driver"] = driver ?: buildJsonObject {}
        val id = (driver?.get("id") as? JsonPrimitive)?.contentOrNull

        if (!id.isNullOrBlank()) {
            val specs = listOf(
                Triple("trucks", "driver_trucks", "assigned vehicle"),
                Triple("compliance_documents", "compliance", "compliance"),
                Triple("deliveries", "driver_deliveries", "trip details"),
                Triple("delivery_expenses", "expenses", "expenses")
            )
            val deferreds = specs.map { (table, key, label) ->
                async {
                    Triple(key, label, fetchSection(table, label, id).second)
                }
            }
            deferreds.forEach { deferred ->
                val (key, label, res) = deferred.await()
                res.fold(
                    onSuccess = {
                        fields[key] = it
                        sections[key] = JsonPrimitive(if (it.isEmpty()) "empty" else "ready")
                    },
                    onFailure = {
                        failed.add(label)
                        sections[key] = JsonPrimitive("error")
                    }
                )
            }
        } else {
            listOf("driver_trucks", "compliance", "driver_deliveries", "expenses").forEach {
                sections[it] = JsonPrimitive("unavailable")
            }
            if (roster != null) failed.add("driver roster link (contact the office)")
        }

        fields["driver_sections"] = JsonObject(sections)
        if (failed.isNotEmpty()) {
            fields["driver_read_error"] = JsonPrimitive("Could not load ${failed.joinToString(", ")}. Refresh to retry.")
        }
        JsonObject(fields)
    }
}
