package com.tanaw.app.feature.workspace

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.location.LocationManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tanaw.app.BuildConfig
import com.tanaw.app.R
import com.tanaw.app.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import java.text.NumberFormat
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt
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

object CustomerBookingRules {
    private val displayTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val displayDayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE MMM d", Locale.ENGLISH)

    fun greeting(now: Instant = Instant.now()): String {
        val hour = now.atZone(MobileBookingRules.zone).hour
        return when (hour) {
            in 5..11 -> "Good morning,"
            in 12..17 -> "Good afternoon,"
            else -> "Good evening,"
        }
    }

    fun vehicleBadge(name: String): String {
        val lower = name.lowercase(Locale.ENGLISH)
        return when {
            "refrig" in lower || "reefer" in lower || "cold" in lower -> "REFRIGERATED"
            "dropside" in lower || "flatbed" in lower || "open" in lower -> "OPEN"
            else -> "ENCLOSED"
        }
    }

    fun vehicleIconRes(name: String): Int {
        val lower = name.lowercase(Locale.ENGLISH)
        return when {
            "dropside" in lower -> R.drawable.ic_vehicle_dropside
            "flatbed" in lower -> R.drawable.ic_vehicle_flatbed
            "refrig" in lower || "reefer" in lower -> R.drawable.ic_vehicle_refrigerated
            "wing" in lower || "10-wheeler" in lower || "10 wheeler" in lower || "ten wheeler" in lower ->
                R.drawable.ic_vehicle_wing_van
            else -> R.drawable.ic_vehicle_closed_van
        }
    }

    fun formatCapacity(rawKg: String): String {
        val value = rawKg.toDoubleOrNull()?.roundToInt() ?: return "Up to $rawKg kg"
        return "Up to ${NumberFormat.getIntegerInstance(Locale.US).format(value)} kg"
    }

    fun resolveDepartureRaw(isPickupNow: Boolean, scheduledRaw: String, now: Instant = Instant.now()): String {
        if (!isPickupNow && scheduledRaw.isNotBlank() && MobileBookingRules.departure(scheduledRaw, now) != null) {
            return scheduledRaw.trim()
        }
        return MobileBookingRules.earliest(now).plusMinutes(5).format(MobileBookingRules.format)
    }

    fun scheduledDays(now: Instant = Instant.now()): List<LocalDate> {
        val tomorrow = now.atZone(MobileBookingRules.zone).toLocalDate().plusDays(1)
        return (0L..29L).map { offset -> tomorrow.plusDays(offset) }
    }

    fun formatSchedulePill(isPickupNow: Boolean, scheduledRaw: String, now: Instant = Instant.now()): String {
        if (isPickupNow || scheduledRaw.isBlank()) return "Pick-up now"
        val parsed = runCatching { LocalDateTime.parse(scheduledRaw.trim(), MobileBookingRules.format) }.getOrNull()
            ?: return "Pick-up now"
        val today = now.atZone(MobileBookingRules.zone).toLocalDate()
        val dayLabel = when (parsed.toLocalDate()) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            else -> parsed.toLocalDate().format(displayDayFormat)
        }
        return "$dayLabel, ${parsed.toLocalTime().format(displayTimeFormat)}"
    }

    fun formatSummarySchedule(isPickupNow: Boolean, scheduledRaw: String, now: Instant = Instant.now()): String {
        return if (isPickupNow || scheduledRaw.isBlank()) {
            "Immediate · ${formatSchedulePill(false, resolveDepartureRaw(true, "", now), now)} PHT"
        } else {
            "Scheduled: ${formatSchedulePill(false, scheduledRaw, now)} PHT"
        }
    }

    fun splitAddress(raw: String): Pair<String, String> {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return "" to ""
        val parts = trimmed.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return if (parts.size <= 1) {
            trimmed to "Philippines"
        } else {
            parts.first() to parts.drop(1).joinToString(", ")
        }
    }

    fun formatDuration(minutes: Int): String {
        if (minutes < 60) return "Est. ${minutes.coerceAtLeast(1)}min"
        val hrs = minutes / 60
        val mins = minutes % 60
        return if (mins == 0) "Est. ${hrs}h" else "Est. ${hrs}h ${mins}min"
    }

    fun weightFitsCapacity(weightRaw: String, capacityRaw: String): Boolean {
        val w = MobileValidation.weight(weightRaw) ?: return false
        val c = capacityRaw.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 } ?: return false
        return w <= c
    }

    val defaultSuggestions: List<SelectedAddress> = listOf(
        SelectedAddress("Cabanatuan City Public Market, Cabanatuan City, Nueva Ecija", 15.4865, 120.9679),
        SelectedAddress("SM City Cabanatuan, Maharlika Highway, Cabanatuan City, Nueva Ecija", 15.4673, 120.9547),
        SelectedAddress("Manila North Harbor, Tondo, Manila, Metro Manila", 14.6158, 120.9585),
        SelectedAddress("Valenzuela Gateway Complex, Paso de Blas, Valenzuela, Metro Manila", 14.7094, 120.9936),
        SelectedAddress("Bulihan Provincial Rd, Balagtas, 3016 Bulacan, Philippines", 14.8181, 120.9092),
        SelectedAddress("Clark Freeport Zone, Angeles City, Pampanga", 15.1859, 120.5600)
    )
}

