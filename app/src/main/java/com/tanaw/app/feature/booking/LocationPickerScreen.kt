package com.tanaw.app.feature.booking

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.tanaw.app.BuildConfig
import com.tanaw.app.R
import com.tanaw.app.data.model.LocationItem
import com.tanaw.app.data.model.MockData
import com.tanaw.app.data.model.PickerLocation
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

private enum class LocationPickerMode { PICKUP, DESTINATION }

private val LUZON_BOUNDS = LatLngBounds.Builder()
    .include(LatLng(18.8, 124.5)) // North-East Luzon
    .include(LatLng(11.8, 119.5)) // South-West Luzon
    .build()

private fun resolveMapStyleUrl(): String {
    val key = runCatching {
        BuildConfig::class.java.getField("MAPTILER_KEY").get(null) as? String
    }.getOrNull()
    return if (!key.isNullOrBlank()) {
        "https://api.maptiler.com/maps/streets-v2/style.json?key=$key"
    } else {
        "https://basemaps.cartocdn.com/gl/voyager-gl-style/style.json"
    }
}

@Composable
fun PickupLocationScreen(
    vehicleTypeId: String? = null,
    onConfirm: (LocationItem) -> Unit,
    onBack: () -> Unit,
) {
    LocationPickerScreen(
        mode = LocationPickerMode.PICKUP,
        vehicleTypeId = vehicleTypeId,
        mapStyleUrl = resolveMapStyleUrl(),
        onConfirm = { pickerLoc -> onConfirm(pickerLoc.toLocationItem()) },
        onBack = onBack,
    )
}

@Composable
fun DestinationScreen(
    vehicleTypeId: String? = null,
    origin: LocationItem? = null,
    onConfirm: (LocationItem) -> Unit,
    onBack: () -> Unit,
) {
    LocationPickerScreen(
        mode = LocationPickerMode.DESTINATION,
        vehicleTypeId = vehicleTypeId,
        origin = origin,
        mapStyleUrl = resolveMapStyleUrl(),
        onConfirm = { pickerLoc -> onConfirm(pickerLoc.toLocationItem()) },
        onBack = onBack,
    )
}

