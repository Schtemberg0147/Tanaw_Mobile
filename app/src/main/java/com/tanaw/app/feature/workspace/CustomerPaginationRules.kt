package com.tanaw.app.feature.workspace

import kotlinx.serialization.json.JsonObject

object CustomerPaginationRules {
    const val SHIPMENTS_PAGE_SIZE = 50
    const val NOTIFICATIONS_PAGE_SIZE = 50

    const val SHIPMENT_COLUMNS = "id,tracking_number,status,pickup_address,dropoff_address,pickup_lat,pickup_lng,dropoff_lat,dropoff_lng,route_geometry,cargo_description,weight_kg,special_handling,scheduled_departure,estimated_arrival,quoted_amount,final_amount,driver_notes,cancelled_reason,created_at,updated_at"
    const val NOTIFICATION_COLUMNS = "id,title,body,shipment_id,read_at,created_at"

    /**
     * Deduplicates shipments/notifications by id while preserving created_at DESC order.
     * When reset is false, incoming items replace existing items with the same id and new items are appended.
     */
    fun mergePage(existing: List<JsonObject>, incoming: List<JsonObject>, reset: Boolean): List<JsonObject> {
        val base = if (reset) {
            incoming
        } else {
            val incomingIds = incoming.mapNotNull { it.value("id").takeIf(String::isNotBlank) }.toSet()
            existing.filterNot { it.value("id") in incomingIds } + incoming
        }
        return base
            .distinctBy { it.value("id").ifBlank { it.toString() } }
            .sortedWith { a, b -> b.value("created_at").compareTo(a.value("created_at")) }
    }

    /**
     * Determines if more items can be fetched based on last fetched page size.
     */
    fun hasMore(lastFetchedCount: Int, pageSize: Int = SHIPMENTS_PAGE_SIZE): Boolean =
        lastFetchedCount >= pageSize

    /**
     * Trims and escapes %, _, ,, (, ) and \ characters so PostgREST or / ilike filters are safe against injection.
     */
    fun sanitizeSearchQuery(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        return buildString {
            for (char in trimmed) {
                when (char) {
                    '\\', '%', '_', ',', '(', ')' -> {
                        append('\\')
                        append(char)
                    }
                    else -> append(char)
                }
            }
        }
    }

    /**
     * In-memory fallback filter for shipments when the app is offline or RLS direct query fails.
     */
    fun filterOfflineShipments(
        cachedShipments: List<JsonObject>,
        tab: String,
        statusFilter: String,
        query: String
    ): List<JsonObject> {
        val trimmedQuery = query.trim()
        return cachedShipments
            .filter { shipment ->
                val status = shipment.value("status")
                val isHistory = status in listOf("completed", "cancelled")
                val tabMatches = if (tab == "History") isHistory else !isHistory
                val filterMatches = tab != "History" || statusFilter == "All" || status.equals(statusFilter, ignoreCase = true)
                val queryMatches = trimmedQuery.isBlank() || listOf("tracking_number", "pickup_address", "dropoff_address").any { key ->
                    shipment.value(key).contains(trimmedQuery, ignoreCase = true)
                }
                tabMatches && filterMatches && queryMatches
            }
            .sortedWith { a, b -> b.value("created_at").compareTo(a.value("created_at")) }
    }

    /**
     * In-memory fallback for notifications when offline.
     */
    fun filterOfflineNotifications(cachedNotifications: List<JsonObject>): List<JsonObject> {
        return cachedNotifications
            .distinctBy { it.value("id").ifBlank { it.toString() } }
            .sortedWith { a, b -> b.value("created_at").compareTo(a.value("created_at")) }
    }
}
