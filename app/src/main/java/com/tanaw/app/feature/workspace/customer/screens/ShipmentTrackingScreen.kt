package com.tanaw.app.feature.workspace.customer.screens

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanaw.app.feature.workspace.customer.components.PickupWaitRiskPanel
import com.tanaw.app.feature.workspace.customer.rules.CustomerNoShowRules
import com.tanaw.app.feature.workspace.rules.MobilePresentation
import com.tanaw.app.feature.workspace.tracking.CustomerEtaPresentation
import com.tanaw.app.feature.workspace.tracking.PositionFreshness
import com.tanaw.app.feature.workspace.tracking.PositionFreshnessStatus
import com.tanaw.app.feature.workspace.tracking.ShipmentMilestoneStep
import com.tanaw.app.feature.workspace.tracking.ShipmentTrackingPresentation
import com.tanaw.app.feature.workspace.tracking.TrackingDriverSummary
import com.tanaw.app.feature.workspace.tracking.TrackingEtaPillState
import com.tanaw.app.feature.workspace.tracking.TrackingSheetSummary
import com.tanaw.app.feature.workspace.viewmodel.value
import com.tanaw.app.ui.components.KeyValueRow
import com.tanaw.app.ui.theme.BackgroundGray
import com.tanaw.app.ui.theme.BorderGray
import com.tanaw.app.ui.theme.DestinationOrange
import com.tanaw.app.ui.theme.ElectricBlueSecondary
import com.tanaw.app.ui.theme.ExpiredRed
import com.tanaw.app.ui.theme.HintGray
import com.tanaw.app.ui.theme.InputGray
import com.tanaw.app.ui.theme.MapPinDestination
import com.tanaw.app.ui.theme.MapPinPickup
import com.tanaw.app.ui.theme.NavyPrimary
import com.tanaw.app.ui.theme.OutlineGray
import com.tanaw.app.ui.theme.StatusGreen
import com.tanaw.app.ui.theme.TanawCustomerTheme
import com.tanaw.app.ui.theme.TanawDisplayFontFamily
import com.tanaw.app.ui.theme.TanawMonoFontFamily
import com.tanaw.app.ui.theme.TextDark
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

