package com.tanaw.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanaw.app.R
import com.tanaw.app.ui.screens.MockData.computeDistance
import com.tanaw.app.ui.theme.*
import java.util.Calendar

// ─── HomeScreen ───────────────────────────────────────────────────────────────
/**
 * @param userName          Display name of the logged-in user
 * @param userCity          City shown under the name
 * @param pickupLocation    Currently selected pickup (null = not set)
 * @param destination       Currently selected destination (null = not set)
 * @param onPickupClick     Open the pickup location picker
 * @param onDestinationClick Open the destination picker
 * @param onContinue        Called with selected vehicle when Continue is tapped
 */

@Composable
fun HomeScreen(
    userName            : String = "Maria Santos",
    userCity            : String = "Cabanatuan City",
    pickupLocation      : LocationItem? = null,
    destination         : LocationItem? = null,
    onPickupClick       : () -> Unit,
    onDestinationClick  : () -> Unit,
    onContinue          : (Vehicle, Int) -> Unit
) {
    var selectedVehicle by remember { mutableStateOf<Vehicle?>(null) }

    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 0..11  -> "Good morning,"
            in 12..17 -> "Good afternoon,"
            else      -> "Good evening,"
        }
    }

    // Add this with your other state variables
    val distanceKm: Int? = remember(pickupLocation, destination) {
        if (pickupLocation != null && destination != null)
            computeDistance(pickupLocation, destination)
        else null
    }

    Box(modifier = Modifier.fillMaxSize().background(BackgroundGray)) {

        LazyColumn(
            modifier            = Modifier.fillMaxSize(),
            contentPadding      = PaddingValues(bottom = if (selectedVehicle != null) 96.dp else 24.dp)
        ) {

            // ── Top bar ───────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Image(
                        painter            = painterResource(id = R.drawable.ic_tanaw_logo),
                        contentDescription = "Tanaw",
                        modifier           = Modifier.height(28.dp)
                    )
                    // Notification bell with orange badge dot
                    Box {
                        Icon(
                            imageVector        = Icons.Outlined.Notifications,
                            contentDescription = "Notifications",
                            tint               = NavyPrimary,
                            modifier           = Modifier.size(26.dp)
                        )
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(OrangeAccent, shape = RoundedCornerShape(50))
                                .align(Alignment.TopEnd)
                        )
                    }
                }
                HorizontalDivider(color = BorderGray, thickness = 1.dp)
            }

            // ── Greeting ──────────────────────────────────────────────────
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                ) {
                    Text(
                        text     = greeting,
                        fontSize = 14.sp,
                        color    = HintGray
                    )
                    Text(
                        text       = userName,
                        fontSize   = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color      = NavyPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector        = Icons.Outlined.LocationOn,
                            contentDescription = null,
                            tint               = HintGray,
                            modifier           = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(text = userCity, fontSize = 13.sp, color = HintGray)
                    }
                }
            }

            // ── Shipping card ─────────────────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(12.dp))
                ShippingCard(
                    pickupLocation     = pickupLocation,
                    destination        = destination,
                    onPickupClick      = onPickupClick,
                    onDestinationClick = onDestinationClick
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            // ── Available Vehicles header ─────────────────────────────────
            item {
                Text(
                    text       = "Available Vehicles",
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color      = NavyPrimary,
                    modifier   = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // ── Vehicle list ──────────────────────────────────────────────
            // ── Vehicle list ──────────────────────────────────────────────
            items(MockData.vehicles) { vehicle ->
                VehicleCard(
                    vehicle    = vehicle,
                    distanceKm = distanceKm,
                    isSelected = selectedVehicle?.id == vehicle.id,
                    onClick    = { if (distanceKm != null) selectedVehicle = vehicle }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        if (selectedVehicle != null) {
            BottomSelectionBar(
                vehicle    = selectedVehicle!!,
                distanceKm = distanceKm ?: 0,
                onContinue = { onContinue(selectedVehicle!!, distanceKm ?: 0) },
                modifier   = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

// ─── Shipping Card ────────────────────────────────────────────────────────────
@Composable
private fun ShippingCard(
    pickupLocation     : LocationItem?,
    destination        : LocationItem?,
    onPickupClick      : () -> Unit,
    onDestinationClick : () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text       = "Where are you shipping?",
                fontSize   = 16.sp,
                fontWeight = FontWeight.Bold,
                color      = NavyPrimary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Pickup row
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Navy dot
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(NavyPrimary, shape = RoundedCornerShape(50))
                )
                Spacer(modifier = Modifier.width(10.dp))
                LocationField(
                    text        = pickupLocation?.name ?: "Enter pick-up location",
                    isSet       = pickupLocation != null,
                    onClick     = onPickupClick,
                    modifier    = Modifier.weight(1f)
                )
            }

            // Dashed connector line between dots
            Row {
                Box(modifier = Modifier.width(14.dp), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(20.dp)
                            .background(BorderGray)
                    )
                }
            }

            // Destination row
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Orange dot
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(OrangeAccent, shape = RoundedCornerShape(50))
                )
                Spacer(modifier = Modifier.width(10.dp))
                LocationField(
                    text     = destination?.name ?: "Enter destination",
                    isSet    = destination != null,
                    onClick  = onDestinationClick,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Pick-up now pill
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(
                    modifier = Modifier
                        .border(1.dp, BorderGray, RoundedCornerShape(50))
                        .clip(RoundedCornerShape(50))
                        .clickable { }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter            = painterResource(id = R.drawable.ic_timer),
                            contentDescription = null,
                            tint               = NavyPrimary,
                            modifier           = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text       = "Pick-up now",
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color      = NavyPrimary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "∨", fontSize = 12.sp, color = NavyPrimary)
                    }
                }
            }
        }
    }
}

