package com.tanaw.app.feature.workspace.customer.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tanaw.app.feature.workspace.customer.components.NoShowTimelineCard
import com.tanaw.app.feature.workspace.customer.components.StrikeWarningCard
import com.tanaw.app.feature.workspace.customer.rules.CustomerNoShowRules
import com.tanaw.app.feature.workspace.customer.rules.CustomerStandingLevel
import com.tanaw.app.feature.workspace.customer.rules.CustomerStandingSummary
import com.tanaw.app.feature.workspace.customer.rules.NoShowTimelineNodeTone
import com.tanaw.app.feature.workspace.customer.rules.NoShowTimelineStep
import com.tanaw.app.ui.components.TanawSubScreenTopBar
import com.tanaw.app.ui.theme.BackgroundGray
import com.tanaw.app.ui.theme.BorderGray
import com.tanaw.app.ui.theme.ExpiredRed
import com.tanaw.app.ui.theme.ExpiredRedBg
import com.tanaw.app.ui.theme.FormFactorPreviews
import com.tanaw.app.ui.theme.HintGray
import com.tanaw.app.ui.theme.LocalTanawWindowSize
import com.tanaw.app.ui.theme.NavyPrimary
import com.tanaw.app.ui.theme.TanawCustomerTheme
import com.tanaw.app.ui.theme.TanawDisplayFontFamily
import com.tanaw.app.ui.theme.TanawMonoFontFamily
import java.time.Instant

/**
 * Dedicated No-Show Cancellation outcome screen (Screens 3 & 4):
 * Displays the cancellation timeline, Strike 1/3 or Strike 2/3 (+ FINAL WARNING) card,
 * cooldown-gated "Book Again" action, and "Contact Support".
 */
