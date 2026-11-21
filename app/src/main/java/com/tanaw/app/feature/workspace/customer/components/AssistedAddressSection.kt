package com.tanaw.app.feature.workspace.customer.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.tanaw.app.feature.workspace.customer.rules.CustomerAddressRules
import com.tanaw.app.feature.workspace.customer.rules.CustomerBookingRules
import com.tanaw.app.feature.workspace.customer.screens.FullScreenLocationPicker
import com.tanaw.app.ui.components.MobileGeocoding
import com.tanaw.app.ui.components.SelectedAddress
import com.tanaw.app.ui.theme.TanawTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.geometry.LatLng
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal val DEFAULT_ADDRESS_CENTER = LatLng(15.4865, 120.9679) // Cabanatuan City, Nueva Ecija

internal fun hasLocationPermission(context: Context): Boolean {
    val fineGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val coarseGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    return fineGranted || coarseGranted
}

/**
 * Assisted address tools ("Use GPS", "Pick on Map", and optional live address search)
 * that resolve a location into structured Street, Barangay, City, and Province fields.
 */
@Composable
fun AddressQuickAssistBar(
    currentAddressSummary: String,
    enabled: Boolean,
    onAddressAutofilled: (CustomerAddressRules.StructuredCustomerAddress) -> Unit,
    modifier: Modifier = Modifier,
    showInlineSearch: Boolean = true,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var gpsLoading by remember { mutableStateOf(false) }
    var assistStatus by remember { mutableStateOf<String?>(null) }
    var assistIsError by remember { mutableStateOf(false) }
    var showMapPicker by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SelectedAddress>>(emptyList()) }
    var searchBusy by remember { mutableStateOf(false) }

    fun applyPickedLocation(selected: SelectedAddress) {
        if (selected.label.startsWith("Pinned location (", ignoreCase = true)) {
            gpsLoading = true
            assistStatus = null
            scope.launch {
                try {
                    val structured = MobileGeocoding.reverseStructured(
                        context,
                        LatLng(selected.latitude, selected.longitude)
                    )
                    if (structured != null && (structured.streetAddress.isNotBlank() || structured.city.isNotBlank())) {
                        onAddressAutofilled(structured)
                        assistIsError = false
                        assistStatus = "Address filled from map pin."
                    } else {
                        assistIsError = true
                        assistStatus = "Could not detect street details for that pin. Please enter street and city manually."
                    }
                } finally {
                    gpsLoading = false
                }
            }
        } else {
            val parsed = CustomerAddressRules.parseFromGeocodedLabel(selected.label)
            onAddressAutofilled(parsed)
            assistIsError = false
            assistStatus = "Address filled from selected location."
        }
    }

    fun triggerGpsAutofill() {
        if (gpsLoading || !enabled) return
        gpsLoading = true
        assistStatus = null
        assistIsError = false
        scope.launch {
            try {
                val point = fetchBestDeviceLatLng(context)
                if (point == null) {
                    assistIsError = true
                    assistStatus = "Location unavailable. Turn on GPS or use Pick on Map."
                    return@launch
                }
                val structured = MobileGeocoding.reverseStructured(context, point)
                if (structured != null && (structured.streetAddress.isNotBlank() || structured.city.isNotBlank() || structured.barangay.isNotBlank())) {
                    onAddressAutofilled(structured)
                    assistIsError = false
                    assistStatus = "Address filled from your current GPS location."
                } else {
                    assistIsError = true
                    assistStatus = "Unable to resolve street address from GPS. Try Pick on Map."
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                assistIsError = true
                assistStatus = "Unable to read GPS location right now. Try Pick on Map."
            } finally {
                gpsLoading = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            triggerGpsAutofill()
        } else {
            assistIsError = true
            assistStatus = "Location permission denied. You can search or pick on the map instead."
        }
    }

    LaunchedEffect(showInlineSearch, searchQuery) {
        if (!showInlineSearch) return@LaunchedEffect
        val trimmed = searchQuery.trim()
        if (trimmed.length < 3) {
            searchResults = emptyList()
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
            searchResults = (remote + localMatches)
                .distinctBy { it.label }
                .take(5)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            searchResults = CustomerBookingRules.defaultSuggestions
                .filter { it.label.contains(trimmed, ignoreCase = true) }
                .take(5)
        } finally {
            searchBusy = false
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val fineGranted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    val coarseGranted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    if (fineGranted || coarseGranted) {
                        triggerGpsAutofill()
                    } else {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                },
                enabled = enabled && !gpsLoading,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
            ) {
                if (gpsLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Locating…",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.MyLocation,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Use GPS",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            OutlinedButton(
                onClick = {
                    focusManager.clearFocus()
                    showMapPicker = true
                },
                enabled = enabled && !gpsLoading,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Map,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Pick on Map",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (showInlineSearch) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                enabled = enabled,
                placeholder = {
                    Text(
                        text = "Search street, barangay, or landmark…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = {
                            searchQuery = ""
                            searchResults = emptyList()
                        }) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Clear search",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
            )

            if (searchBusy) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            if (searchResults.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        searchResults.forEachIndexed { index, suggestion ->
                            val (title, subtitle) = CustomerBookingRules.splitAddress(suggestion.label)
                            if (index > 0) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    thickness = 0.5.dp
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clickable(role = Role.Button) {
                                        focusManager.clearFocus()
                                        applyPickedLocation(suggestion)
                                        searchQuery = ""
                                        searchResults = emptyList()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        assistStatus?.let { status ->
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = if (assistIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
            )
        }
    }

    if (showMapPicker) {
        Dialog(
            onDismissRequest = { showMapPicker = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            TanawTheme {
                val initialPoint = remember {
                    findCachedLocationOrDefault(context)
                }
                FullScreenLocationPicker(
                    isDestination = false,
                    initialAddress = currentAddressSummary,
                    initialPoint = initialPoint,
                    counterpartAddress = "",
                    counterpartPoint = null,
                    savedProfileAddress = currentAddressSummary,
                    routePreview = null,
                    routeLoading = false,
                    onBack = { showMapPicker = false },
                    onConfirm = { picked ->
                        showMapPicker = false
                        applyPickedLocation(picked)
                    },
                    confirmButtonLabel = "Use This Address"
                )
            }
        }
    }
}

/**
 * Displays up to 5 filtered City / Municipality quick-select chips.
 * Selecting a chip populates both City and Province simultaneously.
 */
@Composable
fun CitySuggestionStrip(
    cityQuery: String,
    enabled: Boolean,
    onSelectCity: (CustomerAddressRules.CityProvinceSuggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    val suggestions = remember(cityQuery) {
        CustomerAddressRules.suggestCities(cityQuery, limit = 5)
    }
    if (suggestions.isEmpty()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        suggestions.forEach { item ->
            val selected = cityQuery.trim().equals(item.city, ignoreCase = true)
            SuggestionChip(
                onClick = { onSelectCity(item) },
                enabled = enabled,
                label = {
                    Text(
                        text = item.city,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    labelColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.heightIn(min = 48.dp)
            )
        }
    }
}

@SuppressLint("MissingPermission")
internal fun findCachedDeviceLatLng(context: Context): LatLng? = runCatching {
    if (!hasLocationPermission(context)) return null
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: return null
    val providers = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER
    )
    val best = providers.mapNotNull { provider ->
        runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
    }.maxByOrNull { it.time } ?: return null
    LatLng(best.latitude, best.longitude)
}.getOrNull()

@SuppressLint("MissingPermission")
internal fun findCachedLocationOrDefault(context: Context): LatLng =
    findCachedDeviceLatLng(context) ?: DEFAULT_ADDRESS_CENTER

@SuppressLint("MissingPermission")
internal suspend fun fetchBestDeviceLatLng(context: Context): LatLng? {
    if (!hasLocationPermission(context)) return null
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: return null
    val providers = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER
    )
    val recentCached = providers.mapNotNull { provider ->
        runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
    }.maxByOrNull { it.time }

    val twoMinutesAgo = System.currentTimeMillis() - 120_000L
    if (recentCached != null && recentCached.time >= twoMinutesAgo) {
        return LatLng(recentCached.latitude, recentCached.longitude)
    }

    val activeProvider = providers.firstOrNull { provider ->
        runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
    }
    if (activeProvider != null) {
        val fresh = withTimeoutOrNull(6.seconds) {
            suspendCancellableCoroutine<Location?> { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                runCatching {
                    LocationManagerCompat.getCurrentLocation(
                        manager,
                        activeProvider,
                        signal,
                        ContextCompat.getMainExecutor(context)
                    ) { location ->
                        if (cont.isActive) cont.resume(location)
                    }
                }.onFailure {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
        if (fresh != null) {
            return LatLng(fresh.latitude, fresh.longitude)
        }
    }

    return recentCached?.let { LatLng(it.latitude, it.longitude) }
}

@Preview(showBackground = true, name = "Address Quick Assist")
@Composable
private fun AddressQuickAssistBarPreview() {
    TanawTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AddressQuickAssistBar(
                    currentAddressSummary = "",
                    enabled = true,
                    onAddressAutofilled = {}
                )
                CitySuggestionStrip(
                    cityQuery = "Cab",
                    enabled = true,
                    onSelectCity = {}
                )
            }
        }
    }
}
