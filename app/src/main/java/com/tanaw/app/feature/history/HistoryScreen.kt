package com.tanaw.app.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tanaw.app.R
import com.tanaw.app.domain.model.BookingItemUi
import com.tanaw.app.domain.model.BookingTabFilter
import com.tanaw.app.ui.components.HistoryBookingCard
import com.tanaw.app.ui.theme.NavyBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: BookingsViewModel = hiltViewModel(),
    onViewPOD: (String) -> Unit = {},
    onRebook: (BookingItemUi) -> Unit = {},
    onTrack: (String) -> Unit = {},
    onViewDetails: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is BookingsSideEffect.NavigateToTrack -> onTrack(effect.shipmentId)
                is BookingsSideEffect.NavigateToPod -> onViewPOD(effect.shipmentId)
                is BookingsSideEffect.NavigateToDetails -> onViewDetails(effect.shipmentId)
                is BookingsSideEffect.PreFillRebookAndNavigate -> {
                    // Handled in caller or via booking VM
                }
                is BookingsSideEffect.ShowToast -> {}
            }
        }
    }

    val tabTitles = listOf("All", "Active", "Completed", "Cancelled")
    val selectedTabIndex = when (uiState.selectedTab) {
        BookingTabFilter.ALL -> 0
        BookingTabFilter.ACTIVE -> 1
        BookingTabFilter.COMPLETED -> 2
        BookingTabFilter.CANCELLED -> 3
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        // Title Section
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp)
        ) {
            Text(
                text = "My Bookings",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = NavyBanner
            )
        }

        // Custom TabRow with Navy Underline Indicator
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = Color.White,
            contentColor = NavyBanner,
            indicator = { tabPositions ->
                if (selectedTabIndex < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        height = 3.dp,
                        color = NavyBanner
                    )
                }
            },
            divider = {}
        ) {
            tabTitles.forEachIndexed { index, title ->
                val isSelected = selectedTabIndex == index
                Tab(
                    selected = isSelected,
                    onClick = { viewModel.onEvent(BookingsEvent.TabSelected(index)) },
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

        // Content Area with Pull-to-Refresh
        PullToRefreshBox(
            isRefreshing = (uiState as? BookingsUiState.Content)?.isRefreshing ?: false,
            onRefresh = { viewModel.onEvent(BookingsEvent.Refresh) },
            modifier = Modifier.fillMaxSize()
        ) {
            when (val state = uiState) {
                is BookingsUiState.Loading -> {
                    BookingsSkeletonList()
                }

                is BookingsUiState.Error -> {
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
                            onClick = { viewModel.onEvent(BookingsEvent.Retry) },
                            colors = ButtonDefaults.buttonColors(containerColor = NavyBanner),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Retry", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                is BookingsUiState.Empty -> {
                    val emptyMessage = when (state.selectedTab) {
                        BookingTabFilter.ACTIVE -> "No active bookings yet. Book your first delivery!"
                        BookingTabFilter.COMPLETED -> "No completed bookings found."
                        BookingTabFilter.CANCELLED -> "No cancelled bookings."
                        BookingTabFilter.ALL -> "No bookings recorded yet."
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = emptyMessage,
                            fontSize = 15.sp,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                is BookingsUiState.Content -> {
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
                            viewModel.onEvent(BookingsEvent.LoadMore)
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(
                            items = state.bookings,
                            key = { it.id },
                            contentType = { "booking_card" }
                        ) { booking ->
                            HistoryBookingCard(
                                booking = booking,
                                onTrackClick = { viewModel.onEvent(BookingsEvent.TrackClicked(booking.shipmentId)) },
                                onViewPODClick = { viewModel.onEvent(BookingsEvent.ViewPodClicked(booking.shipmentId)) },
                                onRebookClick = {
                                    onRebook(booking)
                                    viewModel.onEvent(BookingsEvent.RebookClicked(booking))
                                },
                                onCardClick = { viewModel.onEvent(BookingsEvent.CardDetailsClicked(booking.shipmentId)) }
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

@Composable
private fun BookingsSkeletonList() {
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
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFE2E8F0))
            )
        }
    }
}