// ─── Location Field ───────────────────────────────────────────────────────────
@Composable
private fun LocationField(
    text     : String,
    isSet    : Boolean,
    onClick  : () -> Unit,
    modifier : Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(InputGray)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Text(
            text     = text,
            fontSize = 14.sp,
            color    = if (isSet) NavyPrimary else HintGray,
            fontWeight = if (isSet) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun VehicleCard(
    vehicle    : Vehicle,
    distanceKm : Int?,       // ← add this
    isSelected : Boolean,
    onClick    : () -> Unit
) {
    val borderColor = if (isSelected) NavyPrimary else BorderGray
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Card(
            modifier  = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
                .clickable { onClick() },
            shape     = RoundedCornerShape(12.dp),
            colors    = CardDefaults.cardColors(
                containerColor = if (isSelected) NavyPrimary.copy(alpha = 0.04f) else Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier            = Modifier.padding(14.dp),
                verticalAlignment   = Alignment.CenterVertically
            ) {
                // Vehicle icon
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(InputGray, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter            = painterResource(id = vehicle.iconRes),
                        contentDescription = vehicle.name,
                        modifier           = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Name + type badge + weight
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = vehicle.name,
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = NavyPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TypeBadge(vehicle.type)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text     = "Up to ${"%,d".format(vehicle.maxWeightKg)} kg",
                            fontSize = 12.sp,
                            color    = HintGray
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    // Replace the rate/distance text
                    if (distanceKm != null) {
                        Text(
                            text      = "₱${vehicle.ratePerKm}/km × ${distanceKm}km",
                            fontSize  = 11.sp,
                            color     = HintGray,
                            textAlign = TextAlign.End,
                            modifier  = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Replace the price text
                if (distanceKm != null) {
                    Text(
                        text       = "₱${"%,d".format(vehicle.computePrice(distanceKm))}",
                        fontSize   = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color      = if (isSelected) NavyPrimary else TextDark
                    )
                } else {
                    Text(
                        text       = "—",
                        fontSize   = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color      = HintGray
                    )
                }
            }
        }

        // "SELECTED" badge clipped to top-right corner
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(topEnd = 12.dp, bottomStart = 8.dp))
                    .background(NavyPrimary)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text       = "SELECTED",
                    fontSize   = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color.White,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

// ─── Type Badge ───────────────────────────────────────────────────────────────
@Composable
private fun TypeBadge(type: VehicleType) {
    val bgColor = when (type) {
        VehicleType.OPEN         -> Color(0xFFE8F5E9)
        VehicleType.ENCLOSED     -> Color(0xFFE3EAF5)
        VehicleType.REFRIGERATED -> Color(0xFFE3F0FB)
    }
    val textColor = when (type) {
        VehicleType.OPEN         -> Color(0xFF2E7D32)
        VehicleType.ENCLOSED     -> NavyPrimary
        VehicleType.REFRIGERATED -> Color(0xFF0277BD)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text          = type.label,
            fontSize      = 10.sp,
            fontWeight    = FontWeight.Bold,
            color         = textColor,
            letterSpacing = 0.3.sp
        )
    }
}

// ─── Bottom Selection Bar ─────────────────────────────────────────────────────
@Composable
private fun BottomSelectionBar(
    vehicle    : Vehicle,
    distanceKm : Int,      // ← add this
    onContinue : () -> Unit,
    modifier   : Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text          = "SELECTED",
                fontSize      = 10.sp,
                fontWeight    = FontWeight.Bold,
                color         = HintGray,
                letterSpacing = 0.5.sp
            )
            Text(
                text       = vehicle.name,
                fontSize   = 15.sp,
                fontWeight = FontWeight.Bold,
                color      = NavyPrimary
            )
            Text(
                text       = "₱${"%,d".format(vehicle.computePrice(distanceKm))}",
                fontSize   = 16.sp,
                fontWeight = FontWeight.Bold,
                color      = NavyPrimary
            )
        }

        Button(
            onClick  = onContinue,
            shape    = RoundedCornerShape(12.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
            modifier = Modifier.height(52.dp)
        ) {
            Text(
                text       = "Continue →",
                fontSize   = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color      = Color.White
            )
        }
    }
}