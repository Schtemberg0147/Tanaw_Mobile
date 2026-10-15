package com.tanaw.app.data.mapper

import com.tanaw.app.data.dto.DbComplianceDocumentDto
import com.tanaw.app.data.dto.DbDeliveryDto
import com.tanaw.app.data.dto.DbDriverDto
import com.tanaw.app.data.dto.DbDriverTripStatsDto
import com.tanaw.app.domain.model.CargoDetails
import com.tanaw.app.domain.model.ComplianceDocumentItem
import com.tanaw.app.domain.model.ComplianceStatus
import com.tanaw.app.domain.model.DriverProfile
import com.tanaw.app.domain.model.DriverProfileDetails
import com.tanaw.app.domain.model.DriverStatus
import com.tanaw.app.domain.model.DriverTripItem
import com.tanaw.app.domain.model.DriverTripStats
import com.tanaw.app.domain.model.RoutePoint
import com.tanaw.app.domain.model.TripAssignment
import com.tanaw.app.domain.model.TripCardStyle
import com.tanaw.app.domain.model.TripMeta
import com.tanaw.app.domain.model.TripRoute
import com.tanaw.app.domain.model.TripStatus
import com.tanaw.app.domain.model.generateInitials
import java.text.DecimalFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

fun DbDriverDto.toDomain(): DriverProfile {
    val vehicleName = trucks?.vehicleType?.name ?: "10-Wheeler Wing Van"
    val driverStatus = when (status?.lowercase()) {
        "available" -> DriverStatus.AVAILABLE
        "on_trip", "in_transit" -> DriverStatus.ON_TRIP
        "off_duty" -> DriverStatus.OFF_DUTY
        else -> DriverStatus.AVAILABLE
    }
    return DriverProfile(
        id = id,
        userId = userId ?: "",
        fullName = fullName?.takeIf { it.isNotBlank() } ?: "Driver",
        vehicleTypeName = vehicleName,
        status = driverStatus
    )
}

fun DbDriverDto.toProfileDetails(authEmail: String?): DriverProfileDetails {
    val name = fullName?.takeIf { it.isNotBlank() } ?: "Ramon Cruz"
    val initials = generateInitials(name)
    val vehicleName = trucks?.vehicleType?.name ?: "10-Wheeler Wing Van"
    val plate = trucks?.plateNumber?.takeIf { it.isNotBlank() } ?: "ABC-1234"
    val vehicleAndPlate = "$vehicleName · $plate"

    val hiredDate = dateHired?.takeIf { it.isNotBlank() }
        ?: createdAt?.takeIf { it.isNotBlank() }
    val sinceText = formatDriverSince(hiredDate)

    val contact = contactNumber?.takeIf { it.isNotBlank() } ?: "—"
    val email = authEmail?.takeIf { it.isNotBlank() } ?: "ramon.cruz@tanaw.ph"

    return DriverProfileDetails(
        driverId = id,
        fullName = name,
        initials = initials,
        vehicleTypeNameAndPlate = vehicleAndPlate,
        driverSinceText = sinceText,
        email = email,
        contactNumber = contact
    )
}

fun DbComplianceDocumentDto.toDomain(today: LocalDate = LocalDate.now(ZoneId.of("Asia/Manila"))): ComplianceDocumentItem {
    val title = mapDocTypeToTitle(docType)
    val parsedExpiry = parseLocalDate(expiryDate)
    val compStatus = ComplianceStatus.from(parsedExpiry, today)

    return ComplianceDocumentItem(
        id = id,
        title = title,
        docType = docType,
        documentNumber = documentNumber,
        expiryDate = parsedExpiry,
        status = compStatus
    )
}

fun DbDeliveryDto.toDriverTripItem(currentYear: Int = LocalDate.now(ZoneId.of("Asia/Manila")).year): DriverTripItem {
    val shipment = shipments
    val tracking = shipment?.trackingNumber?.ifBlank { "SHP-NE-8063" } ?: "SHP-NE-8063"

    val deliveryStatusStr = this.status
    val tripStatus = mapDeliveryStatusToTripStatus(deliveryStatusStr, shipment?.status)
    val style = TripCardStyle.from(tripStatus)

    val originCity = shipment?.pickupLocation?.city?.takeIf { it.isNotBlank() }
        ?: shipment?.pickupLocation?.name?.takeIf { it.isNotBlank() }
        ?: shipment?.pickupAddress?.split(",")?.firstOrNull()?.trim()
        ?: "Cabanatuan"

    val destinationCity = shipment?.dropoffLocation?.city?.takeIf { it.isNotBlank() }
        ?: shipment?.dropoffLocation?.name?.takeIf { it.isNotBlank() }
        ?: shipment?.dropoffAddress?.split(",")?.firstOrNull()?.trim()
        ?: "Gapan City"

    val tripTimestamp = arrivedAt ?: departedAt ?: assignedAt ?: createdAt ?: shipment?.scheduledDeparture
    val dateText = formatTripShortDate(tripTimestamp, currentYear)

    val durationText = when (tripStatus) {
        TripStatus.COMPLETED -> {
            val mins = calculateDurationMinutesBetween(departedAt ?: assignedAt, arrivedAt ?: shipment?.actualDeliveryAt)
            if (mins != null && mins > 0) formatDurationStrict(mins) else "2h 15min"
        }
        TripStatus.ONGOING -> {
            val estMins = shipment?.estimatedDurationMinutes
            if (estMins != null && estMins > 0) formatDurationStrict(estMins) else "In progress"
        }
        TripStatus.CANCELLED -> "—"
    }

    val dateAndDuration = "$dateText • $durationText"

    val allowanceVal = allowanceAmount ?: 500.0
    val formattedAllowance = formatPesoCurrency(allowanceVal)

    return DriverTripItem(
        id = id.ifBlank { shipmentId },
        shipmentId = shipmentId,
        trackingNumber = tracking,
        status = tripStatus,
        rawStatus = deliveryStatusStr,
        originCity = originCity,
        destinationCity = destinationCity,
        formattedDate = dateText,
        formattedDuration = dateAndDuration,
        allowanceAmount = allowanceVal,
        formattedAllowance = formattedAllowance,
        cardStyle = style
    )
}