private val TrackingSheetBg = Color.White
private val TrackingCardBg = Color.White
private val TrackingDriverCardBg = BackgroundGray
private val TrackingPrimary = NavyPrimary
private val TrackingSecondary = ElectricBlueSecondary
private val TrackingTextPrimary = TextDark
private val TrackingTextSecondary = HintGray
private val TrackingTextMuted = OutlineGray
private val TrackingDivider = BorderGray
private val TrackingHandle = Color(0xFFCBD5E1)
private val TrackingUpcomingNodeBg = InputGray
private val TrackingWarningDot = DestinationOrange
private val TrackingLiveDot = StatusGreen
private val TrackingDanger = ExpiredRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShipmentTrackingScreen(
    shipment: JsonObject,
    deliveries: List<JsonObject>,
    routing: JsonObject?,
    eta: CustomerEtaPresentation?,
    freshness: PositionFreshness?,
    lastTelemetryRecordedAt: String,
    detailLoaded: Boolean,
    detailLoading: Boolean,
    offline: Boolean,
    busy: Boolean,
    errorMessage: String?,
    timelineEvents: List<Pair<String, String>>,
    now: Instant,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    mapSlot: @Composable (recenterTrigger: Int, modifier: Modifier) -> Unit,
    modifier: Modifier = Modifier,
    detail: JsonObject? = null,
    vehicles: List<JsonObject> = emptyList(),
    customerCancellationSlot: (@Composable () -> Unit)? = null,
    initialExpanded: Boolean = false,
    onAcceptQuote: (() -> Unit)? = null,
    onNotifyOnWay: ((deliveryId: String) -> Unit)? = null
) {
    val context = LocalContext.current
    val primaryDelivery = deliveries.firstOrNull()
    val trackingNumber = shipment.value("tracking_number").ifBlank { "Shipment details" }
    val etaPill = remember(shipment, primaryDelivery, routing, eta, now) {
        ShipmentTrackingPresentation.etaPillState(
            shipment = shipment,
            delivery = primaryDelivery,
            routing = routing,
            eta = eta,
            now = now
        )
    }
    val sheetSummary = remember(shipment, primaryDelivery, freshness, offline) {
        ShipmentTrackingPresentation.sheetSummary(
            shipment = shipment,
            delivery = primaryDelivery,
            freshness = freshness,
            offline = offline
        )
    }
    val driverSummary = remember(shipment, primaryDelivery, detail, routing, vehicles) {
        ShipmentTrackingPresentation.driverSummary(
            shipment = shipment,
            delivery = primaryDelivery,
            detail = detail,
            routing = routing,
            vehicles = vehicles
        )
    }
    val cargoSummary = remember(shipment, primaryDelivery) {
        ShipmentTrackingPresentation.cargoSummary(
            shipment = shipment,
            delivery = primaryDelivery
        )
    }
    val pricingSummary = remember(shipment) {
        ShipmentTrackingPresentation.pricingSummary(shipment)
    }
    val quoteState = remember(shipment, deliveries, now) {
        ShipmentTrackingPresentation.quoteApprovalState(shipment, deliveries, now)
    }
    val formattedPickup = remember(shipment) {
        ShipmentTrackingPresentation.formatAddressWithoutPlusCode(shipment.value("pickup_address"))
    }
    val formattedDropoff = remember(shipment) {
        ShipmentTrackingPresentation.formatAddressWithoutPlusCode(shipment.value("dropoff_address"))
    }
    val showCancelAction = remember(shipment, deliveries) {
        ShipmentTrackingPresentation.shouldShowCancelAction(shipment, deliveries)
    }
    val pickupWaitState = remember(shipment, primaryDelivery, now) {
        CustomerNoShowRules.evaluatePickupWait(shipment, primaryDelivery, now)
    }

    var recenterTrigger by remember { mutableIntStateOf(0) }
    var showFullEventLog by rememberSaveable(shipment.value("id")) { mutableStateOf(false) }
    var showFareBreakdown by rememberSaveable(shipment.value("id")) { mutableStateOf(false) }

    val sheetState = rememberStandardBottomSheetState(
        initialValue = if (initialExpanded) SheetValue.Expanded else SheetValue.PartiallyExpanded,
        skipHiddenState = true
    )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)
    val scope = rememberCoroutineScope()

    val toggleSheet: () -> Unit = {
        scope.launch {
            if (sheetState.currentValue == SheetValue.Expanded) {
                sheetState.partialExpand()
            } else {
                sheetState.expand()
            }
        }
    }

    val openDialer: (String) -> Unit = { phone ->
        runCatching {
            context.startActivity(Intent(Intent.ACTION_DIAL, "tel:$phone".toUri()))
        }
    }
    val openSms: (String) -> Unit = { phone ->
        runCatching {
            context.startActivity(Intent(Intent.ACTION_SENDTO, "smsto:$phone".toUri()))
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val peekHeight = if (pickupWaitState.isUrgentBannerVisible) {
            (maxHeight * 0.56f)
                .coerceIn(348.dp, 444.dp)
                .coerceAtMost(maxHeight * 0.64f)
        } else {
            (maxHeight * 0.46f)
                .coerceIn(286.dp, 376.dp)
                .coerceAtMost(maxHeight * 0.56f)
        }
        val mapViewportHeight = (maxHeight - peekHeight + 24.dp).coerceAtLeast(220.dp)
        val expandedSheetMaxHeight = (maxHeight - 92.dp).coerceAtLeast(320.dp)

        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = peekHeight,
            sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            sheetContainerColor = TrackingSheetBg,
            sheetContentColor = TrackingTextPrimary,
            sheetShadowElevation = 12.dp,
            sheetMaxWidth = androidx.compose.ui.unit.Dp.Unspecified,
            sheetDragHandle = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = toggleSheet)
                        .padding(top = 12.dp, bottom = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(42.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(TrackingHandle)
                    )
                }
            },
            sheetContent = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = expandedSheetMaxHeight)
                ) {
                    if (pickupWaitState.isUrgentBannerVisible) {
                        PickupWaitRiskPanel(
                            waitState = pickupWaitState,
                            driverSummary = driverSummary,
                            busy = busy,
                            offline = offline,
                            onNotifyOnWay = {
                                if (pickupWaitState.deliveryId.isNotBlank()) {
                                    onNotifyOnWay?.invoke(pickupWaitState.deliveryId)
                                }
                            },
                            onCallDriver = { driverSummary.phoneNumber?.let(openDialer) }
                        )
                    } else {
                        PeekStatusSection(
                            summary = sheetSummary,
                            driverSummary = driverSummary,
                            isExpanded = sheetState.currentValue == SheetValue.Expanded,
                            onToggleExpand = toggleSheet,
                            onCallDriver = { driverSummary.phoneNumber?.let(openDialer) },
                            onMessageDriver = { driverSummary.phoneNumber?.let(openSms) }
                        )
                    }

                    HorizontalDivider(
                        color = TrackingDivider,
                        thickness = 1.dp,
                        modifier = Modifier.padding(top = 12.dp)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .background(BackgroundGray)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                            .navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (offline) {
                            Surface(
                                color = InputGray,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, TrackingDivider),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Offline mode · showing your last loaded tracking details.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TrackingTextPrimary,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        if (!errorMessage.isNullOrBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = errorMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        if (detailLoaded && timelineEvents.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { showFullEventLog = true },
                                border = BorderStroke(1.dp, TrackingDivider),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.History,
                                    contentDescription = null,
                                    tint = TrackingPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "View full event log (${timelineEvents.size})",
                                    fontWeight = FontWeight.SemiBold,
                                    color = TrackingPrimary
                                )
                            }
                        }

                        SheetCard(title = "Route & Cargo") {
                            TrackingRouteConnectorBlock(
                                pickupAddress = formattedPickup,
                                dropoffAddress = formattedDropoff
                            )
                            HorizontalDivider(color = TrackingDivider)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "CARGO",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = TanawMonoFontFamily,
                                        color = TrackingTextMuted
                                    )
                                    Text(
                                        text = cargoSummary.cargoText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TrackingTextPrimary
                                    )
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "WEIGHT",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = TanawMonoFontFamily,
                                        color = TrackingTextMuted
                                    )
                                    Text(
                                        text = cargoSummary.weightText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = TanawMonoFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TrackingTextPrimary
                                    )
                                }
                            }
                            HorizontalDivider(
                                color = TrackingDivider,
                                thickness = 0.5.dp
                            )
                            KeyValueRow(
                                label = "Recipient Contact",
                                value = cargoSummary.recipientContact,
                                boldValue = cargoSummary.recipientContact != "Not provided",
                                valueFontFamily = if (cargoSummary.recipientContact != "Not provided") TanawMonoFontFamily else null
                            )
                            if (cargoSummary.deliveryNotes.isNotBlank()) {
                                KeyValueRow(
                                    label = "Delivery Notes",
                                    value = cargoSummary.deliveryNotes
                                )
                            }
                            if (shipment.value("cancelled_reason").isNotBlank()) {
                                KeyValueRow(
                                    label = "Cancellation Reason",
                                    value = shipment.value("cancelled_reason"),
                                    valueColor = TrackingDanger
                                )
                            }
                        }

                        SheetCard(title = "Schedule & Telemetry") {
                            val scheduledArrival = shipment.value("estimated_arrival")
                            KeyValueRow(
                                label = "Scheduled Arrival",
                                value = if (scheduledArrival.isNotBlank()) {
                                    MobilePresentation.time(scheduledArrival)
                                } else {
                                    "Pending office schedule"
                                },
                                valueFontFamily = if (scheduledArrival.isNotBlank()) TanawMonoFontFamily else null
                            )
                            if (shipment.value("scheduled_departure").isNotBlank()) {
                                KeyValueRow(
                                    label = "Scheduled Departure",
                                    value = MobilePresentation.time(shipment.value("scheduled_departure")),
                                    valueFontFamily = TanawMonoFontFamily
                                )
                            }
                            if (lastTelemetryRecordedAt.isNotBlank() && freshness != null) {
                                KeyValueRow(
                                    label = "Last GPS Update",
                                    value = "${MobilePresentation.time(lastTelemetryRecordedAt)} · ${freshness.accuracyText}",
                                    valueFontFamily = TanawMonoFontFamily
                                )
                            } else {
                                KeyValueRow(
                                    label = "GPS Signal",
                                    value = "Awaiting driver GPS signal",
                                    valueColor = TrackingTextSecondary
                                )
                            }
                        }

                        SheetCard(
                            title = "Quotation & Pricing",
                            badge = quoteState.badgeText
                        ) {
                            pricingSummary.rows.forEach { row ->
                                KeyValueRow(
                                    label = row.label,
                                    value = row.value,
                                    valueColor = if (row.isEmphasized) TrackingPrimary else TrackingTextPrimary,
                                    boldValue = row.isEmphasized,
                                    valueFontFamily = TanawMonoFontFamily
                                )
                            }

                            if (quoteState.quoteExpiresAtText.isNotBlank()) {
                                val validityValue = if (quoteState.countdownText.isNotBlank() && !quoteState.isExpired) {
                                    "${quoteState.quoteExpiresAtText} (${quoteState.countdownText})"
                                } else if (quoteState.isExpired) {
                                    "${quoteState.quoteExpiresAtText} (Expired)"
                                } else {
                                    quoteState.quoteExpiresAtText
                                }
                                KeyValueRow(
                                    label = "Quote Valid Until",
                                    value = validityValue,
                                    valueColor = if (quoteState.isExpired) TrackingDanger else TrackingTextPrimary,
                                    boldValue = quoteState.isExpired,
                                    valueFontFamily = TanawMonoFontFamily
                                )
                            }

                            if (pricingSummary.breakdownRows.isNotEmpty()) {
                                HorizontalDivider(color = TrackingDivider)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { showFareBreakdown = !showFareBreakdown }
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (showFareBreakdown) "Hide fare breakdown" else "View fare breakdown",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TrackingSecondary
                                    )
                                    Icon(
                                        imageVector = if (showFareBreakdown) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                        contentDescription = null,
                                        tint = TrackingSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                if (showFareBreakdown) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        pricingSummary.breakdownRows.forEach { row ->
                                            KeyValueRow(
                                                label = row.label,
                                                value = row.value,
                                                valueColor = TrackingTextPrimary,
                                                boldValue = row.isEmphasized,
                                                valueFontFamily = TanawMonoFontFamily
                                            )
                                        }
                                    }
                                }
                            }

                            if (quoteState.isExpired) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Quotation expired. Contact the office for an updated quote.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }

                            if (quoteState.canAccept && onAcceptQuote != null) {
                                Button(
                                    onClick = onAcceptQuote,
                                    enabled = !busy && !offline,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = TrackingPrimary,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("AcceptQuoteButton")
                                ) {
                                    Text(
                                        text = "Confirm Quotation",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (showCancelAction && customerCancellationSlot != null) {
                            customerCancellationSlot()
                        }
                    }
                }
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BackgroundGray)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(mapViewportHeight)
                        .align(Alignment.TopCenter)
                ) {
                    mapSlot(recenterTrigger, Modifier.fillMaxSize())

                    if (!pickupWaitState.isUrgentBannerVisible) {
                        FloatingEtaPill(
                            etaPill = etaPill,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .statusBarsPadding()
                                .padding(top = 68.dp, start = 16.dp, end = 16.dp)
                        )
                    }

                    RecenterMapButton(
                        onClick = { recenterTrigger++ },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 36.dp, end = 16.dp)
                    )
                }
            }
        }

        MinimalTrackingTopBar(
            title = trackingNumber,
            busy = busy || detailLoading,
            onBack = onBack,
            onRefresh = onRefresh,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        if (showFullEventLog && timelineEvents.isNotEmpty()) {
            EventLogModalDialog(
                trackingNumber = trackingNumber,
                timelineEvents = timelineEvents,
                onDismiss = { showFullEventLog = false }
            )
        }
    }
}

