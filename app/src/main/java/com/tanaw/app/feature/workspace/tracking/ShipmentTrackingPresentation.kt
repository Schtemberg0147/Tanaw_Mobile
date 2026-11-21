package com.tanaw.app.feature.workspace.tracking

import com.tanaw.app.feature.workspace.customer.rules.CustomerCancellationRules
import com.tanaw.app.feature.workspace.rules.MobilePresentation
import com.tanaw.app.feature.workspace.viewmodel.obj
import com.tanaw.app.feature.workspace.viewmodel.rows
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
    val shortLabel: String,
    val timestamp: String,
    val formattedTime: String,
    val compactTime: String,
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
    val breakdownRows: List<TrackingPricingRow>,
    val totalAmountText: String,
    val awaitingQuotation: Boolean
)

data class TrackingQuoteState(
    val status: String,
    val quoteExpiresAtText: String,
    val countdownText: String,
    val isExpired: Boolean,
    val canAccept: Boolean,
    val isAccepted: Boolean,
    val badgeText: String
)

object ShipmentTrackingPresentation {
    private data class MilestoneDefinition(
        val key: String,
        val title: String,
        val shortLabel: String
    )

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
        MilestoneDefinition("assigned_at", "Driver Assigned", "Assigned"),
        MilestoneDefinition("route_started_at", "En Route to Pickup", "En Route"),
        MilestoneDefinition("pickup_arrived_at", "Arrived at Pickup", "At Pickup"),
        MilestoneDefinition("customer_showed_at", "Cargo Loading", "Loading"),
        MilestoneDefinition("departed_at", "In Transit", "In Transit"),
        MilestoneDefinition("arrived_at", "Delivered", "Delivered")
    )

    fun shortTimestamp(raw: String): String =
        MobilePresentation.parseInstant(raw)?.let { shortDateTimeFormatter.format(it) }.orEmpty()

    fun compactClockTimestamp(raw: String): String =
        MobilePresentation.parseInstant(raw)?.let { clockOnlyFormatter.format(it) }.orEmpty()

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
        val arrivedAt = delivery?.value("arrived_at")?.takeIf(String::isNotBlank)
            ?: shipment.value("actual_delivery_at")

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

        val recordedTimestamps = milestoneDefinitions.map { def ->
            if (def.key == "arrived_at") {
                delivery?.value(def.key)?.takeIf(String::isNotBlank)
                    ?: shipment.value("actual_delivery_at")
            } else {
                delivery?.value(def.key).orEmpty()
            }
        }
        val lastRecordedIndex = recordedTimestamps.indexOfLast { it.isNotBlank() }
        val isDelivered = !isCancelled && (
            lastRecordedIndex == milestoneDefinitions.lastIndex ||
                shipmentStatus in setOf("completed", "delivered") ||
                deliveryStatus in setOf("delivered", "completed")
            )

        val steps = milestoneDefinitions.mapIndexed { index, def ->
            val ts = recordedTimestamps[index]
            val completed = when {
                isCancelled -> ts.isNotBlank()
                isDelivered -> true
                else -> index in 0 until lastRecordedIndex
            }
            val active = !isCancelled && !isDelivered && index == lastRecordedIndex
            ShipmentMilestoneStep(
                key = def.key,
                title = def.title,
                shortLabel = def.shortLabel,
                timestamp = ts,
                formattedTime = shortTimestamp(ts),
                compactTime = compactClockTimestamp(ts),
                isCompleted = completed,
                isActive = active
            )
        }

        val currentStatusTitle = when {
            isCancelled -> "Shipment Cancelled"
            isDelivered -> milestoneDefinitions.last().title
            lastRecordedIndex >= 0 -> milestoneDefinitions[lastRecordedIndex].title
            shipmentStatus == "quoted" -> "Quotation Ready"
            shipmentStatus == "booked" || shipmentStatus == "confirmed" -> "Booking Confirmed"
            shipmentStatus == "pending" -> "Awaiting Office Quotation"
            else -> MobilePresentation.status(shipmentStatus.ifBlank { "pending" })
        }

        val currentStatusTimestamp = when {
            isCancelled -> shortTimestamp(
                shipment.value("cancelled_at")
                    .ifBlank { shipment.value("updated_at") }
                    .ifBlank { shipment.value("created_at") }
            )
            isDelivered -> shortTimestamp(
                recordedTimestamps.last()
                    .ifBlank { shipment.value("actual_delivery_at") }
                    .ifBlank { shipment.value("updated_at") }
            )
            lastRecordedIndex >= 0 -> shortTimestamp(recordedTimestamps[lastRecordedIndex])
            else -> shortTimestamp(shipment.value("created_at"))
        }

        val nextStep = if (!isCancelled && !isDelivered && lastRecordedIndex < milestoneDefinitions.lastIndex) {
            milestoneDefinitions.getOrNull(lastRecordedIndex + 1)?.title
        } else {
            null
        }

        val gpsStatus = when {
            offline -> "Offline · showing saved status"
            freshness == null || freshness.status == PositionFreshnessStatus.UNAVAILABLE -> "Awaiting driver GPS signal"
            freshness.status == PositionFreshnessStatus.FRESH -> "Live GPS active"
            freshness.status == PositionFreshnessStatus.RECENT -> "GPS ${freshness.relativeText.lowercase()}"
            else -> "Awaiting fresh GPS signal"
        }

        val subtitle = when {
            isCancelled -> shipment.value("cancelled_reason").ifBlank { "This shipment has been cancelled" }
            isDelivered -> "All delivery milestones completed"
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
        val detailAssignment = detail?.obj("assignment")?.takeIf { it.isNotEmpty() }
        val detailDelivery = detail?.obj("delivery")?.takeIf { it.isNotEmpty() }
        val routingPlan = routing?.obj("plan")?.takeIf { it.isNotEmpty() }
        val eventSources = buildList {
            if (detail != null) {
                (detail.rows("events") + detail.rows("history")).asReversed().forEach { row ->
                    val meta = row.obj("metadata").takeIf { it.isNotEmpty() }
                        ?: row.obj("meta").takeIf { it.isNotEmpty() }
                    if (meta != null) add(meta)
                    add(row)
                }
            }
        }

        val sources = listOfNotNull(
            detail,
            detailAssignment,
            detailDelivery,
            delivery,
            shipment,
            routing,
            routingPlan
        ) + eventSources

        val nestedDrivers = buildList {
            for (src in listOfNotNull(detail, detailAssignment, detailDelivery, delivery, shipment, routing, routingPlan)) {
                src.obj("driver").takeIf { it.isNotEmpty() }?.let(::add)
                src.obj("assigned_driver").takeIf { it.isNotEmpty() }?.let(::add)
                src.obj("assignedDriver").takeIf { it.isNotEmpty() }?.let(::add)
            }
            for (evt in eventSources) {
                evt.obj("driver").takeIf { it.isNotEmpty() }?.let(::add)
            }
        }

        val nestedTrucks = buildList {
            for (src in listOfNotNull(detail, detailAssignment, detailDelivery, delivery, shipment, routing, routingPlan)) {
                src.obj("truck").takeIf { it.isNotEmpty() }?.let(::add)
                src.obj("vehicle").takeIf { it.isNotEmpty() }?.let(::add)
                src.obj("assigned_truck").takeIf { it.isNotEmpty() }?.let(::add)
                src.obj("assignedTruck").takeIf { it.isNotEmpty() }?.let(::add)
                src.obj("assigned_vehicle").takeIf { it.isNotEmpty() }?.let(::add)
                src.obj("assignedVehicle").takeIf { it.isNotEmpty() }?.let(::add)
            }
            for (evt in eventSources) {
                evt.obj("truck").takeIf { it.isNotEmpty() }?.let(::add)
                evt.obj("vehicle").takeIf { it.isNotEmpty() }?.let(::add)
            }
        }

        fun firstValue(objs: List<JsonObject>, vararg keys: String): String {
            for (obj in objs) {
                for (key in keys) {
                    val v = obj.value(key).trim()
                    if (v.isNotBlank()) return v
                }
            }
            return ""
        }

        val rawDriverName = firstValue(
            sources,
            "driver_name",
            "driverName",
            "driver_full_name",
            "driverFullName",
            "assigned_driver_name",
            "assignedDriverName",
            "assigned_driver",
            "assignedDriver"
        ).ifBlank {
            firstValue(nestedDrivers, "full_name", "fullName", "driver_name", "driverName", "name")
        }

        val rawPhone = firstValue(
            sources,
            "driver_phone",
            "driverPhone",
            "driver_contact_number",
            "driverContactNumber",
            "driver_contact",
            "driverContact",
            "contact_number",
            "contactNumber",
            "phone_number",
            "phoneNumber"
        ).ifBlank {
            firstValue(nestedDrivers, "contact_number", "contactNumber", "phone_number", "phoneNumber", "phone", "mobile", "contact")
        }

        val rawPlate = firstValue(
            sources,
            "plate_number",
            "plateNumber",
            "truck_plate_number",
            "truckPlateNumber",
            "vehicle_plate",
            "vehiclePlate"
        ).ifBlank {
            firstValue(nestedTrucks, "plate_number", "plateNumber", "plate")
        }

        val requestedVehicleId = firstValue(
            listOfNotNull(delivery, shipment, detailAssignment),
            "requested_vehicle_type_id",
            "vehicle_type_id",
            "vehicleTypeId"
        )
        val matchedVehicleName = if (requestedVehicleId.isNotBlank() && (rawDriverName.isNotBlank() || rawPlate.isNotBlank())) {
            vehicles.firstOrNull { it.value("id").trim() == requestedVehicleId }?.value("name")?.trim().orEmpty()
        } else {
            ""
        }

        val rawTruckType = firstValue(
            sources,
            "truck_type",
            "truckType",
            "vehicle_type",
            "vehicleType",
            "vehicle_type_name",
            "vehicleTypeName",
            "truck_model",
            "truckModel",
            "vehicle_name",
            "vehicleName",
            "fleet_code",
            "fleetCode"
        ).ifBlank {
            firstValue(
                nestedTrucks,
                "vehicle_type",
                "vehicleType",
                "vehicle_type_name",
                "vehicleTypeName",
                "type",
                "model",
                "name",
                "fleet_code",
                "fleetCode"
            )
        }.ifBlank { matchedVehicleName }

        val shipmentStatus = shipment.value("status").trim().lowercase()
        val deliveryStatus = delivery?.value("status")?.trim()?.lowercase().orEmpty()
        val hasDriverId = firstValue(sources, "driver_id", "driverId").isNotBlank()
        val hasDriverAssigned = rawDriverName.isNotBlank() ||
            hasDriverId ||
            delivery != null ||
            shipmentStatus in setOf("assigned", "loading", "in_transit", "delayed", "review", "completed", "delivered") ||
            deliveryStatus in setOf("assigned", "loading", "in_transit", "delivered", "completed")

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
        val vehicleSubtitle = when {
            vehicleParts.isNotEmpty() -> vehicleParts.joinToString(" • ")
            hasDriverAssigned -> "Vehicle details unavailable"
            else -> "Assigning vehicle details"
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
        val linehaulRaw = shipment.value("linehaul_amount").trim()
        val tollRaw = shipment.value("toll_amount").trim()
        val mobilizationRaw = shipment.value("mobilization_fee").trim()
        val quotedRaw = shipment.value("quoted_amount").trim()
        val overrideRaw = shipment.value("override_amount").trim()
        val finalRaw = shipment.value("final_amount").trim()

        val linehaulNum = linehaulRaw.toBigDecimalOrNull()
        val tollNum = tollRaw.toBigDecimalOrNull()
        val mobilizationNum = mobilizationRaw.toBigDecimalOrNull()
        val quotedNum = quotedRaw.toBigDecimalOrNull()
        val overrideNum = overrideRaw.toBigDecimalOrNull()
        val finalNum = finalRaw.toBigDecimalOrNull()

        val effectiveTotalRaw = when {
            finalNum != null -> finalRaw
            overrideNum != null -> overrideRaw
            quotedNum != null -> quotedRaw
            else -> ""
        }
        val awaitingQuotation = effectiveTotalRaw.isBlank()
        val totalAmountText = if (awaitingQuotation) {
            "Awaiting office quotation"
        } else {
            MobilePresentation.money(effectiveTotalRaw)
        }

        val rows = when {
            awaitingQuotation -> listOf(
                TrackingPricingRow(
                    label = "Total Quoted Amount",
                    value = "Awaiting office quotation",
                    isEmphasized = false
                )
            )
            quotedNum != null && finalNum != null && quotedNum.compareTo(finalNum) != 0 -> listOf(
                TrackingPricingRow(
                    label = "Quoted Amount",
                    value = MobilePresentation.money(quotedRaw),
                    isEmphasized = false
                ),
                TrackingPricingRow(
                    label = "Total Quoted Amount",
                    value = MobilePresentation.money(finalRaw),
                    isEmphasized = true
                )
            )
            else -> listOf(
                TrackingPricingRow(
                    label = "Total Quoted Amount",
                    value = totalAmountText,
                    isEmphasized = true
                )
            )
        }

        val breakdownRows = buildList {
            if (linehaulNum != null) {
                add(
                    TrackingPricingRow(
                        label = "Linehaul Fare",
                        value = MobilePresentation.money(linehaulRaw),
                        isEmphasized = false
                    )
                )
            }
            if (tollNum != null && tollNum > java.math.BigDecimal.ZERO) {
                add(
                    TrackingPricingRow(
                        label = "Estimated Tolls",
                        value = MobilePresentation.money(tollRaw),
                        isEmphasized = false
                    )
                )
            }
            if (mobilizationNum != null && mobilizationNum > java.math.BigDecimal.ZERO) {
                add(
                    TrackingPricingRow(
                        label = "Mobilization Fee",
                        value = MobilePresentation.money(mobilizationRaw),
                        isEmphasized = false
                    )
                )
            }
            if (overrideNum != null && quotedNum != null && overrideNum.compareTo(quotedNum) != 0) {
                add(
                    TrackingPricingRow(
                        label = "Office Adjusted Total",
                        value = MobilePresentation.money(overrideRaw),
                        isEmphasized = false
                    )
                )
            }
        }

        return TrackingPricingSummary(
            rows = rows,
            breakdownRows = breakdownRows,
            totalAmountText = totalAmountText,
            awaitingQuotation = awaitingQuotation
        )
    }

    fun quoteApprovalState(
        shipment: JsonObject,
        deliveries: List<JsonObject> = emptyList(),
        now: Instant = Instant.now()
    ): TrackingQuoteState {
        val status = shipment.value("status").trim().lowercase()
        val pricing = pricingSummary(shipment)
        val expiresRaw = shipment.value("quote_expires_at").trim()
        val expiresInstant = MobilePresentation.parseInstant(expiresRaw)
        val quoteExpiresAtText = expiresInstant?.let { shortDateTimeFormatter.format(it) + " PHT" }.orEmpty()
        val isExpired = !pricing.awaitingQuotation &&
            status == "quoted" &&
            expiresInstant != null &&
            !now.isBefore(expiresInstant)

        val countdownText = when {
            expiresInstant == null -> ""
            isExpired -> "Expired"
            else -> formatQuoteCountdown(Duration.between(now, expiresInstant))
        }

        val isAccepted = (!pricing.awaitingQuotation && status in setOf(
            "booked",
            "confirmed",
            "assigned",
            "loading",
            "in_transit",
            "delayed",
            "review",
            "delivered",
            "completed"
        )) || deliveries.any { it.value("status").trim().lowercase() != "cancelled" }

        val canAccept = status == "quoted" && !pricing.awaitingQuotation && !isExpired && !isAccepted

        val badgeText = when {
            isAccepted && !pricing.awaitingQuotation -> "Booked"
            isExpired -> "Expired"
            canAccept -> "Quote ready"
            pricing.awaitingQuotation -> "Awaiting quotation"
            else -> MobilePresentation.status(status.ifBlank { "quoted" })
        }

        return TrackingQuoteState(
            status = status,
            quoteExpiresAtText = quoteExpiresAtText,
            countdownText = countdownText,
            isExpired = isExpired,
            canAccept = canAccept,
            isAccepted = isAccepted,
            badgeText = badgeText
        )
    }

    private fun formatQuoteCountdown(duration: Duration): String {
        val totalMinutes = duration.toMinutes().coerceAtLeast(1L)
        val days = totalMinutes / (24L * 60L)
        val hours = (totalMinutes % (24L * 60L)) / 60L
        val mins = totalMinutes % 60L
        return when {
            days > 0 -> "Valid for ${days}d ${hours}h"
            hours > 0 -> "Valid for ${hours}h ${mins}m"
            else -> "Valid for ${mins}m"
        }
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

