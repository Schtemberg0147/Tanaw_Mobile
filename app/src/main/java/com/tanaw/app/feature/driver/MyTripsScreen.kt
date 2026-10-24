package com.tanaw.app.feature.driver

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tanaw.app.domain.model.DriverTripItem
import com.tanaw.app.domain.model.DriverTripStats
import com.tanaw.app.domain.model.TripCardStyle
import com.tanaw.app.domain.model.TripPeriod
import com.tanaw.app.domain.model.TripStatus
import com.tanaw.app.domain.model.TripTabFilter
import com.tanaw.app.feature.driver.components.DriverBottomNavBar
import com.tanaw.app.feature.driver.components.DriverTopBar
import com.tanaw.app.ui.theme.CancelledAccentBar
import com.tanaw.app.ui.theme.ExpiredRed
import com.tanaw.app.ui.theme.ExpiredRedBg
import com.tanaw.app.ui.theme.NavyBanner
import com.tanaw.app.ui.theme.OngoingAccentBar
import com.tanaw.app.ui.theme.OngoingBlue
import com.tanaw.app.ui.theme.OngoingBlueBg
import com.tanaw.app.ui.theme.StatusGreen
import com.tanaw.app.ui.theme.StatusGreenBg
import com.tanaw.app.ui.theme.TanawTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTripsScreen(
    viewModel: MyTripsViewModel = hiltViewModel(),
    onNavigateToRoute: (String) -> Unit = {},
    onActiveTripClick: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.sideEffect.collect { sideEffect ->
            when (sideEffect) {
                is MyTripsSideEffect.NavigateToActiveTrip -> onActiveTripClick(sideEffect.shipmentId)
                is MyTripsSideEffect.ShowToast -> {}
            }
        }
    }

    MyTripsContent(
        uiState = uiState,
        onEvent = { viewModel.onEvent(it) },
        onNavigateToRoute = onNavigateToRoute
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTripsContent(
    uiState: MyTripsUiState,
    onEvent: (MyTripsEvent) -> Unit = {},
    onNavigateToRoute: (String) -> Unit = {}
) {
    val bottomSheetState = rememberModalBottomSheetState()

    Scaffold(
        bottomBar = {
            DriverBottomNavBar(
                selectedTab = "trips",
                onTabSelected = { route -> onNavigateToRoute(route) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAFC))
                .padding(innerPadding)
        ) {
            // Top Bar
            DriverTopBar(
                unreadCount = 1,
                onNotificationsClick = {}
            )

            // Screen Header Title & Filter Icon
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "My Trips",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = NavyBanner,
                    modifier = Modifier.testTag("my_trips_title")
                )

                IconButton(
                    onClick = { onEvent(MyTripsEvent.FilterClicked) },
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                        .size(40.dp)
                        .testTag("filter_icon_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FilterList,
                        contentDescription = "Filter Trips",
                        tint = NavyBanner,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Stats Card Section
            TripStatsCard(
                statsState = uiState.statsState,
                periodLabel = uiState.period.label,
                onRetryStats = { onEvent(MyTripsEvent.Retry) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Tab Navigation Row
            TripTabsRow(
                selectedTab = uiState.selectedTab,
                onTabSelected = { onEvent(MyTripsEvent.TabSelected(it)) }
            )

            // List Content with Pull to Refresh
            PullToRefreshBox(
                isRefreshing = (uiState.tripsState as? TripsContentUiState.Content)?.isRefreshing ?: false,
                onRefresh = { onEvent(MyTripsEvent.Refresh) },
                modifier = Modifier.fillMaxSize()
            ) {
                when (val state = uiState.tripsState) {
                    is TripsContentUiState.Loading -> {
                        TripsSkeletonList()
                    }

                    is TripsContentUiState.Error -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = state.message,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { onEvent(MyTripsEvent.Retry) },
                                colors = ButtonDefaults.buttonColors(containerColor = NavyBanner),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Retry", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    is TripsContentUiState.Empty -> {
                        val emptyMsg = when (state.tab) {
                            TripTabFilter.COMPLETED -> "No completed trips found for ${uiState.period.label}."
                            TripTabFilter.ONGOING -> "No ongoing trips right now."
                            TripTabFilter.CANCELLED -> "No cancelled trips found."
                            TripTabFilter.ALL -> "No trips recorded for ${uiState.period.label}."
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = emptyMsg,
                                fontSize = 15.sp,
                                color = Color(0xFF64748B),
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    is TripsContentUiState.Content -> {
                        val listState = rememberLazyListState()

                        val shouldLoadMore = remember {
                            derivedStateOf {
                                val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                                val totalItems = listState.layoutInfo.totalItemsCount
                                lastVisibleItem != null && lastVisibleItem.index >= totalItems - 3
                            }
                        }

                        LaunchedEffect(shouldLoadMore.value) {
                            if (shouldLoadMore.value && state.hasMore && !state.isLoadingMore) {
                                onEvent(MyTripsEvent.LoadMore)
                            }
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(
                                items = state.trips,
                                key = { it.id },
                                contentType = { "trip_card" }
                            ) { trip ->
                                DriverTripCard(
                                    trip = trip,
                                    onCardClick = { onEvent(MyTripsEvent.TripClicked(trip.shipmentId, trip.status)) }
                                )
                            }

                            if (state.isLoadingMore) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = NavyBanner,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Filter Period Modal Bottom Sheet
    if (uiState.isFilterBottomSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { onEvent(MyTripsEvent.DismissFilter) },
            sheetState = bottomSheetState,
            containerColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "Select Period Filter",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = NavyBanner
                )

                Spacer(modifier = Modifier.height(16.dp))

                uiState.availablePeriods.forEach { periodItem ->
                    val isSelected = periodItem == uiState.period
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEvent(MyTripsEvent.PeriodChanged(periodItem)) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = periodItem.label,
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) NavyBanner else Color(0xFF334155)
                        )

                        RadioButton(
                            selected = isSelected,
                            onClick = { onEvent(MyTripsEvent.PeriodChanged(periodItem)) },
                            colors = RadioButtonDefaults.colors(selectedColor = NavyBanner)
                        )
                    }
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

// ─── Stats Card ───────────────────────────────────────────────────────────────
@Composable
fun TripStatsCard(
    statsState: TripStatsUiState,
    periodLabel: String,
    onRetryStats: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("trip_stats_card"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Navy Left Accent Bar
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(160.dp)
                    .background(NavyBanner)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            ) {
                when (statsState) {
                    is TripStatsUiState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(128.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = NavyBanner, modifier = Modifier.size(24.dp))
                        }
                    }

                    is TripStatsUiState.Error -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Failed to load stats",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                            TextButton(onClick = onRetryStats) {
                                Text("Retry", color = NavyBanner, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    is TripStatsUiState.Content -> {
                        val stats = statsState.stats

                        // Monthly Trips
                        Text(
                            text = "MONTHLY TRIPS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "${stats.completedTripsCount} ",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = NavyBanner
                            )
                            Text(
                                text = "Completed",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF64748B),
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = Color(0xFFF1F5F9))
                        Spacer(modifier = Modifier.height(10.dp))

                        // Total Allowance
                        Text(
                            text = "TOTAL ALLOWANCE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stats.formattedTotalAllowance,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = NavyBanner
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = Color(0xFFF1F5F9))
                        Spacer(modifier = Modifier.height(10.dp))

                        // On-Time Rate
                        Text(
                            text = "ON-TIME RATE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (stats.onTimeRatePercentage != null) "${stats.onTimeRatePercentage}%" else "—",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (stats.onTimeRatePercentage != null) StatusGreen else Color(0xFF64748B)
                        )
                    }
                }
            }
        }
    }
}

// ─── Tabs Row ────────────────────────────────────────────────────────────────
@Composable
fun TripTabsRow(
    selectedTab: TripTabFilter,
    onTabSelected: (TripTabFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf(
        Triple(TripTabFilter.ALL, "All", 0),
        Triple(TripTabFilter.COMPLETED, "Completed", 1),
        Triple(TripTabFilter.ONGOING, "Ongoing", 2),
        Triple(TripTabFilter.CANCELLED, "Cancelled", 3)
    )

    val selectedIndex = tabs.indexOfFirst { it.first == selectedTab }.coerceAtLeast(0)

    TabRow(
        selectedTabIndex = selectedIndex,
        containerColor = Color.White,
        contentColor = NavyBanner,
        indicator = { tabPositions ->
            if (selectedIndex < tabPositions.size) {
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                    height = 3.dp,
                    color = NavyBanner
                )
            }
        },
        divider = { HorizontalDivider(color = Color(0xFFE2E8F0)) },
        modifier = modifier
    ) {
        tabs.forEach { (tabFilter, title, index) ->
            val isSelected = selectedIndex == index
            Tab(
                selected = isSelected,
                onClick = { onTabSelected(tabFilter) },
                text = {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) NavyBanner else Color(0xFF64748B)
                    )
                },
                modifier = Modifier.testTag("tab_$title")
            )
        }
    }
}

// ─── Driver Trip Card ─────────────────────────────────────────────────────────
@Composable
fun DriverTripCard(
    trip: DriverTripItem,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val style = trip.cardStyle

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (style.isTappable) Modifier.clickable { onCardClick() } else Modifier
            )
            .testTag("driver_trip_card_${trip.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = trip.trackingNumber,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (style.isMuted) Color(0xFF64748B) else NavyBanner
                    )

                    // Status Chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(style.chipBackgroundColor)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = style.statusLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = style.chipTextColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Route Section
                TripRouteCities(
                    originCity = trip.originCity,
                    destinationCity = trip.destinationCity,
                    isMuted = style.isMuted
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFFF1F5F9))
                Spacer(modifier = Modifier.height(12.dp))

                // Footer Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Date & Duration
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarToday,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = trip.formattedDuration,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF475569)
                        )
                    }

                    // Allowance Amount
                    Text(
                        text = trip.formattedAllowance,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (style.isMuted) Color(0xFF94A3B8) else NavyBanner,
                        textDecoration = if (style.isAllowanceStrikethrough) TextDecoration.LineThrough else TextDecoration.None
                    )
                }
            }

            // Bottom Accent Color Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(style.accentBarColor)
            )
        }
    }
}

