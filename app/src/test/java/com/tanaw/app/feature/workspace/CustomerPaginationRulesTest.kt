package com.tanaw.app.feature.workspace

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomerPaginationRulesTest {

    @Test
    fun sanitizeSearchQueryTrimsAndEscapesSpecialCharacters() {
        assertEquals("", CustomerPaginationRules.sanitizeSearchQuery("   "))
        assertEquals("TN-12345", CustomerPaginationRules.sanitizeSearchQuery("  TN-12345  "))
        assertEquals(
            """TN\%123\_45\, \(test\)""",
            CustomerPaginationRules.sanitizeSearchQuery(" TN%123_45, (test) ")
        )
        assertEquals(
            """path\\to\%item""",
            CustomerPaginationRules.sanitizeSearchQuery("""path\to%item""")
        )
    }

    @Test
    fun hasMoreReturnsTrueWhenCountReachesPageSize() {
        assertTrue(CustomerPaginationRules.hasMore(50, 50))
        assertTrue(CustomerPaginationRules.hasMore(51, 50))
        assertFalse(CustomerPaginationRules.hasMore(49, 50))
        assertFalse(CustomerPaginationRules.hasMore(0, 50))
    }

    @Test
    fun mergePageDeduplicatesByIdAndSortsByCreatedAtDesc() {
        val item1 = buildJsonObject {
            put("id", "ship-1")
            put("created_at", "2026-11-01T10:00:00Z")
            put("status", "completed")
        }
        val item2 = buildJsonObject {
            put("id", "ship-2")
            put("created_at", "2026-11-03T10:00:00Z")
            put("status", "in_transit")
        }
        val item3 = buildJsonObject {
            put("id", "ship-3")
            put("created_at", "2026-11-02T10:00:00Z")
            put("status", "pending")
        }
        // item1 updated in incoming
        val item1Updated = buildJsonObject {
            put("id", "ship-1")
            put("created_at", "2026-11-01T10:00:00Z")
            put("status", "delivered")
        }

        // Reset = true replaces existing completely and sorts
        val resetResult = CustomerPaginationRules.mergePage(listOf(item1), listOf(item2, item3), reset = true)
        assertEquals(listOf("ship-2", "ship-3"), resetResult.map { it.value("id") })

        // Reset = false merges, deduplicates and sorts
        val mergedResult = CustomerPaginationRules.mergePage(listOf(item1, item2), listOf(item3, item1Updated), reset = false)
        assertEquals(3, mergedResult.size)
        assertEquals(listOf("ship-2", "ship-3", "ship-1"), mergedResult.map { it.value("id") })
        // Check that item1 was updated to "delivered"
        assertEquals("delivered", mergedResult.first { it.value("id") == "ship-1" }.value("status"))
    }

    @Test
    fun filterOfflineShipmentsFiltersByTabAndStatusAndQuery() {
        val active1 = buildJsonObject {
            put("id", "1")
            put("tracking_number", "TN-ACTIVE-1")
            put("status", "in_transit")
            put("pickup_address", "Cabanatuan City")
            put("dropoff_address", "Gapan City")
            put("created_at", "2026-11-05T12:00:00Z")
        }
        val active2 = buildJsonObject {
            put("id", "2")
            put("tracking_number", "TN-ACTIVE-2")
            put("status", "pending")
            put("pickup_address", "San Jose City")
            put("dropoff_address", "Munoz")
            put("created_at", "2026-11-04T12:00:00Z")
        }
        val completed = buildJsonObject {
            put("id", "3")
            put("tracking_number", "TN-COMPLETED")
            put("status", "completed")
            put("pickup_address", "Palayan City")
            put("dropoff_address", "Cabanatuan City")
            put("created_at", "2026-11-03T12:00:00Z")
        }
        val cancelled = buildJsonObject {
            put("id", "4")
            put("tracking_number", "TN-CANCELLED")
            put("status", "cancelled")
            put("pickup_address", "Talavera")
            put("dropoff_address", "Guimba")
            put("created_at", "2026-11-02T12:00:00Z")
        }

        val all = listOf(active1, active2, completed, cancelled)

        // Active tab
        val activeResults = CustomerPaginationRules.filterOfflineShipments(all, "Active", "All", "")
        assertEquals(listOf("1", "2"), activeResults.map { it.value("id") })

        // Active tab with search
        val activeSearch = CustomerPaginationRules.filterOfflineShipments(all, "Active", "All", "Gapan")
        assertEquals(listOf("1"), activeSearch.map { it.value("id") })

        // History tab All
        val historyAll = CustomerPaginationRules.filterOfflineShipments(all, "History", "All", "")
        assertEquals(listOf("3", "4"), historyAll.map { it.value("id") })

        // History tab Completed
        val historyCompleted = CustomerPaginationRules.filterOfflineShipments(all, "History", "Completed", "")
        assertEquals(listOf("3"), historyCompleted.map { it.value("id") })

        // History tab Cancelled
        val historyCancelled = CustomerPaginationRules.filterOfflineShipments(all, "History", "Cancelled", "")
        assertEquals(listOf("4"), historyCancelled.map { it.value("id") })

        // Query matching tracking number
        val queryTracking = CustomerPaginationRules.filterOfflineShipments(all, "History", "All", "TN-CANCELLED")
        assertEquals(listOf("4"), queryTracking.map { it.value("id") })
    }

    @Test
    fun filterOfflineNotificationsDeduplicatesAndSorts() {
        val n1 = buildJsonObject {
            put("id", "n1")
            put("created_at", "2026-11-01T08:00:00Z")
        }
        val n2 = buildJsonObject {
            put("id", "n2")
            put("created_at", "2026-11-05T08:00:00Z")
        }
        val nDuplicate = buildJsonObject {
            put("id", "n1")
            put("created_at", "2026-11-01T08:00:00Z")
        }

        val res = CustomerPaginationRules.filterOfflineNotifications(listOf(n1, n2, nDuplicate))
        assertEquals(listOf("n2", "n1"), res.map { it.value("id") })
    }
}
