package com.tanaw.app.feature.workspace.customer.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.sp
import com.tanaw.app.feature.workspace.customer.components.TanawBrandLogo
import com.tanaw.app.feature.workspace.customer.rules.CustomerAppealRecord
import com.tanaw.app.feature.workspace.customer.rules.CustomerStandingLevel
import com.tanaw.app.feature.workspace.customer.rules.CustomerStandingSummary
import com.tanaw.app.feature.workspace.customer.rules.CustomerStrikeRecord
import com.tanaw.app.ui.components.KeyValueRow
import com.tanaw.app.ui.theme.BackgroundGray
import com.tanaw.app.ui.theme.BorderGray
import com.tanaw.app.ui.theme.ExpiredRed
import com.tanaw.app.ui.theme.ExpiredRedBg
import com.tanaw.app.ui.theme.ExpiringOrange
import com.tanaw.app.ui.theme.ExpiringOrangeBg
import com.tanaw.app.ui.theme.FormFactorPreviews
import com.tanaw.app.ui.theme.HintGray
import com.tanaw.app.ui.theme.LocalTanawWindowSize
import com.tanaw.app.ui.theme.NavyPrimary
import com.tanaw.app.ui.theme.SuccessBannerBg
import com.tanaw.app.ui.theme.SuccessGreen
import com.tanaw.app.ui.theme.TanawCustomerTheme
import com.tanaw.app.ui.theme.TanawDisplayFontFamily
import com.tanaw.app.ui.theme.TanawMonoFontFamily
import com.tanaw.app.ui.theme.TextDark

/**
 * Full-screen Permanent Suspension Gate (Screen 5):
 * Shown when a customer reaches 3 confirmed no-show strikes (or post-reinstatement review).
 * Displays suspension metadata, chronological Strike History, in-app Appeal submission trigger,
 * policy notice, status refresh, and Sign Out.
 */
@Composable
internal fun AccountSuspendedScreen(
    standing: CustomerStandingSummary,
    busy: Boolean,
    offline: Boolean,
    statusMessage: String?,
    errorMessage: String?,
    onOpenAppealForm: () -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val latestAppeal = standing.latestAppeal
    val hasPendingAppeal = latestAppeal?.isPending == true
    val windowSize = LocalTanawWindowSize.current

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("AccountSuspendedScreen"),
        containerColor = BackgroundGray,
        topBar = {
            Surface(
                color = Color.White,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TanawBrandLogo(modifier = Modifier.size(30.dp))
                            Text(
                                text = "TANAW",
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = TanawDisplayFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                color = NavyPrimary
                            )
                        }
                        IconButton(
                            onClick = onRefresh,
                            enabled = !busy,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "Check account status",
                                tint = NavyPrimary
                            )
                        }
                    }
                    if (busy) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp),
                            color = NavyPrimary,
                            trackColor = BorderGray
                        )
                    } else {
                        HorizontalDivider(color = BorderGray, thickness = 1.dp)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            // Hero header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ExpiredRedBg.copy(alpha = 0.45f))
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    shadowElevation = 2.dp,
                    border = BorderStroke(1.dp, BorderGray),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = ExpiredRed,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Text(
                    text = "Account Suspended",
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = TanawDisplayFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    color = NavyPrimary,
                    textAlign = TextAlign.Center
                )

                Surface(
                    shape = CircleShape,
                    color = ExpiredRed
                ) {
                    Text(
                        text = "PERMANENT SUSPENSION",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = TanawMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                    )
                }

                if (standing.formattedSuspendedDate.isNotBlank()) {
                    Text(
                        text = "Effective ${standing.formattedSuspendedDate}",
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
                if (offline) {
                    Surface(
                        color = ExpiringOrangeBg,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ExpiringOrange.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "You're offline. Reconnect to submit an appeal or check for reinstatement.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ExpiringOrange,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                if (!statusMessage.isNullOrBlank()) {
                    Surface(
                        color = SuccessBannerBg,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = SuccessGreen,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                if (!errorMessage.isNullOrBlank()) {
                    Surface(
                        color = ExpiredRedBg,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ExpiredRed.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = ExpiredRed,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // Suspension Details + Strike History Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, BorderGray),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Suspension Details",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = TanawDisplayFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = NavyPrimary
                        )

                        KeyValueRow(
                            label = "Suspended Date",
                            value = standing.formattedSuspendedDate.ifBlank { "Recorded by office" },
                            boldValue = true,
                            valueFontFamily = TanawMonoFontFamily
                        )
                        KeyValueRow(
                            label = "Reason",
                            value = standing.suspensionReason,
                            boldValue = true,
                            valueColor = TextDark
                        )

                        HorizontalDivider(color = BorderGray)

                        Text(
                            text = "STRIKE HISTORY",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = TanawMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp,
                            color = HintGray
                        )

                        if (standing.strikes.isEmpty()) {
                            Text(
                                text = "${standing.strikeCount} confirmed no-show strikes recorded on this account.",
                                style = MaterialTheme.typography.bodySmall,
                                color = HintGray
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                standing.strikes.forEach { strike ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(ExpiredRed)
                                        )
                                        Text(
                                            text = buildString {
                                                append("Strike ${strike.strikeNumber}")
                                                if (strike.formattedDate.isNotBlank()) {
                                                    append(" — ${strike.formattedDate}")
                                                }
                                                if (strike.trackingNumber.isNotBlank()) {
                                                    append(" (${strike.trackingNumber})")
                                                }
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = TanawMonoFontFamily,
                                            color = TextDark
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(2.dp))

                        LinearProgressIndicator(
                            progress = { 1f },
                            color = ExpiredRed,
                            trackColor = ExpiredRedBg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(CircleShape)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "STRIKE STATUS",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = TanawMonoFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                color = HintGray
                            )
                            Text(
                                text = "CRITICAL (${standing.strikeCount}/${standing.maxStrikes})",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = TanawMonoFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                color = ExpiredRed
                            )
                        }
                    }
                }

                // What You Can Do Card (In-App Appeal)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, BorderGray),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "What You Can Do",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = TanawDisplayFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = NavyPrimary
                        )

                        if (hasPendingAppeal) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = SuccessBannerBg,
                                border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.25f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("PendingAppealBanner")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CheckCircle,
                                        contentDescription = null,
                                        tint = SuccessGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = "Appeal Submitted — Under Review",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = SuccessGreen
                                        )
                                        Text(
                                            text = buildString {
                                                if (latestAppeal.formattedCreatedAt.isNotBlank()) {
                                                    append("Submitted ${latestAppeal.formattedCreatedAt}. ")
                                                }
                                                append("Appeals are reviewed within 3–5 business days.")
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SuccessGreen
                                        )
                                    }
                                }
                            }
                        } else {
                            if (latestAppeal?.isRejected == true) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = ExpiredRedBg,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = latestAppeal.adminNotes.takeIf { it.isNotBlank() }
                                            ?.let { "Previous appeal declined: $it" }
                                            ?: "Your previous appeal was declined. You may submit additional context below.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ExpiredRed,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = onOpenAppealForm,
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
                                    .testTag("OpenAppealFormButton")
                            ) {
                                Text(
                                    text = "Submit Suspension Appeal",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                text = "Appeals are reviewed within 3–5 business days.",
                                style = MaterialTheme.typography.bodySmall,
                                color = HintGray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Policy Footnote Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, BorderGray),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = HintGray,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Strikes do not reset after suspension. A 4th no-show after reinstatement will result in permanent account review by the administration.",
                            style = MaterialTheme.typography.bodySmall,
                            color = HintGray,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Sign Out escape hatch (User Control & Freedom)
                OutlinedButton(
                    onClick = onLogout,
                    enabled = !busy,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.5.dp, ExpiredRed),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White,
                        contentColor = ExpiredRed
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("SuspendedSignOutButton")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ExitToApp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Sign Out",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            }
        }
    }
}

