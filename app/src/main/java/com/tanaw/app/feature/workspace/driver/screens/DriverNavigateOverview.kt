package com.tanaw.app.feature.workspace.driver.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.*
import org.maplibre.android.geometry.LatLng
import java.time.Instant
import com.tanaw.app.feature.workspace.driver.components.*
import com.tanaw.app.feature.workspace.model.RoutePreview
import com.tanaw.app.feature.workspace.rules.*
import com.tanaw.app.feature.workspace.state.*
import com.tanaw.app.feature.workspace.tracking.*
import com.tanaw.app.feature.workspace.viewmodel.*
import com.tanaw.app.ui.components.*
import com.tanaw.app.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

private enum class NavigateSection(val label: String) {
    ROUTE("Route & Advisory"),
    CHECKPOINTS("Checkpoints"),
    LOGS("Trip Logs")
}

@Composable internal fun DriverTripSection(title:String,subtitle:String?=null,content:@Composable ColumnScope.()->Unit) {
    Surface(
        Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
        }
    }
}

/** Saved stop pins only. A stale/unrelated GPS sample must not zoom this preview out to the world. */
internal fun driverStopPins(s:JsonObject):List<Pair<String,LatLng>> =
    listOf("Pickup" to "pickup","Destination" to "dropoff").mapNotNull {(label,prefix)->
        val lat=s.value("${prefix}_lat").toDoubleOrNull();val lng=s.value("${prefix}_lng").toDoubleOrNull()
        if(lat!=null && lng!=null && lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0)label to LatLng(lat,lng) else null
    }

internal fun driverMapPins(s: JsonObject, details: JsonObject?, now: Instant = Instant.now()): List<Pair<String, LatLng>> {
    val stops = driverStopPins(s)
    val matchDetails = details?.takeIf { it.value("shipment_id").isBlank() || it.value("shipment_id") == s.value("id") }
    val pos = matchDetails?.obj("position") ?: return stops
    val lat = pos.value("latitude").toDoubleOrNull()
    val lng = pos.value("longitude").toDoubleOrNull()
    if (lat == null || lng == null || !lat.isFinite() || !lng.isFinite() || lat !in -90.0..90.0 || lng !in -180.0..180.0) {
        return stops
    }
    val freshness = TrackingFreshnessRules.evaluatePositionFreshness(
        pos.value("recorded_at"),
        pos.value("accuracy_m"),
        now
    )
    if (freshness.status !in listOf(PositionFreshnessStatus.FRESH, PositionFreshnessStatus.RECENT)) {
        return stops
    }
    if (stops.isNotEmpty()) {
        val nearStop = stops.any { (_, stop) ->
            kotlin.math.abs(stop.latitude - lat) <= 5.0 && kotlin.math.abs(stop.longitude - lng) <= 5.0
        }
        if (!nearStop) return stops
    } else {
        if (lat !in 4.5..21.5 || lng !in 116.0..127.0) return stops
    }
    return stops + ("Vehicle" to LatLng(lat, lng))
}

internal fun normalizeRouteGeoJson(element: JsonElement?): String? = when (element) {
    null, is JsonNull -> null
    is JsonPrimitive -> element.contentOrNull?.trim()?.takeIf {
        it.startsWith("{") && it.endsWith("}") && it.contains("\"type\"")
    }
    is JsonObject -> element.takeIf { it.isNotEmpty() && it.containsKey("type") }?.toString()
    else -> null
}

