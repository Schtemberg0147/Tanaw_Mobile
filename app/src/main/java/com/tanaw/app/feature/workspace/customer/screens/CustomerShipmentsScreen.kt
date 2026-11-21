package com.tanaw.app.feature.workspace.customer.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanaw.app.feature.workspace.customer.rules.CustomerHistoryPresentation
import com.tanaw.app.feature.workspace.rules.MobilePresentation
import com.tanaw.app.feature.workspace.state.MobileState
import com.tanaw.app.feature.workspace.viewmodel.value
import com.tanaw.app.ui.theme.BackgroundGray
import com.tanaw.app.ui.theme.BorderGray
import com.tanaw.app.ui.theme.ElectricBlueSecondary
import com.tanaw.app.ui.theme.ExpiredRed
import com.tanaw.app.ui.theme.ExpiredRedBg
import com.tanaw.app.ui.theme.FormFactorPreviews
import com.tanaw.app.ui.theme.HintGray
import com.tanaw.app.ui.theme.InputGray
import com.tanaw.app.ui.theme.LocalTanawWindowSize
import com.tanaw.app.ui.theme.MapPinDestination
import com.tanaw.app.ui.theme.MapPinPickup
import com.tanaw.app.ui.theme.NavyPrimary
import com.tanaw.app.ui.theme.OnSecondaryContainerBlue
import com.tanaw.app.ui.theme.OutlineGray
import com.tanaw.app.ui.theme.PrimaryContainerLight
import com.tanaw.app.ui.theme.TanawCustomerTheme
import com.tanaw.app.ui.theme.TanawDisplayFontFamily
import com.tanaw.app.ui.theme.TanawMonoFontFamily
import com.tanaw.app.ui.theme.TextDark
import com.tanaw.app.ui.theme.WindowWidthClass
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Immutable
internal data class CustomerShipmentsPalette(
    val background: Color,
    val card: Color,
    val cardBorder: Color,
    val inputBg: Color,
    val accentPrimary: Color,
    val accentPrimaryTextOnCard: Color,
    val accentBlue: Color,
    val accentBlueTextOnCard: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val chipSelectedBg: Color,
    val chipSelectedBorder: Color,
    val chipSelectedText: Color,
    val chipUnselectedBg: Color,
    val chipUnselectedBorder: Color,
    val chipUnselectedText: Color,
    val offlineBannerBg: Color,
    val offlineBannerBorder: Color,
    val offlineBannerText: Color,
    val offlineBannerAction: Color,
    val errorBg: Color,
    val errorBorder: Color,
    val errorText: Color,
)

internal fun customerShipmentsPalette(): CustomerShipmentsPalette = CustomerShipmentsPalette(
    background = BackgroundGray,
    card = Color.White,
    cardBorder = BorderGray,
    inputBg = InputGray,
    accentPrimary = NavyPrimary,
    accentPrimaryTextOnCard = NavyPrimary,
    accentBlue = ElectricBlueSecondary,
    accentBlueTextOnCard = OnSecondaryContainerBlue,
    textPrimary = TextDark,
    textSecondary = HintGray,
    textMuted = OutlineGray,
    chipSelectedBg = PrimaryContainerLight,
    chipSelectedBorder = NavyPrimary,
    chipSelectedText = NavyPrimary,
    chipUnselectedBg = Color.White,
    chipUnselectedBorder = BorderGray,
    chipUnselectedText = HintGray,
    offlineBannerBg = InputGray,
    offlineBannerBorder = BorderGray,
    offlineBannerText = TextDark,
    offlineBannerAction = NavyPrimary,
    errorBg = ExpiredRedBg,
    errorBorder = Color(0xFFFCA5A5),
    errorText = ExpiredRed,
)

