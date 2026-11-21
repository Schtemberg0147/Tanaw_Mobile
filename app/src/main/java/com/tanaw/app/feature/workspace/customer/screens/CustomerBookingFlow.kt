package com.tanaw.app.feature.workspace.customer.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tanaw.app.core.validation.MobileValidation
import com.tanaw.app.feature.workspace.customer.components.DEFAULT_ADDRESS_CENTER
import com.tanaw.app.feature.workspace.customer.components.PickupTimeBottomSheet
import com.tanaw.app.feature.workspace.customer.components.fetchBestDeviceLatLng
import com.tanaw.app.feature.workspace.customer.components.findCachedDeviceLatLng
import com.tanaw.app.feature.workspace.customer.components.hasLocationPermission
import com.tanaw.app.feature.workspace.customer.rules.CustomerAddressRules
import com.tanaw.app.feature.workspace.customer.rules.CustomerBookingRules
import com.tanaw.app.feature.workspace.customer.rules.CustomerNoShowRules
import com.tanaw.app.feature.workspace.customer.rules.MobileBookingRules
import com.tanaw.app.feature.workspace.customer.store.BookingDraft
import com.tanaw.app.feature.workspace.model.RoutePreview
import com.tanaw.app.feature.workspace.state.MobileState
import com.tanaw.app.feature.workspace.viewmodel.MobileWorkspaceViewModel
import com.tanaw.app.feature.workspace.viewmodel.obj
import com.tanaw.app.feature.workspace.viewmodel.rows
import com.tanaw.app.feature.workspace.viewmodel.value
import com.tanaw.app.ui.components.MobileGeocoding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.maplibre.android.geometry.LatLng
import java.time.Instant
import java.util.UUID
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

enum class CustomerBookingStep {
    HOME,
    PICKUP_MAP,
    DESTINATION_MAP,
    ADD_DETAILS,
    SUMMARY,
    SUBMITTED
}

