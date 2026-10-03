package com.tanaw.app.feature.booking

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
import com.tanaw.app.data.model.LocationItem
import com.tanaw.app.data.model.MockData
import com.tanaw.app.data.model.MockData.computeDistance
import com.tanaw.app.data.model.Vehicle
import com.tanaw.app.data.model.VehicleType
import java.util.Calendar

@Composable
fun HomeScreen(
    uiState            : HomeUiState = HomeUiState(isLoading = false),
    onRetry            : () -> Unit = {},
    pickupLocation     : LocationItem? = null,
    destination        : LocationItem? = null,
    onPickupClick      : () -> Unit = {},
    onDestinationClick : () -> Unit = {},
    onContinue         : (Vehicle, Int) -> Unit = { _, _ -> }
) {
    if (uiState.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Preparing database connection...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    if (uiState.errorMessage != null && uiState.userProfile == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Database Connection Error",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onRetry) {
                    Text("Retry Connection")
                }
            }
        }
        return
    }

    val userName = uiState.userProfile?.fullName?.takeIf { it.isNotBlank() } ?: "User"
    val userCity = uiState.userProfile?.city?.takeIf { it.isNotBlank() } ?: "Cabanatuan City"

    var selectedVehicle by remember { mutableStateOf<Vehicle?>(null) }

    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 0..11  -> "Good morning,"
            in 12..17 -> "Good afternoon,"
            else      -> "Good evening,"
        }
    }

    val distanceKm: Int? = remember(pickupLocation, destination) {
        if (pickupLocation != null && destination != null)
            computeDistance(pickupLocation, destination)
        else null
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {

        LazyColumn(
            modifier            = Modifier.fillMaxSize(),
            contentPadding      = PaddingValues(bottom = if (selectedVehicle != null) 96.dp else 24.dp)
        ) {

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Image(
                        painter            = painterResource(id = R.drawable.ic_tanaw_logo),
                        contentDescription = "Tanaw",
                        modifier           = Modifier.height(28.dp)
                    )
                    Box {
                        Icon(
                            imageVector        = Icons.Outlined.Notifications,
                            contentDescription = "Notifications",
                            tint               = MaterialTheme.colorScheme.primary,
                            modifier           = Modifier.size(26.dp)
                        )
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(MaterialTheme.colorScheme.secondary, shape = RoundedCornerShape(50))
                                .align(Alignment.TopEnd)
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                ) {
                    Text(
                        text     = greeting,
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text       = userName,
                        fontSize   = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector        = Icons.Outlined.LocationOn,
                            contentDescription = null,
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier           = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text  = userCity,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

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

            item {
                Text(
                    text       = "Available Vehicles",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.primary,
                    modifier   = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

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
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text       = "Where are you shipping?",
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(50))
                )
                Spacer(modifier = Modifier.width(10.dp))
                LocationField(
                    text        = pickupLocation?.name ?: "Enter pick-up location",
                    isSet       = pickupLocation != null,
                    onClick     = onPickupClick,
                    modifier    = Modifier.weight(1f)
                )
            }

            Row {
                Box(modifier = Modifier.width(14.dp), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(20.dp)
                            .background(MaterialTheme.colorScheme.outline)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(MaterialTheme.colorScheme.secondary, shape = RoundedCornerShape(50))
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

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(
                    modifier = Modifier
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
                        .clip(RoundedCornerShape(50))
                        .clickable { }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter            = painterResource(id = R.drawable.ic_timer),
                            contentDescription = null,
                            tint               = MaterialTheme.colorScheme.primary,
                            modifier           = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text       = "Pick-up now",
                            style      = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color      = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text  = "∨",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

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
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Text(
            text       = text,
            style      = MaterialTheme.typography.bodyMedium,
            color      = if (isSet) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isSet) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun VehicleCard(
    vehicle    : Vehicle,
    distanceKm : Int?,
    isSelected : Boolean,
    onClick    : () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
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
                containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier            = Modifier.padding(14.dp),
                verticalAlignment   = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter            = painterResource(id = vehicle.iconRes),
                        contentDescription = vehicle.name,
                        modifier           = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = vehicle.name,
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TypeBadge(vehicle.type)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text  = "Up to ${"%,d".format(vehicle.maxWeightKg)} kg",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    if (distanceKm != null) {
                        Text(
                            text      = "₱${vehicle.ratePerKm}/km × ${distanceKm}km",
                            style     = MaterialTheme.typography.bodySmall,
                            color     = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                            modifier  = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                if (distanceKm != null) {
                    Text(
                        text       = "₱${"%,d".format(vehicle.computePrice(distanceKm))}",
                        fontSize   = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color      = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Text(
                        text       = "—",
                        fontSize   = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(topEnd = 12.dp, bottomStart = 8.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text          = "SELECTED",
                    style         = MaterialTheme.typography.labelSmall,
                    fontWeight    = FontWeight.Bold,
                    color         = MaterialTheme.colorScheme.onPrimary,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
private fun TypeBadge(type: VehicleType) {
    val bgColor = when (type) {
        VehicleType.OPEN         -> Color(0xFFE8F5E9)
        VehicleType.ENCLOSED     -> MaterialTheme.colorScheme.secondaryContainer
        VehicleType.REFRIGERATED -> Color(0xFFE3F0FB)
    }
    val textColor = when (type) {
        VehicleType.OPEN         -> Color(0xFF2E7D32)
        VehicleType.ENCLOSED     -> MaterialTheme.colorScheme.primary
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
            style         = MaterialTheme.typography.labelSmall,
            fontWeight    = FontWeight.Bold,
            color         = textColor,
            letterSpacing = 0.3.sp
        )
    }
}

@Composable
private fun BottomSelectionBar(
    vehicle    : Vehicle,
    distanceKm : Int,
    onContinue : () -> Unit,
    modifier   : Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text          = "SELECTED",
                style         = MaterialTheme.typography.labelSmall,
                fontWeight    = FontWeight.Bold,
                color         = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp
            )
            Text(
                text       = vehicle.name,
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.primary
            )
            Text(
                text       = "₱${"%,d".format(vehicle.computePrice(distanceKm))}",
                fontSize   = 16.sp,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.primary
            )
        }

        Button(
            onClick  = onContinue,
            shape    = RoundedCornerShape(12.dp),
            colors   = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor   = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier.height(52.dp)
        ) {
            Text(
                text       = "Continue →",
                style      = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}
