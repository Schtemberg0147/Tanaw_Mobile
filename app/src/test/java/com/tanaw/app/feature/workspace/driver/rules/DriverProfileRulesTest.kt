package com.tanaw.app.feature.workspace.driver.rules

import kotlinx.serialization.json.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class DriverProfileRulesTest {
    private val today=LocalDate.of(2026,10,29)
    private fun doc(days:Long,status:String="approved",type:String="drivers_license")=buildJsonObject {
        put("doc_type",type);put("review_status",status);put("expiry_date",today.plusDays(days).toString())
    }
    private fun settings(first:Int=45,critical:Int=10)=buildJsonArray {add(buildJsonObject {
        put("key","compliance_thresholds");put("value",buildJsonObject {
            DriverProfileRules.types.forEach {key->put(key,buildJsonObject {put("first",first);put("critical",critical)})}
        })
    })}
    @Test fun sharedPolicyHasInclusiveFirstAndCriticalEndpoints() {
        val rows=settings()
        assertEquals("CRITICAL",DriverProfileRules.compliance(doc(10),rows,today))
        assertEquals("EXPIRING SOON",DriverProfileRules.compliance(doc(11),rows,today))
        assertEquals("EXPIRING SOON",DriverProfileRules.compliance(doc(45),rows,today))
        assertEquals("VALID",DriverProfileRules.compliance(doc(46),rows,today))
        assertEquals("EXPIRED",DriverProfileRules.compliance(doc(-1),rows,today))
    }
    @Test fun noConfigUsesOfficeThirtyDayFallbackInsteadOfSixty() {
        val rows=JsonArray(emptyList())
        assertEquals("VALID",DriverProfileRules.compliance(doc(31),rows,today))
        assertEquals("EXPIRING SOON",DriverProfileRules.compliance(doc(30),rows,today))
        assertEquals("EXPIRING SOON",DriverProfileRules.compliance(doc(0),rows,today))
    }
    @Test fun legacyAndUnknownTypesUseGlobalWindow() {
        val rows=JsonArray(settings()+buildJsonObject {put("key","compliance_threshold_days");put("value",60)})
        assertEquals("EXPIRING SOON",DriverProfileRules.compliance(doc(60,type="other"),rows,today))
        assertEquals("VALID",DriverProfileRules.compliance(doc(61,type="other"),rows,today))
    }
    @Test fun failedSettingsNeverClaimValidButExpiredAndReviewRemainKnown() {
        assertEquals("WARNING STATUS UNKNOWN",DriverProfileRules.compliance(doc(100),null,today))
        assertEquals("EXPIRED",DriverProfileRules.compliance(doc(-1),null,today))
        assertEquals("REJECTED",DriverProfileRules.compliance(doc(10,"rejected"),settings(),today))
        assertEquals("EXPIRY UNKNOWN",DriverProfileRules.compliance(buildJsonObject {put("review_status","approved");put("expiry_date","2026-02-30")},settings(),today))
    }
    @Test fun malformedPolicyFailsClosed() {
        assertEquals("WARNING STATUS UNKNOWN",DriverProfileRules.compliance(doc(100),settings(10,10),today))
        val partial=buildJsonArray {add(buildJsonObject {put("key","compliance_thresholds");put("value",buildJsonObject {})})}
        assertEquals("WARNING STATUS UNKNOWN",DriverProfileRules.compliance(doc(100),partial,today))
    }
    @Test fun unsafePublishedLinksAndContactsAreRejected() {
        assertNull(DriverProfileRules.https("javascript:alert(1)"))
        assertNull(DriverProfileRules.https("https://user:secret@example.com/policy"))
        assertEquals("https://example.invalid/policy",DriverProfileRules.https("https://example.invalid/policy"))
        assertNull(DriverProfileRules.email("office@example.invalid\r\nBcc:other@example.invalid"))
        assertEquals("office@example.invalid",DriverProfileRules.email("office@example.invalid"))
        assertEquals("+639000000000",DriverProfileRules.phone("+63 900 000 0000"))
        assertNull(DriverProfileRules.phone("911;other"))
    }
    @Test fun complianceSummaryReflectsRealDocumentStates() {
        val rows = settings()
        val docs = listOf(doc(60), doc(20), doc(5), doc(-2))
        val summary = DriverProfileRules.complianceSummary(docs, rows, today)
        assertEquals(4, summary.total)
        assertEquals(1, summary.validCount)
        assertEquals(1, summary.attentionCount)
        assertEquals(2, summary.criticalOrExpiredCount)
        assertEquals("1 valid · 1 attention · 2 critical", DriverProfileRules.formatSummaryLine(summary))
        assertEquals("", DriverProfileRules.formatSummaryLine(ComplianceSummary(0, 0, 0, 0)))
    }
    @Test fun expiryFormattingAndInitialsHandleValidAndMissingInputs() {
        assertEquals("Nov 22, 2026", DriverProfileRules.formatExpiryDate("2026-11-22"))
        assertEquals("Not recorded", DriverProfileRules.formatExpiryDate(""))
        assertEquals("Not recorded", DriverProfileRules.formatExpiryDate("not-a-date"))
        assertEquals("RD", DriverProfileRules.initials("Ramon Dela Cruz"))
        assertEquals("D", DriverProfileRules.initials("   "))
    }
    @Test fun availabilityBadgePrioritizesOfflineAndRosterState() {
        val ready = buildJsonObject {
            put("driver", buildJsonObject { put("id", "d1"); put("status", "available") })
            put("driver_sections", buildJsonObject { put("driver_roster", "ready") })
        }
        assertEquals("Available", DriverProfileRules.availabilityBadge(ready, offline = false).label)
        assertEquals(AvailabilityTone.SUCCESS, DriverProfileRules.availabilityBadge(ready, offline = false).tone)
        assertEquals("Offline · Saved Copy", DriverProfileRules.availabilityBadge(ready, offline = true).label)
        assertEquals(AvailabilityTone.WARNING, DriverProfileRules.availabilityBadge(ready, offline = true).tone)

        val missingRoster = buildJsonObject {
            put("driver_sections", buildJsonObject { put("driver_roster", "error") })
        }
        assertEquals("Roster Unavailable", DriverProfileRules.availabilityBadge(missingRoster, offline = false).label)
    }
}