@Composable
private fun MinimalTrackingTopBar(
    title: String,
    busy: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = TrackingSheetBg.copy(alpha = 0.96f),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = TrackingPrimary
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = TanawMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = TrackingPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                )
                IconButton(
                    onClick = onRefresh,
                    enabled = !busy,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Refresh shipment details",
                        tint = TrackingPrimary
                    )
                }
            }
            if (busy) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = TrackingPrimary,
                    trackColor = TrackingDivider
                )
            } else {
                HorizontalDivider(
                    color = TrackingDivider,
                    thickness = 1.dp
                )
            }
        }
    }
}

@Composable
private fun FloatingEtaPill(
    etaPill: TrackingEtaPillState,
    modifier: Modifier = Modifier
) {
    val dotColor = when {
        etaPill.isLive -> TrackingLiveDot
        etaPill.isCompleted -> TrackingPrimary
        etaPill.isCancelled -> TrackingDanger
        else -> TrackingWarningDot
    }
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = TrackingSheetBg.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, TrackingDivider),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = etaPill.text,
                style = MaterialTheme.typography.labelLarge,
                fontFamily = TanawMonoFontFamily,
                fontWeight = FontWeight.SemiBold,
                color = TrackingPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RecenterMapButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.size(48.dp),
        shape = CircleShape,
        color = TrackingSheetBg.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, TrackingDivider),
        shadowElevation = 4.dp
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = Icons.Outlined.MyLocation,
                contentDescription = "Recenter map",
                tint = TrackingPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun PeekStatusSection(
    summary: TrackingSheetSummary,
    driverSummary: TrackingDriverSummary,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onCallDriver: () -> Unit,
    onMessageDriver: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpand)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = summary.currentStatusTitle,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = TanawDisplayFontFamily,
                fontWeight = FontWeight.Bold,
                color = if (summary.isCancelled) TrackingDanger else TrackingPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (summary.currentStatusTimestamp.isNotBlank()) {
                Spacer(Modifier.width(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = InputGray,
                    border = BorderStroke(1.dp, TrackingDivider)
                ) {
                    Text(
                        text = summary.currentStatusTimestamp,
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = TanawMonoFontFamily,
                        fontWeight = FontWeight.Medium,
                        color = TrackingTextSecondary,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        ConnectedMilestoneStepper(
            steps = summary.steps,
            isCancelled = summary.isCancelled,
            currentStatusTitle = summary.currentStatusTitle
        )

        DriverVehiclePeekCard(
            driver = driverSummary,
            onCall = onCallDriver,
            onMessage = onMessageDriver
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = summary.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TrackingTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = if (isExpanded) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                contentDescription = if (isExpanded) "Collapse details" else "Expand details",
                tint = TrackingTextMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ConnectedMilestoneStepper(
    steps: List<ShipmentMilestoneStep>,
    isCancelled: Boolean,
    currentStatusTitle: String
) {
    val activeOrCompletedCount = steps.count { it.isCompleted || it.isActive }
    val showTimestampsRow = steps.any { (it.isCompleted || it.isActive) && it.compactTime.isNotBlank() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "Delivery progress: $activeOrCompletedCount of ${steps.size} steps reached, $currentStatusTitle"
            },
        verticalAlignment = Alignment.Top
    ) {
        steps.forEachIndexed { index, step ->
            val leftReached = index > 0 && (step.isCompleted || step.isActive)
            val rightReached = index < steps.lastIndex && (steps[index + 1].isCompleted || steps[index + 1].isActive)
            val leftLineColor = when {
                isCancelled && leftReached -> TrackingHandle
                leftReached -> TrackingPrimary
                else -> TrackingDivider
            }
            val rightLineColor = when {
                isCancelled && rightReached -> TrackingHandle
                rightReached -> TrackingPrimary
                else -> TrackingDivider
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(22.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (index == 0) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(2.5.dp)
                                .background(leftLineColor)
                        )
                    }

                    Box(
                        modifier = Modifier.size(22.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            step.isCompleted && !isCancelled -> {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(TrackingPrimary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                            }
                            step.isActive -> {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(TrackingPrimary.copy(alpha = 0.12f))
                                        .border(1.5.dp, TrackingPrimary, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(TrackingPrimary)
                                    )
                                }
                            }
                            step.isCompleted && isCancelled -> {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(TrackingHandle)
                                )
                            }
                            else -> {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(TrackingUpcomingNodeBg)
                                        .border(1.dp, TrackingHandle, CircleShape)
                                )
                            }
                        }
                    }

                    if (index == steps.lastIndex) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(2.5.dp)
                                .background(rightLineColor)
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    text = step.shortLabel,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    letterSpacing = (-0.2).sp,
                    fontWeight = when {
                        step.isActive -> FontWeight.Bold
                        step.isCompleted -> FontWeight.SemiBold
                        else -> FontWeight.Normal
                    },
                    color = when {
                        step.isActive -> TrackingPrimary
                        step.isCompleted -> TrackingTextPrimary
                        else -> TrackingTextMuted
                    },
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 1.dp)
                )

                if (showTimestampsRow) {
                    Text(
                        text = if (step.isCompleted || step.isActive) step.compactTime else "",
                        fontFamily = TanawMonoFontFamily,
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        letterSpacing = (-0.2).sp,
                        color = if (step.isActive) TrackingTextSecondary else TrackingTextMuted,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                }
            }
        }
    }
}

