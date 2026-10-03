package com.tanaw.app.feature.tracking

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanaw.app.R

data class Booking(
    val id: String,
    val origin: String,
    val destination: String,
    val dateAndVehicle: String,
    val status: String,
    val pricePhp: String
)

private val sampleActiveBookings = listOf(
    Booking("SHP-NE-8063", "Cabanatuan City", "Gapan City", "Oct 24 · 10-Wheeler Wing Van", "IN TRANSIT", "₱3,530"),
    Booking("SHP-NE-8099", "Cabanatuan City", "San Jose City", "Oct 25 · 6-Wheeler Dropside", "DRIVER ASSIGNED", "₱2,100")
)

@Composable
fun ActiveBookingsScreen(
    bookings: List<Booking> = sampleActiveBookings,
    onTrackClick: (String) -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "My Bookings",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Active",
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray)
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(bookings) { booking ->
                    BookingCard(booking = booking, onTrackClick = onTrackClick)
                }
            }
        }
    }
}

@Composable
fun BookingCard(booking: Booking, onTrackClick: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFC)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(id = R.drawable.ic_dot),
                    contentDescription = null,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(36.dp)
                        .background(Color(0xFFE6E6EE))
                )
                Spacer(modifier = Modifier.height(6.dp))
                Image(
                    painter = painterResource(id = R.drawable.ic_dot),
                    contentDescription = null,
                    modifier = Modifier.size(12.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = booking.id, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${booking.origin} → ${booking.destination}",
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF4B5563))
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = booking.dateAndVehicle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Spacer(modifier = Modifier.weight(1f))
                    StatusBadge(status = booking.status)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(text = booking.pricePhp, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { onTrackClick(booking.id) },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("Track →")
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: String) {
    val bg = when {
        status.contains("IN TRANSIT", ignoreCase = true) -> Color(0xFFE6F0FF)
        status.contains("DELIVERED", ignoreCase = true) -> Color(0xFFEFF7EC)
        else -> Color(0xFFFFF7E6)
    }
    val textColor = when {
        status.contains("IN TRANSIT", ignoreCase = true) -> Color(0xFF1E6FFF)
        status.contains("DELIVERED", ignoreCase = true) -> Color(0xFF2E7D32)
        else -> Color(0xFFB36B00)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text = status, color = textColor, style = MaterialTheme.typography.bodySmall)
    }
}