@Composable internal fun DriverNavigateOverview(
    s: JsonObject,
    d: JsonObject,
    state: MobileState,
    actions: @Composable (JsonObject, JsonObject) -> Unit,
    expense: () -> Unit,
    fuel: (() -> Unit)? = null,
    routePreview: (suspend (String, LatLng, LatLng) -> RoutePreview)? = null,
    onMessageOffice: (() -> Unit)? = null,
    tripLogsContent: (@Composable () -> Unit)? = null
) {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15.seconds)
            now = Instant.now()
        }
    }
    val stage = DriverUiRules.stage(d)
    val headings = listOf(
        "Ready to start route",
        "Heading to Pick-up",
        "Waiting for Customer",
        "Waiting to Load Cargo",
        "Heading to Destination",
        "Delivery submitted"
    )
    val next = listOf(
        "Start your route when you are ready to leave.",
        "Go to the pickup, then confirm your arrival.",
        "Confirm when the customer is present.",
        "Confirm when all cargo has been loaded.",
        "Go to the destination, then submit delivery proof.",
        "The office will review your delivery proof."
    )
    var mapOpen by rememberSaveable(s.value("id")) { mutableStateOf(false) }
    var selectedSection by rememberSaveable(s.value("id")) { mutableStateOf(NavigateSection.ROUTE.name) }
    val activeSection = NavigateSection.entries.firstOrNull { it.name == selectedSection }
        ?.takeIf { it != NavigateSection.LOGS || tripLogsContent != null }
        ?: NavigateSection.ROUTE

    val points = driverStopPins(s)
    val mapPoints = driverMapPins(s, state.details, now)
    val pinIdentity = points.map { Triple(it.first, it.second.latitude, it.second.longitude) }
    val routing = state.details?.takeIf { it.value("shipment_id") == s.value("id") }?.obj("routing")
        ?: if (state.detailLoading && !state.offline) buildJsonObject { put("loading", true) } else buildJsonObject {}
    val verifiedRouting = state.details?.takeIf { it.value("shipment_id") == s.value("id") }?.obj("routing")
        ?.let { DriverRouteStatus.forAssignment(it, s, d) }
    val savedGeometry = normalizeRouteGeoJson(s["route_geometry"])
        ?: verifiedRouting?.takeIf { it["route_verified"] == JsonPrimitive(true) }?.obj("plan")
            ?.let { normalizeRouteGeoJson(it["geometry"]) }
    var preview by remember(s.value("id"), d.value("id"), d.value("driver_id"), pinIdentity) { mutableStateOf<RoutePreview?>(null) }
    var previewError by remember(s.value("id"), d.value("id"), pinIdentity) { mutableStateOf<String?>(null) }
    var previewLoading by remember { mutableStateOf(false) }
    var previewRevision by remember { mutableIntStateOf(0) }
    val previewLoader by rememberUpdatedState(routePreview)

    LaunchedEffect(mapOpen, s.value("id"), d.value("id"), d.value("driver_id"), pinIdentity, savedGeometry, state.offline, previewRevision) {
        previewLoading = false
        if (!mapOpen || savedGeometry != null || preview != null) return@LaunchedEffect
        if (state.offline) {
            previewError = "Road preview unavailable offline. Reconnect and retry."
            return@LaunchedEffect
        }
        if (points.size != 2) {
            previewError = "The office must save both pickup and destination pins before a road preview is available."
            return@LaunchedEffect
        }
        val loader = previewLoader
        if (loader == null) {
            previewError = "Road preview unavailable. Refresh the trip and retry."
            return@LaunchedEffect
        }
        previewError = null
        previewLoading = true
        try {
            preview = loader(d.value("id"), points[0].second, points[1].second)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            previewError = "Road preview unavailable. Check your connection and the assignment pins, then retry."
        } finally {
            previewLoading = false
        }
    }

    val extendedColors = TanawTheme.extendedColors
    val windowSize = LocalTanawWindowSize.current
    val showExpense = d.value("status") in listOf("in_transit", "delivered")
    val evaluatedRouting = DriverRouteStatus.forAssignment(routing, s, d)
    val etaLine = DriverRouteStatus.eta(evaluatedRouting, state.offline, now)

    val stageHeroCard: @Composable () -> Unit = {
        Surface(
            color = DriverNavy,
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        s.value("tracking_number").ifBlank { "Current trip" },
                        color = Color(0xFFC7DFFF),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            "Step ${stage + 1} of 6",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
                Text(
                    headings[stage],
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    next[stage],
                    color = Color(0xFFE2ECF7),
                    style = MaterialTheme.typography.bodyMedium
                )
                HorizontalDivider(color = Color.White.copy(alpha = .2f))
                Text(
                    if (stage >= 4) "DESTINATION" else "PICKUP",
                    color = Color(0xFFC7DFFF),
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    s.value(if (stage >= 4) "dropoff_address" else "pickup_address").ifBlank { "Address unavailable — contact the office" },
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    etaLine,
                    color = Color(0xFFC7DFFF),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    val nextStepSection: @Composable () -> Unit = {
        DriverTripSection("Next step", "Update the trip only when the action has actually happened.") {
            if (d.value("id").isBlank()) {
                Text("No delivery assignment is available. Refresh or contact the office.", style = MaterialTheme.typography.bodyMedium)
            } else {
                actions(s, d)
            }
            if (fuel != null || showExpense || onMessageOffice != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                QuickOperationsStrip(
                    fuel = fuel,
                    expense = if (showExpense) expense else null,
                    messageOffice = onMessageOffice,
                    enabled = !state.busy && !state.offline,
                    messageEnabled = !state.busy
                )
            }
        }
    }

    val detailsPane: @Composable () -> Unit = {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NavigateSection.entries.forEach { section ->
                    if (section == NavigateSection.LOGS && tripLogsContent == null) return@forEach
                    FilterChip(
                        selected = activeSection == section,
                        onClick = { selectedSection = section.name },
                        label = { Text(section.label) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
            }

            when (activeSection) {
                NavigateSection.ROUTE -> {
                    DriverTripSection("Directions & route", "Open directions for travel. The saved map is a location preview.") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 4.dp)) {
                                    Box(Modifier.size(10.dp).clip(CircleShape).background(MapPinPickup))
                                    Box(Modifier.width(2.dp).height(28.dp).background(MaterialTheme.colorScheme.outlineVariant))
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("PICKUP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(s.value("pickup_address").ifBlank { "Not recorded" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                                Box(Modifier.padding(top = 4.dp).size(10.dp).clip(CircleShape).background(MapPinDestination))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("DESTINATION", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(s.value("dropoff_address").ifBlank { "Not recorded" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                        DriverDirectionsButtons(s, stage, savedGeometry)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        DriverRouteStatusCard(evaluatedRouting, state.offline)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (s.value("scheduled_departure").isNotBlank()) {
                                KeyValueRow("Scheduled departure", MobilePresentation.time(s.value("scheduled_departure")))
                            }
                            KeyValueRow("Office planned arrival", MobilePresentation.time(s.value("estimated_arrival")))
                            KeyValueRow(
                                "Recorded distance",
                                "${MobilePresentation.number(s.value("distance_km"), "km")} · ${MobilePresentation.number(s.value("route_duration_minutes"), "min")}"
                            )
                            if (s.value("weight_kg").isNotBlank()) {
                                KeyValueRow("Cargo weight", MobilePresentation.number(s.value("weight_kg"), "kg"))
                            }
                        }
                        val truck = DriverUiRules.truck(state.data ?: buildJsonObject {})
                        val truckSpec = TruckRestrictionRules.evaluateTruckSpecs(truck, s, verifiedRouting?.obj("plan"))
                        val banAdvisory = TruckRestrictionRules.evaluateBanAdvisory(
                            truckSpec.plateNumber,
                            s.value("scheduled_departure"),
                            s.value("estimated_arrival"),
                            now
                        )
                        DriverTruckRestrictionCard(truckSpec, banAdvisory)
                        if (points.isEmpty()) {
                            Text(
                                "This assignment has no saved map pins. Use address directions and confirm the entrance with the office.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            OutlinedButton(
                                onClick = { mapOpen = !mapOpen },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            ) {
                                Text(if (mapOpen) "Hide route map" else "Show route map")
                            }
                            if (mapOpen) {
                                if (savedGeometry == null) {
                                    Surface(
                                        color = extendedColors.warningContainer,
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("Unverified road preview", style = MaterialTheme.typography.titleSmall, color = extendedColors.onWarningContainer)
                                            Text(
                                                "Not office-confirmed. Truck height, width, loaded weight and access restrictions have not been checked. This preview does not change the confirmed route or live ETA.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (previewLoading) {
                                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                                Text("Loading unverified road preview…", style = MaterialTheme.typography.bodySmall)
                                            }
                                            previewError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                                            if (previewError != null) {
                                                OutlinedButton(
                                                    onClick = { previewRevision++ },
                                                    enabled = !state.offline && !previewLoading,
                                                    shape = RoundedCornerShape(10.dp),
                                                    modifier = Modifier.heightIn(min = 48.dp)
                                                ) { Text("Retry road preview") }
                                            }
                                            preview?.let { Text("Preview road distance: ${it.distanceKm} km", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                        }
                                    }
                                }
                                ShipmentMap(mapPoints, savedGeometry ?: preview?.geometry)
                                Text(
                                    if (mapPoints.any { it.first == "Vehicle" }) "Saved pickup, destination, and your fresh vehicle GPS position."
                                    else "Saved pickup and destination locations. Vehicle GPS is not shown on this preview.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (savedGeometry == null && preview == null && !previewLoading) {
                                    Text(
                                        if (mapPoints.any { it.first == "Vehicle" })
                                            "Showing saved stops and your vehicle GPS until a road preview is available."
                                        else
                                            "Showing saved stops only until a road preview is available.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                NavigateSection.CHECKPOINTS -> {
                    DriverTripSection("Delivery progress", "Current stage: ${headings[stage]}") {
                        val steps = listOf(
                            "Assigned" to "assigned_at",
                            "Route started" to "route_started_at",
                            "Arrived at pickup" to "pickup_arrived_at",
                            "Customer present" to "customer_showed_at",
                            "Cargo loaded" to "departed_at",
                            "Proof submitted" to "arrived_at"
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            steps.forEachIndexed { index, (label, field) ->
                                val raw = d.value(field)
                                val recorded = runCatching { Instant.parse(raw) }.isSuccess
                                val dotColor = when {
                                    recorded -> extendedColors.onSuccessBannerText
                                    index == stage -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.outlineVariant
                                }
                                val itemColor = if (recorded) {
                                    Color(0xFFEFF6FF)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(top = 14.dp)
                                    ) {
                                        Box(Modifier.size(12.dp).clip(CircleShape).background(dotColor))
                                        if (index < steps.lastIndex) {
                                            Box(Modifier.width(2.dp).height(34.dp).background(MaterialTheme.colorScheme.outlineVariant))
                                        }
                                    }
                                    Surface(
                                        color = itemColor,
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("${index + 1}. $label", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                            Text(
                                                if (recorded) MobilePresentation.time(raw) else if (index > stage) "Not reached yet" else "Time not available",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                NavigateSection.LOGS -> {
                    tripLogsContent?.invoke()
                }
            }
        }
    }

    if (windowSize.useTwoPane) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = windowSize.horizontalGutter, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(0.45f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                stageHeroCard()
                nextStepSection()
            }
            Box(modifier = Modifier.weight(0.55f)) {
                detailsPane()
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = windowSize.horizontalGutter, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            stageHeroCard()
            nextStepSection()
            detailsPane()
        }
    }
}

@Composable
private fun QuickOperationsStrip(
    fuel: (() -> Unit)?,
    expense: (() -> Unit)?,
    messageOffice: (() -> Unit)?,
    enabled: Boolean,
    messageEnabled: Boolean
) {
    val actions = buildList {
        fuel?.let { add(Triple("Record Fuel", Icons.Outlined.LocalGasStation, it to enabled)) }
        expense?.let { add(Triple("Add Trip Expense", Icons.AutoMirrored.Outlined.ReceiptLong, it to enabled)) }
        messageOffice?.let { add(Triple("Message Office", Icons.Outlined.ChatBubbleOutline, it to messageEnabled)) }
    }
    if (actions.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.chunked(2).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { (label, icon, actionPair) ->
                    QuickOperationButton(
                        label = label,
                        icon = icon,
                        onClick = actionPair.first,
                        enabled = actionPair.second,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowItems.size == 1 && actions.size > 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun QuickOperationButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@FormFactorPreviews
@Composable
private fun DriverNavigateOverviewLightPreview() {
    DriverTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            DriverNavigateOverview(
                s = buildJsonObject {
                    put("id", "shp-1")
                    put("tracking_number", "TNW-2026-0042")
                    put("pickup_address", "Cabanatuan City Hub, Nueva Ecija")
                    put("dropoff_address", "Gapan City Distribution Centre, Nueva Ecija")
                    put("distance_km", "38")
                    put("route_duration_minutes", "75")
                    put("weight_kg", "6000")
                },
                d = buildJsonObject {
                    put("id", "del-1")
                    put("shipment_id", "shp-1")
                    put("status", "assigned")
                    put("route_started_at", "2026-11-15T01:00:00Z")
                    put("assigned_at", "2026-11-15T00:30:00Z")
                },
                state = MobileState(),
                actions = { _, _ -> DriverButton("I'VE ARRIVED AT PICK-UP") {} },
                expense = {},
                fuel = {},
                onMessageOffice = {}
            )
        }
    }
}