fun DbDriverTripStatsDto.toDomain(): DriverTripStats {
    val formattedTotal = formatPesoCurrency(totalAllowance)
    val ratePct = onTimeRate?.let { (it * 100.0).toInt().coerceIn(0, 100) }

    return DriverTripStats(
        completedTripsCount = completedTripsCount,
        totalAllowance = totalAllowance,
        formattedTotalAllowance = formattedTotal,
        onTimeRatePercentage = ratePct
    )
}

fun DbDeliveryDto.toDomain(): TripAssignment? {
    val shipment = shipments ?: return null

    val categoryName = shipment.cargoCategory?.name ?: "General Cargo"
    val weightVal = shipment.weightKg ?: 0.0
    val formattedWeight = DecimalFormat("#,##0").format(weightVal) + " kg"
    val specialTag = shipment.specialHandling?.takeIf { it.isNotBlank() }
        ?: if (shipment.cargoCategory?.requiresSpecialHandling == true) "SPECIAL HANDLING" else null

    val cargo = CargoDetails(
        categoryName = categoryName,
        formattedWeight = formattedWeight,
        specialHandlingTag = specialTag
    )

    val pickupName = shipment.pickupLocation?.name?.takeIf { it.isNotBlank() }
        ?: shipment.pickupAddress?.split(",")?.firstOrNull()?.trim()
        ?: "Pick-up Location"
    val pickupAddress = shipment.pickupAddress ?: pickupName
    val formattedPickupTime = formatToManilaTime(shipment.scheduledDeparture)

    val pickupPoint = RoutePoint(
        locationName = pickupName,
        fullAddress = pickupAddress,
        formattedScheduledTime = formattedPickupTime
    )

    val dropoffName = shipment.dropoffLocation?.name?.takeIf { it.isNotBlank() }
        ?: shipment.dropoffAddress?.split(",")?.firstOrNull()?.trim()
        ?: "Destination Location"
    val dropoffAddress = shipment.dropoffAddress ?: dropoffName

    val destinationPoint = RoutePoint(
        locationName = dropoffName,
        fullAddress = dropoffAddress,
        formattedScheduledTime = null
    )

    val route = TripRoute(
        pickup = pickupPoint,
        destination = destinationPoint
    )

    val distKm = shipment.distanceKm ?: 0.0
    val formattedDist = DecimalFormat("#,##0.#").format(distKm) + " km"

    val durationMin = shipment.estimatedDurationMinutes
        ?: calculateDurationMinutes(shipment.scheduledDeparture, shipment.estimatedArrival)
        ?: 75

    val formattedDur = formatDuration(durationMin)
    val hasTollRoad = shipment.hasToll ?: true

    val meta = TripMeta(
        distanceKm = distKm,
        formattedDistance = formattedDist,
        formattedDuration = formattedDur,
        hasToll = hasTollRoad
    )

    val allowanceVal = allowanceAmount ?: 500.0
    val formattedAllowance = formatPesoCurrency(allowanceVal)

    val combinedNotes = listOfNotNull(
        notes?.takeIf { it.isNotBlank() },
        shipment.driverNotes?.takeIf { it.isNotBlank() }
    ).distinct().joinToString(" \n").takeIf { it.isNotBlank() }

    return TripAssignment(
        deliveryId = id,
        shipmentId = shipment.id,
        trackingNumber = shipment.trackingNumber.ifBlank { "SHP-NE-8063" },
        cargo = cargo,
        route = route,
        meta = meta,
        formattedAllowance = formattedAllowance,
        notes = combinedNotes
    )
}