@Composable
private fun DriverVehiclePeekCard(
    driver: TrackingDriverSummary,
    onCall: () -> Unit,
    onMessage: () -> Unit
) {
    val hasPhone = driver.phoneNumber != null
    val showActionButtons = driver.hasDriverAssigned || hasPhone
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = TrackingDriverCardBg,
        border = BorderStroke(1.dp, TrackingDivider)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (driver.hasDriverAssigned) TrackingPrimary else InputGray)
                    .border(
                        width = 1.dp,
                        color = if (driver.hasDriverAssigned) TrackingPrimary else TrackingDivider,
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.LocalShipping,
                    contentDescription = null,
                    tint = if (driver.hasDriverAssigned) Color.White else TrackingTextMuted,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = driver.driverName,
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = TanawDisplayFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = TrackingTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = driver.vehicleSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = TanawMonoFontFamily,
                    color = TrackingTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (showActionButtons) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    CircularDriverActionButton(
                        icon = Icons.Outlined.Call,
                        contentDescription = if (hasPhone) "Call driver" else "Call driver unavailable",
                        enabled = hasPhone,
                        onClick = onCall
                    )
                    CircularDriverActionButton(
                        icon = Icons.Outlined.Sms,
                        contentDescription = if (hasPhone) "Message driver" else "Message driver unavailable",
                        enabled = hasPhone,
                        onClick = onMessage
                    )
                }
            }
        }
    }
}

