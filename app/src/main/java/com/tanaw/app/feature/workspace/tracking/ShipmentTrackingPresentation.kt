package com.tanaw.app.feature.workspace.tracking

import com.tanaw.app.feature.workspace.customer.rules.CustomerCancellationRules
import com.tanaw.app.feature.workspace.rules.MobilePresentation
import com.tanaw.app.feature.workspace.viewmodel.obj
import com.tanaw.app.feature.workspace.viewmodel.value
import kotlinx.serialization.json.JsonObject
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ShipmentMilestoneStep(
    val key: String,
    val title: String,
    val timestamp: String,
    val formattedTime: String,
    val isCompleted: Boolean,
    val isActive: Boolean
)

data class TrackingEtaPillState(
    val text: String,
    val isLive: Boolean,
    val isCompleted: Boolean = false,
    val isCancelled: Boolean = false
)

data class TrackingSheetSummary(
    val currentStatusTitle: String,
    val currentStatusTimestamp: String,
    val steps: List<ShipmentMilestoneStep>,
    val subtitle: String,
    val isCancelled: Boolean
)

data class TrackingDriverSummary(
    val driverName: String,
    val initials: String,
    val vehicleSubtitle: String,
    val phoneNumber: String?,
    val hasDriverAssigned: Boolean
)

data class TrackingCargoSummary(
    val cargoText: String,
    val weightText: String,
    val recipientContact: String,
    val deliveryNotes: String
)

data class TrackingPricingRow(
    val label: String,
    val value: String,
    val isEmphasized: Boolean
)

data class TrackingPricingSummary(
    val rows: List<TrackingPricingRow>,
    val awaitingQuotation: Boolean
)

