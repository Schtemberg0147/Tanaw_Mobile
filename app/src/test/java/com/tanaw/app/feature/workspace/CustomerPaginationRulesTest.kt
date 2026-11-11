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

    // --- Vulnerability Tests ---

    @Test
    fun sanitizeSearchQueryMitigatesPostgrestFilterInjection() {
        // Attempting to inject commas and closing parentheses to break out of the PostgREST or(...) group
        val injectionAttempt = "TN-123),status.eq.completed,user_id.eq.admin"
        val sanitized = CustomerPaginationRules.sanitizeSearchQuery(injectionAttempt)
        assertEquals("""TN-123\)\,status.eq.completed\,user\_id.eq.admin""", sanitized)
    }

    @Test
    fun sanitizeSearchQueryStripsDoubleQuotesToPreventTokenizerDisruption() {
        // Double quotes break the PostgREST grammar when unclosed or when injected inside ilike
        val quoteAttempt = """2" pipe "special" cargo"""
        val sanitized = CustomerPaginationRules.sanitizeSearchQuery(quoteAttempt)
        assertEquals("2 pipe special cargo", sanitized)

        val unclosedQuote = "\"malicious-filter"
        assertEquals("malicious-filter", CustomerPaginationRules.sanitizeSearchQuery(unclosedQuote))
    }

    @Test
    fun sanitizeSearchQueryStripsControlCharacters() {
        // Control characters could disrupt URL parsing or downstream logging
        val controlChars = "TN\r\n-123\t\u0000"
        val sanitized = CustomerPaginationRules.sanitizeSearchQuery(controlChars)
        assertEquals("TN-123", sanitized)
    }

    @Test
    fun sanitizeSearchQueryEscapesSqlWildcardsToPreventUnboundedFullTableScans() {
        // Repeated wildcards could cause heavy regex/like evaluations in PostgreSQL
        val wildcards = "%_%_%_"
        val sanitized = CustomerPaginationRules.sanitizeSearchQuery(wildcards)
        assertEquals("""\%\_\%\_\%\_""", sanitized)
    }

    @Test
    fun sanitizeSearchQueryHandlesBackslashEscapeBypass() {
        // An attacker might prepend backslashes attempting to escape the escape character
        val backslashes = """\\,test"""
        val sanitized = CustomerPaginationRules.sanitizeSearchQuery(backslashes)
        assertEquals("""\\\\\,test""", sanitized)
    }

    @Test
    fun compareTimestampsDescSafelyHandlesMalformedAndAttackStrings() {
        // Corrupt or non-standard timestamp inputs should not throw exceptions
        val malformed1 = "<script>alert(1)</script>"
        val malformed2 = "DROP TABLE shipments"
        val valid = "2026-11-01T10:00:00Z"

        // Both malformed: falls back to raw string compare without throwing
        val res = CustomerPaginationRules.compareTimestampsDesc(malformed1, malformed2)
        assertEquals(malformed2.compareTo(malformed1), res)

        // One malformed and one valid: valid timestamp parses and does not crash
        assertTrue(CustomerPaginationRules.compareTimestampsDesc(valid, malformed1) != 0)
    }

    // --- Boundary & Edge Case Tests ---

    @Test
    fun hasMoreReturnsTrueWhenCountReachesPageSize() {
        assertTrue(CustomerPaginationRules.hasMore(50, 50))
        assertTrue(CustomerPaginationRules.hasMore(51, 50))
        assertFalse(CustomerPaginationRules.hasMore(49, 50))
        assertFalse(CustomerPaginationRules.hasMore(0, 50))
    }

    @Test
    fun hasMoreUsesDefaultShipmentsPageSize() {
        assertTrue(CustomerPaginationRules.hasMore(50))
        assertTrue(CustomerPaginationRules.hasMore(55))
        assertFalse(CustomerPaginationRules.hasMore(49))
        assertFalse(CustomerPaginationRules.hasMore(0))
    }

    @Test
    fun hasMoreRejectsZeroOrNegativePageSize() {
        assertFalse(CustomerPaginationRules.hasMore(50, 0))
        assertFalse(CustomerPaginationRules.hasMore(50, -1))
        assertFalse(CustomerPaginationRules.hasMore(-1, 50))
    }

    @Test
    fun compareTimestampsDescOrdersChronologicallyAcrossTimezoneAndFractionalSeconds() {
        val nov1NoonZ = "2026-11-01T12:00:00Z"
        val nov1NoonWithMillis = "2026-11-01T12:00:00.500Z"
        val nov1OnePmZ = "2026-11-01T13:00:00Z"
        val nov1OnePmOffset = "2026-11-01T13:00:00+00:00"

        // nov1OnePm is newer than nov1Noon -> compare(nov1Noon, nov1OnePm) > 0 so nov1OnePm is sorted first
        assertTrue(CustomerPaginationRules.compareTimestampsDesc(nov1NoonZ, nov1OnePmZ) > 0)
        assertTrue(CustomerPaginationRules.compareTimestampsDesc(nov1OnePmZ, nov1NoonZ) < 0)

        // Equivalence between 'Z' and '+00:00'
        assertEquals(0, CustomerPaginationRules.compareTimestampsDesc(nov1OnePmZ, nov1OnePmOffset))

        // Millisecond precision: nov1NoonWithMillis is 500ms newer than nov1NoonZ
        assertTrue(CustomerPaginationRules.compareTimestampsDesc(nov1NoonZ, nov1NoonWithMillis) > 0)
        assertTrue(CustomerPaginationRules.compareTimestampsDesc(nov1NoonWithMillis, nov1NoonZ) < 0)

        // Blank timestamps sort to the end (older)
        assertTrue(CustomerPaginationRules.compareTimestampsDesc("", nov1NoonZ) > 0)
        assertTrue(CustomerPaginationRules.compareTimestampsDesc(nov1NoonZ, "") < 0)
        assertEquals(0, CustomerPaginationRules.compareTimestampsDesc("", ""))
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
    fun mergePageHandlesMixedTimezonesAndEmptyLists() {
        val itemOffset = buildJsonObject {
            put("id", "ship-offset")
            put("created_at", "2026-11-04T08:00:00+08:00") // 00:00:00 UTC
        }
        val itemUtc = buildJsonObject {
            put("id", "ship-utc")
            put("created_at", "2026-11-04T01:00:00Z") // 01:00:00 UTC (1 hour newer)
        }

        val res = CustomerPaginationRules.mergePage(listOf(itemOffset), listOf(itemUtc), reset = false)
        assertEquals(listOf("ship-utc", "ship-offset"), res.map { it.value("id") })

        // Empty incoming leaves existing intact when reset = false
        val emptyIncoming = CustomerPaginationRules.mergePage(listOf(itemOffset), emptyList(), reset = false)
        assertEquals(listOf("ship-offset"), emptyIncoming.map { it.value("id") })

        // Empty incoming with reset = true clears to empty
        val resetEmpty = CustomerPaginationRules.mergePage(listOf(itemOffset), emptyList(), reset = true)
        assertTrue(resetEmpty.isEmpty())

        // Empty existing with incoming items
        val emptyExisting = CustomerPaginationRules.mergePage(emptyList(), listOf(itemOffset), reset = false)
        assertEquals(listOf("ship-offset"), emptyExisting.map { it.value("id") })
    }

    @Test
    fun mergePageHandlesMissingIdsAndMissingTimestamps() {
        val itemNoId = buildJsonObject {
            put("title", "Anonymous notification")
            put("created_at", "2026-11-02T10:00:00Z")
        }
        val itemNoDate = buildJsonObject {
            put("id", "item-no-date")
            put("title", "Undated notification")
        }
        val itemStandard = buildJsonObject {
            put("id", "item-standard")
            put("created_at", "2026-11-05T10:00:00Z")
        }

        val res = CustomerPaginationRules.mergePage(emptyList(), listOf(itemNoId, itemNoDate, itemStandard), reset = false)
        assertEquals(3, res.size)
        // Standard (dated) must come before itemNoDate (blank date sorts to end)
        assertEquals("item-standard", res[0].value("id"))
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

        // Active tab with search by dropoff
        val activeSearch = CustomerPaginationRules.filterOfflineShipments(all, "Active", "All", "Gapan")
        assertEquals(listOf("1"), activeSearch.map { it.value("id") })

        // Active tab with search by pickup
        val activePickupSearch = CustomerPaginationRules.filterOfflineShipments(all, "Active", "All", "San Jose")
        assertEquals(listOf("2"), activePickupSearch.map { it.value("id") })

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

        // Whitespace query should return all matching tab records
        val whitespaceQuery = CustomerPaginationRules.filterOfflineShipments(all, "Active", "All", "   ")
        assertEquals(listOf("1", "2"), whitespaceQuery.map { it.value("id") })
    }

    @Test
    fun filterOfflineShipmentsHandlesCaseInsensitiveStatusValues() {
        val uppercaseCompleted = buildJsonObject {
            put("id", "s-comp")
            put("status", "Completed")
            put("created_at", "2026-11-01T10:00:00Z")
        }
        val allCapsCancelled = buildJsonObject {
            put("id", "s-canc")
            put("status", "CANCELLED")
            put("created_at", "2026-11-02T10:00:00Z")
        }
        val titleCaseActive = buildJsonObject {
            put("id", "s-active")
            put("status", "In_Transit")
            put("created_at", "2026-11-03T10:00:00Z")
        }

        val all = listOf(uppercaseCompleted, allCapsCancelled, titleCaseActive)

        // In History tab, capitalized Completed and CANCELLED must match
        val historyResults = CustomerPaginationRules.filterOfflineShipments(all, "History", "All", "")
        assertEquals(listOf("s-canc", "s-comp"), historyResults.map { it.value("id") })

        val completedOnly = CustomerPaginationRules.filterOfflineShipments(all, "History", "Completed", "")
        assertEquals(listOf("s-comp"), completedOnly.map { it.value("id") })

        val cancelledOnly = CustomerPaginationRules.filterOfflineShipments(all, "History", "Cancelled", "")
        assertEquals(listOf("s-canc"), cancelledOnly.map { it.value("id") })

        // In Active tab, only In_Transit must match
        val activeResults = CustomerPaginationRules.filterOfflineShipments(all, "Active", "All", "")
        assertEquals(listOf("s-active"), activeResults.map { it.value("id") })
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

        // Empty list
        assertTrue(CustomerPaginationRules.filterOfflineNotifications(emptyList()).isEmpty())
    }
}