fun mapDeliveryStatusToTripStatus(deliveryStatus: String, shipmentStatus: String?): TripStatus {
    val statusClean = deliveryStatus.lowercase().trim()
    val shipStatusClean = shipmentStatus?.lowercase()?.trim()

    return when {
        statusClean in listOf("completed", "delivered") || shipStatusClean == "delivered" -> TripStatus.COMPLETED
        statusClean in listOf("cancelled", "rejected", "failed") || shipStatusClean in listOf("cancelled", "rejected") -> TripStatus.CANCELLED
        else -> TripStatus.ONGOING
    }
}

fun mapDocTypeToTitle(docType: String): String {
    return when (docType.lowercase().trim()) {
        "professional_driver_license", "driver_license", "license" -> "Professional Driver's License"
        "medical_certificate", "medical" -> "Medical Certificate"
        "nbi_clearance", "nbi" -> "NBI Clearance"
        "ltfrb_franchise", "ltfrb" -> "LTFRB Franchise"
        "or_cr", "registration" -> "Vehicle Registration (OR/CR)"
        else -> docType.replace("_", " ").split(" ")
            .joinToString(" ") { word -> word.lowercase().replaceFirstChar { it.uppercase() } }
    }
}

fun formatDriverSince(isoOrDateString: String?): String {
    if (isoOrDateString.isNullOrBlank()) return "Driver since January 2022"
    return try {
        val date = parseLocalDate(isoOrDateString)
            ?: ZonedDateTime.parse(isoOrDateString).toLocalDate()
        val monthName = date.month.name.lowercase().replaceFirstChar { it.uppercase() }
        "Driver since $monthName ${date.year}"
    } catch (_: Exception) {
        "Driver since January 2022"
    }
}

fun formatTripShortDate(isoTimestamp: String?, currentYear: Int): String {
    if (isoTimestamp.isNullOrBlank()) return "Oct 24"
    return try {
        val zdt = try {
            ZonedDateTime.parse(isoTimestamp)
        } catch (_: Exception) {
            val instant = Instant.parse(isoTimestamp)
            ZonedDateTime.ofInstant(instant, ZoneId.of("Asia/Manila"))
        }
        val manilaZdt = zdt.withZoneSameInstant(ZoneId.of("Asia/Manila"))
        val pattern = if (manilaZdt.year == currentYear) "MMM dd" else "MMM dd, yyyy"
        manilaZdt.format(DateTimeFormatter.ofPattern(pattern, Locale.US))
    } catch (_: Exception) {
        "Oct 24"
    }
}

fun formatDurationStrict(minutes: Int): String {
    if (minutes <= 0) return "—"
    val hours = minutes / 60
    val mins = minutes % 60
    return when {
        hours > 0 && mins > 0 -> "${hours}h ${mins}min"
        hours > 0 -> "${hours}h"
        else -> "${mins}min"
    }
}

fun formatToManilaTime(isoTimestamp: String?): String {
    if (isoTimestamp.isNullOrBlank()) return "Oct 24, 2023 · 08:00 AM"
    return try {
        val zonedDateTime = try {
            ZonedDateTime.parse(isoTimestamp)
        } catch (_: Exception) {
            val instant = Instant.parse(isoTimestamp)
            ZonedDateTime.ofInstant(instant, ZoneId.of("Asia/Manila"))
        }
        val manilaZone = zonedDateTime.withZoneSameInstant(ZoneId.of("Asia/Manila"))
        val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy · hh:mm a", Locale.US)
        manilaZone.format(formatter)
    } catch (_: Exception) {
        "Oct 24, 2023 · 08:00 AM"
    }
}

fun formatDuration(minutes: Int): String {
    if (minutes <= 0) return "1h 15min"
    val hours = minutes / 60
    val mins = minutes % 60
    return when {
        hours > 0 && mins > 0 -> "${hours}h ${mins}min"
        hours > 0 -> "${hours}h"
        else -> "${mins}min"
    }
}

fun formatPesoCurrency(amount: Double): String {
    val formatter = DecimalFormat("#,##0")
    return "₱" + formatter.format(amount)
}

private fun parseLocalDate(dateStr: String?): LocalDate? {
    if (dateStr.isNullOrBlank()) return null
    return try {
        LocalDate.parse(dateStr.take(10))
    } catch (_: Exception) {
        null
    }
}

private fun calculateDurationMinutesBetween(startIso: String?, endIso: String?): Int? {
    if (startIso.isNullOrBlank() || endIso.isNullOrBlank()) return null
    return try {
        val start = Instant.parse(startIso)
        val end = Instant.parse(endIso)
        val durationSeconds = ChronoUnit.SECONDS.between(start, end)
        (durationSeconds / 60).toInt().coerceAtLeast(0)
    } catch (_: Exception) {
        null
    }
}

private fun calculateDurationMinutes(startIso: String?, endIso: String?): Int? {
    if (startIso.isNullOrBlank() || endIso.isNullOrBlank()) return null
    return try {
        val start = Instant.parse(startIso)
        val end = Instant.parse(endIso)
        val durationSeconds = end.epochSecond - start.epochSecond
        (durationSeconds / 60).toInt().coerceAtLeast(0)
    } catch (_: Exception) {
        null
    }
}
