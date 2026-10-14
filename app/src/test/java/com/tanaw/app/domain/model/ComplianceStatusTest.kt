package com.tanaw.app.domain.model

import com.tanaw.app.data.mapper.formatDurationStrict
import com.tanaw.app.data.mapper.mapDeliveryStatusToTripStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ComplianceStatusTest {

    private val today = LocalDate.of(2024, 10, 24)

    @Test
    fun `when expiry date is null returns VALID with No expiry`() {
        val status = ComplianceStatus.from(null, today)

        assertEquals(ComplianceBadgeState.VALID, status.state)
        assertEquals("VALID", status.badgeLabel)
        assertEquals("No expiry", status.detailText)
        assertFalse(status.isWarning)
    }

    @Test
    fun `when expiry date is before today returns EXPIRED`() {
        val yesterday = today.minusDays(1)
        val status = ComplianceStatus.from(yesterday, today)

        assertEquals(ComplianceBadgeState.EXPIRED, status.state)
        assertEquals("EXPIRED", status.badgeLabel)
        assertEquals("Expired 1 day ago", status.detailText)
        assertTrue(status.isWarning)

        val tenDaysAgo = today.minusDays(10)
        val statusTen = ComplianceStatus.from(tenDaysAgo, today)
        assertEquals("Expired 10 days ago", statusTen.detailText)
    }

    @Test
    fun `when expiry date is exactly today returns EXPIRING SOON with 0 days left`() {
        val status = ComplianceStatus.from(today, today)

        assertEquals(ComplianceBadgeState.EXPIRING_SOON, status.state)
        assertEquals("EXPIRING SOON", status.badgeLabel)
        assertTrue(status.detailText.contains("0 days left"))
        assertTrue(status.isWarning)
    }

    @Test
    fun `when expiry date is 59 60 61 days from today boundary behavior`() {
        // 59 days out -> EXPIRING SOON
        val day59 = today.plusDays(59)
        val status59 = ComplianceStatus.from(day59, today)
        assertEquals(ComplianceBadgeState.EXPIRING_SOON, status59.state)
        assertTrue(status59.detailText.contains("59 days left"))

        // 60 days out -> EXPIRING SOON
        val day60 = today.plusDays(60)
        val status60 = ComplianceStatus.from(day60, today)
        assertEquals(ComplianceBadgeState.EXPIRING_SOON, status60.state)
        assertTrue(status60.detailText.contains("60 days left"))

        // 61 days out -> VALID
        val day61 = today.plusDays(61)
        val status61 = ComplianceStatus.from(day61, today)
        assertEquals(ComplianceBadgeState.VALID, status61.state)
        assertFalse(status61.isWarning)
    }

    @Test
    fun `status to tab mapping correctly maps delivery and shipment statuses`() {
        assertEquals(TripStatus.COMPLETED, mapDeliveryStatusToTripStatus("completed", "delivered"))
        assertEquals(TripStatus.COMPLETED, mapDeliveryStatusToTripStatus("delivered", null))

        assertEquals(TripStatus.CANCELLED, mapDeliveryStatusToTripStatus("cancelled", null))
        assertEquals(TripStatus.CANCELLED, mapDeliveryStatusToTripStatus("rejected", "cancelled"))
        assertEquals(TripStatus.CANCELLED, mapDeliveryStatusToTripStatus("failed", null))

        assertEquals(TripStatus.ONGOING, mapDeliveryStatusToTripStatus("assigned", "booked"))
        assertEquals(TripStatus.ONGOING, mapDeliveryStatusToTripStatus("en_route", "in_transit"))
        assertEquals(TripStatus.ONGOING, mapDeliveryStatusToTripStatus("arrived", null))
    }

    @Test
    fun `duration formatting handles zero and positive minutes correctly`() {
        assertEquals("—", formatDurationStrict(0))
        assertEquals("—", formatDurationStrict(-10))
        assertEquals("45min", formatDurationStrict(45))
        assertEquals("1h 15min", formatDurationStrict(75))
        assertEquals("2h", formatDurationStrict(120))
    }

    @Test
    fun `initials generation handles various full names`() {
        assertEquals("RC", generateInitials("Ramon Cruz"))
        assertEquals("R", generateInitials("Ramon"))
        assertEquals("JD", generateInitials("John Michael Doe"))
        assertEquals("U", generateInitials(""))
    }

    @Test
    fun `on time rate returns null when total completed trips is zero`() {
        val stats = DriverTripStats(
            completedTripsCount = 0,
            totalAllowance = 0.0,
            formattedTotalAllowance = "₱0",
            onTimeRatePercentage = null
        )

        assertNull(stats.onTimeRatePercentage)
    }
}