@Composable
internal fun NoShowCancelledScreen(
    trackingNumber: String,
    cancelledTimestampText: String,
    strikeNumber: Int,
    standing: CustomerStandingSummary,
    timelineSteps: List<NoShowTimelineStep>,
    busy: Boolean,
    onBack: () -> Unit,
    onBookAgain: () -> Unit,
    onContactSupport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canBookAgain = !standing.isBookingBlocked && !busy
    val timelineHeader = if (strikeNumber >= 2) "WHY WAS THIS CANCELLED?" else "CANCELLATION TIMELINE"
    val windowSize = LocalTanawWindowSize.current

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("NoShowCancelledScreen"),
        containerColor = BackgroundGray,
        topBar = {
            TanawSubScreenTopBar(
                title = trackingNumber.ifBlank { "Booking Cancelled" },
                onBack = onBack,
                busy = busy
            )
        },
        bottomBar = {
            Surface(
                color = Color.White,
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    HorizontalDivider(color = BorderGray, thickness = 1.dp)
                    Column(
                        modifier = Modifier
                            .widthIn(max = windowSize.formMaxWidth)
                            .fillMaxWidth()
                            .padding(horizontal = windowSize.horizontalGutter, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onBookAgain,
                            enabled = canBookAgain,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NavyPrimary,
                                contentColor = Color.White,
                                disabledContainerColor = BackgroundGray,
                                disabledContentColor = HintGray
                            ),
                            border = if (!canBookAgain) BorderStroke(1.dp, BorderGray) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("NoShowBookAgainButton")
                        ) {
                            Text(
                                text = "Book Again",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (standing.isInCooldown || strikeNumber in 1..2) {
                            Text(
                                text = CustomerNoShowRules.cooldownHelperSentence(strikeNumber, standing),
                                style = MaterialTheme.typography.bodySmall,
                                color = HintGray,
                                textAlign = TextAlign.Center
                            )
                        }

                        TextButton(
                            onClick = onContactSupport,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("NoShowContactSupportButton")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.SupportAgent,
                                contentDescription = null,
                                tint = NavyPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Contact Support",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = NavyPrimary
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Soft red hero header matching Tanaw light palette
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ExpiredRedBg.copy(alpha = 0.45f))
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(ExpiredRed),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Text(
                    text = "Booking Cancelled",
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = TanawDisplayFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    color = NavyPrimary,
                    textAlign = TextAlign.Center
                )
                if (trackingNumber.isNotBlank()) {
                    Text(
                        text = trackingNumber,
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = TanawMonoFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        color = HintGray
                    )
                }
                if (cancelledTimestampText.isNotBlank()) {
                    Text(
                        text = cancelledTimestampText,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = TanawMonoFontFamily,
                        color = HintGray
                    )
                }
            }

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
            Column(
                modifier = Modifier
                    .widthIn(max = windowSize.formMaxWidth)
                    .fillMaxWidth()
                    .padding(horizontal = windowSize.horizontalGutter, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                NoShowTimelineCard(
                    headerLabel = timelineHeader,
                    steps = timelineSteps
                )

                StrikeWarningCard(
                    strikeNumber = strikeNumber,
                    maxStrikes = standing.maxStrikes
                )
            }
            }
        }
    }
}

@FormFactorPreviews
@Composable
private fun NoShowCancelledStrike1Preview() {
    TanawCustomerTheme {
        NoShowCancelledScreen(
            trackingNumber = "SHP-NE-8042",
            cancelledTimestampText = "Nov 24, 2026 • 10:30 AM",
            strikeNumber = 1,
            standing = CustomerStandingSummary(
                strikeCount = 1,
                level = CustomerStandingLevel.COOLDOWN,
                cooldownUntil = Instant.now().plusSeconds(48L * 3600L),
                cooldownRemainingText = "47h 59m remaining",
                cooldownHoursHint = 48
            ),
            timelineSteps = listOf(
                NoShowTimelineStep("Driver arrived at 10:00 AM", "At Cabanatuan City Warehouse", NoShowTimelineNodeTone.SUCCESS),
                NoShowTimelineStep("Grace period ended at 10:15 AM", "15-minute waiting grace elapsed", NoShowTimelineNodeTone.SUCCESS),
                NoShowTimelineStep("Grace period exceeded", "Customer did not check in at pick-up", NoShowTimelineNodeTone.WARNING),
                NoShowTimelineStep("No-show confirmed at 10:30 AM", "Confirmed by driver / office verification", NoShowTimelineNodeTone.DANGER)
            ),
            busy = false,
            onBack = {},
            onBookAgain = {},
            onContactSupport = {}
        )
    }
}

@Preview(name = "Booking Cancelled — Strike 2 of 3 Final Warning (Screen 4)", showBackground = true)
@Composable
private fun NoShowCancelledStrike2Preview() {
    TanawCustomerTheme {
        NoShowCancelledScreen(
            trackingNumber = "SHP-NE-8099",
            cancelledTimestampText = "Dec 15, 2026 • 02:15 PM",
            strikeNumber = 2,
            standing = CustomerStandingSummary(
                strikeCount = 2,
                level = CustomerStandingLevel.COOLDOWN,
                cooldownUntil = Instant.now().plusSeconds(72L * 3600L),
                cooldownRemainingText = "71h 58m remaining",
                cooldownHoursHint = 72
            ),
            timelineSteps = listOf(
                NoShowTimelineStep("Driver arrived at 01:45 PM", "At San Jose Warehouse", NoShowTimelineNodeTone.SUCCESS),
                NoShowTimelineStep("Grace period ended at 02:00 PM", "15-minute waiting grace elapsed", NoShowTimelineNodeTone.SUCCESS),
                NoShowTimelineStep("Grace period exceeded", "Customer did not check in at pick-up", NoShowTimelineNodeTone.WARNING),
                NoShowTimelineStep("No-show confirmed at 02:15 PM", "Attempted contact via phone 3x", NoShowTimelineNodeTone.DANGER)
            ),
            busy = false,
            onBack = {},
            onBookAgain = {},
            onContactSupport = {}
        )
    }
}
