package com.tanaw.app.feature.driver.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Toll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanaw.app.R
import com.tanaw.app.domain.model.CargoDetails
import com.tanaw.app.domain.model.DriverProfile
import com.tanaw.app.domain.model.TripAssignment
import com.tanaw.app.domain.model.TripMeta
import com.tanaw.app.domain.model.TripRoute
import com.tanaw.app.ui.theme.NavyBanner
import com.tanaw.app.ui.theme.NavyPrimary
import com.tanaw.app.ui.theme.OrangeSpecialTag
import com.tanaw.app.ui.theme.OrangeSpecialTagBg
import com.tanaw.app.ui.theme.RedDestinationDot
import com.tanaw.app.ui.theme.StatusGreen
import com.tanaw.app.ui.theme.StatusGreenBg

// ─── 1. Top Bar ───────────────────────────────────────────────────────────────
@Composable
fun DriverTopBar(
    unreadCount: Int,
    onNotificationsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App Logo
        TanawLogoIcon(modifier = Modifier.size(36.dp))

        // Notification Bell with Red Dot
        Box(modifier = Modifier.testTag("notification_bell_container")) {
            IconButton(
                onClick = onNotificationsClick,
                modifier = Modifier.testTag("notification_bell")
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = stringResource(R.string.notifications),
                    tint = NavyBanner,
                    modifier = Modifier.size(26.dp)
                )
            }

            if (unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp)
                        .clip(CircleShape)
                        .background(RedDestinationDot)
                        .testTag("unread_red_dot")
                )
            }
        }
    }
}

// ─── Logo Vector Composable ────────────────────────────────────────────────────
@Composable
fun TanawLogoIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Outer Eye Curve
        val eyePath = Path().apply {
            moveTo(w * 0.1f, h * 0.5f)
            quadraticTo(w * 0.5f, h * 0.1f, w * 0.9f, h * 0.5f)
            quadraticTo(w * 0.5f, h * 0.9f, w * 0.1f, h * 0.5f)
            close()
        }
        drawPath(path = eyePath, color = NavyBanner)

        // Inner Iris Pin
        drawCircle(
            color = Color.White,
            radius = w * 0.22f,
            center = Offset(w * 0.5f, h * 0.5f)
        )
        drawCircle(
            color = OrangeSpecialTag,
            radius = w * 0.14f,
            center = Offset(w * 0.5f, h * 0.5f)
        )
    }
}

// ─── 2. Driver Header Card ─────────────────────────────────────────────────────
@Composable
fun DriverHeaderCard(
    profile: DriverProfile,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("driver_header_card"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.fullName,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = NavyBanner
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = profile.vehicleTypeName,
                        fontSize = 14.sp,
                        color = Color(0xFF64748B)
                    )
                }

                StatusChip(modifier = Modifier.testTag("status_chip"))
            }

            // 3dp Green Accent Line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(StatusGreen)
            )
        }
    }
}

// ─── 3. Status Chip ───────────────────────────────────────────────────────────
@Composable
fun StatusChip(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(StatusGreenBg)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(StatusGreen)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.available),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF15803D)
        )
    }
}

// ─── 4. Empty Trips State ──────────────────────────────────────────────────────
@Composable
fun EmptyTripsState(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("empty_trips_state"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(Color(0xFFE2E8F0)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 48.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Outlined.LocalShipping,
                contentDescription = null,
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(88.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.no_trips_assigned),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = NavyBanner
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.no_trips_subtitle),
                fontSize = 14.sp,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(StatusGreenBg)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(StatusGreen)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.you_are_available_for_assignments),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF15803D)
                )
            }
        }
    }
}

// ─── 5. New Trip Banner ───────────────────────────────────────────────────────
@Composable
fun NewTripBanner(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .background(NavyBanner)
            .padding(vertical = 14.dp, horizontal = 20.dp)
            .testTag("new_trip_banner"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.LocalShipping,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.new_trip_assigned),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

// ─── 6. Trip Assignment Card ──────────────────────────────────────────────────
@Composable
fun TripAssignmentCard(
    trip: TripAssignment,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCardClick() }
            .testTag("trip_assignment_card"),
        shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
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
                        text = stringResource(R.string.trip_assignment),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = NavyBanner
                    )

                    // Shipment Code Chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFF1F5F9))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .testTag("shipment_code_chip")
                    ) {
                        Text(
                            text = trip.trackingNumber,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF334155)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFFF1F5F9))
                Spacer(modifier = Modifier.height(16.dp))

                // Cargo Section
                CargoSection(cargo = trip.cargo)

                Spacer(modifier = Modifier.height(20.dp))

                // Route Timeline
                RouteTimeline(route = trip.route)

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = Color(0xFFF1F5F9))
                Spacer(modifier = Modifier.height(14.dp))

                // Trip Meta Row
                TripMetaRow(meta = trip.meta)

                Spacer(modifier = Modifier.height(16.dp))

                // Allowance Row
                AllowanceRow(formattedAllowance = trip.formattedAllowance)

                // Dispatcher Notes Box
                if (!trip.notes.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    NotesBox(notes = trip.notes)
                }
            }

            // Bottom Navy Border Line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(NavyBanner)
            )
        }
    }
}