@Composable
internal fun CustomerShipmentsSection(
    tab: String,
    query: String,
    onQueryChange: (String) -> Unit,
    historyFilter: String,
    onHistoryFilterChange: (String) -> Unit,
    shipments: List<JsonObject>,
    deliveries: List<JsonObject>,
    documents: List<JsonObject>,
    isCurrentTabLoaded: Boolean,
    state: MobileState,
    onOpenShipment: (String) -> Unit,
    onViewDocument: (shipmentId: String, documentId: String) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    showHeaderTitle: Boolean = false,
) {
    val palette = remember { customerShipmentsPalette() }
    val focusManager = LocalFocusManager.current
    val windowSize = LocalTanawWindowSize.current
    val gridColumns = if (windowSize.widthClass == WindowWidthClass.COMPACT) 1 else 2
    var offlineBannerDismissed by rememberSaveable(state.offline) { mutableStateOf(false) }
    var pendingProofDocId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(state.busy) {
        if (!state.busy) {
            pendingProofDocId = null
        }
    }

    val isHistoryTab = tab == "History"
    val showOfflineBanner = state.offline && !offlineBannerDismissed
    val isGlobalOfflineErrorRedundant = CustomerHistoryPresentation.isRedundantOfflineError(
        offline = state.offline,
        error = state.error
    )
    val isListOfflineErrorRedundant = CustomerHistoryPresentation.isRedundantOfflineError(
        offline = state.offline,
        error = state.customerShipmentsError
    )
    val visibleGlobalError = state.error?.takeIf { !isGlobalOfflineErrorRedundant }
    val visibleListError = state.customerShipmentsError?.takeIf { !isListOfflineErrorRedundant }
    val showLoadingBar = state.customerShipmentsLoading &&
        (!isCurrentTabLoaded || state.customerShipments == null)

    Surface(
        modifier = modifier.fillMaxSize(),
        color = palette.background
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns),
            modifier = Modifier
                .widthIn(max = windowSize.wideContentMaxWidth)
                .fillMaxSize()
                .imePadding(),
            contentPadding = PaddingValues(
                start = windowSize.horizontalGutter,
                end = windowSize.horizontalGutter,
                top = 16.dp,
                bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (showHeaderTitle) {
                        Text(
                            text = if (isHistoryTab) "Shipment history" else "Active shipments",
                            style = MaterialTheme.typography.headlineSmall,
                            fontFamily = TanawDisplayFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = palette.accentPrimary
                        )
                    }
                    Text(
                        text = if (isHistoryTab) {
                            "Past deliveries, cancellation records, and proof documents."
                        } else {
                            "Search your ongoing deliveries by reference or address."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary
                    )
                }
            }

            if (showOfflineBanner) {
                item(key = "offline_banner", span = { GridItemSpan(maxLineSpan) }) {
                    ConnectionStatusBanner(
                        palette = palette,
                        onRetry = onRetry,
                        onDismiss = { offlineBannerDismissed = true }
                    )
                }
            }

            if (visibleGlobalError != null) {
                item(key = "global_error", span = { GridItemSpan(maxLineSpan) }) {
                    ShipmentErrorBanner(
                        message = visibleGlobalError,
                        palette = palette,
                        onRetry = null
                    )
                }
            }

            if (visibleListError != null) {
                item(key = "list_error", span = { GridItemSpan(maxLineSpan) }) {
                    ShipmentErrorBanner(
                        message = visibleListError,
                        palette = palette,
                        onRetry = onRetry
                    )
                }
            }

            item(key = "search_bar", span = { GridItemSpan(maxLineSpan) }) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = {
                        Text(
                            text = "Search reference or address",
                            color = palette.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                            tint = palette.textMuted
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(
                                onClick = { onQueryChange("") },
                                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Cancel,
                                    contentDescription = "Clear search",
                                    tint = palette.textMuted
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = palette.inputBg,
                        unfocusedContainerColor = palette.inputBg,
                        focusedBorderColor = palette.accentPrimary,
                        unfocusedBorderColor = palette.cardBorder,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary,
                        cursorColor = palette.accentPrimary
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                )
            }

            if (isHistoryTab) {
                item(key = "history_filters", span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("All", "Completed", "Cancelled").forEach { filterOption ->
                            val selected = historyFilter == filterOption
                            FilterChip(
                                selected = selected,
                                onClick = { onHistoryFilterChange(filterOption) },
                                shape = RoundedCornerShape(10.dp),
                                leadingIcon = if (selected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Outlined.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                label = {
                                    Text(
                                        text = filterOption,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = palette.chipUnselectedBg,
                                    labelColor = palette.chipUnselectedText,
                                    iconColor = palette.chipUnselectedText,
                                    selectedContainerColor = palette.chipSelectedBg,
                                    selectedLabelColor = palette.chipSelectedText,
                                    selectedLeadingIconColor = palette.chipSelectedText
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = palette.chipUnselectedBorder,
                                    selectedBorderColor = palette.chipSelectedBorder,
                                    borderWidth = 1.dp,
                                    selectedBorderWidth = 1.25.dp
                                )
                            )
                        }
                    }
                }
            }

            if (showLoadingBar) {
                item(key = "loading_bar", span = { GridItemSpan(maxLineSpan) }) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp)),
                        color = palette.accentPrimary,
                        trackColor = palette.cardBorder.copy(alpha = 0.4f)
                    )
                }
            }

            if (shipments.isEmpty() && !state.customerShipmentsLoading) {
                item(key = "empty_state", span = { GridItemSpan(maxLineSpan) }) {
                    EmptyShipmentsCard(
                        isHistoryTab = isHistoryTab,
                        query = query,
                        historyFilter = historyFilter,
                        palette = palette
                    )
                }
            } else if (!state.customerShipmentsLoading || shipments.isNotEmpty()) {
                items(
                    items = shipments,
                    key = { it.value("id").ifBlank { it.value("tracking_number") + it.value("created_at") } }
                ) { shipment ->
                    val shipmentId = shipment.value("id")
                    val proofDocs = remember(shipmentId, documents) {
                        CustomerHistoryPresentation.resolveProofDocuments(shipmentId, documents)
                    }
                    CustomerShipmentCard(
                        shipment = shipment,
                        deliveries = deliveries,
                        podDocument = proofDocs.podDocument,
                        signatureDocument = proofDocs.signatureDocument,
                        palette = palette,
                        isBusy = state.busy,
                        loadingDocId = pendingProofDocId,
                        onOpen = { onOpenShipment(shipmentId) },
                        onViewDocument = { docId ->
                            pendingProofDocId = docId
                            onViewDocument(shipmentId, docId)
                        }
                    )
                }
            }

            if (isCurrentTabLoaded && state.customerShipmentsHasMore) {
                item(key = "load_more", span = { GridItemSpan(maxLineSpan) }) {
                    OutlinedButton(
                        onClick = onLoadMore,
                        enabled = !state.customerShipmentsLoading,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, palette.accentPrimary.copy(alpha = 0.7f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = palette.accentPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Text(
                            text = if (state.customerShipmentsLoading) {
                                "Loading more shipments..."
                            } else {
                                "Load more shipments"
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun ConnectionStatusBanner(
    palette: CustomerShipmentsPalette,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        color = palette.offlineBannerBg,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, palette.offlineBannerBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.CloudOff,
                contentDescription = null,
                tint = palette.accentPrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Offline · showing saved records",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = palette.offlineBannerText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onRetry,
                modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) {
                Text(
                    text = "Retry",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.offlineBannerAction
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Dismiss offline notice",
                    tint = palette.textMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ShipmentErrorBanner(
    message: String,
    palette: CustomerShipmentsPalette,
    onRetry: (() -> Unit)?,
) {
    Surface(
        color = palette.errorBg,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, palette.errorBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = palette.errorText,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = message,
                color = palette.errorText,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            if (onRetry != null) {
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(
                        text = "Retry",
                        fontWeight = FontWeight.Bold,
                        color = palette.errorText
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyShipmentsCard(
    isHistoryTab: Boolean,
    query: String,
    historyFilter: String,
    palette: CustomerShipmentsPalette,
) {
    val title = when {
        query.isNotBlank() -> "No matching shipments"
        isHistoryTab && historyFilter == "Completed" -> "No completed shipments"
        isHistoryTab && historyFilter == "Cancelled" -> "No cancelled shipments"
        isHistoryTab -> "No past shipments"
        else -> "No active shipments"
    }
    val body = when {
        query.isNotBlank() -> "No shipments match \"$query\"."
        isHistoryTab -> "Completed and cancelled deliveries will appear here."
        else -> "Book a pickup from the Home tab to request an office quotation."
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = palette.card,
        border = BorderStroke(1.dp, palette.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (isHistoryTab) Icons.Outlined.History else Icons.Outlined.LocalShipping,
                contentDescription = null,
                tint = palette.accentPrimaryTextOnCard,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = palette.textPrimary
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary
            )
        }
    }
}

@Composable
private fun CustomerShipmentCard(
    shipment: JsonObject,
    deliveries: List<JsonObject>,
    podDocument: JsonObject?,
    signatureDocument: JsonObject?,
    palette: CustomerShipmentsPalette,
    isBusy: Boolean,
    loadingDocId: String?,
    onOpen: () -> Unit,
    onViewDocument: (String) -> Unit,
) {
    val status = shipment.value("status")
    val isCompleted = CustomerHistoryPresentation.isCompletedStatus(status)
    val trackingNumber = shipment.value("tracking_number").ifBlank { "Pending reference" }
    val pickupAddress = CustomerHistoryPresentation.cleanAddress(shipment.value("pickup_address"))
    val dropoffAddress = CustomerHistoryPresentation.cleanAddress(shipment.value("dropoff_address"))
    val footerTime = CustomerHistoryPresentation.footerTimestamp(shipment, deliveries)
    val formattedAmount = CustomerHistoryPresentation.formattedAmount(shipment)
    val hasNumericAmount = shipment.value("final_amount").ifBlank { shipment.value("quoted_amount") }
        .toBigDecimalOrNull() != null

    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        color = palette.card,
        border = BorderStroke(1.dp, palette.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header row: Bold Reference ID + Clean Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = trackingNumber,
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = TanawMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
                HistoryStatusPill(status = status)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Route Section: Two-row Pickup to Destination with vertical dot-line connector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                Column(
                    modifier = Modifier
                        .width(18.dp)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(MapPinPickup)
                    )
                    Box(
                        modifier = Modifier
                            .width(1.5.dp)
                            .weight(1f)
                            .padding(vertical = 3.dp)
                            .background(palette.cardBorder)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "PICKUP",
                        fontFamily = TanawMonoFontFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.7.sp,
                        color = palette.textMuted
                    )
                    Text(
                        text = pickupAddress,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                Column(
                    modifier = Modifier
                        .width(18.dp)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .width(1.5.dp)
                            .height(5.dp)
                            .background(palette.cardBorder)
                    )
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(MapPinDestination)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "DESTINATION",
                        fontFamily = TanawMonoFontFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.7.sp,
                        color = palette.textMuted
                    )
                    Text(
                        text = dropoffAddress,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(
                color = palette.cardBorder.copy(alpha = 0.7f),
                thickness = 1.dp
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Metadata Footer Row: Timestamp on left, Formatted fee on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = footerTime,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = TanawMonoFontFamily,
                    color = palette.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = formattedAmount,
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = TanawMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = if (hasNumericAmount) palette.accentPrimaryTextOnCard else palette.textMuted,
                    maxLines = 1
                )
            }

            // Action Bar: Only for Completed / Delivered cards
            if (isCompleted) {
                Spacer(modifier = Modifier.height(12.dp))
                val podId = podDocument?.value("id").orEmpty()
                val sigId = signatureDocument?.value("id").orEmpty()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ProofActionButton(
                        label = if (podDocument != null) "View POD" else "No POD uploaded",
                        icon = Icons.Outlined.Description,
                        enabled = podDocument != null && !isBusy,
                        isLoading = isBusy && podId.isNotBlank() && loadingDocId == podId,
                        accentColor = palette.accentPrimary,
                        accentTextColor = palette.accentPrimaryTextOnCard,
                        mutedBorderColor = palette.cardBorder.copy(alpha = 0.65f),
                        mutedTextColor = palette.textMuted,
                        onClick = { if (podId.isNotBlank()) onViewDocument(podId) },
                        modifier = Modifier.weight(1f)
                    )
                    ProofActionButton(
                        label = if (signatureDocument != null) "View Signature" else "No signature",
                        icon = Icons.Outlined.Edit,
                        enabled = signatureDocument != null && !isBusy,
                        isLoading = isBusy && sigId.isNotBlank() && loadingDocId == sigId,
                        accentColor = palette.accentBlue,
                        accentTextColor = palette.accentBlueTextOnCard,
                        mutedBorderColor = palette.cardBorder.copy(alpha = 0.65f),
                        mutedTextColor = palette.textMuted,
                        onClick = { if (sigId.isNotBlank()) onViewDocument(sigId) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProofActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    isLoading: Boolean,
    accentColor: Color,
    accentTextColor: Color,
    mutedBorderColor: Color,
    mutedTextColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = enabled || isLoading
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (active) accentColor.copy(alpha = 0.6f) else mutedBorderColor
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (active) accentColor.copy(alpha = 0.12f) else Color.Transparent,
            contentColor = accentTextColor,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = if (isLoading) accentTextColor else mutedTextColor
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = accentTextColor,
                modifier = Modifier.size(15.dp),
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HistoryStatusPill(
    status: String,
) {
    val normalized = status.trim().lowercase()
    val (bg, border, fg) = when (normalized) {
        "completed", "delivered" ->
            Triple(Color(0xFFDCFCE7), Color(0xFF86EFAC), Color(0xFF166534))
        "cancelled", "delayed" ->
            Triple(Color(0xFFFEE2E2), Color(0xFFFCA5A5), Color(0xFFB91C1C))
        "quoted" ->
            Triple(Color(0xFFFEF3C7), Color(0xFFFCD34D), Color(0xFFB45309))
        "booked", "confirmed", "assigned", "en_route_to_pickup", "arrived_at_pickup", "in_transit", "arrived_at_destination" ->
            Triple(Color(0xFFE0F2FE), Color(0xFF7DD3FC), Color(0xFF0369A1))
        else ->
            Triple(Color(0xFFF1F5F9), Color(0xFFCBD5E1), Color(0xFF334155))
    }

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = bg,
        border = BorderStroke(1.dp, border)
    ) {
        Text(
            text = MobilePresentation.status(status),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

// ─── Previews ─────────────────────────────────────────────────────────────────

private fun previewShipments(): List<JsonObject> = listOf(
    buildJsonObject {
        put("id", "shp-completed-1")
        put("tracking_number", "SHP-NE-26-002351")
        put("status", "completed")
        put("pickup_address", "VW6H+F53, Maharlika Hwy, Cabanatuan City, Nueva Ecija")
        put("dropoff_address", "C6V9+M4J, Brgy. Poblacion, Makati City, Metro Manila")
        put("actual_delivery_at", "2026-11-18T06:45:00Z")
        put("final_amount", "2500.00")
    },
    buildJsonObject {
        put("id", "shp-completed-2")
        put("tracking_number", "SHP-NE-26-002310")
        put("status", "delivered")
        put("pickup_address", "San Jose City Public Market, Nueva Ecija")
        put("dropoff_address", "North Harbor Pier 4, Tondo, Manila")
        put("updated_at", "2026-11-12T09:15:00Z")
        put("quoted_amount", "4800.00")
    },
    buildJsonObject {
        put("id", "shp-cancelled-1")
        put("tracking_number", "SHP-NE-26-002299")
        put("status", "cancelled")
        put("pickup_address", "Gapan City Bypass Road, Nueva Ecija")
        put("dropoff_address", "Clark Freeport Zone, Pampanga")
        put("cancelled_at", "2026-11-10T02:20:00Z")
    }
)

private fun previewDocuments(): List<JsonObject> = listOf(
    buildJsonObject {
        put("id", "doc-pod-1")
        put("shipment_id", "shp-completed-1")
        put("kind", "proof_of_delivery")
        put("file_name", "pod_2351.jpg")
        put("recipient_name", "Juan Dela Cruz")
        put("created_at", "2026-11-18T06:44:00Z")
    },
    buildJsonObject {
        put("id", "doc-sig-1")
        put("shipment_id", "shp-completed-1")
        put("kind", "signature")
        put("file_name", "sig_2351.png")
        put("recipient_name", "Juan Dela Cruz")
        put("created_at", "2026-11-18T06:44:30Z")
    }
)

@FormFactorPreviews
@Composable
private fun CustomerHistoryPreview() {
    TanawCustomerTheme {
        CustomerShipmentsSection(
            tab = "History",
            query = "",
            onQueryChange = {},
            historyFilter = "Completed",
            onHistoryFilterChange = {},
            shipments = previewShipments().take(2),
            deliveries = emptyList(),
            documents = previewDocuments(),
            isCurrentTabLoaded = true,
            state = MobileState(offline = false),
            onOpenShipment = {},
            onViewDocument = { _, _ -> },
            onRetry = {},
            onLoadMore = {}
        )
    }
}