@Composable
private fun CircularDriverActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (enabled) TrackingPrimary else InputGray)
                .border(
                    width = 1.dp,
                    color = if (enabled) TrackingPrimary else TrackingDivider,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) Color.White else TrackingTextMuted.copy(alpha = 0.55f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun EventLogModalDialog(
    trackingNumber: String,
    timelineEvents: List<Pair<String, String>>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TrackingSheetBg,
        titleContentColor = TrackingTextPrimary,
        textContentColor = TrackingTextSecondary,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Delivery Event Log",
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = TanawDisplayFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = TrackingPrimary
                )
                Text(
                    text = "$trackingNumber · ${timelineEvents.size} recorded updates",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = TanawMonoFontFamily,
                    color = TrackingTextMuted
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                timelineEvents.forEach { (time, label) ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BackgroundGray,
                        border = BorderStroke(1.dp, TrackingDivider),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TrackingTextPrimary
                            )
                            Text(
                                text = MobilePresentation.time(time),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = TanawMonoFontFamily,
                                color = TrackingTextSecondary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Close",
                    fontWeight = FontWeight.Bold,
                    color = TrackingPrimary
                )
            }
        }
    )
}

@Composable
private fun SheetCard(
    title: String,
    badge: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = TrackingCardBg,
        border = BorderStroke(1.dp, TrackingDivider)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = TanawDisplayFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = TrackingPrimary,
                    modifier = Modifier.weight(1f)
                )
                if (!badge.isNullOrBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = InputGray,
                        border = BorderStroke(1.dp, TrackingDivider)
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelMedium,
                            color = TrackingPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            content()
        }
    }
}

