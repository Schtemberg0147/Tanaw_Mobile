package com.tanaw.app.feature.workspace.customer.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.tanaw.app.core.validation.MobileValidation
import com.tanaw.app.feature.workspace.customer.components.PickupTimeBottomSheet
import com.tanaw.app.feature.workspace.customer.rules.CustomerAddressRules
import com.tanaw.app.feature.workspace.customer.rules.CustomerBookingRules
import com.tanaw.app.feature.workspace.customer.rules.MobileBookingRules
import com.tanaw.app.feature.workspace.customer.store.BookingDraft
import com.tanaw.app.feature.workspace.model.RoutePreview
import com.tanaw.app.feature.workspace.state.MobileState
import com.tanaw.app.feature.workspace.viewmodel.MobileWorkspaceViewModel
import com.tanaw.app.feature.workspace.viewmodel.obj
import com.tanaw.app.feature.workspace.viewmodel.rows
import com.tanaw.app.feature.workspace.viewmodel.value
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
    modifier: Modifier = Modifier
) {
    val profile = data.obj("profile")
    val customer = data.obj("customer")
    val vehicles = data.rows("vehicles")
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
                isPickupNow = isPickupNow,
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
