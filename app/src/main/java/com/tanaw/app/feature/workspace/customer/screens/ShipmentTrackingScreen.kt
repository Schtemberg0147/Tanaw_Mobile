package com.tanaw.app.feature.workspace.customer.screens

import android.content.Intent
import android.content.res.Configuration
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
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
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
import androidx.compose.material3.darkColorScheme
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
import com.tanaw.app.ui.theme.MapPinDestination
import com.tanaw.app.ui.theme.MapPinPickup
import com.tanaw.app.ui.theme.TanawTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

private val TrackingSheetBg = Color(0xFF222831)
private val TrackingSheetElevated = Color(0xFF393E46)
private val TrackingAccentTeal = Color(0xFF00ADB5)
private val TrackingAccentBlue = Color(0xFF0383D5)
private val TrackingTextPrimary = Color(0xFFF8FAFC)
private val TrackingTextSecondary = Color(0xFFCBD5E1)
private val TrackingTextMuted = Color(0xFF94A3B8)
private val TrackingDivider = Color(0xFF4B5563)
private val TrackingAmberDot = Color(0xFFF59E0B)
private val TrackingLiveDot = Color(0xFF22C55E)
private val TrackingDanger = Color(0xFFF87171)

private val TrackingDarkIndustrialColorScheme = darkColorScheme(
    primary = TrackingAccentTeal,
    onPrimary = Color(0xFF081418),
    primaryContainer = Color(0xFF0F3D42),
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = TrackingAccentBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF133854),
    onSecondaryContainer = Color(0xFFBAE6FD),
    background = TrackingSheetBg,
    onBackground = TrackingTextPrimary,
    surface = TrackingSheetBg,
    onSurface = TrackingTextPrimary,
    surfaceContainerHigh = TrackingSheetElevated,
    surfaceVariant = TrackingSheetElevated,
    onSurfaceVariant = TrackingTextSecondary,
    outline = TrackingTextMuted,
    outlineVariant = TrackingDivider,
    error = TrackingDanger,
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF4C1D1D),
    onErrorContainer = Color(0xFFFECACA),
)

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
    documents: List<JsonObject>,
    now: Instant,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onViewProof: (String) -> Unit,
    mapSlot: @Composable (recenterTrigger: Int, modifier: Modifier) -> Unit,
    modifier: Modifier = Modifier,
    detail: JsonObject? = null,
    vehicles: List<JsonObject> = emptyList(),
    customerCancellationSlot: (@Composable () -> Unit)? = null,
    initialExpanded: Boolean = false,
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
    val formattedPickup = remember(shipment) {
        ShipmentTrackingPresentation.formatAddressWithoutPlusCode(shipment.value("pickup_address"))
    }
    val formattedDropoff = remember(shipment) {
        ShipmentTrackingPresentation.formatAddressWithoutPlusCode(shipment.value("dropoff_address"))
    }
    val showProofCard = remember(shipment, primaryDelivery) {
        ShipmentTrackingPresentation.shouldShowProofOfDelivery(shipment, primaryDelivery)
    }
    val showCancelAction = remember(shipment, deliveries) {
        ShipmentTrackingPresentation.shouldShowCancelAction(shipment, deliveries)
    }

    var recenterTrigger by remember { mutableIntStateOf(0) }
    var showFullEventLog by rememberSaveable(shipment.value("id")) { mutableStateOf(false) }

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
        val peekHeight = (maxHeight * 0.46f)
            .coerceIn(272.dp, 368.dp)
            .coerceAtMost(maxHeight * 0.56f)
        val mapViewportHeight = (maxHeight - peekHeight + 24.dp).coerceAtLeast(260.dp)
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
                            .background(TrackingTextMuted.copy(alpha = 0.55f))
                    )
                }
            },
            sheetContent = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = expandedSheetMaxHeight)
                ) {
                    PeekStatusSection(
                        summary = sheetSummary,
                        driverSummary = driverSummary,
                        isExpanded = sheetState.currentValue == SheetValue.Expanded,
                        onToggleExpand = toggleSheet,
                        onCallDriver = { driverSummary.phoneNumber?.let(openDialer) },
                        onMessageDriver = { driverSummary.phoneNumber?.let(openSms) }
                    )

                    HorizontalDivider(
                        color = TrackingSheetElevated,
                        thickness = 1.dp,
                        modifier = Modifier.padding(top = 14.dp)
                    )

                    MaterialTheme(colorScheme = TrackingDarkIndustrialColorScheme) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp, vertical = 16.dp)
                                .navigationBarsPadding(),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            if (offline) {
                                Surface(
                                    color = TrackingSheetElevated,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, TrackingAmberDot.copy(alpha = 0.45f)),
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

                            SheetCard(title = "Delivery Milestones") {
                                sheetSummary.steps.forEachIndexed { idx, step ->
                                    MilestoneDetailRow(step = step)
                                    if (idx < sheetSummary.steps.lastIndex) {
                                        HorizontalDivider(
                                            color = TrackingDivider.copy(alpha = 0.45f),
                                            thickness = 0.5.dp
                                        )
                                    }
                                }
                                if (detailLoaded && timelineEvents.isNotEmpty()) {
                                    HorizontalDivider(color = TrackingDivider)
                                    OutlinedButton(
                                        onClick = { showFullEventLog = true },
                                        border = BorderStroke(1.dp, TrackingDivider),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                    ) {
                                        Text(
                                            text = "View full event log (${timelineEvents.size})",
                                            fontWeight = FontWeight.SemiBold,
                                            color = TrackingAccentTeal
                                        )
                                    }
                                }
                            }

                            SheetCard(title = "Route & Cargo") {
                                DarkRouteConnectorBlock(
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
                                            color = TrackingTextMuted
                                        )
                                        Text(
                                            text = cargoSummary.weightText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TrackingTextPrimary
                                        )
                                    }
                                }
                                HorizontalDivider(
                                    color = TrackingDivider.copy(alpha = 0.45f),
                                    thickness = 0.5.dp
                                )
                                KeyValueRow(
                                    label = "Recipient Contact",
                                    value = cargoSummary.recipientContact,
                                    boldValue = cargoSummary.recipientContact != "Not provided"
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
                                    }
                                )
                                if (shipment.value("scheduled_departure").isNotBlank()) {
                                    KeyValueRow(
                                        label = "Scheduled Departure",
                                        value = MobilePresentation.time(shipment.value("scheduled_departure"))
                                    )
                                }
                                if (lastTelemetryRecordedAt.isNotBlank() && freshness != null) {
                                    KeyValueRow(
                                        label = "Last GPS Update",
                                        value = "${MobilePresentation.time(lastTelemetryRecordedAt)} · ${freshness.accuracyText}"
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
                                badge = if (pricingSummary.awaitingQuotation) "Awaiting quotation" else null
                            ) {
                                pricingSummary.rows.forEach { row ->
                                    KeyValueRow(
                                        label = row.label,
                                        value = row.value,
                                        valueColor = if (row.isEmphasized) TrackingAccentTeal else TrackingTextPrimary,
                                        boldValue = row.isEmphasized
                                    )
                                }
                            }

                            if (showProofCard) {
                                SheetCard(title = "Proof of Delivery") {
                                    if (detailLoaded) {
                                        if (documents.isEmpty()) {
                                            Text(
                                                text = "No proof documents have been uploaded yet.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = TrackingTextSecondary
                                            )
                                        } else {
                                            documents.forEach { d ->
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = TrackingSheetBg,
                                                    border = BorderStroke(1.dp, TrackingDivider),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(
                                                        modifier = Modifier.padding(14.dp),
                                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(
                                                            text = "${MobilePresentation.status(d.value("kind").replace('_', ' '))} · ${d.value("file_name").ifBlank { "Document recorded" }}",
                                                            style = MaterialTheme.typography.titleSmall,
                                                            color = TrackingTextPrimary
                                                        )
                                                        Text(
                                                            text = "Recipient: ${d.value("recipient_name").ifBlank { "Pending" }}",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = TrackingTextSecondary
                                                        )
                                                        Text(
                                                            text = MobilePresentation.time(d.value("created_at")),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = TrackingTextSecondary
                                                        )
                                                        Spacer(Modifier.height(2.dp))
                                                        OutlinedButton(
                                                            onClick = { onViewProof(d.value("id")) },
                                                            enabled = !busy,
                                                            border = BorderStroke(1.dp, TrackingAccentTeal),
                                                            shape = RoundedCornerShape(10.dp),
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .heightIn(min = 48.dp)
                                                        ) {
                                                            Text(
                                                                text = "View / download",
                                                                fontWeight = FontWeight.Bold,
                                                                color = TrackingAccentTeal
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } else if (detailLoading) {
                                        Text(
                                            text = "Syncing delivery details…",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TrackingTextSecondary
                                        )
                                    } else {
                                        Text(
                                            text = "Tap refresh above to reload delivery documents.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TrackingTextSecondary
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
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(TrackingSheetBg)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(mapViewportHeight)
                        .align(Alignment.TopCenter)
                ) {
                    mapSlot(recenterTrigger, Modifier.fillMaxSize())

                    FloatingEtaPill(
                        etaPill = etaPill,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(top = 68.dp, start = 16.dp, end = 16.dp)
                    )

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
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
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
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
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
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            if (busy) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = TrackingAccentTeal
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
        etaPill.isCompleted -> TrackingAccentTeal
        etaPill.isCancelled -> TrackingDanger
        else -> TrackingAmberDot
    }
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = TrackingSheetBg.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, TrackingSheetElevated),
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
                fontWeight = FontWeight.SemiBold,
                color = TrackingTextPrimary,
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
        color = TrackingSheetBg.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, TrackingSheetElevated),
        shadowElevation = 4.dp
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = Icons.Outlined.MyLocation,
                contentDescription = "Recenter map",
                tint = TrackingAccentTeal,
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
                fontWeight = FontWeight.Bold,
                color = TrackingTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (summary.currentStatusTimestamp.isNotBlank()) {
                Spacer(Modifier.width(12.dp))
                Text(
                    text = summary.currentStatusTimestamp,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = TrackingTextSecondary,
                    maxLines = 1
                )
            }
        }

        val activeOrCompletedCount = summary.steps.count { it.isCompleted || it.isActive }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = "Delivery progress: $activeOrCompletedCount of 6 steps reached, ${summary.currentStatusTitle}"
                },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            summary.steps.forEach { step ->
                val segmentColor = when {
                    step.isCompleted -> TrackingAccentTeal
                    step.isActive -> TrackingAccentBlue
                    else -> TrackingSheetElevated
                }
                val segmentModifier = Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(segmentColor)
                    .let { base ->
                        if (step.isActive) {
                            base.border(1.dp, TrackingAccentTeal, CircleShape)
                        } else {
                            base
                        }
                    }
                Box(modifier = segmentModifier)
            }
        }

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
private fun DriverVehiclePeekCard(
    driver: TrackingDriverSummary,
    onCall: () -> Unit,
    onMessage: () -> Unit
) {
    val hasPhone = driver.phoneNumber != null
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = TrackingSheetElevated,
        border = BorderStroke(1.dp, TrackingDivider.copy(alpha = 0.6f))
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
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        if (driver.hasDriverAssigned) TrackingAccentTeal.copy(alpha = 0.16f)
                        else TrackingSheetBg.copy(alpha = 0.7f)
                    )
                    .border(
                        width = 1.dp,
                        color = if (driver.hasDriverAssigned) TrackingAccentTeal.copy(alpha = 0.45f)
                        else TrackingDivider.copy(alpha = 0.6f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (driver.initials.isNotBlank()) {
                    Text(
                        text = driver.initials,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TrackingAccentTeal
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        tint = if (driver.hasDriverAssigned) TrackingAccentTeal else TrackingTextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = driver.driverName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TrackingTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = driver.vehicleSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TrackingTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                CircularDriverActionButton(
                    icon = Icons.Outlined.Call,
                    contentDescription = if (hasPhone) "Call driver" else "Call driver unavailable",
                    enabled = hasPhone,
                    activeTint = TrackingAccentTeal,
                    onClick = onCall
                )
                CircularDriverActionButton(
                    icon = Icons.Outlined.Sms,
                    contentDescription = if (hasPhone) "Message driver" else "Message driver unavailable",
                    enabled = hasPhone,
                    activeTint = Color(0xFF38BDF8),
                    onClick = onMessage
                )
            }
        }
    }
}

@Composable
private fun CircularDriverActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    activeTint: Color,
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
                .background(
                    if (enabled) TrackingSheetBg.copy(alpha = 0.88f)
                    else TrackingSheetBg.copy(alpha = 0.42f)
                )
                .border(
                    width = 1.dp,
                    color = if (enabled) activeTint.copy(alpha = 0.45f)
                    else TrackingDivider.copy(alpha = 0.35f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) activeTint else TrackingTextMuted.copy(alpha = 0.38f),
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
    MaterialTheme(colorScheme = TrackingDarkIndustrialColorScheme) {
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
                        fontWeight = FontWeight.Bold,
                        color = TrackingTextPrimary
                    )
                    Text(
                        text = "$trackingNumber · ${timelineEvents.size} recorded updates",
                        style = MaterialTheme.typography.bodySmall,
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
                            color = TrackingSheetElevated,
                            border = BorderStroke(1.dp, TrackingDivider.copy(alpha = 0.55f)),
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
                        color = TrackingAccentTeal
                    )
                }
            }
        )
    }
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
        color = TrackingSheetElevated,
        border = BorderStroke(1.dp, TrackingDivider.copy(alpha = 0.6f))
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
                    fontWeight = FontWeight.Bold,
                    color = TrackingTextPrimary,
                    modifier = Modifier.weight(1f)
                )
                if (!badge.isNullOrBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = TrackingSheetBg
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelMedium,
                            color = TrackingAccentTeal,
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
private fun MilestoneDetailRow(step: ShipmentMilestoneStep) {
    val dotColor = when {
        step.isCompleted -> TrackingAccentTeal
        step.isActive -> TrackingAccentBlue
        else -> TrackingTextMuted.copy(alpha = 0.45f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor)
                .let { base ->
                    if (step.isActive) base.border(1.5.dp, TrackingAccentTeal, CircleShape) else base
                }
        )
        Text(
            text = step.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (step.isCompleted || step.isActive) FontWeight.SemiBold else FontWeight.Normal,
            color = if (step.isCompleted || step.isActive) TrackingTextPrimary else TrackingTextSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = step.formattedTime.ifBlank { "Pending" },
            style = MaterialTheme.typography.bodySmall,
            color = if (step.isCompleted || step.isActive) TrackingTextSecondary else TrackingTextMuted
        )
    }
}