@FormFactorPreviews
@Composable
private fun AccountSuspendedScreenPreview() {
    TanawCustomerTheme {
        AccountSuspendedScreen(
            standing = CustomerStandingSummary(
                strikeCount = 3,
                level = CustomerStandingLevel.SUSPENDED,
                formattedSuspendedDate = "Nov 24, 2026",
                suspensionReason = "3 confirmed no-shows",
                strikes = listOf(
                    CustomerStrikeRecord("1", 1, "s1", "SHP-NE-8011", "", "Oct 10, 2026", "No-show", ""),
                    CustomerStrikeRecord("2", 2, "s2", "SHP-NE-8035", "", "Nov 1, 2026", "No-show", ""),
                    CustomerStrikeRecord("3", 3, "s3", "SHP-NE-8042", "", "Nov 24, 2026", "No-show", "")
                )
            ),
            busy = false,
            offline = false,
            statusMessage = null,
            errorMessage = null,
            onOpenAppealForm = {},
            onRefresh = {},
            onLogout = {}
        )
    }
}

@Preview(name = "Account Suspended — Pending Appeal", showBackground = true)
@Composable
private fun AccountSuspendedPendingAppealPreview() {
    TanawCustomerTheme {
        AccountSuspendedScreen(
            standing = CustomerStandingSummary(
                strikeCount = 3,
                level = CustomerStandingLevel.SUSPENDED,
                formattedSuspendedDate = "Nov 24, 2026",
                suspensionReason = "3 confirmed no-shows",
                strikes = listOf(
                    CustomerStrikeRecord("1", 1, "s1", "SHP-NE-8011", "", "Oct 10, 2026", "No-show", ""),
                    CustomerStrikeRecord("2", 2, "s2", "SHP-NE-8035", "", "Nov 1, 2026", "No-show", ""),
                    CustomerStrikeRecord("3", 3, "s3", "SHP-NE-8042", "", "Nov 24, 2026", "No-show", "")
                ),
                latestAppeal = CustomerAppealRecord(
                    id = "app-1",
                    status = "pending",
                    reason = "Warehouse gate was delayed by emergency roadwork.",
                    adminNotes = "",
                    createdAt = "2026-11-24T04:00:00Z",
                    formattedCreatedAt = "Nov 24, 2026",
                    reviewedAt = ""
                )
            ),
            busy = false,
            offline = false,
            statusMessage = null,
            errorMessage = null,
            onOpenAppealForm = {},
            onRefresh = {},
            onLogout = {}
        )
    }
}
