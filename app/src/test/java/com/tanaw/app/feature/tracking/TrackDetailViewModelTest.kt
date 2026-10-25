package com.tanaw.app.feature.tracking

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
import kotlinx.coroutines.flow.MutableStateFlow
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
class TrackDetailViewModelTest {

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
    fun `loads tracking content and updates live driver location`() = runTest {
        val locationFlow = MutableStateFlow(Pair(15.4828, 120.9692))
        val fakeRepo = FakeTrackDriverRepository(locationFlow)
        val savedStateHandle = SavedStateHandle(mapOf("bookingId" to "bk-100"))

        val viewModel = TrackDetailViewModel(
            driverRepository = fakeRepo,
            savedStateHandle = savedStateHandle,
            ioDispatcher = testDispatcher
        )

        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            var state = awaitItem()
            if (state is TrackDetailUiState.Loading) {
                state = awaitItem()
            }

            assertTrue("Expected Content state", state is TrackDetailUiState.Content)
            val content = state as TrackDetailUiState.Content
            assertEquals("Ramon Cruz", content.driverName)
            assertEquals("IN TRANSIT", content.currentStatusLabel)
            assertEquals(15.4828, content.driverLat, 0.0001)

            // Emit live location update
            locationFlow.value = Pair(15.4900, 120.9750)

            state = awaitItem()
            val updated = state as TrackDetailUiState.Content
            assertEquals(15.4900, updated.driverLat, 0.0001)
            assertEquals(120.9750, updated.driverLng, 0.0001)
        }
    }
}

private class FakeTrackDriverRepository(
    private val locationFlow: Flow<Pair<Double, Double>>
) : DriverRepository {

    override fun observeDeliveryLocations(deliveryId: String): Flow<Pair<Double, Double>> = locationFlow

    override fun observeActiveAssignment(driverId: String): Flow<Result<TripAssignment?>> = flowOf(Result.success(null))
    override suspend fun getDriverIdForCurrentUser(): String = "drv-1"
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

    override suspend fun reportDeliveryIncident(
        deliveryId: String,
        shipmentId: String,
        category: IncidentCategory,
        description: String?,
        delayMinutes: Int?,
        lat: Double?,
        lng: Double?
    ): Result<Unit> = Result.success(Unit)

    override fun observeDeliveryIncidents(shipmentId: String): Flow<Result<List<DeliveryIncidentItem>>> = flowOf(Result.success(emptyList()))

    override fun observeDriverProfile(userId: String): Flow<Result<DriverProfile>> = flowOf(
        Result.success(DriverProfile("drv-1", userId, "Ramon Cruz", "10-Wheeler", DriverStatus.AVAILABLE))
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
