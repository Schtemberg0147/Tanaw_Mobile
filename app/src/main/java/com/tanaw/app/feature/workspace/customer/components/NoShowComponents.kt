package com.tanaw.app.feature.workspace.customer.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanaw.app.feature.workspace.customer.rules.CustomerNoShowRules
import com.tanaw.app.feature.workspace.customer.rules.CustomerStandingSummary
import com.tanaw.app.feature.workspace.customer.rules.NoShowTimelineNodeTone
import com.tanaw.app.feature.workspace.customer.rules.NoShowTimelineStep
import com.tanaw.app.feature.workspace.customer.rules.PickupWaitPhase
import com.tanaw.app.feature.workspace.customer.rules.PickupWaitState
import com.tanaw.app.feature.workspace.tracking.TrackingDriverSummary
import com.tanaw.app.ui.theme.BackgroundGray
import com.tanaw.app.ui.theme.BorderGray
import com.tanaw.app.ui.theme.DestinationOrange
import com.tanaw.app.ui.theme.ExpiredRed
import com.tanaw.app.ui.theme.ExpiredRedBg
import com.tanaw.app.ui.theme.ExpiringOrange
import com.tanaw.app.ui.theme.ExpiringOrangeBg
import com.tanaw.app.ui.theme.HintGray
import com.tanaw.app.ui.theme.InputGray
import com.tanaw.app.ui.theme.NavyPrimary
import com.tanaw.app.ui.theme.OutlineGray
import com.tanaw.app.ui.theme.StatusGreen
import com.tanaw.app.ui.theme.SuccessBannerBg
import com.tanaw.app.ui.theme.SuccessGreen
import com.tanaw.app.ui.theme.TanawCustomerTheme
import com.tanaw.app.ui.theme.TanawDisplayFontFamily
import com.tanaw.app.ui.theme.TanawMonoFontFamily
import com.tanaw.app.ui.theme.TextDark

/**
 * Shared 3-circle strike indicator matching both Mobile strike cards and the Web Admin
 * `STRIKE STATUS` table column: filled crimson circles with white `!` vs hollow slate circles.
 */
@Composable
internal fun StrikeStatusIndicator(
    strikeCount: Int,
    maxStrikes: Int = CustomerNoShowRules.MAX_STRIKES,
    dotSize: Dp = 20.dp,
    spacing: Dp = 6.dp,
    modifier: Modifier = Modifier
) {
    val clamped = strikeCount.coerceIn(0, maxStrikes)
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "Strike $clamped of $maxStrikes"
        },
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (index in 1..maxStrikes) {
            val isFilled = index <= clamped
            if (isFilled) {
                Box(
                    modifier = Modifier
                        .size(dotSize)
                        .clip(CircleShape)
                        .background(ExpiredRed),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PriorityHigh,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(dotSize * 0.68f)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(dotSize)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(1.5.dp, OutlineGray, CircleShape)
                )
            }
        }
    }
}

/**
 * Customer tracking sheet panel shown when the driver has waited past the pickup grace period
 * (Screen 1: Grace Period Ended) or entered the final 5-minute cancellation risk window
 * (Screen 2: Booking At Risk).
 */
