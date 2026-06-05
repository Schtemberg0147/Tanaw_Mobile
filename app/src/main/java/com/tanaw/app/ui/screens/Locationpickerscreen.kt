package com.tanaw.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanaw.app.R
import com.tanaw.app.ui.theme.*

// ─── Pickup Location Picker ───────────────────────────────────────────────────
/**
 * @param onConfirm   Called with the chosen LocationItem
 * @param onBack      Navigate back
 */
@Composable
fun PickupLocationScreen(
    onConfirm : (LocationItem) -> Unit,
    onBack    : () -> Unit
) {
    LocationPickerScreen(
        mode      = LocationPickerMode.PICKUP,
        onConfirm = onConfirm,
        onBack    = onBack
    )
}

// ─── Destination Picker ───────────────────────────────────────────────────────
/**
 * @param origin      The already-confirmed pickup location (shown in route summary)
 * @param onConfirm   Called with the chosen LocationItem
 * @param onBack      Navigate back
 */
@Composable
fun DestinationScreen(
    origin    : LocationItem?,
    onConfirm : (LocationItem) -> Unit,
    onBack    : () -> Unit
) {
    LocationPickerScreen(
        mode      = LocationPickerMode.DESTINATION,
        origin    = origin,
        onConfirm = onConfirm,
        onBack    = onBack
    )
}

// ─── Mode enum ────────────────────────────────────────────────────────────────
private enum class LocationPickerMode { PICKUP, DESTINATION }

