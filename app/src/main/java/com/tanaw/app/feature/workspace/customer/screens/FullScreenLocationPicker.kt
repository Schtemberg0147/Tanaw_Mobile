package com.tanaw.app.feature.workspace.customer.screens

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tanaw.app.BuildConfig
import com.tanaw.app.R
import com.tanaw.app.feature.workspace.customer.rules.CustomerBookingRules
import com.tanaw.app.feature.workspace.model.RoutePreview
import com.tanaw.app.ui.components.MobileGeocoding
import com.tanaw.app.ui.components.SelectedAddress
import com.tanaw.app.ui.theme.TanawDisplayFontFamily
import com.tanaw.app.ui.theme.TanawMonoFontFamily
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

// ─── Full-Screen Location Picker (Images 2 & 3) ──────────────────────────────
@SuppressLint("MissingPermission", "ClickableViewAccessibility")
@Composable
fun FullScreenLocationPicker(
    isDestination: Boolean,
    initialAddress: String,
    initialPoint: LatLng,
    counterpartAddress: String,
    counterpartPoint: LatLng?,
    savedProfileAddress: String,
    routePreview: RoutePreview?,
    routeLoading: Boolean,
    onBack: () -> Unit,
    onConfirm: (SelectedAddress) -> Unit
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    var currentCenter by remember { mutableStateOf(initialPoint) }
    var currentAddress by rememberSaveable { mutableStateOf(initialAddress.ifBlank { "Moving map pin…" }) }
    var preserveExplicitAddress by remember { mutableStateOf(initialAddress.isNotBlank()) }
    var reverseBusy by remember { mutableStateOf(false) }
    var searchMode by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf(initialAddress) }
    var searchResults by remember { mutableStateOf<List<SelectedAddress>>(emptyList()) }
    var searchBusy by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    val searchFocusRequester = remember { FocusRequester() }

    var fallbackStyle by remember { mutableStateOf(BuildConfig.MAPTILER_KEY.isBlank()) }
    var styleReady by remember { mutableStateOf(false) }
    val mapView = remember { MapLibre.getInstance(context); MapView(context).apply { onCreate(null) } }

    // Camera-idle reverse geocoding (only when not preserving an explicit searched/initial address)
    LaunchedEffect(currentCenter.latitude, currentCenter.longitude, preserveExplicitAddress) {
        if (preserveExplicitAddress && currentAddress.isNotBlank() && currentAddress != "Moving map pin…") {
            reverseBusy = false
            return@LaunchedEffect
        }
        reverseBusy = true
        try {
            delay(350.milliseconds)
            val resolved = MobileGeocoding.reverse(context, currentCenter)
            if (!resolved.isNullOrBlank()) {
                currentAddress = resolved
            } else if (currentAddress.isBlank() || currentAddress == "Moving map pin…") {
                currentAddress = String.format(
                    Locale.US,
                    "Pinned location (%.5f, %.5f)",
                    currentCenter.latitude,
                    currentCenter.longitude
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (currentAddress.isBlank() || currentAddress == "Moving map pin…") {
                currentAddress = String.format(
                    Locale.US,
                    "Pinned location (%.5f, %.5f)",
                    currentCenter.latitude,
                    currentCenter.longitude
                )
            }
        } finally {
            reverseBusy = false
        }
    }

    // Live debounced search results in Search Mode (Image 3)
    LaunchedEffect(searchMode, searchQuery) {
        if (!searchMode) return@LaunchedEffect
        searchError = null
        val trimmed = searchQuery.trim()
        if (trimmed.length < 3) {
            val saved = savedProfileAddress.takeIf { it.length >= 5 }?.let {
                listOf(SelectedAddress(it, initialPoint.latitude, initialPoint.longitude))
            }.orEmpty()
            searchResults = saved + CustomerBookingRules.defaultSuggestions
            searchBusy = false
            return@LaunchedEffect
        }
        searchBusy = true
        try {
            delay(300.milliseconds)
            val remote = runCatching { MobileGeocoding.search(trimmed) }.getOrNull().orEmpty()
            val localMatches = CustomerBookingRules.defaultSuggestions.filter {
                it.label.contains(trimmed, ignoreCase = true)
            }
            searchResults = (remote + localMatches).distinctBy { it.label }
            if (searchResults.isEmpty()) {
                searchResults = listOf(
                    SelectedAddress(trimmed, currentCenter.latitude, currentCenter.longitude)
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            searchError = "Live search unavailable. Select your typed address below or drag the map pin."
            searchResults = listOf(
                SelectedAddress(trimmed, currentCenter.latitude, currentCenter.longitude)
            )
        } finally {
            searchBusy = false
        }
    }

    val gpsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            findDeviceLocation(context)?.let { loc ->
                preserveExplicitAddress = false
                currentCenter = loc
                mapView.getMapAsync { map ->
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(loc, 15.5))
                }
            }
        }
    }

    DisposableEffect(mapView, lifecycle) {
        mapView.getMapAsync { map ->
            map.cameraPosition = CameraPosition.Builder().target(initialPoint).zoom(15.0).build()
            map.addOnCameraIdleListener {
                val target = map.cameraPosition.target
                if (target != null && !preserveExplicitAddress) {
                    currentCenter = LatLng(target.latitude, target.longitude)
                }
            }
        }
        mapView.addOnDidFailLoadingMapListener {
            if (!fallbackStyle) {
                fallbackStyle = true
                mapView.getMapAsync { map ->
                    map.setStyle("https://tiles.openfreemap.org/styles/liberty") { styleReady = true }
                }
            }
        }
        mapView.setOnTouchListener { _, event ->
            if (event.actionMasked == android.view.MotionEvent.ACTION_MOVE) {
                preserveExplicitAddress = false
            }
            mapView.parent?.requestDisallowInterceptTouchEvent(
                event.actionMasked != android.view.MotionEvent.ACTION_UP &&
                    event.actionMasked != android.view.MotionEvent.ACTION_CANCEL
            )
            false
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    val (titleText, subtitleText) = CustomerBookingRules.splitAddress(
        currentAddress.ifBlank { if (isDestination) "Select destination" else "Select pick-up location" }
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Full-Screen MapView
        AndroidView(
            factory = { mapView },
            modifier = Modifier
                .fillMaxSize()
                .testTag("FullScreenPickerMap"),
            update = { view ->
                view.getMapAsync { map ->
                    if (!styleReady) {
                        val url = if (fallbackStyle) {
                            "https://tiles.openfreemap.org/styles/liberty"
                        } else {
                            "https://api.maptiler.com/maps/streets-v2/style.json?key=${BuildConfig.MAPTILER_KEY}"
                        }
                        map.setStyle(url) { styleReady = true }
                    }
                }
            }
        )

        // Center Target Ring Cursor (Image 2)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-24).dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    shape = CircleShape,
                    color = Color.White,
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(10.dp)
                        .background(MaterialTheme.colorScheme.secondary)
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary)
                )
            }
        }

        // Top Floating Address Search Card (Image 2)
        Surface(
            onClick = {
                searchQuery = if (currentAddress == "Moving map pin…") "" else currentAddress
                searchMode = true
            },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth()
                .testTag("TopFloatingAddressCard")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Pin ring indicator
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .border(3.dp, MaterialTheme.colorScheme.secondary, CircleShape)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (reverseBusy) "Locating address…" else titleText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "Search address",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Bottom-Right GPS Recenter FAB + Bottom Confirmation Sheet (Image 2 & Image 1 Screen 1)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 16.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Surface(
                    onClick = {
                        val fineGranted = ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.ACCESS_FINE_LOCATION
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        val coarseGranted = ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        if (fineGranted || coarseGranted) {
                            findDeviceLocation(context)?.let { loc ->
                                preserveExplicitAddress = false
                                currentCenter = loc
                                mapView.getMapAsync { map ->
                                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(loc, 15.5))
                                }
                            }
                        } else {
                            gpsPermissionLauncher.launch(
                                arrayOf(
                                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.MyLocation,
                            contentDescription = "My location",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 12.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.address_details),
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = TanawDisplayFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (counterpartAddress.isNotBlank() && counterpartPoint != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                    Text(
                                        text = if (isDestination) counterpartAddress else currentAddress,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.secondary)
                                    )
                                    Text(
                                        text = if (isDestination) currentAddress else counterpartAddress,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (routeLoading) {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                } else if (routePreview != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${routePreview.distanceKm} km · ${CustomerBookingRules.formatDuration(routePreview.minutes)}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontFamily = TanawMonoFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer
                                        ) {
                                            Text(
                                                text = "ROAD ROUTE",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = TanawMonoFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                fontSize = 9.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            text = currentAddress,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = {
                            val finalLabel = currentAddress.takeIf {
                                it.isNotBlank() && it != "Moving map pin…"
                            } ?: String.format(
                                Locale.US,
                                "Pinned location (%.5f, %.5f)",
                                currentCenter.latitude,
                                currentCenter.longitude
                            )
                            onConfirm(
                                SelectedAddress(
                                    label = finalLabel,
                                    latitude = currentCenter.latitude,
                                    longitude = currentCenter.longitude
                                )
                            )
                        },
                        enabled = !reverseBusy,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("ConfirmLocationButton")
                    ) {
                        Text(
                            text = if (isDestination) stringResource(R.string.confirm_destination)
                            else stringResource(R.string.confirm_pickup_location),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }

        // Full-Screen Address Autocomplete Search Overlay (Image 3)
        if (searchMode) {
            BackHandler { searchMode = false }
            LaunchedEffect(Unit) {
                runCatching { searchFocusRequester.requestFocus() }
            }
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    // Top Rounded Search Input Card (Image 3)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 4.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            IconButton(
                                onClick = { searchMode = false },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = "Back to map",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .border(3.dp, MaterialTheme.colorScheme.secondary, CircleShape)
                            )

                            Box(modifier = Modifier.weight(1f)) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = if (isDestination) stringResource(R.string.enter_destination)
                                        else stringResource(R.string.enter_pickup_location),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Text,
                                        imeAction = ImeAction.Search
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(searchFocusRequester)
                                        .testTag("LocationSearchInput")
                                )
                            }

                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Cancel,
                                        contentDescription = "Clear",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (searchBusy) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    searchError?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        itemsIndexed(searchResults) { _, item ->
                            val (itemTitle, itemSub) = CustomerBookingRules.splitAddress(item.label)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        preserveExplicitAddress = true
                                        reverseBusy = false
                                        currentAddress = item.label
                                        currentCenter = LatLng(item.latitude, item.longitude)
                                        searchMode = false
                                        mapView.getMapAsync { map ->
                                            map.animateCamera(
                                                CameraUpdateFactory.newLatLngZoom(
                                                    LatLng(item.latitude, item.longitude),
                                                    15.5
                                                )
                                            )
                                        }
                                    }
                                    .padding(horizontal = 20.dp, vertical = 14.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    // Target pin icon on left (matching Image 3)
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .border(4.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .width(2.dp)
                                                .height(5.dp)
                                                .background(MaterialTheme.colorScheme.onSurfaceVariant)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = itemTitle,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = itemSub,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 34.dp, top = 14.dp),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun findDeviceLocation(context: Context): LatLng? = runCatching {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
    val best = providers.mapNotNull { provider ->
        runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
    }.maxByOrNull { it.time } ?: return null
    LatLng(best.latitude, best.longitude)
}.getOrNull()