// ─── Cargo Section ────────────────────────────────────────────────────────────
@Composable
fun CargoSection(
    cargo: CargoDetails,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Inventory2,
                    contentDescription = null,
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.cargo),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = cargo.categoryName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = NavyBanner
            )
            Text(
                text = cargo.formattedWeight,
                fontSize = 14.sp,
                color = Color(0xFF64748B)
            )
        }

        if (!cargo.specialHandlingTag.isNullOrBlank()) {
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.LocalShipping,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.special),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF64748B)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(OrangeSpecialTagBg)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("special_tag_chip")
                ) {
                    Text(
                        text = cargo.specialHandlingTag,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = OrangeSpecialTag
                    )
                }
            }
        }
    }
}

// ─── Route Timeline Composable ────────────────────────────────────────────────
@Composable
fun RouteTimeline(
    route: TripRoute,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // Canvas Timeline Line and Dots
        Canvas(
            modifier = Modifier
                .width(16.dp)
                .height(110.dp)
        ) {
            val radius = 5.dp.toPx()
            val startY = 12.dp.toPx()
            val endY = 98.dp.toPx()
            val centerX = size.width / 2f

            // Connecting Vertical Line
            drawLine(
                color = NavyBanner,
                start = Offset(centerX, startY),
                end = Offset(centerX, endY),
                strokeWidth = 2.dp.toPx()
            )

            // Top Navy Dot (Pickup)
            drawCircle(
                color = NavyBanner,
                radius = radius,
                center = Offset(centerX, startY)
            )

            // Bottom Red Dot (Destination)
            drawCircle(
                color = RedDestinationDot,
                radius = radius,
                center = Offset(centerX, endY)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Pick-up
            Text(
                text = stringResource(R.string.pickup),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64748B)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = route.pickup.locationName,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = NavyBanner
            )
            if (!route.pickup.formattedScheduledTime.isNullOrBlank()) {
                Text(
                    text = route.pickup.formattedScheduledTime,
                    fontSize = 13.sp,
                    color = Color(0xFF64748B)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Destination
            Text(
                text = stringResource(R.string.destination),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64748B)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = route.destination.locationName,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = NavyBanner
            )
        }
    }
}

// ─── Trip Meta Row ────────────────────────────────────────────────────────────
@Composable
fun TripMetaRow(
    meta: TripMeta,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        // Distance
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Straighten,
                contentDescription = null,
                tint = Color(0xFF475569),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = meta.formattedDistance,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF334155)
            )
        }

        Spacer(modifier = Modifier.width(20.dp))

        // Duration
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.AccessTime,
                contentDescription = null,
                tint = Color(0xFF475569),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = meta.formattedDuration,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF334155)
            )
        }

        if (meta.hasToll) {
            Spacer(modifier = Modifier.width(20.dp))

            // Toll Road Indicator
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Toll,
                    contentDescription = null,
                    tint = Color(0xFF475569),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.toll_road),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF334155)
                )
            }
        }
    }
}

// ─── Allowance Row ────────────────────────────────────────────────────────────
@Composable
fun AllowanceRow(
    formattedAllowance: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFEEF2FF))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.allowance),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = NavyBanner
        )

        Text(
            text = formattedAllowance,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = NavyBanner
        )
    }
}

// ─── Notes Box ────────────────────────────────────────────────────────────────
@Composable
fun NotesBox(
    notes: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
            .background(Color(0xFFFAFAFA))
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Outlined.Notes,
            contentDescription = null,
            tint = Color(0xFF64748B),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = notes,
            fontSize = 13.sp,
            color = Color(0xFF334155)
        )
    }
}

// ─── Bottom Navigation Bar Composable ─────────────────────────────────────────
@Composable
fun DriverBottomNavBar(
    selectedTab: String = "home",
    onTabSelected: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(color = Color(0xFFE2E8F0))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val navItems = listOf(
                Triple("home", stringResource(R.string.home), Icons.Default.Home),
                Triple("navigate", stringResource(R.string.navigate), Icons.Default.Navigation),
                Triple("trips", stringResource(R.string.trips), Icons.Default.Route),
                Triple("profile", stringResource(R.string.profile), Icons.Default.Person)
            )

            navItems.forEach { (route, label, icon) ->
                val isSelected = route == selectedTab
                val tint = if (isSelected) NavyBanner else Color(0xFF94A3B8)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { onTabSelected(route) }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = tint,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = tint
                    )
                }
            }
        }
    }
}
