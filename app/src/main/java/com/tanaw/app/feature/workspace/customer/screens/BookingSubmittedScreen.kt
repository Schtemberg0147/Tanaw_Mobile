package com.tanaw.app.feature.workspace.customer.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanaw.app.R
import com.tanaw.app.feature.workspace.customer.components.TimelineRow
import com.tanaw.app.ui.theme.FormFactorPreviews
import com.tanaw.app.ui.theme.LocalTanawWindowSize
import com.tanaw.app.ui.theme.StatusGreen
import com.tanaw.app.ui.theme.StatusGreenBg
import com.tanaw.app.ui.theme.TanawCustomerTheme
import com.tanaw.app.ui.theme.TanawDisplayFontFamily
import com.tanaw.app.ui.theme.TanawMonoFontFamily

// ─── Booking Submitted! Confirmation Screen (Image 1, Screen 4) ───────────────
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookingSubmittedScreen(
    trackingNumber: String,
    pickup: String,
    dropoff: String,
    vehicleName: String,
    scheduleSummary: String,
    onTrackShipment: () -> Unit,
    onBookAnother: () -> Unit
) {
    val greenAccent = StatusGreen
    val greenBg = StatusGreenBg
    val windowSize = LocalTanawWindowSize.current

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = windowSize.formMaxWidth)
                    .fillMaxWidth()
                    .padding(horizontal = windowSize.horizontalGutter, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
            // Green Checkmark Box
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = greenBg,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = greenAccent,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            Text(
                text = stringResource(R.string.booking_submitted_title),
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = TanawDisplayFontFamily,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = stringResource(R.string.booking_submitted_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // Booking Reference Card with top green accent bar
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 2.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .background(greenAccent)
                    )
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.booking_reference_label),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = TanawMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.7.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                        Text(
                            text = trackingNumber,
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = TanawMonoFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Text(
                                text = pickup,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.tertiary)
                            )
                            Text(
                                text = dropoff,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(modifier = Modifier.widthIn(min = 96.dp, max = 180.dp)) {
                                Text(
                                    text = stringResource(R.string.vehicle_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = TanawMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 9.sp
                                )
                                Text(
                                    text = vehicleName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Column(modifier = Modifier.widthIn(min = 96.dp, max = 180.dp)) {
                                Text(
                                    text = stringResource(R.string.schedule_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = TanawMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 9.sp
                                )
                                Text(
                                    text = scheduleSummary,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = TanawMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Column(
                                modifier = Modifier.widthIn(min = 96.dp, max = 180.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.total_fee_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = TanawMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 9.sp
                                )
                                Text(
                                    text = "Pending quote",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // 3-Step Status Timeline Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    TimelineRow(
                        dotColor = greenAccent,
                        title = stringResource(R.string.timeline_booking_submitted),
                        subtitle = stringResource(R.string.timeline_just_now),
                        active = true
                    )
                    TimelineRow(
                        dotColor = MaterialTheme.colorScheme.secondary,
                        title = stringResource(R.string.timeline_pending_admin_review),
                        subtitle = stringResource(R.string.timeline_office_quotation),
                        active = true
                    )
                    TimelineRow(
                        dotColor = MaterialTheme.colorScheme.outline,
                        title = stringResource(R.string.timeline_driver_assigned),
                        subtitle = stringResource(R.string.timeline_youll_be_notified),
                        active = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = onTrackShipment,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .testTag("TrackShipmentButton")
            ) {
                Text(
                    text = stringResource(R.string.track_shipment_button),
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                onClick = onBookAnother,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .testTag("BookAnotherButton")
            ) {
                Text(
                    text = stringResource(R.string.book_another_delivery_button),
                    fontWeight = FontWeight.Bold
                )
            }
            }
        }
    }
}

@FormFactorPreviews
@Composable
private fun BookingSubmittedPreview() {
    TanawCustomerTheme {
        BookingSubmittedScreen(
            trackingNumber = "TNW-2026-0847",
            pickup = "Cabanatuan City Public Market",
            dropoff = "Manila North Harbor, Manila",
            vehicleName = "Closed Van",
            scheduleSummary = "Today, 2:30 PM",
            onTrackShipment = {},
            onBookAnother = {}
        )
    }
}
