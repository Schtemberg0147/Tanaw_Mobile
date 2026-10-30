package com.tanaw.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanaw.app.R
import com.tanaw.app.domain.model.BookingCardStyle
import com.tanaw.app.domain.model.BookingItemUi
import com.tanaw.app.domain.model.BookingStatusEnum
import com.tanaw.app.ui.theme.NavyBanner

@Composable
fun StatusBadge(
    style: BookingCardStyle,
    modifier: Modifier = Modifier
) {
    Surface(
        color = style.chipBackgroundColor,
        shape = RoundedCornerShape(4.dp),
        tonalElevation = 0.dp,
        modifier = modifier.testTag("status_badge_${style.statusLabel}")
    ) {
        Text(
            text = style.statusLabel,
            color = style.chipTextColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun HistoryBookingCard(
    booking: BookingItemUi,
    onTrackClick: () -> Unit,
    onViewPODClick: () -> Unit,
    onRebookClick: () -> Unit,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val style = booking.cardStyle
    val contentAlpha = if (style.isMuted) 0.65f else 1.0f

    val cardContentDesc = "${booking.id}, status ${style.statusLabel}, pickup ${booking.pickupCity}, destination ${booking.destinationCity}, price ${booking.formattedPrice}"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCardClick() }
            .semantics { contentDescription = cardContentDesc }
            .testTag("booking_card_${booking.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (style.isMuted) Color(0xFFF8FAFC) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (style.isMuted) 1.dp else 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .alpha(contentAlpha)
        ) {
            // Header Row: Shipment Code & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = booking.id,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = if (style.isMuted) Color(0xFF64748B) else NavyBanner,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                StatusBadge(style = style)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Two-point Route Timeline
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Route Canvas Dots & Line
                Canvas(
                    modifier = Modifier
                        .width(16.dp)
                        .height(48.dp)
                ) {
                    val radius = 4.5.dp.toPx()
                    val startY = 8.dp.toPx()
                    val endY = 40.dp.toPx()
                    val centerX = size.width / 2f

                    // Vertical Connecting Line
                    drawLine(
                        color = style.timelineLineColor,
                        start = Offset(centerX, startY),
                        end = Offset(centerX, endY),
                        strokeWidth = 2.dp.toPx()
                    )

                    // Top Pickup Dot
                    drawCircle(
                        color = style.pickupDotColor,
                        radius = radius,
                        center = Offset(centerX, startY)
                    )

                    // Bottom Destination Dot
                    drawCircle(
                        color = style.dropoffDotColor,
                        radius = radius,
                        center = Offset(centerX, endY)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = booking.pickupCity,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (style.isMuted) Color(0xFF64748B) else NavyBanner,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = booking.destinationCity,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (style.isMuted) Color(0xFF64748B) else NavyBanner,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Footer Section: Date & Vehicle, Price, Actions
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = booking.formattedDateAndVehicle,
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = booking.formattedPrice,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (style.isMuted) Color(0xFF94A3B8) else NavyBanner,
                    textDecoration = if (style.isPriceStrikethrough) TextDecoration.LineThrough else TextDecoration.None,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Action Buttons based on Status
                when (booking.status) {
                    BookingStatusEnum.BOOKED, BookingStatusEnum.ASSIGNED, BookingStatusEnum.IN_TRANSIT -> {
                        TextButton(
                            onClick = onTrackClick,
                            modifier = Modifier.testTag("action_track_${booking.id}")
                        ) {
                            Text(
                                text = "Track →",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = NavyBanner
                            )
                        }
                    }

                    BookingStatusEnum.DELIVERED -> {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (booking.hasPod) {
                                TextButton(
                                    onClick = onViewPODClick,
                                    modifier = Modifier.testTag("action_pod_${booking.id}")
                                ) {
                                    Text(
                                        text = "View POD",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NavyBanner
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                            }

                            TextButton(
                                onClick = onRebookClick,
                                modifier = Modifier.testTag("action_rebook_${booking.id}")
                            ) {
                                Text(
                                    text = "Re-book",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyBanner
                                )
                            }
                        }
                    }

                    BookingStatusEnum.CANCELLED -> {
                        TextButton(
                            onClick = onRebookClick,
                            modifier = Modifier.testTag("action_rebook_${booking.id}")
                        ) {
                            Text(
                                text = "Re-book",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = NavyBanner
                            )
                        }
                    }
                }
            }
        }
    }
}
