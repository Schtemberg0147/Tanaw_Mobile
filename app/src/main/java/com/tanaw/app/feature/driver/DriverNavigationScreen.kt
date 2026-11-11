package com.tanaw.app.feature.driver

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.BorderStroke
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import java.util.Locale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tanaw.app.R
import com.tanaw.app.domain.model.ActiveTripStage
import com.tanaw.app.domain.model.TripAssignment
import com.tanaw.app.feature.driver.components.AddTripExpenseSheet
import com.tanaw.app.feature.driver.components.ReportIncidentSheet
import com.tanaw.app.ui.theme.NavyBanner
import com.tanaw.app.ui.theme.RedDestinationDot
import com.tanaw.app.ui.theme.StatusGreen
import com.tanaw.app.ui.theme.StatusGreenBg
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverNavigationScreen(
    viewModel: DriverNavigationViewModel = hiltViewModel(),
    onNavigateToSubmitPod: (String) -> Unit = {},
    onNavigateToDeliveryComplete: (String) -> Unit = {},
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.sideEffect.collect { sideEffect ->
            when (sideEffect) {
                is DriverNavigationSideEffect.NavigateToSubmitPod -> onNavigateToSubmitPod(sideEffect.deliveryId)
                is DriverNavigationSideEffect.NavigateToDeliveryComplete -> onNavigateToDeliveryComplete(sideEffect.deliveryId)
                is DriverNavigationSideEffect.ShowToast -> snackbarHostState.showSnackbar(sideEffect.message)
                DriverNavigationSideEffect.NavigateBack -> onBack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val tracking = (uiState as? DriverNavigationUiState.Success)?.trip?.trackingNumber ?: "Trip Navigation"
                    Text(
                        text = "Trip #$tracking",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("nav_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    if (uiState is DriverNavigationUiState.Success) {
                        IconButton(
                            onClick = { viewModel.onEvent(DriverNavigationUiEvent.OpenIncidentSheet) },
                            modifier = Modifier.testTag("open_incident_sheet_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Report Incident",
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        OutlinedButton(
                            onClick = { viewModel.onEvent(DriverNavigationUiEvent.OpenExpenseSheet) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = BorderStroke(1.dp, Color.White),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .testTag("open_expense_sheet_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Expense", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBanner)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFF8FAFC))
        ) {
            when (val state = uiState) {
                DriverNavigationUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = NavyBanner, modifier = Modifier.size(48.dp))
                    }
                }

                is DriverNavigationUiState.Error -> {
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
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.loadActiveTrip() },
                            colors = ButtonDefaults.buttonColors(containerColor = NavyBanner),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(text = "Retry", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                is DriverNavigationUiState.Success -> {
                    val trip = state.trip
                    val stage = state.currentStage

                    Column(modifier = Modifier.fillMaxSize()) {
                        // Top Route Status Banner
                        RouteStatusBanner(stage = stage, countdownSeconds = state.countdownSeconds)

                        // Interactive Map Area (MapLibre background with fallback)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            DriverMapBackground(
                                driverLat = state.driverLat ?: 15.4828,
                                driverLng = state.driverLng ?: 120.9692
                            )
                        }

                        // Bottom Navigation Drawer / Timeline Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("navigation_timeline_card"),
                            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(20.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                // Error Banner if present
                                if (!state.errorMessage.isNullOrBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFFFEE2E2))
                                            .padding(12.dp)
                                            .testTag("driver_error_banner")
                                    ) {
                                        Text(
                                            text = state.errorMessage,
                                            fontSize = 13.sp,
                                            color = Color(0xFFDC2626),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                }

                                // Interactive 5-Step Timeline
                                NavigationTimeline(currentStage = stage)

                                Spacer(modifier = Modifier.height(16.dp))

                                // Route Location Summary
                                RouteSummaryRow(
                                    pickupName = trip.route.pickup.locationName,
                                    dropoffName = trip.route.destination.locationName
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                // Primary Stage Action Button
                                PrimaryStageButton(
                                    stage = stage,
                                    isSubmitting = state.isSubmitting,
                                    onClick = {
                                        when (stage) {
                                            ActiveTripStage.ASSIGNED -> viewModel.onEvent(DriverNavigationUiEvent.ArriveAtPickup)
                                            ActiveTripStage.PICKUP_ARRIVED -> viewModel.onEvent(DriverNavigationUiEvent.ConfirmCustomerShowed)
                                            ActiveTripStage.CUSTOMER_SHOWED -> viewModel.onEvent(DriverNavigationUiEvent.ConfirmCargoLoaded)
                                            ActiveTripStage.CARGO_LOADED -> viewModel.onEvent(DriverNavigationUiEvent.MarkAsDelivered)
                                            ActiveTripStage.DELIVERED -> viewModel.onEvent(DriverNavigationUiEvent.ViewCompletionSummary)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Add Trip Expense Modal Sheet
                    if (state.isExpenseSheetVisible) {
                        AddTripExpenseSheet(
                            deliveryId = trip.deliveryId,
                            trackingNumber = trip.trackingNumber,
                            isSubmitting = state.expenseSubmitting,
                            errorMessage = state.expenseErrorMessage,
                            onDismiss = { viewModel.onEvent(DriverNavigationUiEvent.CloseExpenseSheet) },
                            onSubmit = { category, amount, bytes ->
                                viewModel.onEvent(DriverNavigationUiEvent.SubmitExpense(category, amount, bytes))
                            }
                        )
                    }

                    // Report Incident Modal Sheet
                    if (state.isIncidentSheetVisible) {
                        ReportIncidentSheet(
                            deliveryId = trip.deliveryId,
                            trackingNumber = trip.trackingNumber,
                            isSubmitting = state.incidentSubmitting,
                            errorMessage = state.incidentErrorMessage,
                            onDismiss = { viewModel.onEvent(DriverNavigationUiEvent.CloseIncidentSheet) },
                            onSubmit = { category, description, delayMins ->
                                viewModel.onEvent(DriverNavigationUiEvent.SubmitIncident(category, description, delayMins))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RouteStatusBanner(
    stage: ActiveTripStage,
    countdownSeconds: Long,
    modifier: Modifier = Modifier
) {
    val (statusText, subtitleText, bannerColor) = when (stage) {
        ActiveTripStage.ASSIGNED -> Triple("Heading to Pick-up", "Tap below when you arrive at pick-up location", NavyBanner)
        ActiveTripStage.PICKUP_ARRIVED -> {
            val mins = countdownSeconds / 60
            val secs = countdownSeconds % 60
            val formattedTime = String.format(Locale.US, "%02d:%02d", mins, secs)
            Triple("Waiting for Customer", "Waiting countdown: $formattedTime remaining", Color(0xFFD97706))
        }
        ActiveTripStage.CUSTOMER_SHOWED -> Triple("Customer Arrived · Loading Cargo", "Confirm cargo loaded when complete", NavyBanner)
        ActiveTripStage.CARGO_LOADED -> Triple("Heading to Destination", "In transit to drop-off location", StatusGreen)
        ActiveTripStage.DELIVERED -> Triple("Delivery Complete", "POD submitted successfully", StatusGreen)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bannerColor)
            .padding(vertical = 12.dp, horizontal = 20.dp)
            .testTag("route_status_banner"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (stage == ActiveTripStage.PICKUP_ARRIVED) Icons.Default.Schedule else Icons.Default.Navigation,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = statusText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = subtitleText,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}

@Composable
fun DriverMapBackground(
    driverLat: Double,
    driverLng: Double,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context)
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize().testTag("driver_map_view"),
        factory = {
            mapView.apply {
                getMapAsync { map ->
                    map.cameraPosition = CameraPosition.Builder()
                        .target(LatLng(driverLat, driverLng))
                        .zoom(14.0)
                        .build()
                    map.setStyle("https://basemaps.cartocdn.com/gl/voyager-gl-style/style.json")
                }
            }
        }
    )
}

@Composable
fun NavigationTimeline(
    currentStage: ActiveTripStage,
    modifier: Modifier = Modifier
) {
    val steps = listOf(
        Pair("Assigned", ActiveTripStage.ASSIGNED),
        Pair("At Pick-up", ActiveTripStage.PICKUP_ARRIVED),
        Pair("In Transit", ActiveTripStage.CARGO_LOADED),
        Pair("Delivered", ActiveTripStage.DELIVERED)
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("navigation_timeline_steps"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, (label, stageStep) ->
            val isPassed = currentStage.ordinal > stageStep.ordinal
            val isCurrent = currentStage == stageStep

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isPassed -> StatusGreen
                                isCurrent -> NavyBanner
                                else -> Color(0xFFCBD5E1)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isPassed) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Text(
                            text = "${index + 1}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = label,
                    fontSize = 10.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    color = if (isPassed || isCurrent) NavyBanner else Color(0xFF94A3B8)
                )
            }
        }
    }
}

@Composable
fun RouteSummaryRow(
    pickupName: String,
    dropoffName: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(NavyBanner)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Pick-up: $pickupName",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = NavyBanner
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(RedDestinationDot)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Drop-off: $dropoffName",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = NavyBanner
            )
        }
    }
}

@Composable
fun PrimaryStageButton(
    stage: ActiveTripStage,
    isSubmitting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val buttonText = when (stage) {
        ActiveTripStage.ASSIGNED -> "I'VE ARRIVED AT PICK-UP LOCATION"
        ActiveTripStage.PICKUP_ARRIVED -> "CUSTOMER ARRIVED AT PICK-UP"
        ActiveTripStage.CUSTOMER_SHOWED -> "CARGO LOADED"
        ActiveTripStage.CARGO_LOADED -> "MARK AS DELIVERED"
        ActiveTripStage.DELIVERED -> "VIEW COMPLETION SUMMARY"
    }

    Button(
        onClick = onClick,
        enabled = !isSubmitting,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (stage == ActiveTripStage.DELIVERED) StatusGreen else NavyBanner
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("primary_stage_action_button")
    ) {
        if (isSubmitting) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
        } else {
            Text(
                text = buttonText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}