@Composable
internal fun PickupWaitRiskPanel(
    waitState: PickupWaitState,
    driverSummary: TrackingDriverSummary,
    busy: Boolean,
    offline: Boolean,
    onNotifyOnWay: () -> Unit,
    onCallDriver: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAtRisk = waitState.phase == PickupWaitPhase.AT_RISK ||
        waitState.phase == PickupWaitPhase.EXPIRED_WAITING_CONFIRMATION

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Status pill
        Surface(
            shape = CircleShape,
            color = if (isAtRisk) ExpiredRed else ExpiringOrange,
            modifier = Modifier.testTag(if (isAtRisk) "BookingAtRiskPill" else "GraceEndedPill")
        ) {
            Text(
                text = when (waitState.phase) {
                    PickupWaitPhase.GRACE_ENDED -> "WAITING — GRACE PERIOD ENDED"
                    PickupWaitPhase.AT_RISK -> "BOOKING AT RISK"
                    PickupWaitPhase.EXPIRED_WAITING_CONFIRMATION -> "BOOKING AT RISK — TIME ELAPSED"
                    else -> "WAITING AT PICK-UP"
                },
                style = MaterialTheme.typography.labelSmall,
                fontFamily = TanawMonoFontFamily,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }

        // Warning / Critical notice box
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isAtRisk) ExpiredRedBg else ExpiringOrangeBg,
            border = BorderStroke(
                width = 1.dp,
                color = if (isAtRisk) ExpiredRed.copy(alpha = 0.28f) else ExpiringOrange.copy(alpha = 0.28f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = if (isAtRisk) Icons.Outlined.NotificationsActive else Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = if (isAtRisk) ExpiredRed else ExpiringOrange,
                    modifier = Modifier.size(20.dp)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = when (waitState.phase) {
                            PickupWaitPhase.GRACE_ENDED -> "Grace period has ended."
                            PickupWaitPhase.AT_RISK -> {
                                val minsLeft = ((waitState.remainingBeforeCancelSeconds + 59L) / 60L).coerceIn(1L, 5L)
                                if (minsLeft == 1L) "Your booking will be cancelled in under 1 minute"
                                else "Your booking will be cancelled in $minsLeft minutes"
                            }
                            PickupWaitPhase.EXPIRED_WAITING_CONFIRMATION -> "Wait limit reached — cancellation pending."
                            else -> "Driver is waiting at pick-up."
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = TanawDisplayFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = if (isAtRisk) ExpiredRed else ExpiringOrange
                    )
                    Text(
                        text = if (isAtRisk) {
                            "Please proceed to the pick-up location immediately or contact your driver."
                        } else {
                            "Your driver or the office may cancel your booking as a no-show once the wait limit is reached."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isAtRisk) ExpiredRed else ExpiringOrange
                    )
                }
            }
        }

        // Timer card
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (isAtRisk) ExpiredRedBg.copy(alpha = 0.65f) else ExpiringOrangeBg.copy(alpha = 0.65f),
            border = BorderStroke(
                width = 1.dp,
                color = if (isAtRisk) ExpiredRed.copy(alpha = 0.25f) else ExpiringOrange.copy(alpha = 0.25f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (isAtRisk) "TIME REMAINING" else "Time Over Grace Period",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isAtRisk) ExpiredRed else ExpiringOrange
                )
                Text(
                    text = if (isAtRisk) {
                        CustomerNoShowRules.formatMmSs(waitState.remainingBeforeCancelSeconds)
                    } else {
                        CustomerNoShowRules.formatMmSs(waitState.elapsedOverGraceSeconds)
                    },
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontFamily = TanawMonoFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 34.sp,
                        lineHeight = 40.sp,
                        fontFeatureSettings = "tnum"
                    ),
                    color = if (isAtRisk) ExpiredRed else ExpiringOrange,
                    modifier = Modifier.testTag("PickupWaitTimerText")
                )
                LinearProgressIndicator(
                    progress = {
                        if (isAtRisk) waitState.atRiskProgress else waitState.graceOverProgress
                    },
                    color = if (isAtRisk) ExpiredRed else DestinationOrange,
                    trackColor = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                )
                Text(
                    text = if (isAtRisk) {
                        "Booking may be cancelled at 00:00"
                    } else {
                        "Booking will be cancelled after ${waitState.totalWaitMinutes} minutes total wait time."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isAtRisk) ExpiredRed else ExpiringOrange,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Primary recovery action per phase (Hick's Law: one clear action)
        if (!isAtRisk) {
            if (waitState.hasNotifiedOnWay) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SuccessBannerBg,
                    border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        val notifiedClock = CustomerNoShowRules.formatClock(waitState.customerOnWayAt)
                        Text(
                            text = if (notifiedClock.isNotBlank()) {
                                "Driver notified that you're on your way ($notifiedClock)."
                            } else {
                                "Driver notified that you're on your way."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = SuccessGreen
                        )
                    }
                }
            } else {
                OutlinedButton(
                    onClick = onNotifyOnWay,
                    enabled = !busy && !offline,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.5.dp, NavyPrimary),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White,
                        contentColor = NavyPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("NotifyDriverOnWayButton")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DirectionsRun,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (busy) "Notifying driver…" else "I'm On My Way — Notify Driver",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            // Screen 2 driver call card + Arrived at Location indicator
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = BackgroundGray,
                border = BorderStroke(1.dp, BorderGray),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(NavyPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = driverSummary.initials,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = driverSummary.driverName,
                            style = MaterialTheme.typography.titleSmall,
                            fontFamily = TanawDisplayFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = TextDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = driverSummary.vehicleSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = TanawMonoFontFamily,
                            color = HintGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (!driverSummary.phoneNumber.isNullOrBlank()) {
                        Surface(
                            onClick = onCallDriver,
                            shape = RoundedCornerShape(10.dp),
                            color = NavyPrimary,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("AtRiskCallDriverButton")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Call,
                                    contentDescription = "Call driver ${driverSummary.driverName}",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = SuccessGreen,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (waitState.arrivedTimeText.isNotBlank()) {
                        "Driver arrived at location · ${waitState.arrivedTimeText}"
                    } else {
                        "Arrived at location"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextDark
                )
            }
        }
    }
}

/**
 * Connected 4-node vertical timeline explaining how a no-show cancellation occurred
 * (used on Screens 3 and 4).
 */
@Composable
internal fun NoShowTimelineCard(
    headerLabel: String,
    steps: List<NoShowTimelineStep>,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, BorderGray)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = headerLabel.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = TanawMonoFontFamily,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                color = HintGray
            )

            Column(modifier = Modifier.fillMaxWidth()) {
                steps.forEachIndexed { index, step ->
                    val isLast = index == steps.lastIndex
                    val nodeColor = when (step.tone) {
                        NoShowTimelineNodeTone.SUCCESS -> StatusGreen
                        NoShowTimelineNodeTone.WARNING -> DestinationOrange
                        NoShowTimelineNodeTone.DANGER -> ExpiredRed
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(18.dp)
                        ) {
                            if (step.tone == NoShowTimelineNodeTone.DANGER) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(ExpiredRed),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(nodeColor)
                                )
                            }
                            if (!isLast) {
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .padding(vertical = 4.dp)
                                        .background(BorderGray)
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(bottom = if (isLast) 0.dp else 14.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = step.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (step.tone == NoShowTimelineNodeTone.DANGER) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (step.tone == NoShowTimelineNodeTone.DANGER) ExpiredRed else TextDark
                            )
                            if (step.subtitle.isNotBlank()) {
                                Text(
                                    text = step.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = HintGray
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Strike 1 of 3 / Strike 2 of 3 (with nested FINAL WARNING) notice card.
 */
@Composable
internal fun StrikeWarningCard(
    strikeNumber: Int,
    maxStrikes: Int = CustomerNoShowRules.MAX_STRIKES,
    modifier: Modifier = Modifier
) {
    val displayStrike = strikeNumber.coerceIn(1, maxStrikes)
    val isFinalWarning = displayStrike == maxStrikes - 1

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = ExpiringOrangeBg,
        border = BorderStroke(1.dp, ExpiringOrange.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StrikeStatusIndicator(
                strikeCount = displayStrike,
                maxStrikes = maxStrikes
            )
            Text(
                text = "Strike $displayStrike of $maxStrikes",
                style = MaterialTheme.typography.titleLarge,
                fontFamily = TanawDisplayFontFamily,
                fontWeight = FontWeight.ExtraBold,
                color = NavyPrimary
            )
            Text(
                text = if (isFinalWarning) {
                    "You have reached 2 confirmed no-show strikes. Your booking privilege is paused for 72 hours."
                } else {
                    "A confirmed no-show adds a strike to your account. Booking is temporarily paused for 48 hours."
                },
                style = MaterialTheme.typography.bodySmall,
                color = ExpiringOrange
            )

            if (isFinalWarning) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ExpiredRedBg,
                    border = BorderStroke(1.dp, ExpiredRed.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = ExpiredRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "FINAL WARNING",
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = TanawMonoFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                color = ExpiredRed
                            )
                            Text(
                                text = "One more confirmed no-show (3 of 3) will result in permanent account suspension.",
                                style = MaterialTheme.typography.bodySmall,
                                color = ExpiredRed
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Prominent cooldown banner displayed on CustomerBookingHomeScreen when the customer is
 * within a 48h (Strike 1) or 72h (Strike 2) post-no-show booking pause.
 */
@Composable
internal fun BookingCooldownBanner(
    standing: CustomerStandingSummary,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("BookingCooldownBanner"),
        shape = RoundedCornerShape(14.dp),
        color = ExpiringOrangeBg,
        border = BorderStroke(1.dp, ExpiringOrange.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = ExpiringOrange,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Booking Paused · Strike ${standing.displayStrikesClamped} of ${standing.maxStrikes}",
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = TanawDisplayFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = ExpiringOrange
                    )
                }
                StrikeStatusIndicator(
                    strikeCount = standing.displayStrikesClamped,
                    maxStrikes = standing.maxStrikes,
                    dotSize = 16.dp,
                    spacing = 4.dp
                )
            }
            Text(
                text = buildString {
                    append("New bookings are paused due to a confirmed no-show.")
                    if (standing.cooldownRemainingText.isNotBlank()) {
                        append(" ${standing.cooldownRemainingText}.")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = ExpiringOrange
            )
        }
    }
}

/**
 * Account Standing summary card shown on CustomerProfileScreen when strikeCount > 0.
 */
@Composable
internal fun CustomerStandingCard(
    standing: CustomerStandingSummary,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, BorderGray)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Account Standing",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = TanawDisplayFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = NavyPrimary
                    )
                    Text(
                        text = "No-show strike status (${standing.strikeCount}/${standing.maxStrikes})",
                        style = MaterialTheme.typography.bodySmall,
                        color = HintGray
                    )
                }
                StrikeStatusIndicator(
                    strikeCount = standing.displayStrikesClamped,
                    maxStrikes = standing.maxStrikes
                )
            }

            if (standing.isInCooldown) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ExpiringOrangeBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Booking cooldown active · ${standing.cooldownRemainingText}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = ExpiringOrange,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            } else if (standing.isReinstated) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = InputGray,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Account reinstated. Prior strikes remain on file; an additional no-show triggers permanent review.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HintGray,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Preview(name = "Pickup Wait — Grace Ended (Screen 1)", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun PickupWaitGraceEndedPreview() {
    TanawCustomerTheme {
        Surface(color = Color.White, modifier = Modifier.padding(vertical = 16.dp)) {
            PickupWaitRiskPanel(
                waitState = PickupWaitState(
                    phase = PickupWaitPhase.GRACE_ENDED,
                    deliveryId = "del-1",
                    totalWaitMinutes = 30L,
                    elapsedOverGraceSeconds = 9L,
                    graceOverProgress = 0.08f
                ),
                driverSummary = TrackingDriverSummary(
                    driverName = "Rico M. Blanco",
                    initials = "RB",
                    vehicleSubtitle = "Isuzu Giga • ABC-1234",
                    phoneNumber = "+639171234567",
                    hasDriverAssigned = true
                ),
                busy = false,
                offline = false,
                onNotifyOnWay = {},
                onCallDriver = {}
            )
        }
    }
}

@Preview(name = "Pickup Wait — Booking At Risk (Screen 2)", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun PickupWaitAtRiskPreview() {
    TanawCustomerTheme {
        Surface(color = Color.White, modifier = Modifier.padding(vertical = 16.dp)) {
            PickupWaitRiskPanel(
                waitState = PickupWaitState(
                    phase = PickupWaitPhase.AT_RISK,
                    deliveryId = "del-1",
                    totalWaitMinutes = 30L,
                    remainingBeforeCancelSeconds = 296L,
                    atRiskProgress = 0.98f,
                    arrivedTimeText = "10:00 AM"
                ),
                driverSummary = TrackingDriverSummary(
                    driverName = "Rico M. Blanco",
                    initials = "RB",
                    vehicleSubtitle = "Isuzu Giga • ABC-1234",
                    phoneNumber = "+639171234567",
                    hasDriverAssigned = true
                ),
                busy = false,
                offline = false,
                onNotifyOnWay = {},
                onCallDriver = {}
            )
        }
    }
}