@Composable
private fun TrackingRouteConnectorBlock(
    pickupAddress: String,
    dropoffAddress: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(MapPinPickup)
                )
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(22.dp)
                        .background(TrackingDivider)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "PICKUP",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = TanawMonoFontFamily,
                    color = TrackingTextMuted
                )
                Text(
                    text = pickupAddress.ifBlank { "Not provided" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TrackingTextPrimary
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(MapPinDestination)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "DESTINATION",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = TanawMonoFontFamily,
                    color = TrackingTextMuted
                )
                Text(
                    text = dropoffAddress.ifBlank { "Not provided" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TrackingTextPrimary
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Tracking Screen Peek (Assigned)")
@Composable
private fun ShipmentTrackingPeekPreview() {
    ShipmentTrackingPreviewContent(initialExpanded = false)
}

@Preview(showBackground = true, name = "Tracking Screen Expanded")
@Composable
private fun ShipmentTrackingExpandedPreview() {
    ShipmentTrackingPreviewContent(initialExpanded = true)
}

@Preview(showBackground = true, name = "Tracking Screen Quoted FTL")
@Composable
private fun ShipmentTrackingQuotedPreview() {
    val now = Instant.parse("2026-11-19T02:00:00Z")
    val quotedShipment = buildJsonObject {
        put("id", "ship-q1")
        put("tracking_number", "SHP-NE-26-002401")
        put("status", "quoted")
        put("pickup_address", "Cabanatuan City Public Market, Nueva Ecija")
        put("dropoff_address", "Manila North Harbor, Manila")
        put("cargo_description", "250 sacks of rice")
        put("weight_kg", "6000")
        put("linehaul_amount", "14500")
        put("toll_amount", "1200")
        put("mobilization_fee", "800")
        put("quoted_amount", "16500")
        put("final_amount", "16500")
        put("quote_expires_at", "2026-11-19T14:00:00Z")
        put("scheduled_departure", "2026-11-20T01:00:00Z")
        put("estimated_arrival", "2026-11-20T06:30:00Z")
    }
    val freshness = PositionFreshness(
        status = PositionFreshnessStatus.UNAVAILABLE,
        ageSeconds = null,
        relativeText = "GPS position unavailable",
        accuracyText = "accuracy unknown"
    )
    TanawCustomerTheme {
        ShipmentTrackingScreen(
            shipment = quotedShipment,
            deliveries = emptyList(),
            routing = null,
            eta = null,
            freshness = freshness,
            lastTelemetryRecordedAt = "",
            detailLoaded = true,
            detailLoading = false,
            offline = false,
            busy = false,
            errorMessage = null,
            timelineEvents = emptyList(),
            now = now,
            onBack = {},
            onRefresh = {},
            onAcceptQuote = {},
            mapSlot = { _, mapModifier ->
                Box(
                    modifier = mapModifier.background(InputGray),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "MapLibre Light Viewport",
                        color = TrackingPrimary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            },
            initialExpanded = true
        )
    }
}

@Preview(showBackground = true, name = "Tracking Screen Delivered")
@Composable
private fun ShipmentTrackingDeliveredPreview() {
    val now = Instant.parse("2026-11-19T05:30:00Z")
    val deliveredShipment = buildJsonObject {
        put("id", "ship-2")
        put("tracking_number", "SHP-NE-26-002310")
        put("status", "completed")
        put("pickup_address", "FRH7+5W3, Cabanatuan City Public Market, Nueva Ecija")
        put("dropoff_address", "G32P+4M Pier 14, Manila North Harbor, Manila")
        put("cargo_description", "200 boxes")
        put("weight_kg", "2500")
        put("quoted_amount", "12500")
        put("final_amount", "12500.00")
    }
    val deliveredDelivery = buildJsonObject {
        put("id", "del-2")
        put("shipment_id", "ship-2")
        put("status", "delivered")
        put("assigned_at", "2026-11-19T01:00:00Z")
        put("route_started_at", "2026-11-19T01:15:00Z")
        put("pickup_arrived_at", "2026-11-19T01:30:00Z")
        put("customer_showed_at", "2026-11-19T01:40:00Z")
        put("departed_at", "2026-11-19T01:55:00Z")
        put("arrived_at", "2026-11-19T04:50:00Z")
        put("driver_name", "Juan Dela Cruz")
        put("driver_phone", "+639171234567")
        put("truck_type", "6-Wheeler Closed Van")
        put("plate_number", "NCV 4821")
    }
    val freshness = PositionFreshness(
        status = PositionFreshnessStatus.UNAVAILABLE,
        ageSeconds = null,
        relativeText = "GPS position unavailable",
        accuracyText = "accuracy unknown"
    )
    TanawCustomerTheme {
        ShipmentTrackingScreen(
            shipment = deliveredShipment,
            deliveries = listOf(deliveredDelivery),
            routing = null,
            eta = null,
            freshness = freshness,
            lastTelemetryRecordedAt = "",
            detailLoaded = true,
            detailLoading = false,
            offline = false,
            busy = false,
            errorMessage = null,
            timelineEvents = listOf(
                "2026-11-19T01:00:00Z" to "Driver Assigned",
                "2026-11-19T04:50:00Z" to "Delivered"
            ),
            now = now,
            onBack = {},
            onRefresh = {},
            mapSlot = { _, mapModifier ->
                Box(
                    modifier = mapModifier.background(InputGray),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "MapLibre Light Viewport",
                        color = TrackingPrimary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            },
            initialExpanded = true
        )
    }
}

@Composable
internal fun ShipmentTrackingPreviewContent(
    initialExpanded: Boolean = false
) {
    val now = Instant.parse("2026-11-19T02:00:00Z")
    val sampleShipment = buildJsonObject {
        put("id", "ship-1")
        put("tracking_number", "SHP-NE-26-002349")
        put("status", "in_transit")
        put("pickup_address", "FRH7+5W3, Cabanatuan City Public Market, Nueva Ecija")
        put("dropoff_address", "G32P+4M Pier 14, Manila North Harbor, Manila")
        put("cargo_description", "200 boxes")
        put("weight_kg", "2500")
        put("driver_notes", "Contact: +639175551234\nCall warehouse supervisor upon arrival at Gate 3.")
        put("quoted_amount", "12500")
        put("final_amount", "12500.00")
        put("scheduled_departure", "2026-11-19T02:00:00Z")
        put("estimated_arrival", "2026-11-19T06:30:00Z")
    }
    val sampleDelivery = buildJsonObject {
        put("id", "del-1")
        put("shipment_id", "ship-1")
        put("status", "in_transit")
        put("assigned_at", "2026-11-19T01:00:00Z")
        put("route_started_at", "2026-11-19T01:15:00Z")
        put("pickup_arrived_at", "2026-11-19T01:30:00Z")
        put("customer_showed_at", "2026-11-19T01:40:00Z")
        put("departed_at", "2026-11-19T01:55:00Z")
        put("driver_name", "Juan Dela Cruz")
        put("driver_phone", "+639171234567")
        put("truck_type", "6-Wheeler Closed Van")
        put("plate_number", "NCV 4821")
    }
    val sampleFreshness = PositionFreshness(
        status = PositionFreshnessStatus.UNAVAILABLE,
        ageSeconds = null,
        relativeText = "GPS position unavailable",
        accuracyText = "accuracy unknown"
    )
    TanawCustomerTheme {
        ShipmentTrackingScreen(
            shipment = sampleShipment,
            deliveries = listOf(sampleDelivery),
            routing = null,
            eta = null,
            freshness = sampleFreshness,
            lastTelemetryRecordedAt = "",
            detailLoaded = true,
            detailLoading = false,
            offline = false,
            busy = false,
            errorMessage = null,
            timelineEvents = listOf(
                "2026-11-19T01:00:00Z" to "Driver Assigned",
                "2026-11-19T01:55:00Z" to "In Transit"
            ),
            now = now,
            onBack = {},
            onRefresh = {},
            mapSlot = { _, mapModifier ->
                Box(
                    modifier = mapModifier.background(InputGray),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "MapLibre Light Viewport",
                        color = TrackingPrimary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            },
            customerCancellationSlot = {
                OutlinedButton(
                    onClick = {},
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, TrackingDanger),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text("Cancel Shipment", color = TrackingDanger, fontWeight = FontWeight.SemiBold)
                }
            },
            initialExpanded = initialExpanded
        )
    }
}