// ─── Route Dots & Cities Composable ──────────────────────────────────────────
@Composable
fun TripRouteCities(
    originCity: String,
    destinationCity: String,
    isMuted: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(
            modifier = Modifier
                .width(16.dp)
                .height(64.dp)
        ) {
            val radius = 5.dp.toPx()
            val startY = 10.dp.toPx()
            val endY = 54.dp.toPx()
            val centerX = size.width / 2f

            val dotColor = if (isMuted) Color(0xFF94A3B8) else NavyBanner

            // Line
            drawLine(
                color = Color(0xFFCBD5E1),
                start = Offset(centerX, startY),
                end = Offset(centerX, endY),
                strokeWidth = 2.dp.toPx()
            )

            // Origin Dot (Hollow Navy)
            drawCircle(
                color = dotColor,
                radius = radius,
                center = Offset(centerX, startY),
                style = Stroke(width = 2.dp.toPx())
            )

            // Destination Dot (Filled Gray/Red)
            drawCircle(
                color = Color(0xFF94A3B8),
                radius = radius,
                center = Offset(centerX, endY)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = originCity,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (isMuted) Color(0xFF64748B) else NavyBanner
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = destinationCity,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64748B)
            )
        }
    }
}

@Composable
private fun TripsSkeletonList() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFE2E8F0))
            )
        }
    }
}

