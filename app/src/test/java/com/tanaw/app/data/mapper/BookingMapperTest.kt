package com.tanaw.app.data.mapper

import com.tanaw.app.data.dto.DbDocumentSummaryDto
import com.tanaw.app.data.dto.DbLocationSummaryDto
import com.tanaw.app.data.dto.DbShipmentSummaryDto
import com.tanaw.app.data.dto.DbVehicleTypeSummaryDto
import com.tanaw.app.domain.model.BookingStatusEnum
import com.tanaw.app.domain.model.BookingTabFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookingMapperTest {

    @Test
    fun `toUiModel maps in_transit shipment correctly`() {
        val dto = DbShipmentSummaryDto(
            id = "shp-1",
            trackingNumber = "SHP-NE-8063",
            status = "in_transit",
            finalAmount = 3530.0,
            scheduledDeparture = "2023-10-24T08:00:00Z",
            pickupLocation = DbLocationSummaryDto(name = "Hub Alpha", city = "Cabanatuan City"),
            dropoffLocation = DbLocationSummaryDto(name = "Distribution Ctr", city = "Gapan City"),
            requestedVehicleType = DbVehicleTypeSummaryDto(name = "10-Wheeler Wing Van")
        )

        val uiModel = dto.toUiModel()

        assertEquals("SHP-NE-8063", uiModel.id)
        assertEquals("Cabanatuan City", uiModel.pickupCity)
        assertEquals("Gapan City", uiModel.destinationCity)
        assertEquals("₱3,530", uiModel.formattedPrice)
        assertEquals(BookingStatusEnum.IN_TRANSIT, uiModel.status)
        assertEquals("IN TRANSIT", uiModel.cardStyle.statusLabel)
        assertFalse(uiModel.cardStyle.isMuted)
        assertFalse(uiModel.cardStyle.isPriceStrikethrough)
    }

    @Test
    fun `toUiModel maps cancelled shipment correctly with muted style and strikethrough`() {
        val dto = DbShipmentSummaryDto(
            id = "shp-2",
            trackingNumber = "SHP-NE-7203",
            status = "cancelled",
            finalAmount = 3040.0,
            scheduledDeparture = "2023-10-10T08:00:00Z",
            pickupLocation = DbLocationSummaryDto(name = "Palayan Hub", city = "Palayan"),
            dropoffLocation = DbLocationSummaryDto(name = "Cabanatuan Hub", city = "Cabanatuan"),
            requestedVehicleType = DbVehicleTypeSummaryDto(name = "10-Wheeler Flatbed")
        )

        val uiModel = dto.toUiModel()

        assertEquals("SHP-NE-7203", uiModel.id)
        assertEquals(BookingStatusEnum.CANCELLED, uiModel.status)
        assertEquals("CANCELLED", uiModel.cardStyle.statusLabel)
        assertTrue(uiModel.cardStyle.isMuted)
        assertTrue(uiModel.cardStyle.isPriceStrikethrough)
    }

    @Test
    fun `toUiModel detects proof of delivery correctly`() {
        val dto = DbShipmentSummaryDto(
            id = "shp-3",
            trackingNumber = "SHP-NE-7821",
            status = "delivered",
            deliveryDocuments = listOf(DbDocumentSummaryDto(id = "doc-1", kind = "proof_of_delivery"))
        )

        val uiModel = dto.toUiModel()

        assertTrue(uiModel.hasPod)
        assertEquals(BookingStatusEnum.DELIVERED, uiModel.status)
    }

    @Test
    fun `belongsToTab filters correctly per tab`() {
        assertTrue(BookingStatusEnum.IN_TRANSIT.belongsToTab(BookingTabFilter.ACTIVE))
        assertTrue(BookingStatusEnum.BOOKED.belongsToTab(BookingTabFilter.ACTIVE))
        assertTrue(BookingStatusEnum.DELIVERED.belongsToTab(BookingTabFilter.COMPLETED))
        assertTrue(BookingStatusEnum.CANCELLED.belongsToTab(BookingTabFilter.CANCELLED))
        assertTrue(BookingStatusEnum.CANCELLED.belongsToTab(BookingTabFilter.ALL))
    }
}
