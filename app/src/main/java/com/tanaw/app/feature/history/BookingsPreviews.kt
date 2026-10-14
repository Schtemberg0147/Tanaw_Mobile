package com.tanaw.app.feature.history

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tanaw.app.data.mapper.mapBookingCardStyle
import com.tanaw.app.domain.model.BookingItemUi
import com.tanaw.app.domain.model.BookingStatusEnum
import com.tanaw.app.ui.components.HistoryBookingCard
import com.tanaw.app.ui.theme.TanawTheme

private val sampleInTransitBooking = BookingItemUi(
    id = "SHP-NE-8063",
    shipmentId = "shp-1",
    pickupCity = "Cabanatuan City",
    destinationCity = "Gapan City",
    formattedDateAndVehicle = "Oct 24 · 10-Wheeler Wing Van",
    formattedPrice = "₱3,530",
    status = BookingStatusEnum.IN_TRANSIT,
    cardStyle = mapBookingCardStyle(BookingStatusEnum.IN_TRANSIT),
    hasPod = false
)

private val sampleDeliveredBooking = BookingItemUi(
    id = "SHP-NE-7821",
    shipmentId = "shp-2",
    pickupCity = "Cabanatuan City",
    destinationCity = "Talavera",
    formattedDateAndVehicle = "Oct 20 · 6-Wheeler Dropside",
    formattedPrice = "₱2,090",
    status = BookingStatusEnum.DELIVERED,
    cardStyle = mapBookingCardStyle(BookingStatusEnum.DELIVERED),
    hasPod = true
)

private val sampleCancelledBooking = BookingItemUi(
    id = "SHP-NE-7203",
    shipmentId = "shp-3",
    pickupCity = "Palayan",
    destinationCity = "Cabanatuan",
    formattedDateAndVehicle = "Oct 10 · 10-Wheeler Flatbed",
    formattedPrice = "₱3,040",
    status = BookingStatusEnum.CANCELLED,
    cardStyle = mapBookingCardStyle(BookingStatusEnum.CANCELLED),
    hasPod = false
)

@Preview(showBackground = true, name = "1. Active In-Transit Card")
@Composable
fun ActiveBookingCardPreview() {
    TanawTheme {
        Box(
            modifier = Modifier
                .background(Color(0xFFF8FAFC))
                .padding(16.dp)
        ) {
            HistoryBookingCard(
                booking = sampleInTransitBooking,
                onTrackClick = {},
                onViewPODClick = {},
                onRebookClick = {},
                onCardClick = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "2. Delivered Booking Card")
@Composable
fun DeliveredBookingCardPreview() {
    TanawTheme {
        Box(
            modifier = Modifier
                .background(Color(0xFFF8FAFC))
                .padding(16.dp)
        ) {
            HistoryBookingCard(
                booking = sampleDeliveredBooking,
                onTrackClick = {},
                onViewPODClick = {},
                onRebookClick = {},
                onCardClick = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "3. Cancelled Booking Card (Muted + Strikethrough)")
@Composable
fun CancelledBookingCardPreview() {
    TanawTheme {
        Box(
            modifier = Modifier
                .background(Color(0xFFF8FAFC))
                .padding(16.dp)
        ) {
            HistoryBookingCard(
                booking = sampleCancelledBooking,
                onTrackClick = {},
                onViewPODClick = {},
                onRebookClick = {},
                onCardClick = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "4. All Cards List Preview")
@Composable
fun HistoryListPreview() {
    TanawTheme {
        Scaffold { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF8FAFC))
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HistoryBookingCard(sampleInTransitBooking, {}, {}, {}, {})
                HistoryBookingCard(sampleDeliveredBooking, {}, {}, {}, {})
                HistoryBookingCard(sampleCancelledBooking, {}, {}, {}, {})
            }
        }
    }
}

@Preview(
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    name = "5. History List - Dark Mode"
)
@Composable
fun HistoryListDarkPreview() {
    TanawTheme(darkTheme = true) {
        Scaffold { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF10141D))
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HistoryBookingCard(sampleInTransitBooking, {}, {}, {}, {})
                HistoryBookingCard(sampleDeliveredBooking, {}, {}, {}, {})
                HistoryBookingCard(sampleCancelledBooking, {}, {}, {}, {})
            }
        }
    }
}
