package com.tanaw.app.feature.driver

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.tanaw.app.domain.model.ActiveTripStage
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DriverNavigationViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val sampleTrip = TripAssignment(
        deliveryId = "del-101",
        shipmentId = "shp-101",
        trackingNumber = "SHP-NE-8063",
        cargo = CargoDetails("General Freight", "5,000 kg", null),
        route = TripRoute(
            pickup = RoutePoint("Cabanatuan Hub", "Maharlika Highway, Cabanatuan City"),
            destination = RoutePoint("Gapan Market", "Pan-Philippine Hwy, Gapan City")
        ),
        meta = TripMeta(24.5, "24.5 km", "45 min", true),
        allowanceAmount = 500.0,
        formattedAllowance = "₱500",
        notes = "Deliver before 5 PM"
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
    fun `loads active trip assignment successfully`() = runTest {
        val fakeRepo = FakeNavDriverRepository(assignment = sampleTrip)
        val savedStateHandle = SavedStateHandle(mapOf("deliveryId" to "del-101"))

        val viewModel = DriverNavigationViewModel(
            driverRepository = fakeRepo,
            savedStateHandle = savedStateHandle,
            ioDispatcher = testDispatcher
        )

        viewModel.uiState.test {
            var state = awaitItem()
            if (state is DriverNavigationUiState.Loading) {
                state = awaitItem()
            }

            assertTrue("Expected Success state", state is DriverNavigationUiState.Success)
            val success = state as DriverNavigationUiState.Success
            assertEquals("SHP-NE-8063", success.trip.trackingNumber)
            assertEquals(ActiveTripStage.ASSIGNED, success.currentStage)
        }
    }

    @Test
    fun `arriveAtPickup updates stage to PICKUP_ARRIVED on success`() = runTest {
        val fakeRepo = FakeNavDriverRepository(assignment = sampleTrip)
        val savedStateHandle = SavedStateHandle(mapOf("deliveryId" to "del-101"))

        val viewModel = DriverNavigationViewModel(
            driverRepository = fakeRepo,
            savedStateHandle = savedStateHandle,
            ioDispatcher = testDispatcher
        )

        viewModel.uiState.test {
            var state = awaitItem()
            if (state is DriverNavigationUiState.Loading) {
                state = awaitItem()
            }

            viewModel.onEvent(DriverNavigationUiEvent.ArriveAtPickup)

            state = awaitItem() // Submitting state
            if ((state as? DriverNavigationUiState.Success)?.isSubmitting == true) {
                state = awaitItem()
            }

            val success = state as DriverNavigationUiState.Success
            assertEquals(ActiveTripStage.PICKUP_ARRIVED, success.currentStage)
            assertTrue(fakeRepo.arriveAtPickupCalled)
        }
    }

    @Test
    fun `arriveAtPickup displays error when repository returns ArrivalTooFar`() = runTest {
        val fakeRepo = FakeNavDriverRepository(
            assignment = sampleTrip,
            arriveAtPickupResult = Result.failure(DriverError.ArrivalTooFar(350.0, 100.0))
        )
        val savedStateHandle = SavedStateHandle(mapOf("deliveryId" to "del-101"))

        val viewModel = DriverNavigationViewModel(
            driverRepository = fakeRepo,
            savedStateHandle = savedStateHandle,
            ioDispatcher = testDispatcher
        )

        viewModel.uiState.test {
            var state = awaitItem()
            if (state is DriverNavigationUiState.Loading) {
                state = awaitItem()
            }

            viewModel.onEvent(DriverNavigationUiEvent.ArriveAtPickup)

            state = awaitItem()
            if ((state as? DriverNavigationUiState.Success)?.isSubmitting == true) {
                state = awaitItem()
            }

            val success = state as DriverNavigationUiState.Success
            assertNotNull(success.errorMessage)
            assertTrue(success.errorMessage!!.contains("350m away"))
        }
    }

    @Test
    fun `submitExpense records expense successfully`() = runTest {
        val fakeRepo = FakeNavDriverRepository(assignment = sampleTrip)
        val savedStateHandle = SavedStateHandle(mapOf("deliveryId" to "del-101"))

        val viewModel = DriverNavigationViewModel(
            driverRepository = fakeRepo,
            savedStateHandle = savedStateHandle,
            ioDispatcher = testDispatcher
        )

        viewModel.uiState.test {
            var state = awaitItem()
            if (state is DriverNavigationUiState.Loading) {
                state = awaitItem()
            }

            viewModel.onEvent(DriverNavigationUiEvent.SubmitExpense("Toll Fees", 150.0, null))

            state = awaitItem()
            if ((state as? DriverNavigationUiState.Success)?.expenseSubmitting == true) {
                state = awaitItem()
            }

            val success = state as DriverNavigationUiState.Success
            assertEquals("Expense recorded successfully!", success.expenseSuccessMessage)
            assertTrue(fakeRepo.addExpenseCalled)
        }
    }
}

private class FakeNavDriverRepository(
    private val assignment: TripAssignment?,
    private val arriveAtPickupResult: Result<Unit> = Result.success(Unit)
) : DriverRepository {

    var arriveAtPickupCalled = false
    var addExpenseCalled = false

    override fun observeActiveAssignment(driverId: String): Flow<Result<TripAssignment?>> = flowOf(Result.success(assignment))

    override suspend fun getDriverIdForCurrentUser(): String = "drv-101"

    override suspend fun driverArriveAtPickup(deliveryId: String, lat: Double, lng: Double): Result<Unit> {
        arriveAtPickupCalled = true
        return arriveAtPickupResult
    }

    override suspend fun driverConfirmCustomerShowed(deliveryId: String): Result<Unit> = Result.success(Unit)

    override suspend fun driverConfirmCargoLoaded(deliveryId: String): Result<Unit> = Result.success(Unit)

    override suspend fun driverStartRoute(deliveryId: String): Result<Unit> = Result.success(Unit)

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
    ): Result<Unit> {
        addExpenseCalled = true
        return Result.success(Unit)
    }

    override fun observeDriverProfile(userId: String): Flow<Result<DriverProfile>> = flowOf(
        Result.success(DriverProfile("drv-101", userId, "Ramon Cruz", "10-Wheeler", DriverStatus.AVAILABLE))
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

    override suspend fun publishDriverLocation(
        deliveryId: String,
        lat: Double,
        lng: Double,
        heading: Double,
        speed: Double
    ): Result<Unit> = Result.success(Unit)

    override fun observeDeliveryLocations(deliveryId: String): Flow<Pair<Double, Double>> = flowOf(Pair(15.4828, 120.9692))

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
}
