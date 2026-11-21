package com.tanaw.app.feature.workspace.customer.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tanaw.app.R
import com.tanaw.app.ui.theme.BorderGray
import com.tanaw.app.ui.theme.ElectricBlueSecondary
import com.tanaw.app.ui.theme.NavyPrimary
import com.tanaw.app.ui.theme.TanawCustomerTheme

@Composable
fun CustomerTopBar(
    unreadAlertsCount: Int,
    isBusy: Boolean,
    onRefresh: () -> Unit,
    onOpenAlerts: () -> Unit,
    modifier: Modifier = Modifier,
    applyStatusBarsPadding: Boolean = false,
    containerColor: Color = Color.White,
    contentColor: Color = NavyPrimary,
    dividerColor: Color = BorderGray,
    progressColor: Color = NavyPrimary,
    badgeContainerColor: Color = ElectricBlueSecondary,
    badgeContentColor: Color = Color.White,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerColor
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .let { if (applyStatusBarsPadding) it.statusBarsPadding() else it }
                    .heightIn(min = 60.dp)
                    .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TanawBrandLogo(
                    modifier = Modifier.size(36.dp)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onRefresh,
                        enabled = !isBusy,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = stringResource(id = R.string.refresh),
                            tint = if (isBusy) contentColor.copy(alpha = 0.45f) else contentColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(
                        onClick = onOpenAlerts,
                        modifier = Modifier.size(48.dp)
                    ) {
                        BadgedBox(
                            badge = {
                                if (unreadAlertsCount > 0) {
                                    Badge(
                                        containerColor = badgeContainerColor,
                                        contentColor = badgeContentColor
                                    ) {
                                        Text(unreadAlertsCount.coerceAtMost(99).toString())
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Notifications,
                                contentDescription = stringResource(id = R.string.notifications),
                                tint = contentColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
            if (isBusy) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = progressColor,
                    trackColor = dividerColor.copy(alpha = 0.4f)
                )
            } else {
                HorizontalDivider(
                    color = dividerColor,
                    thickness = 1.dp
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Customer Top Bar")
@Composable
private fun CustomerTopBarPreview() {
    TanawCustomerTheme {
        CustomerTopBar(
            unreadAlertsCount = 3,
            isBusy = false,
            onRefresh = {},
            onOpenAlerts = {}
        )
    }
}
