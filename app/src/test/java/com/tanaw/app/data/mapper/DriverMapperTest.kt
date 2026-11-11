package com.tanaw.app.data.mapper

import com.tanaw.app.data.dto.DbCargoCategoryDto
import com.tanaw.app.data.dto.DbDeliveryDto
import com.tanaw.app.data.dto.DbDriverDto
import com.tanaw.app.data.dto.DbLocationDto
import com.tanaw.app.data.dto.DbShipmentDto
import com.tanaw.app.domain.model.DriverStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriverMapperTest {

    @Test
    fun `toDomain converts DbDriverDto correctly`() {
        val dto = DbDriverDto(
            id = "drv-1",
            userId = "usr-1",
            fullName = "Ramon Cruz",
            status = "available"
        )

        val domain = dto.toDomain()

        assertEquals("drv-1", domain.id)
        assertEquals("usr-1", domain.userId)
        assertEquals("Ramon Cruz", domain.fullName)
        assertEquals(DriverStatus.AVAILABLE, domain.status)
        assertEquals("10-Wheeler Wing Van", domain.vehicleTypeName)
    }

    @Test
    fun `toDomain converts DbDeliveryDto to TripAssignment with valid fields`() {
        val deliveryDto = DbDeliveryDto(
            id = "del-101",
            shipmentId = "shp-8063",
            allowanceAmount = 500.0,
            notes = "Handle with care. Stack max 3 high.",
            shipments = DbShipmentDto(
                id = "shp-8063",
                trackingNumber = "SHP-NE-8063",
                weightKg = 6000.0,
                specialHandling = "KEEP DRY",
                cargoCategory = DbCargoCategoryDto(name = "Agricultural Bulk"),
                pickupLocation = DbLocationDto(name = "Cabanatuan City Hub Alpha"),
                dropoffLocation = DbLocationDto(name = "Gapan City Distribution Ctr"),
                distanceKm = 38.0,
                scheduledDeparture = "2023-10-24T08:00:00+08:00",
                hasToll = true
            )
        )

        val result = deliveryDto.toDomain()

        assertNotNull(result)
        assertEquals("SHP-NE-8063", result?.trackingNumber)
        assertEquals("Agricultural Bulk", result?.cargo?.categoryName)
        assertEquals("6,000 kg", result?.cargo?.formattedWeight)
        assertEquals("KEEP DRY", result?.cargo?.specialHandlingTag)
        assertEquals("Cabanatuan City Hub Alpha", result?.route?.pickup?.locationName)
        assertEquals("Gapan City Distribution Ctr", result?.route?.destination?.locationName)
        assertEquals("38 km", result?.meta?.formattedDistance)
        assertTrue(result?.meta?.hasToll == true)
        assertEquals("₱500", result?.formattedAllowance)
        assertEquals("Handle with care. Stack max 3 high.", result?.notes)
    }

    @Test
    fun `formatToManilaTime formats ISO string in Asia Manila timezone`() {
        val isoString = "2023-10-24T08:00:00Z"
        val formatted = formatToManilaTime(isoString)

        assertTrue(formatted.contains("Oct 24, 2023"))
    }

    @Test
    fun `formatDuration handles hours and minutes correctly`() {
        assertEquals("1h 15min", formatDuration(75))
        assertEquals("2h", formatDuration(120))
        assertEquals("45min", formatDuration(45))
    }

    @Test
    fun `formatPesoCurrency formats currency correctly`() {
        assertEquals("₱500", formatPesoCurrency(500.0))
        assertEquals("₱1,250", formatPesoCurrency(1250.0))
    }
}
