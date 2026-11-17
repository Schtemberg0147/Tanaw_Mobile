package com.tanaw.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tanaw.app.feature.workspace.rules.MobilePresentation
import com.tanaw.app.ui.theme.TanawTheme

@Composable
fun WorkspaceCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        color    = MaterialTheme.colorScheme.surface,
        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier            = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content             = content
        )
    }
}

@Composable
fun StatusBadge(
    status: String,
    modifier: Modifier = Modifier
) {
    val extendedColors = TanawTheme.extendedColors
    val normalized = status.trim().lowercase()
    val (containerColor, contentColor) = when (normalized) {
        "completed", "delivered", "approved", "valid" ->
            extendedColors.successBannerBackground to extendedColors.onSuccessBannerText
        "cancelled", "delayed", "correction_requested", "expired", "rejected" ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
        "pending", "loading", "expiring", "unread" ->
            extendedColors.warningContainer to extendedColors.onWarningContainer
        "booked", "assigned", "en_route_to_pickup", "arrived_at_pickup", "in_transit", "arrived_at_destination" ->
            extendedColors.infoContainer to extendedColors.onInfoContainer
        else ->
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier,
        color    = containerColor,
        shape    = RoundedCornerShape(6.dp)
    ) {
        Text(
            text       = MobilePresentation.status(status),
            modifier   = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            color      = contentColor,
            style      = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun SupportingText(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text     = text,
        modifier = modifier,
        style    = MaterialTheme.typography.bodyMedium,
        color    = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun TanawSubScreenTopBar(
    title: String,
    onBack: () -> Unit,
    trailing: @Composable RowScope.() -> Unit = {},
    busy: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color    = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .padding(start = 4.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick  = onBack,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector        = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint               = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text       = title,
                    style      = MaterialTheme.typography.titleMedium,
                    color      = MaterialTheme.colorScheme.onSurface,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    modifier   = Modifier.weight(1f)
                )
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    content               = trailing
                )
            }
            if (busy) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color    = MaterialTheme.colorScheme.primary
                )
            } else {
                HorizontalDivider(
                    color     = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 1.dp
                )
            }
        }
    }
}

@Composable
fun KeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    boldValue: Boolean = false
) {
    Row(
        modifier              = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment     = Alignment.Top
    ) {
        Text(
            text     = label,
            style    = MaterialTheme.typography.bodyMedium,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text       = value,
            style      = MaterialTheme.typography.bodyMedium,
            fontWeight = if (boldValue) FontWeight.Bold else FontWeight.Medium,
            color      = valueColor,
            textAlign  = TextAlign.End,
            modifier   = Modifier.weight(0.58f)
        )
    }
}

@Preview(showBackground = true, name = "Workspace Design Light")
@Composable
private fun WorkspaceDesignLightPreview() {
    TanawTheme(darkTheme = false) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TanawSubScreenTopBar(
                title = "TNW-2026-0042",
                onBack = {},
                trailing = { StatusBadge("in_transit") },
                busy = false
            )
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                WorkspaceCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatusBadge("pending")
                        StatusBadge("completed")
                        StatusBadge("cancelled")
                    }
                    KeyValueRow(label = "Quoted Amount", value = "₱12,500.00", boldValue = true)
                    KeyValueRow(label = "Pickup", value = "Brgy. Poblacion, Makati City")
                    SupportingText("Amounts in PHP · Philippine time")
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Workspace Design Dark")
@Composable
private fun WorkspaceDesignDarkPreview() {
    TanawTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TanawSubScreenTopBar(
                    title = "TNW-2026-0042",
                    onBack = {},
                    trailing = { StatusBadge("in_transit") },
                    busy = true
                )
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    WorkspaceCard {
                        KeyValueRow(label = "Quoted Amount", value = "₱12,500.00", boldValue = true)
                        SupportingText("Amounts in PHP · Philippine time")
                    }
                }
            }
        }
    }
}