object ShipmentTrackingPresentation {
    private val plusCodeRegex = Regex(
        """(?i)(?:^|(?<=[\s,]))[23456789CFGHJMPQRVWX]{4,8}\+[23456789CFGHJMPQRVWX]{2,4}(?=[\s,]|$)"""
    )
    private val repeatedCommasRegex = Regex("""\s*(?:,\s*){2,}""")
    private val repeatedSpacesRegex = Regex("""\s{2,}""")
    private val leadingContactLineRegex = Regex("""(?i)^contact:\s*(.+)$""")
    private val manilaZone: ZoneId = ZoneId.of("Asia/Manila")
    private val shortDateTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d • h:mm a", Locale.ENGLISH).withZone(manilaZone)
    private val pillDateTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.ENGLISH).withZone(manilaZone)
    private val clockOnlyFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH).withZone(manilaZone)

    private val milestoneDefinitions = listOf(
        "assigned_at" to "Driver Assigned",
        "route_started_at" to "En Route to Pickup",
        "pickup_arrived_at" to "Arrived at Pickup",
        "customer_showed_at" to "Cargo Loading",
        "departed_at" to "In Transit",
        "arrived_at" to "Delivered"
    )

    fun shortTimestamp(raw: String): String =
        MobilePresentation.parseInstant(raw)?.let { shortDateTimeFormatter.format(it) }.orEmpty()

    fun pillTimestamp(raw: String, now: Instant = Instant.now()): String {
        val instant = MobilePresentation.parseInstant(raw) ?: return ""
        val instantDate = instant.atZone(manilaZone).toLocalDate()
        val nowDate = now.atZone(manilaZone).toLocalDate()
        return if (instantDate == nowDate) {
            clockOnlyFormatter.format(instant)
        } else {
            pillDateTimeFormatter.format(instant)
        }
    }

    fun etaPillState(
        shipment: JsonObject,
        delivery: JsonObject?,
        routing: JsonObject?,
        eta: CustomerEtaPresentation?,
        now: Instant = Instant.now()
    ): TrackingEtaPillState {
        val shipmentStatus = shipment.value("status").trim().lowercase()
        val deliveryStatus = delivery?.value("status")?.trim()?.lowercase().orEmpty()
        val arrivedAt = delivery?.value("arrived_at").orEmpty()

        if (shipmentStatus in listOf("completed", "delivered") || deliveryStatus == "delivered" || arrivedAt.isNotBlank()) {
            val deliveredTime = pillTimestamp(arrivedAt.ifBlank { shipment.value("updated_at") }, now)
            val label = if (deliveredTime.isNotBlank()) "Delivered • $deliveredTime" else "Shipment Delivered"
            return TrackingEtaPillState(text = label, isLive = false, isCompleted = true)
        }

        if (shipmentStatus == "cancelled" || deliveryStatus == "cancelled") {
            return TrackingEtaPillState(text = "Shipment Cancelled", isLive = false, isCancelled = true)
        }

        if (eta?.isLive == true && routing != null) {
            val etaInstant = MobilePresentation.parseInstant(routing.value("eta_at"))
            if (etaInstant != null) {
                val mins = Duration.between(now, etaInstant).toMinutes().coerceAtLeast(1L)
                val unit = if (mins == 1L) "min" else "mins"
                val etaClock = clockOnlyFormatter.format(etaInstant)
                return TrackingEtaPillState(
                    text = "Arriving in $mins $unit • $etaClock",
                    isLive = true
                )
            }
        }

        val scheduledRaw = shipment.value("estimated_arrival")
        val scheduledFormatted = pillTimestamp(scheduledRaw, now)
        val scheduledText = if (scheduledFormatted.isNotBlank()) {
            "Scheduled Arrival • $scheduledFormatted"
        } else {
            "Scheduled Arrival • Pending schedule"
        }
        return TrackingEtaPillState(text = scheduledText, isLive = false)
    }

    fun sheetSummary(
        shipment: JsonObject,
        delivery: JsonObject?,
        freshness: PositionFreshness?,
        offline: Boolean
    ): TrackingSheetSummary {
        val shipmentStatus = shipment.value("status").trim().lowercase()
        val deliveryStatus = delivery?.value("status")?.trim()?.lowercase().orEmpty()
        val isCancelled = shipmentStatus == "cancelled" || deliveryStatus == "cancelled"

        val recordedTimestamps = milestoneDefinitions.map { (key, _) ->
            delivery?.value(key).orEmpty()
        }
        val lastRecordedIndex = recordedTimestamps.indexOfLast { it.isNotBlank() }

        val steps = milestoneDefinitions.mapIndexed { index, (key, title) ->
            val ts = recordedTimestamps[index]
            val isDelivered = lastRecordedIndex == milestoneDefinitions.lastIndex
            val completed = when {
                isCancelled -> ts.isNotBlank()
                isDelivered -> true
                else -> index in 0 until lastRecordedIndex
            }
            val active = !isCancelled && !isDelivered && index == lastRecordedIndex
            ShipmentMilestoneStep(
                key = key,
                title = title,
                timestamp = ts,
                formattedTime = shortTimestamp(ts),
                isCompleted = completed,
                isActive = active
            )
        }

        val currentStatusTitle = when {
            isCancelled -> "Shipment Cancelled"
            lastRecordedIndex >= 0 -> milestoneDefinitions[lastRecordedIndex].second
            shipmentStatus == "booked" -> "Booking Confirmed"
            shipmentStatus == "pending" -> "Awaiting Driver"
            else -> MobilePresentation.status(shipmentStatus.ifBlank { "pending" })
        }

        val currentStatusTimestamp = when {
            isCancelled -> shortTimestamp(shipment.value("updated_at").ifBlank { shipment.value("created_at") })
            lastRecordedIndex >= 0 -> shortTimestamp(recordedTimestamps[lastRecordedIndex])
            else -> shortTimestamp(shipment.value("created_at"))
        }

        val nextStep = if (!isCancelled && lastRecordedIndex < milestoneDefinitions.lastIndex) {
            milestoneDefinitions.getOrNull(lastRecordedIndex + 1)?.second
        } else {
            null
        }

        val gpsStatus = when {
            offline -> "Offline — showing saved status"
            freshness == null || freshness.status == PositionFreshnessStatus.UNAVAILABLE -> "Awaiting driver GPS signal"
            freshness.status == PositionFreshnessStatus.FRESH -> "Live GPS active"
            freshness.status == PositionFreshnessStatus.RECENT -> "GPS ${freshness.relativeText.lowercase()}"
            else -> "Awaiting fresh GPS signal"
        }

        val subtitle = when {
            isCancelled -> shipment.value("cancelled_reason").ifBlank { "This shipment has been cancelled" }
            lastRecordedIndex == milestoneDefinitions.lastIndex -> "All delivery milestones completed"
            nextStep != null && (freshness == null || freshness.status == PositionFreshnessStatus.UNAVAILABLE || offline) ->
                "Next: $nextStep • $gpsStatus"
            nextStep != null -> "Next: $nextStep"
            else -> gpsStatus
        }

        return TrackingSheetSummary(
            currentStatusTitle = currentStatusTitle,
            currentStatusTimestamp = currentStatusTimestamp,
            steps = steps,
            subtitle = subtitle,
            isCancelled = isCancelled
        )
    }

    fun driverSummary(
        shipment: JsonObject,
        delivery: JsonObject?,
        detail: JsonObject? = null,
        routing: JsonObject? = null,
        vehicles: List<JsonObject> = emptyList()
    ): TrackingDriverSummary {
        val sources = listOfNotNull(
            detail,
            delivery,
            shipment,
            routing,
            routing?.obj("plan")
        )
        val nestedDrivers = listOfNotNull(
            detail?.obj("driver"),
            delivery?.obj("driver"),
            shipment.obj("driver"),
            routing?.obj("driver")
        )
        val nestedTrucks = listOfNotNull(
            detail?.obj("truck"),
            delivery?.obj("truck"),
            shipment.obj("truck"),
            routing?.obj("truck")
        )

        fun firstValue(objs: List<JsonObject>, vararg keys: String): String {
            for (obj in objs) {
                for (key in keys) {
                    val v = obj.value(key).trim()
                    if (v.isNotBlank()) return v
                }
            }
            return ""
        }

        val rawDriverName = firstValue(sources, "driver_name", "driver_full_name", "assigned_driver_name")
            .ifBlank { firstValue(nestedDrivers, "full_name", "name") }

        val rawPhone = firstValue(sources, "driver_phone", "driver_contact_number", "driver_contact")
            .ifBlank { firstValue(nestedDrivers, "contact_number", "phone") }

        val rawPlate = firstValue(sources, "plate_number", "truck_plate_number")
            .ifBlank { firstValue(nestedTrucks, "plate_number") }

        val requestedVehicleId = shipment.value("requested_vehicle_type_id").trim()
        val matchedVehicleName = if (requestedVehicleId.isNotBlank() && (rawDriverName.isNotBlank() || rawPlate.isNotBlank())) {
            vehicles.firstOrNull { it.value("id").trim() == requestedVehicleId }?.value("name")?.trim().orEmpty()
        } else {
            ""
        }

        val rawTruckType = firstValue(
            sources,
            "truck_type",
            "vehicle_type",
            "vehicle_type_name",
            "truck_model",
            "vehicle_name"
        ).ifBlank { firstValue(nestedTrucks, "vehicle_type", "model", "name") }
            .ifBlank { matchedVehicleName }

        val shipmentStatus = shipment.value("status").trim().lowercase()
        val hasDriverAssigned = rawDriverName.isNotBlank() ||
            delivery != null ||
            shipmentStatus in setOf("assigned", "loading", "in_transit", "delayed", "review", "completed", "delivered")

        val driverName = when {
            rawDriverName.isNotBlank() -> rawDriverName
            shipmentStatus == "cancelled" && delivery == null -> "Driver Unassigned"
            hasDriverAssigned -> "Assigned Driver"
            else -> "Awaiting Driver"
        }

        val initials = computeInitials(rawDriverName)

        val vehicleParts = listOfNotNull(
            rawTruckType.takeIf { it.isNotBlank() },
            rawPlate.takeIf { it.isNotBlank() }
        )
        val vehicleSubtitle = if (vehicleParts.isNotEmpty()) {
            vehicleParts.joinToString(" • ")
        } else {
            "Assigning vehicle details"
        }

        val sanitizedPhone = sanitizeDialablePhone(rawPhone)

        return TrackingDriverSummary(
            driverName = driverName,
            initials = initials,
            vehicleSubtitle = vehicleSubtitle,
            phoneNumber = sanitizedPhone,
            hasDriverAssigned = hasDriverAssigned
        )
    }

    fun formatAddressWithoutPlusCode(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val withoutCodes = plusCodeRegex.replace(trimmed, "")
        val collapsedCommas = repeatedCommasRegex.replace(withoutCodes, ", ")
        val collapsedSpaces = repeatedSpacesRegex.replace(collapsedCommas, " ")
        val cleaned = collapsedSpaces.trim { it.isWhitespace() || it == ',' }
        return cleaned.ifBlank { trimmed }
    }

    fun cargoSummary(shipment: JsonObject, delivery: JsonObject?): TrackingCargoSummary {
        val cargoText = shipment.value("cargo_description").trim().ifBlank { "Not specified" }
        val weightText = MobilePresentation.number(shipment.value("weight_kg"), "kg")

        var parsedContact = ""
        val noteLines = mutableListOf<String>()
        val rawNotes = shipment.value("driver_notes")
        if (rawNotes.isNotBlank()) {
            rawNotes.lines().forEach { line ->
                val trimmedLine = line.trim()
                if (trimmedLine.isEmpty()) return@forEach
                val match = leadingContactLineRegex.matchEntire(trimmedLine)
                if (match != null && parsedContact.isBlank()) {
                    parsedContact = match.groupValues[1].trim()
                } else {
                    noteLines.add(trimmedLine)
                }
            }
        }

        val explicitContact = listOf(
            shipment.value("recipient_contact"),
            shipment.value("contact_number"),
            shipment.value("recipient_phone"),
            delivery?.value("recipient_contact").orEmpty(),
            delivery?.value("contact_number").orEmpty(),
            parsedContact
        ).firstOrNull { it.isNotBlank() }?.trim().orEmpty()

        val recipientName = delivery?.value("recipient_name")?.trim().orEmpty()
        val recipientContact = when {
            recipientName.isNotBlank() && explicitContact.isNotBlank() -> "$recipientName · $explicitContact"
            explicitContact.isNotBlank() -> explicitContact
            recipientName.isNotBlank() -> recipientName
            else -> "Not provided"
        }

        return TrackingCargoSummary(
            cargoText = cargoText,
            weightText = weightText,
            recipientContact = recipientContact,
            deliveryNotes = noteLines.joinToString("\n")
        )
    }

    fun pricingSummary(shipment: JsonObject): TrackingPricingSummary {
        val quotedRaw = shipment.value("quoted_amount").trim()
        val finalRaw = shipment.value("final_amount").trim()
        val quotedNum = quotedRaw.toBigDecimalOrNull()
        val finalNum = finalRaw.toBigDecimalOrNull()

        return when {
            quotedNum == null && finalNum == null -> TrackingPricingSummary(
                rows = listOf(
                    TrackingPricingRow(
                        label = "Total Amount",
                        value = MobilePresentation.money(""),
                        isEmphasized = false
                    )
                ),
                awaitingQuotation = true
            )
            quotedNum != null && finalNum != null && quotedNum.compareTo(finalNum) != 0 -> TrackingPricingSummary(
                rows = listOf(
                    TrackingPricingRow(
                        label = "Quoted Amount",
                        value = MobilePresentation.money(quotedRaw),
                        isEmphasized = false
                    ),
                    TrackingPricingRow(
                        label = "Final Amount",
                        value = MobilePresentation.money(finalRaw),
                        isEmphasized = true
                    )
                ),
                awaitingQuotation = false
            )
            else -> {
                val effectiveRaw = if (finalNum != null) finalRaw else quotedRaw
                TrackingPricingSummary(
                    rows = listOf(
                        TrackingPricingRow(
                            label = "Total Amount",
                            value = MobilePresentation.money(effectiveRaw),
                            isEmphasized = true
                        )
                    ),
                    awaitingQuotation = false
                )
            }
        }
    }

    fun shouldShowProofOfDelivery(shipment: JsonObject, delivery: JsonObject?): Boolean {
        val shipmentStatus = shipment.value("status").trim().lowercase()
        val deliveryStatus = delivery?.value("status")?.trim()?.lowercase().orEmpty()
        val arrivedAt = delivery?.value("arrived_at").orEmpty().trim()
        return !(shipmentStatus == "cancelled" && deliveryStatus != "delivered") && (
            shipmentStatus in setOf("delivered", "completed", "review") ||
                deliveryStatus in setOf("delivered", "completed") ||
                arrivedAt.isNotBlank()
            )
    }

    fun shouldShowCancelAction(shipment: JsonObject, deliveries: List<JsonObject>): Boolean =
        CustomerCancellationRules.canCancelShipment(shipment, deliveries)

    private fun computeInitials(name: String): String {
        val parts = name.trim().split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (parts.isEmpty()) return ""
        return if (parts.size == 1) {
            parts.first().take(2).uppercase(Locale.ENGLISH)
        } else {
            "${parts.first().first()}${parts.last().first()}".uppercase(Locale.ENGLISH)
        }
    }

    private fun sanitizeDialablePhone(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val cleaned = buildString {
            trimmed.forEachIndexed { idx, ch ->
                if (ch.isDigit() || (ch == '+' && idx == 0)) {
                    append(ch)
                }
            }
        }
        return cleaned.takeIf { it.count(Char::isDigit) >= 7 }
    }
}
