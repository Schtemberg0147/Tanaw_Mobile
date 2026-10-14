package com.tanaw.app.feature.driver

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tanaw.app.domain.model.CargoDetails
import com.tanaw.app.domain.model.DriverProfile
import com.tanaw.app.domain.model.DriverStatus
import com.tanaw.app.domain.model.RoutePoint
import com.tanaw.app.domain.model.TripAssignment
import com.tanaw.app.domain.model.TripMeta
import com.tanaw.app.domain.model.TripRoute
import com.tanaw.app.feature.driver.components.DriverBottomNavBar
import com.tanaw.app.feature.driver.components.DriverHeaderCard
import com.tanaw.app.feature.driver.components.DriverTopBar
import com.tanaw.app.feature.driver.components.EmptyTripsState
import com.tanaw.app.feature.driver.components.NewTripBanner
import com.tanaw.app.feature.driver.components.TripAssignmentCard
import com.tanaw.app.ui.theme.TanawTheme

private val sampleDriver = DriverProfile(
    id = "drv-001",
    userId = "usr-001",
    fullName = "Ramon Cruz",
    vehicleTypeName = "10-Wheeler Wing Van",
    status = DriverStatus.AVAILABLE
)

private val sampleTrip = TripAssignment(
    deliveryId = "del-101",
    shipmentId = "shp-8063",
    trackingNumber = "SHP-NE-8063",
    cargo = CargoDetails(
        categoryName = "Agricultural Bulk",
        formattedWeight = "6,000 kg",
        specialHandlingTag = "KEEP DRY"
    ),
    route = TripRoute(
        pickup = RoutePoint(
            locationName = "Cabanatuan City Hub Alpha",
            fullAddress = "Cabanatuan City Hub Alpha, NE",
            formattedScheduledTime = "Oct 24, 2023 · 08:00 AM"
        ),
        destination = RoutePoint(
            locationName = "Gapan City Distribution Ctr",
            fullAddress = "Gapan City Distribution Ctr, NE",
            formattedScheduledTime = null
        )
    ),
    meta = TripMeta(
        distanceKm = 38.0,
        formattedDistance = "38 km",
        formattedDuration = "1h 15min",
        hasToll = true
    ),
    formattedAllowance = "₱500",
    notes = "Handle with care. Stack max 3 high."
)

@Preview(showBackground = true, name = "1. Driver Home - Empty State")
@Composable
fun DriverHomeEmptyPreview() {
    TanawTheme {
        Scaffold(
            bottomBar = { DriverBottomNavBar(selectedTab = "home") }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF8FAFC))
                    .padding(padding)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    DriverTopBar(unreadCount = 1, onNotificationsClick = {})
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        DriverHeaderCard(profile = sampleDriver)
                        Spacer(modifier = Modifier.height(16.dp))
                        EmptyTripsState()
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "2. Driver Home - Assigned Trip State")
@Composable
fun DriverHomeAssignedPreview() {
    TanawTheme {
        Scaffold(
            bottomBar = { DriverBottomNavBar(selectedTab = "home") }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF8FAFC))
                    .padding(padding)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    DriverTopBar(unreadCount = 1, onNotificationsClick = {})
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        DriverHeaderCard(profile = sampleDriver.copy(status = DriverStatus.ON_TRIP))
                        Spacer(modifier = Modifier.height(16.dp))
                        NewTripBanner()
                        TripAssignmentCard(trip = sampleTrip, onCardClick = {})
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "3. Driver Home - Assigned (No Notes/Toll)")
@Composable
fun DriverHomeAssignedSimplePreview() {
    val simpleTrip = sampleTrip.copy(
        meta = sampleTrip.meta.copy(hasToll = false),
        notes = null
    )
    TanawTheme {
        Scaffold(
            bottomBar = { DriverBottomNavBar(selectedTab = "home") }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF8FAFC))
                    .padding(padding)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    DriverTopBar(unreadCount = 0, onNotificationsClick = {})
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        DriverHeaderCard(profile = sampleDriver.copy(status = DriverStatus.ON_TRIP))
                        Spacer(modifier = Modifier.height(16.dp))
                        NewTripBanner()
                        TripAssignmentCard(trip = simpleTrip, onCardClick = {})
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Preview(
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    name = "4. Driver Home - Dark Theme"
)
@Composable
fun DriverHomeDarkThemePreview() {
    TanawTheme(darkTheme = true) {
        Scaffold(
            bottomBar = { DriverBottomNavBar(selectedTab = "home") }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF10141D))
                    .padding(padding)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    DriverTopBar(unreadCount = 1, onNotificationsClick = {})
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        DriverHeaderCard(profile = sampleDriver.copy(status = DriverStatus.ON_TRIP))
                        Spacer(modifier = Modifier.height(16.dp))
                        NewTripBanner()
                        TripAssignmentCard(trip = sampleTrip, onCardClick = {})
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}