@Composable
private fun LocationPickerScreen(
    mode: LocationPickerMode,
    vehicleTypeId: String?,
    mapStyleUrl: String,
    onConfirm: (PickerLocation) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    origin: LocationItem? = null,
    vm: LocationPickerViewModel = hiltViewModel(),
) {
    LaunchedEffect(vehicleTypeId) {
        if (vehicleTypeId != null) {
            vm.setVehicleTypeId(vehicleTypeId)
        }
    }

    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var isSearching by remember { mutableStateOf(false) }

    // Initial camera target: prefers origin, current GPS, or selected location
    val initialTarget = remember(origin, state.selected, state.myLocation) {
        when {
            origin?.latitude != null && origin.longitude != null -> LatLng(origin.latitude!!, origin.longitude!!)
            state.selected != null -> LatLng(state.selected!!.latitude, state.selected!!.longitude)
            state.myLocation != null -> LatLng(state.myLocation!!.first, state.myLocation!!.second)
            else -> LatLng(15.4828, 120.9681) // Cabanatuan City default fallback
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            fetchCurrentLocation(context) { lat, lng ->
                if (lat != null && lng != null) vm.onMyLocation(context, lat, lng) else vm.onNoLocation()
            }
        } else {
            vm.onNoLocation()
        }
    }
    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        fetchCurrentLocation(context) { lat, lng ->
            if (lat != null && lng != null) vm.onMyLocation(context, lat, lng)
        }
    }

    if (isSearching) {
        SearchLocationScreen(
            state = state,
            onQueryChange = vm::onQuery,
            onSelectLocation = { loc ->
                vm.select(loc)
                isSearching = false
                mapInstance?.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 14.0)
                )
            },
            onBack = { isSearching = false },
            modifier = modifier
        )
    } else {
        Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {

            // Real MapLibre Map Background
            LocationsMap(
                styleUrl = mapStyleUrl,
                initialTarget = initialTarget,
                locations = state.locations,
                myLocation = state.myLocation,
                selected = state.selected,
                origin = origin,
                onMarkerTap = { id ->
                    vm.selectById(id)
                    state.locations.firstOrNull { it.id == id }?.let { loc ->
                        mapInstance?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 14.0))
                    }
                },
                onCameraIdle = { lat, lng ->
                    vm.onCameraIdle(context, lat, lng)
                },
                onMapReady = { mapInstance = it },
                modifier = Modifier.fillMaxSize(),
            )

            // Top Floating Address Card
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .clickable { isSearching = true },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack, modifier = Modifier.size(28.dp)) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))

                        // Target Ring Icon
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(50))
                                .padding(2.dp)
                                .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(50))
                                .padding(2.dp)
                                .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(50))
                        )
                        Spacer(modifier = Modifier.width(10.dp))

                        Text(
                            text = state.selected?.name ?: "Drag map to select location",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { isSearching = true }
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { isSearching = true }
                        )
                    }

                    val fullAddress = state.selected?.address ?: listOfNotNull(
                        state.selected?.sublabel,
                        state.selected?.city,
                        state.selected?.province
                    ).joinToString(", ").ifBlank { "Locating address..." }

                    Text(
                        text = fullAddress,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        modifier = Modifier.padding(start = 46.dp, top = 2.dp, end = 28.dp)
                    )
                }
            }

            // Dead-Centered Target Cursor & Facility Label (aligned at vertical center)
            Box(
                modifier = Modifier.align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                TargetRingCursor(
                    color = MaterialTheme.colorScheme.primary
                )

                val facilityName = state.selected?.name
                if (!facilityName.isNullOrBlank()) {
                    Text(
                        text = facilityName,
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            shadow = Shadow(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                                blurRadius = 8f
                            )
                        ),
                        maxLines = 2,
                        modifier = Modifier
                            .offset(x = 18.dp)
                            .widthIn(max = 220.dp)
                    )
                }
            }

            // Floating "Recenter to My Location" Button
            FloatingActionButton(
                onClick = {
                    val myLoc = state.myLocation
                    if (myLoc != null) {
                        mapInstance?.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(LatLng(myLoc.first, myLoc.second), 14.0)
                        )
                    } else {
                        fetchCurrentLocation(context) { lat, lng ->
                            if (lat != null && lng != null) {
                                vm.onMyLocation(context, lat, lng)
                                mapInstance?.animateCamera(
                                    CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 14.0)
                                )
                            }
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 220.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.GpsFixed,
                    contentDescription = "Recenter to My Location"
                )
            }

            // Confirm Bottom Sheet
            val activeSelected = state.selected
            val displayLocation = activeSelected?.toLocationItem() ?: LocationItem(
                "current", "Current Location", state.myLocationName ?: "Luzon, PH"
            )

            ConfirmBottomSheet(
                mode = mode,
                location = displayLocation,
                origin = origin,
                onConfirm = {
                    if (activeSelected != null) {
                        onConfirm(activeSelected)
                    } else {
                        val fallback = PickerLocation(
                            id = displayLocation.id,
                            name = displayLocation.name,
                            address = displayLocation.subName,
                            latitude = state.myLocation?.first ?: 15.4828,
                            longitude = state.myLocation?.second ?: 120.9681
                        )
                        onConfirm(fallback)
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun SearchLocationScreen(
    state: PickerUiState,
    onQueryChange: (String) -> Unit,
    onSelectLocation: (PickerLocation) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
    ) {
        // Top Search Bar (matching Image 2)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Orange Target Ring Icon
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .background(Color(0xFFE55B2B), shape = RoundedCornerShape(50))
                        .padding(2.5.dp)
                        .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(50))
                        .padding(2.5.dp)
                        .background(Color(0xFFE55B2B), shape = RoundedCornerShape(50))
                )

                Spacer(modifier = Modifier.width(10.dp))

                BasicTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    textStyle = TextStyle(
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    ),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        if (state.query.isEmpty()) {
                            Text(
                                text = "Search address or landmark...",
                                style = TextStyle(
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            )
                        }
                        innerTextField()
                    }
                )

                if (state.query.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cancel,
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        // Suggestions / Results List (matching Image 2)
        val displayList = remember(state.query, state.searchResults, state.locations) {
            if (state.query.isNotBlank()) state.searchResults else state.locations
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(displayList, key = { it.id }) { loc ->
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectLocation(loc) }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Location Target Pin Icon (circle outline with dot inside - matching Image 2)
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .border(2.dp, MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(50)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant, shape = RoundedCornerShape(50))
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = loc.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val subText = loc.address ?: listOfNotNull(loc.sublabel, loc.city, loc.province).joinToString(", ")
                            Text(
                                text = subText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TargetRingCursor(
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(24.dp)
            .background(color, shape = RoundedCornerShape(50))
            .padding(3.dp)
            .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(50))
            .padding(3.dp)
            .background(color, shape = RoundedCornerShape(50))
    )
}

@Composable
private fun ConfirmBottomSheet(
    mode: LocationPickerMode,
    location: LocationItem,
    origin: LocationItem?,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.outline)
                .align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (mode == LocationPickerMode.PICKUP) {
            Text(
                text = "PICK-UP LOCATION",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = location.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = location.subName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(verticalAlignment = Alignment.Top) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                    )
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(28.dp)
                            .background(MaterialTheme.colorScheme.outline)
                    )
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(MaterialTheme.colorScheme.secondary, shape = RoundedCornerShape(50))
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Origin", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = origin?.name ?: "Current Location",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = location.name,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val distanceKm = remember(origin, location) {
                if (origin != null) {
                    MockData.computeDistance(origin, location)
                } else 38
            }
            val durationMinutes = remember(distanceKm) {
                ((distanceKm / 35.0) * 60).toInt() + 15
            }
            val hours = durationMinutes / 60
            val mins = durationMinutes % 60
            val durationText = when {
                hours > 0 && mins > 0 -> "${hours}h ${mins}min"
                hours > 0 -> "${hours}h"
                else -> "${mins}min"
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_car),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$distanceKm km  •  $durationText",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f))
                            .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "⊙ TOLL ROAD",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.secondary,
                            letterSpacing = 0.3.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text(
                text = if (mode == LocationPickerMode.PICKUP)
                    "Confirm Pick-up Location" else "Confirm Pick-up Destination",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
    )
}

