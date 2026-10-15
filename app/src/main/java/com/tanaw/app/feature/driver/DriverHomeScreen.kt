package com.tanaw.app.feature.driver

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tanaw.app.R
import com.tanaw.app.feature.driver.components.DriverBottomNavBar
import com.tanaw.app.feature.driver.components.DriverHeaderCard
import com.tanaw.app.feature.driver.components.DriverTopBar
import com.tanaw.app.feature.driver.components.EmptyTripsState
import com.tanaw.app.feature.driver.components.NewTripBanner
import com.tanaw.app.feature.driver.components.TripAssignmentCard
import com.tanaw.app.ui.theme.NavyBanner
import com.tanaw.app.ui.theme.OrangeAccent

@Composable
fun DriverHomeScreen(
    viewModel: DriverHomeViewModel = hiltViewModel(),
    onNavigateToRoute: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.sideEffect.collect { sideEffect ->
            when (sideEffect) {
                is DriverHomeSideEffect.NavigateTo -> onNavigateToRoute(sideEffect.route)
                is DriverHomeSideEffect.ShowToast -> {}
            }
        }
    }

    Scaffold(
        bottomBar = {
            DriverBottomNavBar(
                selectedTab = "home",
                onTabSelected = { route -> onNavigateToRoute(route) }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAFC))
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is DriverHomeUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = NavyBanner,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                is DriverHomeUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = state.message,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.onEvent(DriverHomeUiEvent.Retry) },
                            colors = ButtonDefaults.buttonColors(containerColor = NavyBanner),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.retry),
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                is DriverHomeUiState.Empty -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        DriverTopBar(
                            unreadCount = state.unreadNotificationCount,
                            onNotificationsClick = { viewModel.onEvent(DriverHomeUiEvent.OpenNotifications) }
                        )

                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            DriverHeaderCard(profile = state.driver)

                            Spacer(modifier = Modifier.height(16.dp))

                            EmptyTripsState()
                        }
                    }
                }

                is DriverHomeUiState.Assigned -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        DriverTopBar(
                            unreadCount = state.unreadNotificationCount,
                            onNotificationsClick = { viewModel.onEvent(DriverHomeUiEvent.OpenNotifications) }
                        )

                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            DriverHeaderCard(profile = state.driver)

                            Spacer(modifier = Modifier.height(16.dp))

                            NewTripBanner()

                            TripAssignmentCard(
                                trip = state.trip,
                                onCardClick = {
                                    viewModel.onEvent(DriverHomeUiEvent.OpenTripDetails(state.trip.shipmentId))
                                }
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}