@Composable
fun TanawBrandLogo(
    modifier: Modifier = Modifier,
    darkTheme: Boolean = isSystemInDarkTheme()
) {
    Image(
        painter = painterResource(id = if (darkTheme) R.drawable.ic_tanaw_logo_dark else R.drawable.ic_tanaw_logo),
        contentDescription = "TANAW logo",
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}

@Composable
fun CustomerBookingFlow(
    vm: MobileWorkspaceViewModel,
    state: MobileState,
    data: JsonObject,
    onOpenAlerts: () -> Unit,
    onTrackShipment: (String) -> Unit,
    onFullScreenStepChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val profile = data.obj("profile")
    val customer = data.obj("customer")
    val vehicles = data.rows("vehicles")
    val unreadCount = data.rows("notifications").count { it.value("read_at").isBlank() }
    val customerId = customer.value("id").ifBlank { profile.value("id") }

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

    var isPickupNow by rememberSaveable { mutableStateOf(true) }
    var scheduledDeparture by rememberSaveable { mutableStateOf("") }
    var showScheduleSheet by rememberSaveable { mutableStateOf(false) }

    var selectedVehicleId by rememberSaveable { mutableStateOf("") }
    val selectedVehicle = vehicles.firstOrNull { it.value("id") == selectedVehicleId } ?: vehicles.firstOrNull()
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
            isPickupNow = conf.isPickupNow
            scheduledDeparture = conf.scheduledDeparture
            setStep(CustomerBookingStep.SUBMITTED)
            vm.clearRestoredBookingConfirmation()
        }
    }

    var availableDraft by remember { mutableStateOf<BookingDraft?>(null) }
    var draftOfferDismissed by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(customerId) {
        if (customerId.isNotBlank() && !draftOfferDismissed && pickup.isBlank() && dropoff.isBlank() && cargo.isBlank() && step == CustomerBookingStep.HOME) {
            val saved = vm.bookingDraftStore.read(customerId)
            if (saved != null && saved.hasMeaningfulContent() && !saved.submitted) {
                availableDraft = saved
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
            isPickupNow = draft.isPickupNow
            scheduledDeparture = draft.scheduledDeparture
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
            vm.bookingDraftStore.clear(customerId)
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
            vm.bookingDraftStore.clear(customerId)
        }
        availableDraft = null
        requestId = UUID.randomUUID().toString()
        pickup = ""
        pickupLat = null
        pickupLng = null
        dropoff = ""
        dropoffLat = null
        dropoffLng = null
        isPickupNow = true
        scheduledDeparture = ""
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
        dropoff, dropoffLat, dropoffLng, isPickupNow, scheduledDeparture,
        selectedVehicleId, contact, cargo, weight, handling, notes
    ) {
        if (customerId.isNotBlank() && step != CustomerBookingStep.SUBMITTED && availableDraft == null) {
            val currentDraft = BookingDraft(
                requestId = requestId,
                stepName = step.name,
                pickup = pickup,
                pickupLat = pickupLat,
                pickupLng = pickupLng,
                dropoff = dropoff,
                dropoffLat = dropoffLat,
                dropoffLng = dropoffLng,
                isPickupNow = isPickupNow,
                scheduledDeparture = scheduledDeparture,
                selectedVehicleId = selectedVehicleId,
                contact = contact,
                cargo = cargo,
                weight = weight,
                handling = handling,
                notes = notes,
                submitted = false
            )
            if (currentDraft.hasMeaningfulContent()) {
                vm.bookingDraftStore.save(customerId, currentDraft)
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

    Box(modifier = modifier.fillMaxSize()) {
        when (step) {
            CustomerBookingStep.HOME -> {
                CustomerBookingHomeContent(
                    customerName = profile.value("full_name").ifBlank { "Customer" },
                    customerCity = customer.value("city").ifBlank { "Cabanatuan City" },
                    unreadCount = unreadCount,
                    activeShipmentsCount = activeShipmentsCount,
                    completedShipmentsCount = completedShipmentsCount,
                    pickup = pickup,
                    dropoff = dropoff,
                    scheduleLabel = CustomerBookingRules.formatSchedulePill(isPickupNow, scheduledDeparture, bookingClock),
                    vehicles = vehicles,
                    selectedVehicleId = selectedVehicle?.value("id").orEmpty(),
                    routePreview = routePreview,
                    busy = state.busy,
                    errorMessage = state.error,
                    statusMessage = state.message,
                    availableDraft = availableDraft,
                    onResumeDraft = onResumeDraft,
                    onDiscardDraft = onDiscardDraft,
                    onRefresh = vm::refresh,
                    onOpenAlerts = onOpenAlerts,
                    onOpenActiveShipments = { onTrackShipment("") },
                    onPickupClick = { setStep(CustomerBookingStep.PICKUP_MAP) },
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
                    onSelectVehicle = { selectedVehicleId = it },
                    onContinue = {
                        when {
                            pickup.trim().length < 5 || pickupPoint == null -> setStep(CustomerBookingStep.PICKUP_MAP)
                            dropoff.trim().length < 5 || dropoffPoint == null -> setStep(CustomerBookingStep.DESTINATION_MAP)
                            pickup.trim().equals(dropoff.trim(), ignoreCase = true) -> setStep(CustomerBookingStep.DESTINATION_MAP)
                            else -> setStep(CustomerBookingStep.ADD_DETAILS)
                        }
                    }
                )
            }

            CustomerBookingStep.PICKUP_MAP -> {
                FullScreenLocationPicker(
                    isDestination = false,
                    initialAddress = pickup,
                    initialPoint = pickupPoint ?: dropoffPoint ?: LatLng(15.4865, 120.9679),
                    counterpartAddress = dropoff,
                    counterpartPoint = dropoffPoint,
                    savedProfileAddress = customer.value("address"),
                    routePreview = routePreview,
                    routeLoading = routeLoading,
                    onBack = { setStep(CustomerBookingStep.HOME) },
                    onConfirm = { selected ->
                        pickup = selected.label
                        pickupLat = selected.latitude
                        pickupLng = selected.longitude
                        setStep(CustomerBookingStep.HOME)
                    }
                )
            }

            CustomerBookingStep.DESTINATION_MAP -> {
                FullScreenLocationPicker(
                    isDestination = true,
                    initialAddress = dropoff,
                    initialPoint = dropoffPoint ?: pickupPoint ?: LatLng(14.6158, 120.9585),
                    counterpartAddress = pickup,
                    counterpartPoint = pickupPoint,
                    savedProfileAddress = customer.value("address"),
                    routePreview = routePreview,
                    routeLoading = routeLoading,
                    onBack = { setStep(CustomerBookingStep.HOME) },
                    onConfirm = { selected ->
                        dropoff = selected.label
                        dropoffLat = selected.latitude
                        dropoffLng = selected.longitude
                        setStep(CustomerBookingStep.HOME)
                    }
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
                    onBack = { setStep(CustomerBookingStep.HOME) },
                    onEditRoute = { setStep(CustomerBookingStep.HOME) },
                    onContactChange = { contact = it },
                    onCargoChange = { cargo = it },
                    onWeightChange = { newWeight ->
                        weight = newWeight
                        val currentCap = selectedVehicle?.value("default_capacity_kg").orEmpty()
                        if (!CustomerBookingRules.weightFitsCapacity(newWeight, currentCap)) {
                            vehicles.firstOrNull { CustomerBookingRules.weightFitsCapacity(newWeight, it.value("default_capacity_kg")) }
                                ?.let { selectedVehicleId = it.value("id") }
                        }
                    },
                    onSelectVehicle = { selectedVehicleId = it },
                    onHandlingChange = { handling = it },
                    onNotesChange = { notes = it },
                    onConfirm = { setStep(CustomerBookingStep.SUMMARY) }
                )
            }

            CustomerBookingStep.SUMMARY -> {
                val resolvedDepartureRaw = CustomerBookingRules.resolveDepartureRaw(isPickupNow, scheduledDeparture, bookingClock)
                val resolvedInstant = MobileBookingRules.departure(resolvedDepartureRaw, bookingClock)
                val parsedWeight = MobileValidation.weight(weight)
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
                    scheduleSummary = CustomerBookingRules.formatSummarySchedule(isPickupNow, scheduledDeparture, bookingClock),
                    busy = state.busy,
                    offline = state.offline,
                    canSubmit = canSubmit,
                    errorMessage = state.error,
                    onBack = { setStep(CustomerBookingStep.ADD_DETAILS) },
                    onEditRoute = { setStep(CustomerBookingStep.HOME) },
                    onSubmitBooking = {
                        val dep = MobileBookingRules.departure(
                            CustomerBookingRules.resolveDepartureRaw(isPickupNow, scheduledDeparture, Instant.now()),
                            Instant.now()
                        ) ?: return@BookingSummaryScreen
                        val payload = buildJsonObject {
                            put("pickup", pickup.trim())
                            put("dropoff", dropoff.trim())
                            put("cargo", cargo.trim())
                            put("weight", parsedWeight)
                            put("vehicle_id", selectedVehicle?.value("id").orEmpty())
                            put("contact", normalizedContact)
                            put("departure", dep)
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
                    scheduleSummary = CustomerBookingRules.formatSchedulePill(isPickupNow, scheduledDeparture, bookingClock),
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
            isPickupNow = isPickupNow,
            scheduledRaw = scheduledDeparture,
            now = bookingClock,
            onDismiss = { showScheduleSheet = false },
            onSelectNow = {
                isPickupNow = true
                scheduledDeparture = ""
                showScheduleSheet = false
            },
            onSchedule = { formatted ->
                isPickupNow = false
                scheduledDeparture = formatted
                showScheduleSheet = false
            }
        )
    }
}

@Composable
internal fun CustomerBookingHomeContent(
    customerName: String,
    customerCity: String,
    unreadCount: Int,
    pickup: String,
    dropoff: String,
    scheduleLabel: String,
    vehicles: List<JsonObject>,
    selectedVehicleId: String,
    routePreview: RoutePreview?,
    busy: Boolean,
    errorMessage: String?,
    statusMessage: String?,
    onRefresh: () -> Unit,
    onOpenAlerts: () -> Unit,
    onPickupClick: () -> Unit,
    onDestinationClick: () -> Unit,
    onSwapLocations: () -> Unit,
    onScheduleClick: () -> Unit,
    onSelectVehicle: (String) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    activeShipmentsCount: Int = 0,
    completedShipmentsCount: Int = 0,
    onOpenActiveShipments: () -> Unit = {},
    availableDraft: BookingDraft? = null,
    onResumeDraft: () -> Unit = {},
    onDiscardDraft: () -> Unit = {}
) {
    val selectedVehicle = vehicles.firstOrNull { it.value("id") == selectedVehicleId }
    val sameLocationError = pickup.trim().length >= 5 && dropoff.trim().length >= 5 &&
        pickup.trim().equals(dropoff.trim(), ignoreCase = true)
    val routeReady = pickup.trim().length >= 5 && dropoff.trim().length >= 5 && !sameLocationError

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Header: TANAW Logo (dark-mode aware) + Notification Bell + Refresh
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TanawBrandLogo(modifier = Modifier.size(40.dp))
                    Text(
                        text = "TANAW",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onRefresh, enabled = !busy) {
                        Text("Refresh")
                    }
                    IconButton(onClick = onOpenAlerts) {
                        BadgedBox(
                            badge = {
                                if (unreadCount > 0) {
                                    Badge(containerColor = MaterialTheme.colorScheme.secondary) {
                                        Text(unreadCount.coerceAtMost(99).toString())
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Notifications,
                                contentDescription = stringResource(R.string.notifications),
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
            }

            if (busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            errorMessage?.let {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = it,
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            statusMessage?.let {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = it,
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            availableDraft?.let { draft ->
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Assignment,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Unfinished Booking Draft",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        val summaryText = buildString {
                            if (draft.pickup.isNotBlank() || draft.dropoff.isNotBlank()) {
                                append(draft.pickup.ifBlank { "Pickup" })
                                append(" → ")
                                append(draft.dropoff.ifBlank { "Dropoff" })
                            }
                            if (draft.cargo.isNotBlank()) {
                                if (isNotEmpty()) append(" • ")
                                append(draft.cargo)
                            }
                        }.ifBlank { "Draft inputs from previous session" }
                        Text(
                            text = summaryText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                            maxLines = 2
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = onDiscardDraft) {
                                Text(
                                    text = "Discard",
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = onResumeDraft,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    contentColor = MaterialTheme.colorScheme.onSecondary
                                )
                            ) {
                                Text("Resume Draft")
                            }
                        }
                    }
                }
            }

            // Greeting + Customer Name + City Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = CustomerBookingRules.greeting(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = customerName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = customerCity,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (activeShipmentsCount > 0 || completedShipmentsCount > 0) {
                Surface(
                    onClick = onOpenActiveShipments,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.LocalShipping,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "$activeShipmentsCount active  ·  $completedShipmentsCount completed",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "View →",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // "Where are you shipping?" Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 2.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.where_are_you_shipping),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Pick-up Location Row
                            LocationSelectorField(
                                label = stringResource(R.string.pickup),
                                value = pickup,
                                placeholder = stringResource(R.string.enter_pickup_location),
                                dotColor = MaterialTheme.colorScheme.primary,
                                onClick = onPickupClick,
                                modifier = Modifier.testTag("PickupLocationField")
                            )

                            // Destination Row
                            LocationSelectorField(
                                label = stringResource(R.string.destination),
                                value = dropoff,
                                placeholder = stringResource(R.string.enter_destination),
                                dotColor = MaterialTheme.colorScheme.secondary,
                                onClick = onDestinationClick,
                                modifier = Modifier.testTag("DestinationLocationField")
                            )
                        }

                        if (pickup.isNotBlank() || dropoff.isNotBlank()) {
                            Surface(
                                onClick = onSwapLocations,
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 3.dp,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 10.dp)
                                    .size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.SwapVert,
                                        contentDescription = "Swap pick-up and destination",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (sameLocationError) {
                        Text(
                            text = "Pick-up and destination must be different locations.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    // Bottom row inside card: route distance preview (if loaded) + Pick-up time pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (routePreview != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Route,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "${routePreview.distanceKm} km · ${CustomerBookingRules.formatDuration(routePreview.minutes)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        OutlinedButton(
                            onClick = onScheduleClick,
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("SchedulePillButton")
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_timer),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = scheduleLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Outlined.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Available Vehicles Header
            Text(
                text = stringResource(R.string.available_vehicles),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            if (vehicles.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No vehicle types are available right now. Refresh or contact the office.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                vehicles.forEach { vehicle ->
                    val id = vehicle.value("id")
                    val name = vehicle.value("name")
                    val capacityKg = vehicle.value("default_capacity_kg")
                    val isSelected = id == selectedVehicleId
                    VehicleSelectionCard(
                        name = name,
                        capacityText = CustomerBookingRules.formatCapacity(capacityKg),
                        badgeText = CustomerBookingRules.vehicleBadge(name),
                        iconRes = CustomerBookingRules.vehicleIconRes(name),
                        selected = isSelected,
                        onClick = { onSelectVehicle(id) }
                    )
                }
            }
        }

        // Sticky Bottom Bar when a vehicle is available/selected
        if (selectedVehicle != null) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedVehicle.value("name").uppercase(Locale.ENGLISH),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.awaiting_office_quotation),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (routeReady) stringResource(R.string.estimated_upon_review)
                            else "Select pick-up & destination to continue",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = onContinue,
                        enabled = !busy,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("BookingContinueButton")
                    ) {
                        Text(
                            text = stringResource(R.string.continue_action),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationSelectorField(
    label: String,
    value: String,
    placeholder: String,
    dotColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
                Text(
                    text = value.ifBlank { placeholder },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (value.isBlank()) FontWeight.Normal else FontWeight.SemiBold,
                    color = if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun VehicleSelectionCard(
    name: String,
    capacityText: String,
    badgeText: String,
    iconRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val borderWidth = if (selected) 2.dp else 1.dp
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (selected) 3.dp else 1.dp,
        shadowElevation = if (selected) 3.dp else 1.dp,
        border = BorderStroke(borderWidth, borderColor),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            if (selected) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = stringResource(R.string.selected_badge),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            fontSize = 10.sp
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(64.dp)
                ) {
                    val isDark = isSystemInDarkTheme()
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(6.dp)) {
                        Image(
                            painter = painterResource(id = iconRes),
                            contentDescription = name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                            colorFilter = ColorFilter.tint(if (isDark) Color.White else NavyPrimary)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 9.sp
                            )
                        }
                    }
                    Text(
                        text = capacityText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.awaiting_office_quotation),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

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
                            .background(OrangeAccent),
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
                        .background(OrangeAccent)
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(OrangeAccent)
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
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Orange ring indicator
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .border(3.dp, OrangeAccent, CircleShape)
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
                    modifier = Modifier.size(46.dp)
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
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.address_details),
                        style = MaterialTheme.typography.titleMedium,
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
                                modifier = Modifier.size(36.dp)
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
                                    .border(3.dp, OrangeAccent, CircleShape)
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
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(searchFocusRequester)
                                        .testTag("LocationSearchInput")
                                )
                            }

                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(32.dp)
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

// ─── Pick-up Time Bottom Sheet (Image 4) ──────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickupTimeBottomSheet(
    isPickupNow: Boolean,
    scheduledRaw: String,
    now: Instant,
    onDismiss: () -> Unit,
    onSelectNow: () -> Unit,
    onSchedule: (String) -> Unit
) {
    val earliest = MobileBookingRules.earliest(now)
    val todayDate = now.atZone(MobileBookingRules.zone).toLocalDate()
    val tomorrowDate = todayDate.plusDays(1)
    val initialDateTime = runCatching { LocalDateTime.parse(scheduledRaw.trim(), MobileBookingRules.format) }
        .getOrNull()
        ?.takeIf { !it.toLocalDate().isBefore(tomorrowDate) && !it.isBefore(earliest) }
        ?: tomorrowDate.atTime(9, 0).let { defaultTomorrow ->
            if (defaultTomorrow.isBefore(earliest)) earliest.plusMinutes(15) else defaultTomorrow
        }

    // Scheduled dates start from Tomorrow to future (30 days out)
    val days = remember(tomorrowDate) {
        CustomerBookingRules.scheduledDays(now)
    }
    var selectedModeNow by remember { mutableStateOf(isPickupNow) }
    var selectedDate by remember {
        mutableStateOf(
            initialDateTime.toLocalDate().takeIf { !it.isBefore(tomorrowDate) } ?: tomorrowDate
        )
    }
    val isSameDayAsEarliest = selectedDate == earliest.toLocalDate()
    val availableHours = remember(isSameDayAsEarliest, earliest.hour) {
        (0..23).filter { !isSameDayAsEarliest || it >= earliest.hour }
    }
    var selectedHour by remember {
        mutableIntStateOf(initialDateTime.hour.coerceIn(availableHours.first(), availableHours.last()))
    }
    LaunchedEffect(availableHours) {
        if (selectedHour !in availableHours) {
            selectedHour = availableHours.first()
        }
    }
    val availableMinutes = remember(isSameDayAsEarliest, selectedHour, earliest.hour, earliest.minute) {
        (0..59).filter { !isSameDayAsEarliest || selectedHour > earliest.hour || it >= earliest.minute }
    }
    var selectedMinute by remember {
        mutableIntStateOf(
            initialDateTime.minute.takeIf { it in availableMinutes } ?: availableMinutes.first()
        )
    }
    LaunchedEffect(availableMinutes) {
        if (selectedMinute !in availableMinutes) {
            selectedMinute = availableMinutes.first()
        }
    }

    val candidateDateTime = selectedDate.atTime(selectedHour, selectedMinute)
    val candidateFormatted = candidateDateTime.format(MobileBookingRules.format)
    val displayScheduledLabel = CustomerBookingRules.formatSchedulePill(false, candidateFormatted, now)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.pickup_time_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // "Now" Option Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        selectedModeNow = true
                        onSelectNow()
                    }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_timer),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = stringResource(R.string.schedule_now),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selectedModeNow) FontWeight.Bold else FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (selectedModeNow) {
                    Surface(
                        shape = CircleShape,
                        color = OrangeAccent,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = "Selected",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            // Scheduled Date/Time Option Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedModeNow = false }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = displayScheduledLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (!selectedModeNow) FontWeight.Bold else FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (!selectedModeNow) {
                    Surface(
                        shape = CircleShape,
                        color = OrangeAccent,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = "Selected",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // 3-Column Wheel Picker (Day | Hour | Minute) matching Image 4
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(156.dp),
                contentAlignment = Alignment.Center
            ) {
                // Center Selection Band
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(8.dp)
                        )
                )

                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Column 1: Day (Tomorrow -> future dates)
                    val dayFormatter = remember { DateTimeFormatter.ofPattern("EEE MMM d", Locale.ENGLISH) }
                    WheelColumn(
                        items = days,
                        selectedItem = selectedDate,
                        label = { d ->
                            when (d) {
                                tomorrowDate -> "Tomorrow"
                                else -> d.format(dayFormatter)
                            }
                        },
                        onSelect = {
                            selectedModeNow = false
                            selectedDate = it
                        },
                        modifier = Modifier.weight(1.3f)
                    )

                    // Column 2: Hour (12-hour AM/PM display)
                    WheelColumn(
                        items = availableHours,
                        selectedItem = selectedHour,
                        label = { h ->
                            val amPm = if (h < 12) "AM" else "PM"
                            val hour12 = when {
                                h == 0 -> 12
                                h > 12 -> h - 12
                                else -> h
                            }
                            "$hour12 $amPm"
                        },
                        onSelect = {
                            selectedModeNow = false
                            selectedHour = it
                        },
                        modifier = Modifier.weight(1f)
                    )

                    // Column 3: Minute
                    WheelColumn(
                        items = availableMinutes,
                        selectedItem = selectedMinute,
                        label = { m -> m.toString().padStart(2, '0') },
                        onSelect = {
                            selectedModeNow = false
                            selectedMinute = it
                        },
                        modifier = Modifier.weight(0.8f)
                    )
                }
            }

            Button(
                onClick = {
                    if (selectedModeNow) onSelectNow() else onSchedule(candidateFormatted)
                },
                enabled = selectedModeNow || MobileBookingRules.departure(candidateFormatted, now) != null,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OrangeAccent,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("ConfirmScheduleButton")
            ) {
                Text(
                    text = stringResource(R.string.schedule_button),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun <T> WheelColumn(
    items: List<T>,
    selectedItem: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedIndex = items.indexOf(selectedItem).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val coroutineScope = rememberCoroutineScope()
    val currentItems by rememberUpdatedState(items)
    val currentOnSelect by rememberUpdatedState(onSelect)
    var userScrolled by remember { mutableStateOf(false) }

    val centeredIndex by remember(listState) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) {
                listState.firstVisibleItemIndex
            } else {
                val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                visibleItems.minByOrNull { itemInfo ->
                    kotlin.math.abs((itemInfo.offset + itemInfo.size / 2) - viewportCenter)
                }?.index ?: listState.firstVisibleItemIndex
            }
        }
    }

    // Track user-initiated scroll and auto-select centered item while scrolling & when finger is released
    LaunchedEffect(listState) {
        snapshotFlow { Triple(listState.isScrollInProgress, centeredIndex, currentItems.size) }
            .distinctUntilChanged()
            .collect { (scrolling, centerIdx, _) ->
                if (scrolling) {
                    userScrolled = true
                }
                if (userScrolled) {
                    currentItems.getOrNull(centerIdx)?.let { centeredItem ->
                        currentOnSelect(centeredItem)
                    }
                    if (!scrolling) {
                        userScrolled = false
                    }
                }
            }
    }

    // Sync list position when selectedItem changes externally (e.g., available hours/minutes list updates)
    LaunchedEffect(selectedIndex, items.size) {
        if (!listState.isScrollInProgress && !userScrolled && centeredIndex != selectedIndex) {
            listState.scrollToItem(selectedIndex)
        }
    }

    LazyColumn(
        state = listState,
        flingBehavior = flingBehavior,
        contentPadding = PaddingValues(vertical = 54.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxHeight()
    ) {
        itemsIndexed(items) { index, item ->
            val isCentered = index == centeredIndex
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable {
                        userScrolled = false
                        onSelect(item)
                        coroutineScope.launch {
                            listState.animateScrollToItem(index)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label(item),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isCentered) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCentered) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ─── Add More Details Screen (Image 1, Screen 2) ──────────────────────────────
@Composable
fun AddMoreDetailsScreen(
    pickup: String,
    dropoff: String,
    routePreview: RoutePreview?,
    vehicles: List<JsonObject>,
    selectedVehicle: JsonObject?,
    contact: String,
    cargo: String,
    weight: String,
    handling: String,
    notes: String,
    busy: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onEditRoute: () -> Unit,
    onContactChange: (String) -> Unit,
    onCargoChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
    onSelectVehicle: (String) -> Unit,
    onHandlingChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onConfirm: () -> Unit
) {
    val parsedWeight = MobileValidation.weight(weight)
    val normalizedContact = MobileValidation.phone(contact)
    val vehicleCap = selectedVehicle?.value("default_capacity_kg").orEmpty()
    val weightFits = selectedVehicle != null && CustomerBookingRules.weightFitsCapacity(weight, vehicleCap)
    val canConfirm = !busy &&
        pickup.trim().length in 5..500 &&
        dropoff.trim().length in 5..500 &&
        !pickup.trim().equals(dropoff.trim(), ignoreCase = true) &&
        cargo.trim().length in 3..1000 &&
        parsedWeight != null &&
        weightFits &&
        normalizedContact != null &&
        notes.length <= 2000

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, enabled = !busy) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = stringResource(R.string.add_more_details),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                LinearProgressIndicator(
                    progress = { 0.66f },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.total_fee_label),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.awaiting_office_quotation),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Button(
                        onClick = onConfirm,
                        enabled = canConfirm,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("ConfirmDetailsButton")
                    ) {
                        Text(
                            text = stringResource(R.string.confirm_action),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            errorMessage?.let {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = it,
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Route Summary Card
            RouteSummaryCard(
                pickup = pickup,
                dropoff = dropoff,
                routePreview = routePreview,
                onEdit = onEditRoute
            )

            // Cargo Details Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                shadowElevation = 1.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.cargo_details),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // ORDER CONTACT NUMBER
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.order_contact_number),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = contact,
                            onValueChange = onContactChange,
                            placeholder = { Text("0912 345 6789") },
                            singleLine = true,
                            isError = contact.isNotBlank() && normalizedContact == null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("OrderContactInput")
                        )
                        if (contact.isNotBlank() && normalizedContact == null) {
                            Text(
                                text = "Enter a valid Philippine mobile number (e.g. 0912 345 6789).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // CARGO DESCRIPTION
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.cargo_description_label),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = cargo,
                            onValueChange = onCargoChange,
                            placeholder = { Text(stringResource(R.string.cargo_description_placeholder)) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("CargoDescriptionInput")
                        )
                    }

                    // EST. WEIGHT (kg)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.est_weight_label),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = weight,
                            onValueChange = onWeightChange,
                            placeholder = { Text("0") },
                            suffix = { Text("kg", fontWeight = FontWeight.SemiBold) },
                            singleLine = true,
                            isError = weight.isNotBlank() && (parsedWeight == null || !weightFits),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("EstWeightInput")
                        )
                        if (selectedVehicle != null) {
                            Text(
                                text = "Selected vehicle: ${selectedVehicle.value("name")} (${CustomerBookingRules.formatCapacity(vehicleCap)})",
                                style = MaterialTheme.typography.bodySmall,
                                color = if ( parsedWeight != null && !weightFits) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (parsedWeight != null && !weightFits) {
                            val larger = vehicles.filter {
                                CustomerBookingRules.weightFitsCapacity(weight, it.value("default_capacity_kg"))
                            }
                            if (larger.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    larger.forEach { v ->
                                        AssistChip(
                                            onClick = { onSelectVehicle(v.value("id")) },
                                            label = { Text("Switch to ${v.value("name")}") }
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = "Weight exceeds available vehicle capacities. Contact the office for heavy cargo.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    // SPECIAL HANDLING Chips
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.special_handling_label),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "None",
                                "Fragile",
                                "Keep Dry",
                                "Temperature Sensitive",
                                "Perishable",
                                "Cold Chain (Refrigerated)"
                            ).forEach { option ->
                                FilterChip(
                                    selected = handling == option,
                                    onClick = { onHandlingChange(option) },
                                    label = { Text(option) }
                                )
                            }
                        }
                    }

                    // NOTES TO DRIVER
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.notes_to_driver_label),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = notes,
                            onValueChange = onNotesChange,
                            placeholder = { Text(stringResource(R.string.notes_to_driver_placeholder)) },
                            minLines = 3,
                            maxLines = 5,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("NotesToDriverInput")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteSummaryCard(
    pickup: String,
    dropoff: String,
    routePreview: RoutePreview?,
    onEdit: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.route_section),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.edit_action),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.clickable(onClick = onEdit)
                )
            }

            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 5.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Column {
                    Text(
                        text = stringResource(R.string.pickup),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                    Text(
                        text = pickup,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 5.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary)
                )
                Column {
                    Text(
                        text = stringResource(R.string.destination),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                    Text(
                        text = dropoff,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (routePreview != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⇅ ${routePreview.distanceKm} km",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "⏱ ${CustomerBookingRules.formatDuration(routePreview.minutes)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ─── Booking Summary Screen (Image 1, Screen 3) ───────────────────────────────
@Composable
fun BookingSummaryScreen(
    pickup: String,
    dropoff: String,
    routePreview: RoutePreview?,
    vehicleName: String,
    vehicleCapacity: String,
    weight: String,
    cargo: String,
    handling: String,
    contact: String,
    notes: String,
    scheduleSummary: String,
    busy: Boolean,
    offline: Boolean,
    canSubmit: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onEditRoute: () -> Unit,
    onSubmitBooking: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, enabled = !busy) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = stringResource(R.string.booking_summary_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                LinearProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onSubmitBooking,
                        enabled = canSubmit,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("ConfirmBookingButton")
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.confirm_booking_button),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.booking_terms_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            errorMessage?.let {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = it,
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (offline) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Reconnect before submitting. Your current details are kept here.",
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Route Card
            RouteSummaryCard(
                pickup = pickup,
                dropoff = dropoff,
                routePreview = routePreview,
                onEdit = onEditRoute
            )

            // Shipment Details Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                shadowElevation = 1.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.shipment_details_section),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.vehicle_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                            Text(
                                text = vehicleName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = CustomerBookingRules.formatCapacity(vehicleCapacity),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.cargo_weight_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                            Text(
                                text = "$weight kg",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.cargo_description_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                            Text(
                                text = cargo,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.special_handling_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Text(
                                    text = handling,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Column {
                        Text(
                            text = stringResource(R.string.order_contact_number),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                        Text(
                            text = contact,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (notes.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "NOTES",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                                Text(
                                    text = notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = scheduleSummary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Quotation Status Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                shadowElevation = 1.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.price_breakdown_section),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.total_fee_label),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.awaiting_office_quotation),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Text(
                        text = stringResource(R.string.estimated_upon_review),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Office Review Notice Box
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.office_review_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }
    }
}

// ─── Booking Submitted! Confirmation Screen (Image 1, Screen 4) ───────────────
@Composable
fun BookingSubmittedScreen(
    trackingNumber: String,
    pickup: String,
    dropoff: String,
    vehicleName: String,
    scheduleSummary: String,
    onTrackShipment: () -> Unit,
    onBookAnother: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Green Checkmark Box
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = StatusGreenBg,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = StatusGreen,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            Text(
                text = stringResource(R.string.booking_submitted_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = stringResource(R.string.booking_submitted_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // Booking Reference Card with top green accent bar
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 2.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .background(StatusGreen)
                    )
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.booking_reference_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                        Text(
                            text = trackingNumber,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )

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
                                text = pickup,
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
                                text = dropoff,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = stringResource(R.string.vehicle_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 9.sp
                                )
                                Text(
                                    text = vehicleName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Column {
                                Text(
                                    text = stringResource(R.string.schedule_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 9.sp
                                )
                                Text(
                                    text = scheduleSummary,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = stringResource(R.string.total_fee_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 9.sp
                                )
                                Text(
                                    text = "Pending quote",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }

            // 3-Step Status Timeline Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    TimelineRow(
                        dotColor = StatusGreen,
                        title = stringResource(R.string.timeline_booking_submitted),
                        subtitle = stringResource(R.string.timeline_just_now),
                        active = true
                    )
                    TimelineRow(
                        dotColor = OrangeAccent,
                        title = stringResource(R.string.timeline_pending_admin_review),
                        subtitle = stringResource(R.string.timeline_within_30_mins),
                        active = true
                    )
                    TimelineRow(
                        dotColor = MaterialTheme.colorScheme.outline,
                        title = stringResource(R.string.timeline_driver_assigned),
                        subtitle = stringResource(R.string.timeline_youll_be_notified),
                        active = false
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onTrackShipment,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("TrackShipmentButton")
            ) {
                Text(
                    text = stringResource(R.string.track_shipment_button),
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                onClick = onBookAnother,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("BookAnotherButton")
            ) {
                Text(
                    text = stringResource(R.string.book_another_delivery_button),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TimelineRow(
    dotColor: Color,
    title: String,
    subtitle: String,
    active: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─── Previews ─────────────────────────────────────────────────────────────────
private val previewVehicles = listOf(
    buildJsonObject {
        put("id", "v-1")
        put("name", "Dropside Truck")
        put("default_capacity_kg", "2000")
    },
    buildJsonObject {
        put("id", "v-2")
        put("name", "Closed Van")
        put("default_capacity_kg", "3000")
    },
    buildJsonObject {
        put("id", "v-3")
        put("name", "Wing Van")
        put("default_capacity_kg", "8000")
    }
)

@Preview(showBackground = true, name = "Customer Home Light")
@Composable
private fun CustomerBookingHomeLightPreview() {
    TanawTheme(darkTheme = false) {
        CustomerBookingHomeContent(
            customerName = "Maria Santos",
            customerCity = "Cabanatuan City",
            unreadCount = 2,
            pickup = "Cabanatuan City Public Market",
            dropoff = "Manila North Harbor, Manila",
            scheduleLabel = "Pick-up now",
            vehicles = previewVehicles,
            selectedVehicleId = "v-2",
            routePreview = RoutePreview("{}", 118.4, 145),
            busy = false,
            errorMessage = null,
            statusMessage = null,
            onRefresh = {},
            onOpenAlerts = {},
            onPickupClick = {},
            onDestinationClick = {},
            onSwapLocations = {},
            onScheduleClick = {},
            onSelectVehicle = {},
            onContinue = {}
        )
    }
}

@Preview(showBackground = true, name = "Customer Home Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CustomerBookingHomeDarkPreview() {
    TanawTheme(darkTheme = true) {
        CustomerBookingHomeContent(
            customerName = "Maria Santos",
            customerCity = "Cabanatuan City",
            unreadCount = 2,
            pickup = "Cabanatuan City Public Market",
            dropoff = "Manila North Harbor, Manila",
            scheduleLabel = "Pick-up now",
            vehicles = previewVehicles,
            selectedVehicleId = "v-2",
            routePreview = RoutePreview("{}", 118.4, 145),
            busy = false,
            errorMessage = null,
            statusMessage = null,
            onRefresh = {},
            onOpenAlerts = {},
            onPickupClick = {},
            onDestinationClick = {},
            onSwapLocations = {},
            onScheduleClick = {},
            onSelectVehicle = {},
            onContinue = {}
        )
    }
}

@Preview(showBackground = true, name = "Add More Details Preview")
@Composable
private fun AddMoreDetailsPreview() {
    TanawTheme(darkTheme = false) {
        AddMoreDetailsScreen(
            pickup = "Cabanatuan City Public Market",
            dropoff = "Manila North Harbor, Manila",
            routePreview = RoutePreview("{}", 118.4, 145),
            vehicles = previewVehicles,
            selectedVehicle = previewVehicles[1],
            contact = "09171234567",
            cargo = "50 sacks of milled rice",
            weight = "2500",
            handling = "Keep Dry",
            notes = "Gate 3, call upon arrival",
            busy = false,
            errorMessage = null,
            onBack = {},
            onEditRoute = {},
            onContactChange = {},
            onCargoChange = {},
            onWeightChange = {},
            onSelectVehicle = {},
            onHandlingChange = {},
            onNotesChange = {},
            onConfirm = {}
        )
    }
}

@Preview(showBackground = true, name = "Booking Summary Preview")
@Composable
private fun BookingSummaryPreview() {
    TanawTheme(darkTheme = false) {
        BookingSummaryScreen(
            pickup = "Cabanatuan City Public Market",
            dropoff = "Manila North Harbor, Manila",
            routePreview = RoutePreview("{}", 118.4, 145),
            vehicleName = "Closed Van",
            vehicleCapacity = "3000",
            weight = "2500",
            cargo = "50 sacks of milled rice",
            handling = "Keep Dry",
            contact = "+639171234567",
            notes = "Gate 3, call upon arrival",
            scheduleSummary = "Scheduled: Today, 2:30 PM PHT",
            busy = false,
            offline = false,
            canSubmit = true,
            errorMessage = null,
            onBack = {},
            onEditRoute = {},
            onSubmitBooking = {}
        )
    }
}

@Preview(showBackground = true, name = "Booking Submitted Preview")
@Composable
private fun BookingSubmittedPreview() {
    TanawTheme(darkTheme = false) {
        BookingSubmittedScreen(
            trackingNumber = "TNW-2026-0847",
            pickup = "Cabanatuan City Public Market",
            dropoff = "Manila North Harbor, Manila",
            vehicleName = "Closed Van",
            scheduleSummary = "Today, 2:30 PM",
            onTrackShipment = {},
            onBookAnother = {}
        )
    }
}