@Composable
private fun DarkRouteConnectorBlock(
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
    ShipmentTrackingPreviewContent(
        darkTheme = false,
        initialExpanded = false
    )
}

@Preview(showBackground = true, name = "Tracking Screen Expanded (Dark)", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ShipmentTrackingExpandedPreview() {
    ShipmentTrackingPreviewContent(
        darkTheme = true,
        initialExpanded = true
    )
}

@Composable
internal fun ShipmentTrackingPreviewContent(
    darkTheme: Boolean,
    initialExpanded: Boolean = false
) {
    val now = Instant.parse("2026-11-19T02:00:00Z")
    val sampleShipment = buildJsonObject {
        put("id", "ship-1")
        put("tracking_number", "SHP-NE-26-002349")
        put("status", "assigned")
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
        put("status", "assigned")
        put("assigned_at", "2026-11-19T01:44:00Z")
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
    TanawTheme(darkTheme = darkTheme) {
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
            timelineEvents = listOf("2026-11-19T01:44:00Z" to "Driver Assigned"),
            documents = emptyList(),
            now = now,
            onBack = {},
            onRefresh = {},
            onViewProof = {},
            mapSlot = { _, mapModifier ->
                Box(
                    modifier = mapModifier.background(Color(0xFFDBEAFE)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "MapLibre Edge-to-Edge Viewport",
                        color = Color(0xFF1E3A8A),
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
