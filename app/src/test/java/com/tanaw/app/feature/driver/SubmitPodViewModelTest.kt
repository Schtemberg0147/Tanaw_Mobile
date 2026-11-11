package com.tanaw.app.feature.driver

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.tanaw.app.domain.model.CargoDetails
import com.tanaw.app.domain.model.ComplianceDocumentItem
import com.tanaw.app.domain.model.DeliveryIncidentItem
import com.tanaw.app.domain.model.DriverError
import com.tanaw.app.domain.model.DriverProfile
import com.tanaw.app.domain.model.DriverProfileDetails
import com.tanaw.app.domain.model.DriverStatus
import com.tanaw.app.domain.model.DriverTripItem
import com.tanaw.app.domain.model.DriverTripStats
import com.tanaw.app.domain.model.IncidentCategory
import com.tanaw.app.domain.model.RoutePoint
import com.tanaw.app.domain.model.TripAssignment
import com.tanaw.app.domain.model.TripMeta
import com.tanaw.app.domain.model.TripPeriod
import com.tanaw.app.domain.model.TripRoute
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SubmitPodViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val sampleTrip = TripAssignment(
        deliveryId = "del-202",
        shipmentId = "shp-202",
        trackingNumber = "SHP-NE-8063",
        cargo = CargoDetails("General Cargo", "2,000 kg", null),
        route = TripRoute(RoutePoint("Origin", "Address A"), RoutePoint("Dest", "Address B")),
        meta = TripMeta(15.0, "15 km", "30 min", false),
        allowanceAmount = 500.0,
        formattedAllowance = "₱500",
        notes = null
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `shows error when recipient name is blank on submit`() = runTest {
        val fakeRepo = FakePodDriverRepository(assignment = sampleTrip)
        val savedStateHandle = SavedStateHandle(mapOf("deliveryId" to "del-202"))

        val viewModel = SubmitPodViewModel(
            driverRepository = fakeRepo,
            savedStateHandle = savedStateHandle,
            ioDispatcher = testDispatcher
        )

        viewModel.onEvent(SubmitPodUiEvent.SubmitPod)

        viewModel.uiState.test {
            val state = awaitItem()
            assertNotNull(state.errorMessage)
            assertTrue(state.errorMessage!!.contains("recipient full name"))
            assertFalse(fakeRepo.submitPodCalled)
        }
    }

    @Test
    fun `submits POD successfully when all fields are valid`() = runTest {
        val fakeRepo = FakePodDriverRepository(assignment = sampleTrip)
        val savedStateHandle = SavedStateHandle(mapOf("deliveryId" to "del-202"))

        val viewModel = SubmitPodViewModel(
            driverRepository = fakeRepo,
            savedStateHandle = savedStateHandle,
            ioDispatcher = testDispatcher
        )

        viewModel.onEvent(SubmitPodUiEvent.RecipientNameChanged("Maria Santos"))
        viewModel.onEvent(SubmitPodUiEvent.CargoPhotoSelected(byteArrayOf(1, 2, 3)))
        viewModel.onEvent(SubmitPodUiEvent.SignatureCaptured(byteArrayOf(4, 5, 6)))

        viewModel.sideEffect.test {
            viewModel.onEvent(SubmitPodUiEvent.SubmitPod)

            val effect = awaitItem()
            assertTrue("Expected NavigateToDeliveryComplete side effect", effect is SubmitPodSideEffect.NavigateToDeliveryComplete)
            assertEquals("del-202", (effect as SubmitPodSideEffect.NavigateToDeliveryComplete).deliveryId)
            assertTrue(fakeRepo.submitPodCalled)
        }
    }

    @Test
    fun `shows error when repository returns InvalidPodFile`() = runTest {
        val fakeRepo = FakePodDriverRepository(
            assignment = sampleTrip,
            submitPodResult = Result.failure(DriverError.InvalidPodFile("Corrupted image file"))
        )
        val savedStateHandle = SavedStateHandle(mapOf("deliveryId" to "del-202"))

        val viewModel = SubmitPodViewModel(
            driverRepository = fakeRepo,
            savedStateHandle = savedStateHandle,
            ioDispatcher = testDispatcher
        )

        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(SubmitPodUiEvent.RecipientNameChanged("Maria Santos"))
        viewModel.onEvent(SubmitPodUiEvent.CargoPhotoSelected(byteArrayOf(1, 2, 3)))
        viewModel.onEvent(SubmitPodUiEvent.SignatureCaptured(byteArrayOf(4, 5, 6)))

        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val initialState = awaitItem()
            assertEquals("Maria Santos", initialState.recipientName)
            assertTrue("Expected cargo photo to be set", initialState.hasCargoPhoto)
            assertTrue("Expected signature to be set", initialState.hasSignature)

            viewModel.onEvent(SubmitPodUiEvent.SubmitPod)

            var state = awaitItem()
            if (state.isSubmitting) {
                state = awaitItem()
            }
            assertNotNull("Error message should not be null", state.errorMessage)
            assertTrue("Expected error message to contain 'Corrupted image file' but was '${state.errorMessage}'", state.errorMessage!!.contains("Corrupted image file"))
            assertFalse("Delivery must not be marked success on POD error", state.isSuccess)
        }
    }
}

private class FakePodDriverRepository(
    private val assignment: TripAssignment?,
    private val submitPodResult: Result<Unit> = Result.success(Unit)
) : DriverRepository {

    var submitPodCalled = false

    override fun observeActiveAssignment(driverId: String): Flow<Result<TripAssignment?>> = flowOf(Result.success(assignment))

    override suspend fun getDriverIdForCurrentUser(): String = "drv-202"

    override suspend fun driverSubmitPod(
        deliveryId: String,
        recipientName: String,
        photoBytes: ByteArray,
        signatureBytes: ByteArray
    ): Result<Unit> {
        submitPodCalled = true
        return submitPodResult
    }

    override suspend fun driverStartRoute(deliveryId: String): Result<Unit> = Result.success(Unit)
    override suspend fun driverArriveAtPickup(deliveryId: String, lat: Double, lng: Double): Result<Unit> = Result.success(Unit)
    override suspend fun driverConfirmCustomerShowed(deliveryId: String): Result<Unit> = Result.success(Unit)
    override suspend fun driverConfirmCargoLoaded(deliveryId: String): Result<Unit> = Result.success(Unit)
    override suspend fun addDeliveryExpense(
        deliveryId: String,
        category: String,
        amount: Double,
        receiptBytes: ByteArray?
    ): Result<Unit> = Result.success(Unit)

    override fun observeDriverProfile(userId: String): Flow<Result<DriverProfile>> = flowOf(
        Result.success(DriverProfile("drv-202", userId, "Ramon Cruz", "10-Wheeler", DriverStatus.AVAILABLE))
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
    override suspend fun publishDriverLocation(deliveryId: String, lat: Double, lng: Double, heading: Double, speed: Double): Result<Unit> = Result.success(Unit)
    override fun observeDeliveryLocations(deliveryId: String): Flow<Pair<Double, Double>> = flowOf(Pair(15.4828, 120.9692))
    override suspend fun reportDeliveryIncident(deliveryId: String, shipmentId: String, category: IncidentCategory, description: String?, delayMinutes: Int?, lat: Double?, lng: Double?): Result<Unit> = Result.success(Unit)
    override fun observeDeliveryIncidents(shipmentId: String): Flow<Result<List<DeliveryIncidentItem>>> = flowOf(Result.success(emptyList()))
}
