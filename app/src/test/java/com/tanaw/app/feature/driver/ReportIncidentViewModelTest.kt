package com.tanaw.app.feature.driver

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.tanaw.app.domain.model.ComplianceDocumentItem
import com.tanaw.app.domain.model.DeliveryIncidentItem
import com.tanaw.app.domain.model.DriverProfile
import com.tanaw.app.domain.model.DriverProfileDetails
import com.tanaw.app.domain.model.DriverStatus
import com.tanaw.app.domain.model.DriverTripItem
import com.tanaw.app.domain.model.DriverTripStats
import com.tanaw.app.domain.model.IncidentCategory
import com.tanaw.app.domain.model.TripAssignment
import com.tanaw.app.domain.model.TripPeriod
import com.tanaw.app.domain.model.TripTabFilter
import com.tanaw.app.domain.repository.DriverRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReportIncidentViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `reportDeliveryIncident submits incident details successfully`() = runTest {
        val fakeRepo = FakeIncidentDriverRepository()

        val result = fakeRepo.reportDeliveryIncident(
            deliveryId = "del-505",
            shipmentId = "shp-505",
            category = IncidentCategory.TRAFFIC,
            description = "Heavy traffic near Plaridel Bypass",
            delayMinutes = 20,
            lat = 15.4828,
            lng = 120.9692
        )

        assertTrue(result.isSuccess)
        assertTrue(fakeRepo.reportIncidentCalled)
        assertEquals(IncidentCategory.TRAFFIC, fakeRepo.lastCategory)
        assertEquals(20, fakeRepo.lastDelayMinutes)
    }
}

private class FakeIncidentDriverRepository : DriverRepository {

    var reportIncidentCalled = false
    var lastCategory: IncidentCategory? = null
    var lastDelayMinutes: Int? = null

    override suspend fun reportDeliveryIncident(
        deliveryId: String,
        shipmentId: String,
        category: IncidentCategory,
        description: String?,
        delayMinutes: Int?,
        lat: Double?,
        lng: Double?
    ): Result<Unit> {
        reportIncidentCalled = true
        lastCategory = category
        lastDelayMinutes = delayMinutes
        return Result.success(Unit)
    }

    override fun observeDeliveryIncidents(shipmentId: String): Flow<Result<List<DeliveryIncidentItem>>> = flowOf(Result.success(emptyList()))

    override fun observeActiveAssignment(driverId: String): Flow<Result<TripAssignment?>> = flowOf(Result.success(null))
    override suspend fun getDriverIdForCurrentUser(): String = "drv-505"
    override suspend fun driverStartRoute(deliveryId: String): Result<Unit> = Result.success(Unit)
    override suspend fun driverArriveAtPickup(deliveryId: String, lat: Double, lng: Double): Result<Unit> = Result.success(Unit)
    override suspend fun driverConfirmCustomerShowed(deliveryId: String): Result<Unit> = Result.success(Unit)
    override suspend fun driverConfirmCargoLoaded(deliveryId: String): Result<Unit> = Result.success(Unit)
    override suspend fun driverSubmitPod(
        deliveryId: String,
        recipientName: String,
        photoBytes: ByteArray,
        signatureBytes: ByteArray
    ): Result<Unit> = Result.success(Unit)
    override suspend fun addDeliveryExpense(
        deliveryId: String,
        category: String,
        amount: Double,
        receiptBytes: ByteArray?
    ): Result<Unit> = Result.success(Unit)
    override suspend fun publishDriverLocation(
        deliveryId: String,
        lat: Double,
        lng: Double,
        heading: Double,
        speed: Double
    ): Result<Unit> = Result.success(Unit)

    override fun observeDeliveryLocations(deliveryId: String): Flow<Pair<Double, Double>> = flowOf(Pair(15.4828, 120.9692))

    override fun observeDriverProfile(userId: String): Flow<Result<DriverProfile>> = flowOf(
        Result.success(DriverProfile("drv-505", userId, "Ramon Cruz", "10-Wheeler", DriverStatus.AVAILABLE))
    )
    override fun observeUnreadNotificationCount(userId: String): Flow<Int> = flowOf(0)
    override fun observeDriverProfileDetails(): Flow<Result<DriverProfileDetails>> = flowOf(Result.failure(NotImplementedError()))
    override fun observeComplianceDocuments(): Flow<Result<List<ComplianceDocumentItem>>> = flowOf(Result.failure(NotImplementedError()))
    override suspend fun getDriverTripStats(period: TripPeriod): Result<DriverTripStats> = Result.failure(NotImplementedError())
    override fun observeDriverTrips(
        tab: TripTabFilter,
        period: TripPeriod,
        page: Int,
        pageSize: Int
    ): Flow<Result<List<DriverTripItem>>> = flowOf(Result.success(emptyList()))
    override fun observeRealtimeTripChanges(): Flow<Unit> = flowOf(Unit)
    override suspend fun signOut(): Result<Unit> = Result.success(Unit)
}
