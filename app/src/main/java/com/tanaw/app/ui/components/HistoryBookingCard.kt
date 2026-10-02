package com.tanaw.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanaw.app.data.model.HistoryBooking

@Composable
fun StatusBadge(status: BookingStatus) {
    val (bg, textColor) = when (status) {
        BookingStatus.DELIVERED -> Pair(Color(0xFFDFF7E2), Color(0xFF0A8A3A))
        BookingStatus.CANCELLED -> Pair(Color(0xFFFDECEA), Color(0xFFB00020))
        else -> Pair(Color(0xFFFFF7E6), Color(0xFFB36B00))
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 0.dp,
        modifier = Modifier.padding(start = 4.dp)
    ) {
        Text(
            text = status.name.replace('_', ' '),
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun HistoryBookingCard(
    booking: HistoryBooking,
    showPOD: Boolean,
    onViewPOD: () -> Unit,
    onRebook: () -> Unit,
    onViewDetails: () -> Unit
) {
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
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(36.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(36.dp)
                        .background(Color(0xFFE6E6EE))
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Text(
                    text = booking.id,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${booking.pickup.name} → ${booking.destination.name}",
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF4B5563)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = booking.dateAndVehicle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Vehicle: ${booking.vehicle.name}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = booking.currentLocationText,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF4B5563),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))
                StatusBadge(status = booking.status)
                booking.cancelReason?.let { reason ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Reason: $reason",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF8B0000),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.widthIn(min = 88.dp, max = 120.dp)
            ) {
                Text(text = booking.pricePhp, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))

                if (showPOD) {
                    OutlinedButton(
                        onClick = onViewPOD,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text("View POD")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onRebook,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Re-book")
                    }
                } else {
                    Button(
                        onClick = onRebook,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Re-book")
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                TextButton(onClick = onViewDetails) {
                    Text("Details", color = Color.Gray)
                }
            }
        }
    }
}