@Composable
private fun CurrentLocationRow(locationLabel: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
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
                    text = "Current Location",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(text = locationLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = "USE THIS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.clickable { onClick() }
        )
    }
}

@Composable
private fun LocationRow(
    item: LocationItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = item.subName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@SuppressLint("MissingPermission")
private fun fetchCurrentLocation(context: Context, onResult: (Double?, Double?) -> Unit) {
    val fusedClient = LocationServices.getFusedLocationProviderClient(context)
    fusedClient.lastLocation.addOnSuccessListener { last ->
        if (last != null && last.latitude != 0.0 && last.longitude != 0.0) {
            onResult(last.latitude, last.longitude)
        } else {
            fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { cur -> onResult(cur?.latitude, cur?.longitude) }
                .addOnFailureListener { onResult(null, null) }
        }
    }.addOnFailureListener {
        fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            .addOnSuccessListener { cur -> onResult(cur?.latitude, cur?.longitude) }
            .addOnFailureListener { onResult(null, null) }
    }
}

private const val SRC_LOCATIONS = "src-locations"
private const val SRC_SELECTED = "src-selected"
private const val SRC_ME = "src-me"
private const val LAYER_LOCATIONS = "layer-locations"
private const val LAYER_SELECTED = "layer-selected"
private const val LAYER_ME = "layer-me"

@Composable
private fun LocationsMap(
    styleUrl: String,
    initialTarget: LatLng,
    locations: List<PickerLocation>,
    myLocation: Pair<Double, Double>?,
    selected: PickerLocation?,
    origin: LocationItem?,
    onMarkerTap: (String) -> Unit,
    onCameraIdle: (Double, Double) -> Unit,
    onMapReady: (MapLibreMap) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mapView = rememberMapViewWithLifecycle()
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    val currentOnTap by rememberUpdatedState(onMarkerTap)
    val currentOnCameraIdle by rememberUpdatedState(onCameraIdle)
    var isUserGestureActive by remember { mutableStateOf(false) }
    var hasCenteredOnMyLocation by remember { mutableStateOf(false) }

    AndroidView(
        modifier = modifier,
        factory = {
            mapView.also { mv ->
                mv.getMapAsync { m ->
                    m.cameraPosition = CameraPosition.Builder()
                        .target(initialTarget)
                        .zoom(13.0)
                        .build()
                    m.setMinZoomPreference(6.0)
                    m.setMaxZoomPreference(18.0)
                    runCatching {
                        m.setLatLngBoundsForCameraTarget(LUZON_BOUNDS)
                    }

                    m.addOnCameraMoveStartedListener { reason ->
                        if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                            isUserGestureActive = true
                        }
                    }

                    m.addOnCameraIdleListener {
                        val center = m.cameraPosition.target
                        if (center != null && isUserGestureActive) {
                            currentOnCameraIdle(center.latitude, center.longitude)
                        }
                    }

                    m.setStyle(styleUrl) { s ->
                        s.addSource(GeoJsonSource(SRC_LOCATIONS))
                        s.addSource(GeoJsonSource(SRC_SELECTED))
                        s.addSource(GeoJsonSource(SRC_ME))
                        s.addLayer(circle(LAYER_LOCATIONS, SRC_LOCATIONS, 8f, "#1E6BD6"))
                        s.addLayer(circle(LAYER_SELECTED, SRC_SELECTED, 13f, "#E5484D"))
                        s.addLayer(circle(LAYER_ME, SRC_ME, 7f, "#12B76A"))
                        m.addOnMapClickListener { latLng ->
                            val point = m.projection.toScreenLocation(latLng)
                            val hit = m.queryRenderedFeatures(point, LAYER_SELECTED, LAYER_LOCATIONS)
                                .firstOrNull()
                            val id = hit?.getStringProperty("id")
                            if (id != null) currentOnTap(id)
                            id != null
                        }
                        map = m
                        style = s
                        onMapReady(m)
                    }
                }
            }
        },
    )

    LaunchedEffect(style, locations) {
        style?.getSourceAs<GeoJsonSource>(SRC_LOCATIONS)?.setGeoJson(locations.toFeatureCollection())
    }
    LaunchedEffect(style, selected) {
        style?.getSourceAs<GeoJsonSource>(SRC_SELECTED)
            ?.setGeoJson(listOfNotNull(selected).toFeatureCollection())
    }
    LaunchedEffect(style, myLocation) {
        val me = myLocation ?: return@LaunchedEffect
        style?.getSourceAs<GeoJsonSource>(SRC_ME)
            ?.setGeoJson(Feature.fromGeometry(Point.fromLngLat(me.second, me.first)))

        if (!hasCenteredOnMyLocation && origin == null) {
            hasCenteredOnMyLocation = true
            map?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(me.first, me.second), 14.0)
            )
        }
    }
}

private fun circle(layerId: String, sourceId: String, radius: Float, color: String) =
    CircleLayer(layerId, sourceId).withProperties(
        circleRadius(radius),
        circleColor(color),
        circleStrokeWidth(2f),
        circleStrokeColor("#FFFFFF"),
    )

private fun List<PickerLocation>.toFeatureCollection(): FeatureCollection =
    FeatureCollection.fromFeatures(map { l ->
        Feature.fromGeometry(Point.fromLngLat(l.longitude, l.latitude)).apply {
            addStringProperty("id", l.id)
        }
    })

@Composable
private fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context)
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(Bundle())
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
            if (lifecycle.currentState != Lifecycle.State.DESTROYED) {
                mapView.onPause(); mapView.onStop(); mapView.onDestroy()
            }
        }
    }
    return mapView
}
