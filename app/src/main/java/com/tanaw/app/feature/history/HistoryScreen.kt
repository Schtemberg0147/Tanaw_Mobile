package com.tanaw.app.feature.history

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tanaw.app.data.model.HistoryBooking
import com.tanaw.app.data.model.MockData
import com.tanaw.app.data.model.MockData.computeDistance
import com.tanaw.app.ui.components.BookingStatus
import com.tanaw.app.ui.components.HistoryBookingCard

@Composable
fun HistoryScreen(
    onViewPOD: (String) -> Unit,
    onRebook: (String) -> Unit,
    onViewDetails: (String, BookingStatus) -> Unit
) {
    val completedBookings = remember {
        val pickup = MockData.pickupLocations.first()
        val destGapan = MockData.recentDestinations.firstOrNull { it.name.contains("Gapan", ignoreCase = true) }
            ?: MockData.recentDestinations.first()
        val destTalavera = MockData.recentDestinations.firstOrNull { it.name.contains("Talavera", ignoreCase = true) }
            ?: MockData.recentDestinations.first()
        val vehicleClosed = MockData.vehicles.firstOrNull { it.name.contains("Closed Van", ignoreCase = true) }
            ?: MockData.vehicles.first()
        val dist1 = computeDistance(pickup, destGapan)
        val dist2 = computeDistance(pickup, destTalavera)

        listOf(
            HistoryBooking(
                id = "SHP-NE-7821",
                pickup = pickup,
                destination = destTalavera,
                dateAndVehicle = "Oct 20 · ${vehicleClosed.name}",
                status = BookingStatus.DELIVERED,
                vehicle = vehicleClosed,
                pricePhp = "₱%,d".format(vehicleClosed.computePrice(dist2)),
                podAvailable = true,
                currentLocationText = "Delivered at: ${destTalavera.name}"
            ),
            HistoryBooking(
                id = "SHP-NE-7654",
                pickup = pickup,
                destination = destGapan,
                dateAndVehicle = "Oct 15 · ${vehicleClosed.name}",
                status = BookingStatus.DELIVERED,
                vehicle = vehicleClosed,
                pricePhp = "₱%,d".format(vehicleClosed.computePrice(dist1)),
                podAvailable = true,
                currentLocationText = "Delivered at: ${destGapan.name}"
            )
        )
    }

    val cancelledBookings = remember {
        val pickup = MockData.pickupLocations.first()
        val dest = MockData.recentDestinations.first()
        val vehicle = MockData.vehicles.first()
        listOf(
            HistoryBooking(
                id = "SHP-NE-7990",
                pickup = pickup,
                destination = dest,
                dateAndVehicle = "Oct 22 · ${vehicle.name}",
                status = BookingStatus.CANCELLED,
                vehicle = vehicle,
                pricePhp = "₱0",
                podAvailable = false,
                cancelReason = "Customer no-show",
                currentLocationText = "Cancelled"
            )
        )
    }

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Completed", "Cancelled")

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Booking History",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = "Completed and cancelled bookings", style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray))
        }

        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        when (selectedTab) {
            0 -> CompletedList(
                bookings = completedBookings,
                onViewPOD = onViewPOD,
                onRebook = onRebook,
                onViewDetails = onViewDetails
            )
            1 -> CancelledList(
                bookings = cancelledBookings,
                onRebook = onRebook,
                onViewDetails = onViewDetails
            )
        }
    }
}

@Composable
private fun CompletedList(
    bookings: List<HistoryBooking>,
    onViewPOD: (String) -> Unit,
    onRebook: (String) -> Unit,
    onViewDetails: (String, BookingStatus) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(bookings) { booking ->
            HistoryBookingCard(
                booking = booking,
                showPOD = booking.podAvailable,
                onViewPOD = { onViewPOD(booking.id) },
                onRebook = { onRebook(booking.id) },
                onViewDetails = { onViewDetails(booking.id, booking.status) }
            )
        }
    }
}

@Composable
private fun CancelledList(
    bookings: List<HistoryBooking>,
    onRebook: (String) -> Unit,
    onViewDetails: (String, BookingStatus) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(bookings) { booking ->
            HistoryBookingCard(
                booking = booking,
                showPOD = false,
                onViewPOD = {},
                onRebook = { onRebook(booking.id) },
                onViewDetails = { onViewDetails(booking.id, booking.status) }
            )
        }
    }
}
