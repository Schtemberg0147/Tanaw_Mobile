package com.tanaw.app.domain.repository

import com.tanaw.app.domain.model.ComplianceDocumentItem
import com.tanaw.app.domain.model.DeliveryIncidentItem
import com.tanaw.app.domain.model.DriverProfile
import com.tanaw.app.domain.model.DriverProfileDetails
import com.tanaw.app.domain.model.DriverTripItem
import com.tanaw.app.domain.model.DriverTripStats
import com.tanaw.app.domain.model.IncidentCategory
import com.tanaw.app.domain.model.TripAssignment
import com.tanaw.app.domain.model.TripPeriod
import com.tanaw.app.domain.model.TripTabFilter
import kotlinx.coroutines.flow.Flow

interface DriverRepository {
    fun observeActiveAssignment(driverId: String): Flow<Result<TripAssignment?>>
    fun observeDriverProfile(userId: String): Flow<Result<DriverProfile>>
    fun observeUnreadNotificationCount(userId: String): Flow<Int>

    suspend fun getDriverIdForCurrentUser(): String?

    fun observeDriverProfileDetails(): Flow<Result<DriverProfileDetails>>
    fun observeComplianceDocuments(): Flow<Result<List<ComplianceDocumentItem>>>

    suspend fun getDriverTripStats(period: TripPeriod): Result<DriverTripStats>
    fun observeDriverTrips(
        tab: TripTabFilter,
        period: TripPeriod,
        page: Int,
        pageSize: Int
    ): Flow<Result<List<DriverTripItem>>>

    fun observeRealtimeTripChanges(): Flow<Unit>

    suspend fun signOut(): Result<Unit>

    suspend fun driverStartRoute(deliveryId: String): Result<Unit>
    suspend fun driverArriveAtPickup(deliveryId: String, lat: Double, lng: Double): Result<Unit>
    suspend fun driverConfirmCustomerShowed(deliveryId: String): Result<Unit>
    suspend fun driverConfirmCargoLoaded(deliveryId: String): Result<Unit>
    suspend fun driverSubmitPod(
        deliveryId: String,
        recipientName: String,
        photoBytes: ByteArray,
        signatureBytes: ByteArray
    ): Result<Unit>
    suspend fun addDeliveryExpense(
        deliveryId: String,
        category: String,
        amount: Double,
        receiptBytes: ByteArray?
    ): Result<Unit>

    suspend fun publishDriverLocation(
        deliveryId: String,
        lat: Double,
        lng: Double,
        heading: Double = 0.0,
        speed: Double = 0.0
    ): Result<Unit>

    fun observeDeliveryLocations(deliveryId: String): Flow<Pair<Double, Double>>

    suspend fun reportDeliveryIncident(
        deliveryId: String,
        shipmentId: String,
        category: IncidentCategory,
        description: String?,
        delayMinutes: Int?,
        lat: Double?,
        lng: Double?
    ): Result<Unit>

    fun observeDeliveryIncidents(shipmentId: String): Flow<Result<List<DeliveryIncidentItem>>>
}
