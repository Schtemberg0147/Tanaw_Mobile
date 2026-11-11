package com.tanaw.app.feature.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tanaw.app.R
import com.tanaw.app.ui.theme.TanawTheme

@Composable
fun CustomerTopBar(
    unreadAlertsCount: Int,
    isBusy: Boolean,
    onRefresh: () -> Unit,
    onOpenAlerts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TanawBrandLogo(modifier = Modifier.size(40.dp))
            Text(
                text = "TANAW",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = onRefresh,
                enabled = !isBusy
            ) {
                Text(stringResource(id = R.string.refresh))
            }
            IconButton(onClick = onOpenAlerts) {
                BadgedBox(
                    badge = {
                        if (unreadAlertsCount > 0) {
                            Badge(containerColor = MaterialTheme.colorScheme.secondary) {
                                Text(unreadAlertsCount.coerceAtMost(99).toString())
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Notifications,
                        contentDescription = stringResource(id = R.string.notifications),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CustomerTopBarPreview() {
    TanawTheme {
        Surface {
            CustomerTopBar(
                unreadAlertsCount = 3,
                isBusy = false,
                onRefresh = {},
                onOpenAlerts = {}
            )
        }
    }
}
