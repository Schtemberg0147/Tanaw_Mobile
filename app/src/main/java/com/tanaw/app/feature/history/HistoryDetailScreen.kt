package com.tanaw.app.feature.history

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanaw.app.R
import com.tanaw.app.data.model.HistoryBooking
import com.tanaw.app.data.model.MockData
import com.tanaw.app.data.model.MockData.computeDistance
import com.tanaw.app.ui.components.BookingStatus
import com.tanaw.app.ui.components.TimelineStepWithLine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDetailScreen(
    bookingId: String,
    onBack: () -> Unit
) {
    val all = buildList {
        addAll(buildCompletedSample())
        addAll(buildCancelledSample())
    }
    val booking = all.firstOrNull { it.id == bookingId } ?: all.first()

    val timeline = listOf(
        BookingStatus.DRIVER_ASSIGNED,
        BookingStatus.ROUTE_TO_PICKUP,
        BookingStatus.WAITING_FOR_CUSTOMER,
        BookingStatus.CUSTOMER_ARRIVED,
        BookingStatus.CARGO_LOADED,
        BookingStatus.ARRIVING_AT_DESTINATION,
        BookingStatus.DELIVERED
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Booking Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painter = painterResource(id = R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.map_mock),
                contentDescription = "POD or route snapshot",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = booking.id, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = booking.vehicle.name, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = booking.dateAndVehicle, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = booking.currentLocationText, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = booking.status.name.replace('_', ' '), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(text = booking.pricePhp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Timeline", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            val statusIndex = when (booking.status) {
                BookingStatus.DELIVERED -> timeline.lastIndex
                BookingStatus.CANCELLED -> -1
                else -> timeline.indexOf(booking.status)
            }

            timeline.forEachIndexed { index, step ->
                val completed = statusIndex >= 0 && index <= statusIndex
                val showTimer = step == BookingStatus.WAITING_FOR_CUSTOMER && !completed
                TimelineStepWithLine(
                    label = step.name.replace('_', ' '),
                    completed = completed,
                    showTimer = showTimer
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedButton(onClick = { }, modifier = Modifier.fillMaxWidth()) {
                Text("View Proof of Delivery")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { }, modifier = Modifier.fillMaxWidth()) {
                Text("Re-book")
            }
        }
    }
}

private fun buildCompletedSample(): List<HistoryBooking> {
    val pickup = MockData.pickupLocations.first()
    val destGapan = MockData.recentDestinations.firstOrNull { it.name.contains("Gapan", ignoreCase = true) }
        ?: MockData.recentDestinations.first()
    val destTalavera = MockData.recentDestinations.firstOrNull { it.name.contains("Talavera", ignoreCase = true) }
        ?: MockData.recentDestinations.first()
    val vehicleClosed = MockData.vehicles.firstOrNull { it.name.contains("Closed Van", ignoreCase = true) }
        ?: MockData.vehicles.first()
    val dist1 = computeDistance(pickup, destGapan)
    val dist2 = computeDistance(pickup, destTalavera)

    return listOf(
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

private fun buildCancelledSample(): List<HistoryBooking> {
    val pickup = MockData.pickupLocations.first()
    val dest = MockData.recentDestinations.first()
    val vehicle = MockData.vehicles.first()
    return listOf(
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