// ─── Shared Location Picker ───────────────────────────────────────────────────
@Composable
private fun LocationPickerScreen(
    mode      : LocationPickerMode,
    origin    : LocationItem? = null,
    onConfirm : (LocationItem) -> Unit,
    onBack    : () -> Unit
) {
    var query           by remember { mutableStateOf("") }
    var selectedLocation by remember { mutableStateOf<LocationItem?>(null) }

    // Filtered lists driven by search query
    val pickupResults = remember(query) {
        if (query.isBlank()) MockData.pickupLocations
        else MockData.pickupLocations.filter {
            it.name.contains(query, ignoreCase = true) ||
                    it.subName.contains(query, ignoreCase = true)
        }
    }
    val recentResults = remember(query) {
        if (query.isBlank()) MockData.recentDestinations
        else MockData.recentDestinations.filter {
            it.name.contains(query, ignoreCase = true) ||
                    it.subName.contains(query, ignoreCase = true)
        }
    }
    val suggestedResults = remember(query) {
        if (query.isBlank()) MockData.suggestedDestinations
        else MockData.suggestedDestinations.filter {
            it.name.contains(query, ignoreCase = true) ||
                    it.subName.contains(query, ignoreCase = true)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFBFC8D4))) {

        // ── Mock map background ───────────────────────────────────────────
        MockMapBackground(mode = mode)

        // ── Search panel (top sheet) ──────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
                .background(Color.White)
        ) {
            Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(modifier = Modifier.height(8.dp))

            // Search bar row
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint               = NavyPrimary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                // Navy dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(NavyPrimary, shape = RoundedCornerShape(50))
                )
                Spacer(modifier = Modifier.width(10.dp))
                BasicTextField(
                    value         = query,
                    onValueChange = { query = it },
                    modifier      = Modifier.weight(1f),
                    textStyle     = TextStyle(
                        fontSize   = 15.sp,
                        color      = NavyPrimary,
                        fontWeight = FontWeight.Normal
                    ),
                    singleLine    = true,
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text(
                                text     = if (mode == LocationPickerMode.PICKUP)
                                    "Search pick-up location..." else "Search pick-up location...",
                                fontSize = 15.sp,
                                color    = HintGray
                            )
                        }
                        inner()
                    }
                )
            }

            HorizontalDivider(color = BorderGray, thickness = 1.dp)

            // Location list
            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {

                if (mode == LocationPickerMode.PICKUP) {
                    // Current location row
                    item {
                        CurrentLocationRow(
                            city    = "Cabanatuan City",
                            onClick = {
                                selectedLocation = LocationItem(
                                    "current", "Cabanatuan City Hub Alpha",
                                    "Cabanatuan City, Nueva Ecija"
                                )
                            }
                        )
                        HorizontalDivider(color = BorderGray.copy(alpha = 0.5f))
                    }
                    items(pickupResults) { loc ->
                        LocationRow(
                            item      = loc,
                            isRecent  = false,
                            isSelected = selectedLocation?.id == loc.id,
                            onClick   = { selectedLocation = loc }
                        )
                    }
                } else {
                    // Recent section
                    if (recentResults.isNotEmpty()) {
                        item {
                            SectionLabel("RECENT")
                        }
                        items(recentResults) { loc ->
                            LocationRow(
                                item      = loc,
                                isRecent  = true,
                                isSelected = selectedLocation?.id == loc.id,
                                onClick   = { selectedLocation = loc }
                            )
                        }
                    }
                    // Suggested section
                    if (suggestedResults.isNotEmpty()) {
                        item { SectionLabel("SUGGESTED") }
                        items(suggestedResults) { loc ->
                            LocationRow(
                                item      = loc,
                                isRecent  = false,
                                isSelected = selectedLocation?.id == loc.id,
                                onClick   = { selectedLocation = loc }
                            )
                        }
                    }
                }
            }
        }

        // ── "Drag map to adjust" pill ─────────────────────────────────────
        if (selectedLocation == null) {
            val pillText = if (mode == LocationPickerMode.PICKUP)
                "DRAG MAP TO ADJUST" else "Drag map to set destination"
            val pillBg   = if (mode == LocationPickerMode.PICKUP)
                Color.White else NavyPrimary
            val pillText2Color = if (mode == LocationPickerMode.PICKUP)
                NavyPrimary else Color.White

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-40).dp)
                    .clip(RoundedCornerShape(50))
                    .background(pillBg)
                    .border(if (mode == LocationPickerMode.PICKUP) 1.dp else 0.dp,
                        BorderGray, RoundedCornerShape(50))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text       = pillText,
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = pillText2Color,
                    letterSpacing = if (mode == LocationPickerMode.PICKUP) 0.5.sp else 0.sp
                )
            }
        }

        // ── Crosshair / pin icon in map center ────────────────────────────
        if (mode == LocationPickerMode.PICKUP) {
            Text(
                text     = "+",
                fontSize = 28.sp,
                color    = NavyPrimary,
                fontWeight = FontWeight.Light,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            // Destination pin box
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = 20.dp)
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(NavyPrimary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Outlined.LocationOn,
                    contentDescription = null,
                    tint               = Color.White,
                    modifier           = Modifier.size(26.dp)
                )
            }
        }

        // ── Bottom sheet confirmation ──────────────────────────────────────
        if (selectedLocation != null || mode == LocationPickerMode.PICKUP) {
            val displayLocation = selectedLocation ?: LocationItem(
                "current", "Cabanatuan City Hub Alpha", "Cabanatuan City, Nueva Ecija"
            )

            ConfirmBottomSheet(
                mode     = mode,
                location = displayLocation,
                origin   = origin,
                onConfirm = { onConfirm(displayLocation) },
                modifier  = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

// ─── Mock Map Background ──────────────────────────────────────────────────────
@Composable
private fun MockMapBackground(mode: LocationPickerMode) {
    // Simulates a map — dark grid for pickup (satellite), green terrain for destination
    val bgColor  = if (mode == LocationPickerMode.PICKUP) Color(0xFF3A4A5C) else Color(0xFF7A9E7E)
    val gridColor = if (mode == LocationPickerMode.PICKUP)
        Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.12f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        // Horizontal grid lines
        Column(modifier = Modifier.fillMaxSize()) {
            repeat(20) {
                HorizontalDivider(color = gridColor, thickness = 1.dp)
                Spacer(modifier = Modifier.weight(1f))
            }
        }
        // Vertical grid lines
        Row(modifier = Modifier.fillMaxSize()) {
            repeat(10) {
                VerticalDivider(color = gridColor, thickness = 1.dp)
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

// ─── Confirm Bottom Sheet ─────────────────────────────────────────────────────
@Composable
private fun ConfirmBottomSheet(
    mode      : LocationPickerMode,
    location  : LocationItem,
    origin    : LocationItem?,
    onConfirm : () -> Unit,
    modifier  : Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(Color.White)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Drag handle
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(BorderGray)
                .align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (mode == LocationPickerMode.PICKUP) {
            // ── Pickup confirmation layout ─────────────────────────────────
            Text(
                text          = "PICK-UP LOCATION",
                fontSize      = 10.sp,
                fontWeight    = FontWeight.Bold,
                color         = HintGray,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text       = location.name,
                fontSize   = 18.sp,
                fontWeight = FontWeight.Bold,
                color      = NavyPrimary
            )
            Text(
                text     = location.subName,
                fontSize = 13.sp,
                color    = HintGray
            )
        } else {
            // ── Destination confirmation layout ────────────────────────────
            Row(verticalAlignment = Alignment.Top) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .border(2.dp, NavyPrimary, RoundedCornerShape(50))
                    )
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(28.dp)
                            .background(BorderGray)
                    )
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(OrangeAccent, shape = RoundedCornerShape(50))
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = "Origin", fontSize = 11.sp, color = HintGray)
                    Text(
                        text       = origin?.name ?: "Cabanatuan City Hub Alpha",
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color      = NavyPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text       = location.name,
                        fontSize   = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color      = NavyPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Route info row
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(InputGray)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter            = painterResource(id = R.drawable.ic_car),
                            contentDescription = null,
                            tint               = NavyPrimary,
                            modifier           = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text       = "38 km  •  1h 15min",
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color      = NavyPrimary
                        )
                    }
                    // Toll road badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(OrangeAccent.copy(alpha = 0.12f))
                            .border(1.dp, OrangeAccent.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text          = "⊙ TOLL ROAD",
                            fontSize      = 10.sp,
                            fontWeight    = FontWeight.SemiBold,
                            color         = OrangeAccent,
                            letterSpacing = 0.3.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Confirm button
        Button(
            onClick  = onConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape  = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary)
        ) {
            Text(
                text       = if (mode == LocationPickerMode.PICKUP)
                    "Confirm Pick-up Location" else "Confirm Pick-up Destination",
                fontSize   = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color      = Color.White
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

// ─── Section Label ────────────────────────────────────────────────────────────
@Composable
private fun SectionLabel(text: String) {
    Text(
        text          = text,
        fontSize      = 10.sp,
        fontWeight    = FontWeight.Bold,
        color         = HintGray,
        letterSpacing = 0.8.sp,
        modifier      = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
    )
}

// ─── Current Location Row ─────────────────────────────────────────────────────
@Composable
private fun CurrentLocationRow(city: String, onClick: () -> Unit) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector        = Icons.Outlined.LocationOn,
            contentDescription = null,
            tint               = NavyPrimary,
            modifier           = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color(0xFF4CAF50), shape = RoundedCornerShape(50))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text       = "Current Location",
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = NavyPrimary
                )
            }
            Text(text = city, fontSize = 12.sp, color = HintGray)
        }
        Text(
            text       = "USE THIS",
            fontSize   = 12.sp,
            fontWeight = FontWeight.Bold,
            color      = OrangeAccent,
            modifier   = Modifier.clickable { onClick() }
        )
    }
}

// ─── Location Row ─────────────────────────────────────────────────────────────
@Composable
private fun LocationRow(
    item       : LocationItem,
    isRecent   : Boolean,
    isSelected : Boolean,
    onClick    : () -> Unit
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .background(if (isSelected) NavyPrimary.copy(alpha = 0.05f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector        = if (isRecent) Icons.Outlined.LocationOn else Icons.Outlined.LocationOn,
            contentDescription = null,
            tint               = if (isRecent) HintGray else HintGray,
            modifier           = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                text       = item.name,
                fontSize   = 14.sp,
                fontWeight = FontWeight.Medium,
                color      = NavyPrimary
            )
            Text(
                text     = item.subName,
                fontSize = 12.sp,
                color    = HintGray
            )
        }
    }
}