@Composable
fun CustomerBookingFlow(
    vm: MobileWorkspaceViewModel,
    state: MobileState,
    data: JsonObject,
    onOpenAlerts: () -> Unit,
    onTrackShipment: (String) -> Unit,
    onFullScreenStepChanged: (Boolean) -> Unit = {},
    onOpenProfile: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val profile = data.obj("profile")
    val customer = data.obj("customer")
    val vehicles = data.rows("vehicles")
    val pricingRates = remember(data) {
        data.rows("pricing_rates").ifEmpty { data.rows("pricingRates") }
    }
    val unreadCount = data.rows("notifications").count { it.value("read_at").isBlank() }
    val customerId = customer.value("id").ifBlank { profile.value("id") }
    val coroutineScope = rememberCoroutineScope()

    val savedDefaultAddress = remember(customer) {
        CustomerAddressRules.parseFromSaved(
            rawAddress = customer.value("address"),
            rawCity = customer.value("city"),
            explicitBarangay = customer.value("barangay"),
            explicitProvince = customer.value("province")
        )
    }
    val fullSavedProfileAddress = savedDefaultAddress.fullDisplayAddress().ifBlank { customer.value("address") }

    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    var userOriginLat by rememberSaveable { mutableStateOf<Double?>(null) }
    var userOriginLng by rememberSaveable { mutableStateOf<Double?>(null) }
    val userOriginPoint = userOriginLat?.let { lat -> userOriginLng?.let { lng -> LatLng(lat, lng) } }
    var userOriginAddress by rememberSaveable { mutableStateOf("") }
    var userOriginCity by rememberSaveable { mutableStateOf("") }
    var isLocatingUser by remember { mutableStateOf(false) }
    var locationPermissionGranted by remember { mutableStateOf(hasLocationPermission(context)) }
    var locationPermissionPrompted by rememberSaveable(customerId) { mutableStateOf(false) }

    fun updateOriginCoordinate(point: LatLng) {
        val currentLat = userOriginLat
        val currentLng = userOriginLng
        if (currentLat == null || currentLng == null ||
            abs(currentLat - point.latitude) >= 0.0005 ||
            abs(currentLng - point.longitude) >= 0.0005
        ) {
            userOriginLat = point.latitude
            userOriginLng = point.longitude
        }
    }

    fun refreshUserLocation() {
        locationPermissionGranted = hasLocationPermission(context)
        if (!locationPermissionGranted) return
        findCachedDeviceLatLng(context)?.let { cached ->
            updateOriginCoordinate(cached)
        }
        coroutineScope.launch {
            isLocatingUser = true
            try {
                fetchBestDeviceLatLng(context)?.let { fresh ->
                    updateOriginCoordinate(fresh)
                }
            } finally {
                isLocatingUser = false
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            hasLocationPermission(context)
        locationPermissionGranted = granted
        if (granted) {
            refreshUserLocation()
        }
    }

    // Automatically track user location on login/home entry, or prompt for precise/approximate permission if not yet configured
    LaunchedEffect(customerId) {
        val granted = hasLocationPermission(context)
        locationPermissionGranted = granted
        if (granted) {
            refreshUserLocation()
        } else if (!locationPermissionPrompted) {
            locationPermissionPrompted = true
            runCatching {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    // Continuous location tracking while in the foreground when permission is granted
    @SuppressLint("MissingPermission")
    DisposableEffect(context, lifecycle, locationPermissionGranted) {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val locationListener = LocationListener { location ->
            updateOriginCoordinate(LatLng(location.latitude, location.longitude))
        }
        var registered = false

        fun startTracking() {
            if (registered || !hasLocationPermission(context) || manager == null) return
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            providers.forEach { provider ->
                if (runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)) {
                    runCatching {
                        manager.requestLocationUpdates(
                            provider,
                            15_000L,
                            50f,
                            locationListener,
                            Looper.getMainLooper()
                        )
                        registered = true
                    }
                }
            }
        }

        fun stopTracking() {
            if (!registered || manager == null) return
            runCatching { manager.removeUpdates(locationListener) }
            registered = false
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    val nowGranted = hasLocationPermission(context)
                    locationPermissionGranted = nowGranted
                    if (nowGranted) {
                        refreshUserLocation()
                        startTracking()
                    }
                }
                Lifecycle.Event.ON_PAUSE -> stopTracking()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && locationPermissionGranted) {
            startTracking()
        }
        onDispose {
            lifecycle.removeObserver(observer)
            stopTracking()
        }
    }

    // Reverse-geocode the tracked user origin coordinates into city & address
    LaunchedEffect(userOriginLat, userOriginLng) {
        val point = userOriginPoint ?: return@LaunchedEffect
        isLocatingUser = true
        try {
            val structured = MobileGeocoding.reverseStructured(context, point)
            if (structured != null) {
                val city = structured.city.trim()
                val province = structured.province.trim()
                val formattedCity = when {
                    city.isNotBlank() && province.isNotBlank() -> "$city, $province"
                    city.isNotBlank() -> city
                    province.isNotBlank() -> province
                    else -> ""
                }
                if (formattedCity.isNotBlank()) {
                    userOriginCity = formattedCity
                }
                val fullAddr = structured.fullDisplayAddress()
                if (fullAddr.isNotBlank()) {
                    userOriginAddress = fullAddr
                }
            }
            if (userOriginAddress.isBlank() || userOriginCity.isBlank()) {
                val rawLabel = MobileGeocoding.reverse(context, point)
                if (!rawLabel.isNullOrBlank()) {
                    if (userOriginAddress.isBlank()) {
                        userOriginAddress = rawLabel
                    }
                    if (userOriginCity.isBlank()) {
                        userOriginCity = CustomerBookingRules.resolveHeaderCity(
                            rawAddress = "",
                            rawCity = "",
                            liveLocationAddress = rawLabel
                        )
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Keep existing resolved origin city/address if geocoding fails offline
        } finally {
            isLocatingUser = false
        }
    }

    var stepName by rememberSaveable { mutableStateOf(CustomerBookingStep.HOME.name) }
    val step = runCatching { CustomerBookingStep.valueOf(stepName) }.getOrDefault(CustomerBookingStep.HOME)
    fun setStep(next: CustomerBookingStep) {
        stepName = next.name
    }

    LaunchedEffect(step) {
        onFullScreenStepChanged(step != CustomerBookingStep.HOME)
    }

    var requestId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var pickup by rememberSaveable { mutableStateOf("") }
    var pickupLat by rememberSaveable { mutableStateOf<Double?>(null) }
    var pickupLng by rememberSaveable { mutableStateOf<Double?>(null) }
    var dropoff by rememberSaveable { mutableStateOf("") }
    var dropoffLat by rememberSaveable { mutableStateOf<Double?>(null) }
    var dropoffLng by rememberSaveable { mutableStateOf<Double?>(null) }

    var scheduledDeparture by rememberSaveable { mutableStateOf("") }
    var scheduleAdjustmentNotice by rememberSaveable { mutableStateOf<String?>(null) }
    var showScheduleSheet by rememberSaveable { mutableStateOf(false) }

    var selectedVehicleId by rememberSaveable { mutableStateOf("") }
    val selectedVehicle = vehicles.firstOrNull { it.value("id") == selectedVehicleId } ?: vehicles.firstOrNull()
    val minLeadTimeHours = CustomerBookingRules.minLeadTimeHours(selectedVehicle)
    val leadTimeHelperText = CustomerBookingRules.leadTimeHelperText(minLeadTimeHours)
    LaunchedEffect(vehicles) {
        if (selectedVehicleId.isBlank() && vehicles.isNotEmpty()) {
            selectedVehicleId = vehicles.first().value("id")
        }
    }

    var contact by rememberSaveable(profile.value("phone")) { mutableStateOf(profile.value("phone")) }
    var cargo by rememberSaveable { mutableStateOf("") }
    var weight by rememberSaveable { mutableStateOf("") }
    var handling by rememberSaveable { mutableStateOf("None") }
    var notes by rememberSaveable { mutableStateOf("") }

    var submittedShipmentId by rememberSaveable { mutableStateOf("") }
    var submittedTrackingNumber by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.restoredBookingConfirmation) {
        state.restoredBookingConfirmation?.let { conf ->
            submittedShipmentId = conf.id
            submittedTrackingNumber = conf.trackingNumber
            pickup = conf.pickup
            dropoff = conf.dropoff
            if (conf.vehicleId.isNotBlank()) selectedVehicleId = conf.vehicleId
            scheduledDeparture = conf.scheduledDeparture
            scheduleAdjustmentNotice = null
            setStep(CustomerBookingStep.SUBMITTED)
            vm.clearRestoredBookingConfirmation()
        }
    }

    var availableDraft by remember { mutableStateOf<BookingDraft?>(null) }
    var draftOfferDismissed by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(customerId) {
        if (customerId.isNotBlank() && !draftOfferDismissed && pickup.isBlank() && dropoff.isBlank() && cargo.isBlank() && step == CustomerBookingStep.HOME) {
            val saved = withContext(Dispatchers.IO) { vm.bookingDraftStore.read(customerId) }
            if (saved != null && saved.hasMeaningfulContent()) {
                availableDraft = if (saved.submitted) saved.copy(submitted = false) else saved
            }
        }
    }

    val onResumeDraft: () -> Unit = {
        availableDraft?.let { draft ->
            requestId = draft.requestId
            pickup = draft.pickup
            pickupLat = draft.pickupLat
            pickupLng = draft.pickupLng
            dropoff = draft.dropoff
            dropoffLat = draft.dropoffLat
            dropoffLng = draft.dropoffLng
            scheduledDeparture = draft.scheduledDeparture
            scheduleAdjustmentNotice = null
            if (draft.selectedVehicleId.isNotBlank()) selectedVehicleId = draft.selectedVehicleId
            if (draft.contact.isNotBlank()) contact = draft.contact
            cargo = draft.cargo
            weight = draft.weight
            handling = draft.handling
            notes = draft.notes
            val targetStep = runCatching { CustomerBookingStep.valueOf(draft.stepName) }.getOrDefault(CustomerBookingStep.HOME)
            if (targetStep != CustomerBookingStep.SUBMITTED) {
                setStep(targetStep)
            }
            availableDraft = null
        }
    }
    val onDiscardDraft: () -> Unit = {
        if (customerId.isNotBlank()) {
            coroutineScope.launch(Dispatchers.IO) {
                vm.bookingDraftStore.clear(customerId)
            }
        }
        availableDraft = null
        draftOfferDismissed = true
    }

    var bookingClock by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            bookingClock = Instant.now()
            delay(1.seconds)
        }
    }

    fun selectVehicleAndValidateSchedule(newVehicleId: String) {
        selectedVehicleId = newVehicleId
        val nextVehicle = vehicles.firstOrNull { it.value("id") == newVehicleId }
        val nextLeadHours = CustomerBookingRules.minLeadTimeHours(nextVehicle)
        if (scheduledDeparture.isNotBlank()) {
            val validation = CustomerBookingRules.ensureValidScheduledRaw(
                raw = scheduledDeparture,
                now = bookingClock,
                minLeadTimeHours = nextLeadHours
            )
            if (validation.wasAdjusted) {
                scheduledDeparture = validation.raw
                scheduleAdjustmentNotice =
                    "Pick-up time adjusted to ${CustomerBookingRules.formatSchedulePill(validation.raw, bookingClock)} (${CustomerBookingRules.leadTimeHelperText(nextLeadHours).lowercase()})."
            } else {
                scheduleAdjustmentNotice = null
            }
        } else {
            scheduleAdjustmentNotice = null
        }
    }

    var routePreview by remember { mutableStateOf<RoutePreview?>(null) }
    var routeLoading by remember { mutableStateOf(false) }
    val pickupPoint = pickupLat?.let { lat -> pickupLng?.let { lng -> LatLng(lat, lng) } }
    val dropoffPoint = dropoffLat?.let { lat -> dropoffLng?.let { lng -> LatLng(lat, lng) } }

    LaunchedEffect(pickupPoint?.latitude, pickupPoint?.longitude, dropoffPoint?.latitude, dropoffPoint?.longitude) {
        routePreview = null
        if (pickupPoint != null && dropoffPoint != null) {
            routeLoading = true
            try {
                delay(250.milliseconds)
                routePreview = vm.previewRoute(pickupPoint, dropoffPoint)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                routePreview = null
            } finally {
                routeLoading = false
            }
        } else {
            routeLoading = false
        }
    }

    fun resetForm() {
        if (customerId.isNotBlank()) {
            coroutineScope.launch(Dispatchers.IO) {
                vm.bookingDraftStore.clear(customerId)
            }
        }
        availableDraft = null
        requestId = UUID.randomUUID().toString()
        pickup = ""
        pickupLat = null
        pickupLng = null
        dropoff = ""
        dropoffLat = null
        dropoffLng = null
        scheduledDeparture = ""
        scheduleAdjustmentNotice = null
        cargo = ""
        weight = ""
        handling = "None"
        notes = ""
        submittedShipmentId = ""
        submittedTrackingNumber = ""
        setStep(CustomerBookingStep.HOME)
    }

    LaunchedEffect(
        customerId, requestId, step, pickup, pickupLat, pickupLng,
        dropoff, dropoffLat, dropoffLng, scheduledDeparture,
        selectedVehicleId, contact, cargo, weight, handling, notes,
        state.busy
    ) {
        if (customerId.isNotBlank() && step != CustomerBookingStep.SUBMITTED && availableDraft == null && !state.busy) {
            delay(300.milliseconds)
            val currentDraft = BookingDraft(
                requestId = requestId,
                stepName = step.name,
                pickup = pickup,
                pickupLat = pickupLat,
                pickupLng = pickupLng,
                dropoff = dropoff,
                dropoffLat = dropoffLat,
                dropoffLng = dropoffLng,
                scheduledDeparture = scheduledDeparture,
                selectedVehicleId = selectedVehicleId,
                contact = contact,
                cargo = cargo,
                weight = weight,
                handling = handling,
                notes = notes,
                submitted = false,
                owner = profile.value("id")
            )
            if (currentDraft.hasMeaningfulContent()) {
                withContext(Dispatchers.IO) {
                    vm.bookingDraftStore.save(customerId, currentDraft)
                }
            }
        }
    }

    BackHandler(step != CustomerBookingStep.HOME && !state.busy) {
        when (step) {
            CustomerBookingStep.PICKUP_MAP,
            CustomerBookingStep.DESTINATION_MAP,
            CustomerBookingStep.ADD_DETAILS -> setStep(CustomerBookingStep.HOME)
            CustomerBookingStep.SUMMARY -> setStep(CustomerBookingStep.ADD_DETAILS)
            CustomerBookingStep.SUBMITTED -> resetForm()
            CustomerBookingStep.HOME -> Unit
        }
    }

    val shipments = data.rows("shipments")
    val activeShipmentsCount = shipments.count { it.value("status") !in listOf("completed", "cancelled") }
    val completedShipmentsCount = shipments.count { it.value("status") == "completed" }
    val parsedWeight = MobileValidation.weight(weight)
    val baseLinehaulEstimate = remember(selectedVehicle, pricingRates, routePreview?.distanceKm, parsedWeight) {
        CustomerBookingRules.estimateBaseLinehaul(
            vehicle = selectedVehicle,
            pricingRates = pricingRates,
            distanceKm = routePreview?.distanceKm,
            weightKg = parsedWeight
        )
    }
    val hasValidSchedule = scheduledDeparture.isNotBlank() &&
        MobileBookingRules.departure(scheduledDeparture, bookingClock, minLeadTimeHours.toLong()) != null
    val customerStanding = remember(data, bookingClock) {
        CustomerNoShowRules.evaluateStanding(data, bookingClock)
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (step) {
            CustomerBookingStep.HOME -> {
                val resolvedHeaderCity = remember(customer, pickup, userOriginCity, userOriginAddress) {
                    CustomerBookingRules.resolveHeaderCity(
                        rawAddress = customer.value("address"),
                        rawCity = customer.value("city"),
                        explicitBarangay = customer.value("barangay"),
                        explicitProvince = customer.value("province"),
                        pickupAddress = pickup,
                        liveLocationCity = userOriginCity,
                        liveLocationAddress = userOriginAddress
                    )
                }
                CustomerBookingHomeContent(
                    customerName = profile.value("full_name").ifBlank { "Customer" },
                    customerCity = resolvedHeaderCity,
                    unreadCount = unreadCount,
                    activeShipmentsCount = activeShipmentsCount,
                    completedShipmentsCount = completedShipmentsCount,
                    pickup = pickup,
                    dropoff = dropoff,
                    scheduleLabel = CustomerBookingRules.formatSchedulePill(scheduledDeparture, bookingClock),
                    vehicles = vehicles,
                    selectedVehicleId = selectedVehicle?.value("id").orEmpty(),
                    routePreview = routePreview,
                    busy = state.busy,
                    errorMessage = state.error,
                    statusMessage = state.message,
                    availableDraft = availableDraft,
                    onResumeDraft = onResumeDraft,
                    onDiscardDraft = onDiscardDraft,
                    onOpenProfile = onOpenProfile,
                    onRefresh = vm::refresh,
                    onOpenAlerts = onOpenAlerts,
                    onOpenActiveShipments = { onTrackShipment("") },
                    onPickupClick = { setStep(CustomerBookingStep.PICKUP_MAP) },
                    showTopBar = false,
                    leadTimeHelperText = leadTimeHelperText,
                    scheduleAdjustmentNotice = scheduleAdjustmentNotice,
                    pricingRates = pricingRates,
                    isScheduleSet = hasValidSchedule,
                    customerStanding = customerStanding,
                    isLocatingUser = isLocatingUser,
                    onLocationHeaderClick = {
                        if (hasLocationPermission(context)) {
                            refreshUserLocation()
                        } else {
                            runCatching {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        }
                    },
                    onDestinationClick = { setStep(CustomerBookingStep.DESTINATION_MAP) },
                    onSwapLocations = {
                        val tmpAddr = pickup
                        val tmpLat = pickupLat
                        val tmpLng = pickupLng
                        pickup = dropoff
                        pickupLat = dropoffLat
                        pickupLng = dropoffLng
                        dropoff = tmpAddr
                        dropoffLat = tmpLat
                        dropoffLng = tmpLng
                    },
                    onScheduleClick = { showScheduleSheet = true },
                    onSelectVehicle = { selectVehicleAndValidateSchedule(it) },
                    onContinue = {
                        when {
                            pickup.trim().length < 5 || pickupPoint == null -> setStep(CustomerBookingStep.PICKUP_MAP)
                            dropoff.trim().length < 5 || dropoffPoint == null -> setStep(CustomerBookingStep.DESTINATION_MAP)
                            pickup.trim().equals(dropoff.trim(), ignoreCase = true) -> setStep(CustomerBookingStep.DESTINATION_MAP)
                            !hasValidSchedule -> showScheduleSheet = true
                            else -> setStep(CustomerBookingStep.ADD_DETAILS)
                        }
                    }
                )
            }

            CustomerBookingStep.PICKUP_MAP -> {
                val originFallback = userOriginPoint ?: findCachedDeviceLatLng(context)
                FullScreenLocationPicker(
                    isDestination = false,
                    initialAddress = pickup,
                    initialPoint = pickupPoint ?: originFallback ?: dropoffPoint ?: DEFAULT_ADDRESS_CENTER,
                    counterpartAddress = dropoff,
                    counterpartPoint = dropoffPoint,
                    savedProfileAddress = fullSavedProfileAddress,
                    routePreview = routePreview,
                    routeLoading = routeLoading,
                    onBack = { setStep(CustomerBookingStep.HOME) },
                    onConfirm = { selected ->
                        pickup = selected.label
                        pickupLat = selected.latitude
                        pickupLng = selected.longitude
                        setStep(CustomerBookingStep.HOME)
                    },
                    userOriginPoint = originFallback,
                    userOriginAddress = userOriginAddress
                )
            }

            CustomerBookingStep.DESTINATION_MAP -> {
                val originFallback = userOriginPoint ?: findCachedDeviceLatLng(context) ?: pickupPoint
                FullScreenLocationPicker(
                    isDestination = true,
                    initialAddress = dropoff,
                    initialPoint = dropoffPoint ?: originFallback ?: LatLng(14.6158, 120.9585),
                    counterpartAddress = pickup,
                    counterpartPoint = pickupPoint,
                    savedProfileAddress = fullSavedProfileAddress,
                    routePreview = routePreview,
                    routeLoading = routeLoading,
                    onBack = { setStep(CustomerBookingStep.HOME) },
                    onConfirm = { selected ->
                        dropoff = selected.label
                        dropoffLat = selected.latitude
                        dropoffLng = selected.longitude
                        setStep(CustomerBookingStep.HOME)
                    },
                    userOriginPoint = originFallback,
                    userOriginAddress = userOriginAddress
                )
            }

            CustomerBookingStep.ADD_DETAILS -> {
                AddMoreDetailsScreen(
                    pickup = pickup,
                    dropoff = dropoff,
                    routePreview = routePreview,
                    vehicles = vehicles,
                    selectedVehicle = selectedVehicle,
                    contact = contact,
                    cargo = cargo,
                    weight = weight,
                    handling = handling,
                    notes = notes,
                    busy = state.busy,
                    errorMessage = state.error,
                    baseLinehaulEstimate = baseLinehaulEstimate,
                    onBack = { setStep(CustomerBookingStep.HOME) },
                    onEditRoute = { setStep(CustomerBookingStep.HOME) },
                    onContactChange = { contact = it },
                    onCargoChange = { cargo = it },
                    onWeightChange = { newWeight ->
                        weight = newWeight
                        val currentCap = selectedVehicle?.value("default_capacity_kg").orEmpty()
                        if (!CustomerBookingRules.weightFitsCapacity(newWeight, currentCap)) {
                            vehicles.firstOrNull { CustomerBookingRules.weightFitsCapacity(newWeight, it.value("default_capacity_kg")) }
                                ?.let { selectVehicleAndValidateSchedule(it.value("id")) }
                        }
                    },
                    onSelectVehicle = { selectVehicleAndValidateSchedule(it) },
                    onHandlingChange = { handling = it },
                    onNotesChange = { notes = it },
                    onConfirm = { setStep(CustomerBookingStep.SUMMARY) },
                    savedContact = profile.value("phone").ifBlank { customer.value("phone") }
                )
            }

            CustomerBookingStep.SUMMARY -> {
                val resolvedInstant = if (scheduledDeparture.isNotBlank()) {
                    MobileBookingRules.departure(scheduledDeparture, bookingClock, minLeadTimeHours.toLong())
                } else {
                    null
                }
                val estimatedArrivalIso = resolvedInstant?.let {
                    MobileBookingRules.calculateEstimatedArrival(
                        departureIso = it,
                        routeDurationMinutes = routePreview?.minutes,
                        loadingBufferMinutes = CustomerBookingRules.loadingBufferMinutes(selectedVehicle)
                    )
                }
                val estimatedArrivalSummary = estimatedArrivalIso?.let {
                    CustomerBookingRules.formatEstimatedArrival(it)
                } ?: ""
                val normalizedContact = MobileValidation.phone(contact)
                val vehicleCap = selectedVehicle?.value("default_capacity_kg").orEmpty()
                val canSubmit = !state.busy && !state.offline &&
                    pickup.trim().length in 5..500 &&
                    dropoff.trim().length in 5..500 &&
                    !pickup.trim().equals(dropoff.trim(), ignoreCase = true) &&
                    pickupLat != null && pickupLng != null &&
                    dropoffLat != null && dropoffLng != null &&
                    cargo.trim().length in 3..1000 &&
                    parsedWeight != null &&
                    selectedVehicle != null &&
                    CustomerBookingRules.weightFitsCapacity(weight, vehicleCap) &&
                    normalizedContact != null &&
                    resolvedInstant != null &&
                    notes.length <= 2000

                BookingSummaryScreen(
                    pickup = pickup,
                    dropoff = dropoff,
                    routePreview = routePreview,
                    vehicleName = selectedVehicle?.value("name").orEmpty(),
                    vehicleCapacity = selectedVehicle?.value("default_capacity_kg").orEmpty(),
                    weight = weight,
                    cargo = cargo,
                    handling = handling,
                    contact = normalizedContact ?: contact,
                    notes = notes,
                    scheduleSummary = CustomerBookingRules.formatSummarySchedule(scheduledDeparture, bookingClock),
                    estimatedArrivalSummary = estimatedArrivalSummary,
                    baseLinehaulEstimate = baseLinehaulEstimate,
                    busy = state.busy,
                    offline = state.offline,
                    canSubmit = canSubmit,
                    errorMessage = state.error,
                    onBack = { setStep(CustomerBookingStep.ADD_DETAILS) },
                    onEditRoute = { setStep(CustomerBookingStep.HOME) },
                    onSubmitBooking = {
                        val dep = MobileBookingRules.departure(
                            raw = scheduledDeparture,
                            now = Instant.now(),
                            minLeadTimeHours = minLeadTimeHours.toLong()
                        ) ?: return@BookingSummaryScreen
                        val estArrival = MobileBookingRules.calculateEstimatedArrival(
                            departureIso = dep,
                            routeDurationMinutes = routePreview?.minutes,
                            loadingBufferMinutes = CustomerBookingRules.loadingBufferMinutes(selectedVehicle)
                        )
                        val payload = buildJsonObject {
                            put("pickup", pickup.trim())
                            put("dropoff", dropoff.trim())
                            put("cargo", cargo.trim())
                            put("weight", parsedWeight)
                            put("vehicle_id", selectedVehicle?.value("id").orEmpty())
                            put("contact", normalizedContact)
                            put("departure", dep)
                            put("scheduled_departure", dep)
                            estArrival?.let { put("estimated_arrival", it) }
                            routePreview?.let {
                                put("distance_km", it.distanceKm)
                                put("route_distance_km", it.distanceKm)
                                put("route_duration_minutes", it.minutes)
                            }
                            baseLinehaulEstimate?.let { put("estimated_linehaul", it.amount) }
                            put("notes", notes.trim())
                            put("pickup_lat", pickupLat)
                            put("pickup_lng", pickupLng)
                            put("dropoff_lat", dropoffLat)
                            put("dropoff_lng", dropoffLng)
                            put("handling", handling)
                        }
                        if (customerId.isNotBlank()) {
                            vm.bookingDraftStore.markSubmitted(customerId, requestId)
                        }
                        vm.book(requestId, payload) { id, tracking ->
                            if (customerId.isNotBlank()) {
                                vm.bookingDraftStore.clear(customerId)
                            }
                            submittedShipmentId = id
                            submittedTrackingNumber = tracking
                            setStep(CustomerBookingStep.SUBMITTED)
                        }
                    }
                )
            }

            CustomerBookingStep.SUBMITTED -> {
                BookingSubmittedScreen(
                    trackingNumber = submittedTrackingNumber.ifBlank { "Pending Reference" },
                    pickup = pickup,
                    dropoff = dropoff,
                    vehicleName = selectedVehicle?.value("name").orEmpty(),
                    scheduleSummary = CustomerBookingRules.formatSchedulePill(scheduledDeparture, bookingClock),
                    onTrackShipment = {
                        val targetId = submittedShipmentId
                        resetForm()
                        onTrackShipment(targetId)
                    },
                    onBookAnother = { resetForm() }
                )
            }
        }
    }

    if (showScheduleSheet) {
        PickupTimeBottomSheet(
            scheduledRaw = scheduledDeparture,
            minLeadTimeHours = minLeadTimeHours,
            helperText = leadTimeHelperText,
            now = bookingClock,
            onDismiss = { showScheduleSheet = false },
            onSchedule = { formatted ->
                scheduledDeparture = formatted
                scheduleAdjustmentNotice = null
                showScheduleSheet = false
            }
        )
    }
}
