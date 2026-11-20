package com.tanaw.app.feature.workspace.customer.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanaw.app.R
import com.tanaw.app.feature.workspace.customer.components.BookingCooldownBanner
import com.tanaw.app.feature.workspace.customer.components.CustomerTopBar
import com.tanaw.app.feature.workspace.customer.components.LocationSelectorField
import com.tanaw.app.feature.workspace.customer.components.VehicleSelectionCard
import com.tanaw.app.feature.workspace.customer.rules.CustomerBookingRules
import com.tanaw.app.feature.workspace.customer.rules.CustomerStandingSummary
import com.tanaw.app.feature.workspace.customer.store.BookingDraft
import com.tanaw.app.feature.workspace.model.RoutePreview
import com.tanaw.app.feature.workspace.viewmodel.value
import com.tanaw.app.ui.theme.TanawCustomerTheme
import com.tanaw.app.ui.theme.TanawDisplayFontFamily
import com.tanaw.app.ui.theme.TanawMonoFontFamily
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Locale

@Composable
internal fun CustomerBookingHomeContent(
    customerName: String,
    customerCity: String,
    unreadCount: Int,
    pickup: String,
    dropoff: String,
    scheduleLabel: String,
    vehicles: List<JsonObject>,
    selectedVehicleId: String,
    routePreview: RoutePreview?,
    busy: Boolean,
    errorMessage: String?,
    statusMessage: String?,
    onRefresh: () -> Unit,
    onOpenAlerts: () -> Unit,
    onPickupClick: () -> Unit,
    onDestinationClick: () -> Unit,
    onSwapLocations: () -> Unit,
    onScheduleClick: () -> Unit,
    onSelectVehicle: (String) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    activeShipmentsCount: Int = 0,
    completedShipmentsCount: Int = 0,
    onOpenActiveShipments: () -> Unit = {},
    savedDefaultAddress: String = "",
    onUseSavedAddress: (() -> Unit)? = null,
    availableDraft: BookingDraft? = null,
    onResumeDraft: () -> Unit = {},
    onDiscardDraft: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    showTopBar: Boolean = true,
    leadTimeHelperText: String = CustomerBookingRules.leadTimeHelperText(24),
    scheduleAdjustmentNotice: String? = null,
    pricingRates: List<JsonObject> = emptyList(),
    isScheduleSet: Boolean = true,
    customerStanding: CustomerStandingSummary = CustomerStandingSummary()
) {
    val selectedVehicle = vehicles.firstOrNull { it.value("id") == selectedVehicleId }
    val selectedEstimate = remember(selectedVehicle, pricingRates, routePreview?.distanceKm) {
        CustomerBookingRules.estimateBaseLinehaul(selectedVehicle, pricingRates, routePreview?.distanceKm)
    }
    val sameLocationError = pickup.trim().length >= 5 && dropoff.trim().length >= 5 &&
        pickup.trim().equals(dropoff.trim(), ignoreCase = true)
    val routeReady = pickup.trim().length >= 5 && dropoff.trim().length >= 5 && !sameLocationError

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = if (showTopBar) 12.dp else 16.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (showTopBar) {
                CustomerTopBar(
                    unreadAlertsCount = unreadCount,
                    isBusy = busy,
                    onRefresh = onRefresh,
                    onOpenAlerts = onOpenAlerts
                )
            }

            if (showTopBar && busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            errorMessage?.let {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = it,
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            statusMessage?.let {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = it,
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (customerStanding.isInCooldown) {
                BookingCooldownBanner(standing = customerStanding)
            }

            availableDraft?.let { draft ->
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Assignment,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Unfinished Booking Draft",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        val summaryText = buildString {
                            if (draft.pickup.isNotBlank() || draft.dropoff.isNotBlank()) {
                                append(draft.pickup.ifBlank { "Pickup" })
                                append(" → ")
                                append(draft.dropoff.ifBlank { "Dropoff" })
                            }
                            if (draft.cargo.isNotBlank()) {
                                if (isNotEmpty()) append(" • ")
                                append(draft.cargo)
                            }
                        }.ifBlank { "Draft inputs from previous session" }
                        Text(
                            text = summaryText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                            maxLines = 2
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = onDiscardDraft) {
                                Text(
                                    text = "Discard",
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = onResumeDraft,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text("Resume Draft")
                            }
                        }
                    }
                }
            }

            // Greeting + Customer Name + City Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = CustomerBookingRules.greeting(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = customerName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = TanawDisplayFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                if (customerCity.isNotBlank()) {
                    Row(
                        modifier = Modifier.widthIn(max = 175.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = customerCity,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(role = Role.Button, onClick = onOpenProfile)
                            .padding(horizontal = 4.dp)
                            .testTag("SetDefaultAddressButton"),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Set default address",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (activeShipmentsCount > 0 || completedShipmentsCount > 0) {
                Surface(
                    onClick = onOpenActiveShipments,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.LocalShipping,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "$activeShipmentsCount active  ·  $completedShipmentsCount completed",
                                style = MaterialTheme.typography.labelLarge,
                                fontFamily = TanawMonoFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "View →",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // "Where are you shipping?" Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 2.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.where_are_you_shipping),
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = TanawDisplayFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (pickup.isBlank() && savedDefaultAddress.isNotBlank() && onUseSavedAddress != null) {
                        Surface(
                            onClick = onUseSavedAddress,
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Home,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Use default address: $savedDefaultAddress",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        var topFieldHeightPx by remember { mutableIntStateOf(0) }
                        var bottomFieldHeightPx by remember { mutableIntStateOf(0) }
                        val pickupDotColor = MaterialTheme.colorScheme.primary
                        val destinationDotColor = MaterialTheme.colorScheme.tertiary
                        val connectorColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Canvas(
                                modifier = Modifier
                                    .width(16.dp)
                                    .fillMaxHeight()
                            ) {
                                val centerX = size.width / 2f
                                val dotRadius = 5.dp.toPx()
                                val topCenterY = if (topFieldHeightPx > 0) {
                                    topFieldHeightPx / 2f
                                } else {
                                    size.height * 0.23f
                                }
                                val bottomCenterY = if (bottomFieldHeightPx > 0) {
                                    size.height - bottomFieldHeightPx / 2f
                                } else {
                                    size.height * 0.77f
                                }
                                val lineStartY = topCenterY + dotRadius + 4.dp.toPx()
                                val lineEndY = bottomCenterY - dotRadius - 4.dp.toPx()

                                if (lineEndY > lineStartY) {
                                    drawLine(
                                        color = connectorColor,
                                        start = Offset(centerX, lineStartY),
                                        end = Offset(centerX, lineEndY),
                                        strokeWidth = 1.75.dp.toPx(),
                                        pathEffect = PathEffect.dashPathEffect(
                                            floatArrayOf(4.dp.toPx(), 4.dp.toPx()),
                                            0f
                                        )
                                    )
                                }
                                drawCircle(
                                    color = pickupDotColor,
                                    radius = dotRadius,
                                    center = Offset(centerX, topCenterY)
                                )
                                drawCircle(
                                    color = destinationDotColor,
                                    radius = dotRadius,
                                    center = Offset(centerX, bottomCenterY)
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Pick-up Location Row
                                LocationSelectorField(
                                    label = stringResource(R.string.pickup),
                                    value = pickup,
                                    placeholder = stringResource(R.string.enter_pickup_location),
                                    dotColor = pickupDotColor,
                                    showDot = false,
                                    onClick = onPickupClick,
                                    modifier = Modifier
                                        .onSizeChanged { topFieldHeightPx = it.height }
                                        .testTag("PickupLocationField")
                                )

                                // Destination Row
                                LocationSelectorField(
                                    label = stringResource(R.string.destination),
                                    value = dropoff,
                                    placeholder = stringResource(R.string.enter_destination),
                                    dotColor = destinationDotColor,
                                    showDot = false,
                                    onClick = onDestinationClick,
                                    modifier = Modifier
                                        .onSizeChanged { bottomFieldHeightPx = it.height }
                                        .testTag("DestinationLocationField")
                                )
                            }
                        }

                        if (pickup.isNotBlank() || dropoff.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 4.dp)
                                    .size(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    onClick = onSwapLocations,
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surface,
                                    shadowElevation = 3.dp,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Outlined.SwapVert,
                                            contentDescription = "Swap pick-up and destination",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (sameLocationError) {
                        Text(
                            text = "Pick-up and destination must be different locations.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    // Bottom row inside card: route distance preview (if loaded) + Schedule pick-up pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (routePreview != null) {
                            Row(
                                modifier = Modifier.weight(1f, fill = false),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Route,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "${routePreview.distanceKm} km · ${CustomerBookingRules.formatDuration(routePreview.minutes)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = TanawMonoFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        OutlinedButton(
                            onClick = onScheduleClick,
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            border = BorderStroke(
                                width = if (!isScheduleSet && routeReady) 1.5.dp else 1.dp,
                                color = if (!isScheduleSet && routeReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("SchedulePillButton")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = scheduleLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Outlined.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Text(
                        text = leadTimeHelperText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (!scheduleAdjustmentNotice.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = scheduleAdjustmentNotice,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // Available Vehicles Header
            Text(
                text = stringResource(R.string.available_vehicles),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = TanawDisplayFontFamily,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            if (vehicles.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No vehicle types are available right now. Refresh or contact the office.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                vehicles.forEach { vehicle ->
                    val id = vehicle.value("id")
                    val name = vehicle.value("name")
                    val capacityKg = vehicle.value("default_capacity_kg")
                    val isSelected = id == selectedVehicleId
                    val leadTimeText = CustomerBookingRules.leadTimeBadge(
                        CustomerBookingRules.minLeadTimeHours(vehicle)
                    )
                    val estimate = CustomerBookingRules.estimateBaseLinehaul(
                        vehicle = vehicle,
                        pricingRates = pricingRates,
                        distanceKm = routePreview?.distanceKm
                    )
                    VehicleSelectionCard(
                        name = name,
                        capacityText = CustomerBookingRules.formatCapacity(capacityKg),
                        badgeText = CustomerBookingRules.vehicleBadge(name),
                        iconRes = CustomerBookingRules.vehicleIconRes(name),
                        selected = isSelected,
                        leadTimeText = leadTimeText,
                        estimateText = estimate?.formattedAmount,
                        onClick = { onSelectVehicle(id) }
                    )
                }
            }
        }

        // Sticky Bottom Bar when a vehicle is available/selected
        if (selectedVehicle != null) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedVehicle.value("name").uppercase(Locale.ENGLISH),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = TanawMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (selectedEstimate != null) {
                                "Est. Base Linehaul: ${selectedEstimate.formattedAmount}"
                            } else {
                                stringResource(R.string.awaiting_office_quotation)
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontFamily = TanawDisplayFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when {
                                customerStanding.isBookingBlocked -> {
                                    if (customerStanding.cooldownRemainingText.isNotBlank()) {
                                        "Booking paused · ${customerStanding.cooldownRemainingText}"
                                    } else {
                                        "Booking temporarily unavailable"
                                    }
                                }
                                !routeReady -> "Select pick-up & destination to continue"
                                !isScheduleSet -> "Select a pick-up schedule to continue"
                                else -> stringResource(R.string.estimated_upon_review)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (customerStanding.isBookingBlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = onContinue,
                        enabled = !busy && !customerStanding.isBookingBlocked,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("BookingContinueButton")
                    ) {
                        Text(
                            text = stringResource(R.string.continue_action),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ─── Previews ─────────────────────────────────────────────────────────────────
internal val previewVehicles = listOf(
    buildJsonObject {
        put("id", "v-1")
        put("name", "Dropside Truck")
        put("default_capacity_kg", "2000")
        put("min_lead_time_hours", 24)
    },
    buildJsonObject {
        put("id", "v-2")
        put("name", "Closed Van")
        put("default_capacity_kg", "3000")
        put("min_lead_time_hours", 24)
    },
    buildJsonObject {
        put("id", "v-3")
        put("name", "Refrigerated Van")
        put("default_capacity_kg", "8000")
        put("min_lead_time_hours", 48)
    }
)

@Preview(showBackground = true, name = "Customer Home")
@Composable
private fun CustomerBookingHomeLightPreview() {
    TanawCustomerTheme {
        CustomerBookingHomeContent(
            customerName = "Maria Santos",
            customerCity = "Quezon City, Metro Manila",
            unreadCount = 2,
            pickup = "",
            dropoff = "",
            scheduleLabel = "Tomorrow, 9:00 AM",
            vehicles = previewVehicles,
            selectedVehicleId = "v-2",
            routePreview = RoutePreview("{}", 118.4, 145),
            busy = false,
            errorMessage = null,
            statusMessage = null,
            onRefresh = {},
            onOpenAlerts = {},
            onPickupClick = {},
            onDestinationClick = {},
            onSwapLocations = {},
            onScheduleClick = {},
            onSelectVehicle = {},
            onContinue = {}
        )
    }
}