// ─── Previews ────────────────────────────────────────────────────────────────
@Preview(showBackground = true)
@Composable
fun MyTripsScreenPreview() {
    val sampleTrip1 = DriverTripItem(
        id = "1",
        shipmentId = "shp-1",
        trackingNumber = "SHP-NE-8063",
        status = TripStatus.COMPLETED,
        rawStatus = "completed",
        originCity = "Cabanatuan",
        destinationCity = "Gapan City",
        formattedDate = "Oct 24",
        formattedDuration = "Oct 24 • 2h 15min",
        allowanceAmount = 500.0,
        formattedAllowance = "₱500",
        cardStyle = TripCardStyle.from(TripStatus.COMPLETED)
    )

    val sampleTrip2 = DriverTripItem(
        id = "2",
        shipmentId = "shp-2",
        trackingNumber = "SHP-NE-8045",
        status = TripStatus.COMPLETED,
        rawStatus = "completed",
        originCity = "Guimba",
        destinationCity = "Palayan City",
        formattedDate = "Oct 22",
        formattedDuration = "Oct 22 • 3h 30min",
        allowanceAmount = 500.0,
        formattedAllowance = "₱500",
        cardStyle = TripCardStyle.from(TripStatus.COMPLETED)
    )

    val sampleStats = DriverTripStats(
        completedTripsCount = 24,
        totalAllowance = 12000.0,
        formattedTotalAllowance = "₱12,000",
        onTimeRatePercentage = 96
    )

    val sampleUiState = MyTripsUiState(
        selectedTab = TripTabFilter.COMPLETED,
        period = TripPeriod(2024, 10, "October 2024"),
        statsState = TripStatsUiState.Content(sampleStats),
        tripsState = TripsContentUiState.Content(listOf(sampleTrip1, sampleTrip2))
    )

    TanawTheme {
        MyTripsContent(uiState = sampleUiState)
    }
}
