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
import com.tanaw.app.domain.model.TripCardStyle
import com.tanaw.app.domain.model.TripPeriod
import com.tanaw.app.domain.model.TripStatus
import com.tanaw.app.domain.model.TripTabFilter
import com.tanaw.app.domain.repository.DriverRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MyTripsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val sampleTrip1 = DriverTripItem(
        id = "del-1",
        shipmentId = "shp-8063",
        trackingNumber = "SHP-NE-8063",
        status = TripStatus.COMPLETED,
        rawStatus = "completed",
        originCity = "Cabanatuan",
        destinationCity = "Gapan City",
        formattedDate = "Oct 24",
        formattedDuration = "Oct 24 • 2h 15min",
        allowanceAmount = 500.0,
        formattedAllowance = "₱500",
        cardStyle = TripCardStyle.from(TripStatus.COMPLETED)
    )

    private val sampleStats = DriverTripStats(
        completedTripsCount = 24,
        totalAllowance = 12000.0,
        formattedTotalAllowance = "₱12,000",
        onTimeRatePercentage = 96
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
    fun `tab selection updates selected tab in state`() = runTest {
        val fakeRepo = FakeFullDriverRepository(
            tripsResult = Result.success(listOf(sampleTrip1)),
            statsResult = Result.success(sampleStats)
        )

        val savedState = SavedStateHandle()
        val viewModel = MyTripsViewModel(
            driverRepository = fakeRepo,
            savedStateHandle = savedState,
            ioDispatcher = testDispatcher
        )

        viewModel.uiState.test {
            var initial = awaitItem()
            while (initial.tripsState is TripsContentUiState.Loading) {
                initial = awaitItem()
            }
            assertEquals(TripTabFilter.COMPLETED, initial.selectedTab)

            viewModel.onEvent(MyTripsEvent.TabSelected(TripTabFilter.ONGOING))

            var updated = awaitItem()
            while (updated.selectedTab != TripTabFilter.ONGOING) {
                updated = awaitItem()
            }
            assertEquals(TripTabFilter.ONGOING, updated.selectedTab)
        }
    }

    @Test
    fun `stats failure does not break the trips list`() = runTest {
        val fakeRepo = FakeFullDriverRepository(
            tripsResult = Result.success(listOf(sampleTrip1)),
            statsResult = Result.failure(RuntimeException("Database stats error"))
        )

        val savedState = SavedStateHandle()
        val viewModel = MyTripsViewModel(
            driverRepository = fakeRepo,
            savedStateHandle = savedState,
            ioDispatcher = testDispatcher
        )

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.tripsState is TripsContentUiState.Loading) {
                state = awaitItem()
            }

            assertTrue("Expected Stats Error but was ${state.statsState}", state.statsState is TripStatsUiState.Error)
            assertTrue("Expected Trips Content despite Stats Error", state.tripsState is TripsContentUiState.Content)

            val content = state.tripsState as TripsContentUiState.Content
            assertEquals(1, content.trips.size)
            assertEquals("SHP-NE-8063", content.trips[0].trackingNumber)
        }
    }

    @Test
    fun `period change updates period and triggers stats query`() = runTest {
        val fakeRepo = FakeFullDriverRepository(
            tripsResult = Result.success(listOf(sampleTrip1)),
            statsResult = Result.success(sampleStats)
        )

        val savedState = SavedStateHandle()
        val viewModel = MyTripsViewModel(
            driverRepository = fakeRepo,
            savedStateHandle = savedState,
            ioDispatcher = testDispatcher
        )

        val newPeriod = TripPeriod(2024, 9, "September 2024")

        viewModel.uiState.test {
            var initial = awaitItem()
            while (initial.tripsState is TripsContentUiState.Loading) {
                initial = awaitItem()
            }

            assertEquals(defaultCurrentPeriod().label, initial.period.label)

            viewModel.onEvent(MyTripsEvent.PeriodChanged(newPeriod))

            var updated = awaitItem()
            while (updated.period != newPeriod) {
                updated = awaitItem()
            }
            assertEquals("September 2024", updated.period.label)
        }
    }
}

private class FakeFullDriverRepository(
    private val tripsResult: Result<List<DriverTripItem>>,
    private val statsResult: Result<DriverTripStats>
) : DriverRepository {

    override fun observeActiveAssignment(driverId: String): Flow<Result<TripAssignment?>> = flowOf(Result.success(null))

    override fun observeDriverProfile(userId: String): Flow<Result<DriverProfile>> = flowOf(
        Result.success(DriverProfile("d1", "u1", "Ramon Cruz", "10-Wheeler", DriverStatus.AVAILABLE))
    )

    override fun observeUnreadNotificationCount(userId: String): Flow<Int> = flowOf(1)

    override suspend fun getDriverIdForCurrentUser(): String = "drv-1"

    override fun observeDriverProfileDetails(): Flow<Result<DriverProfileDetails>> = flowOf(
        Result.success(DriverProfileDetails("d1", "Ramon Cruz", "RC", "10-Wheeler · ABC-1234", "Driver since Jan 2022", "ramon@tanaw.ph", "09995130476"))
    )

    override fun observeComplianceDocuments(): Flow<Result<List<ComplianceDocumentItem>>> = flowOf(Result.success(emptyList()))

    override suspend fun getDriverTripStats(period: TripPeriod): Result<DriverTripStats> = statsResult

    override fun observeDriverTrips(
        tab: TripTabFilter,
        period: TripPeriod,
        page: Int,
        pageSize: Int
    ): Flow<Result<List<DriverTripItem>>> = flowOf(tripsResult)

    override fun observeRealtimeTripChanges(): Flow<Unit> = flowOf(Unit)

    override suspend fun signOut(): Result<Unit> = Result.success(Unit)

    override suspend fun driverStartRoute(deliveryId: String): Result<Unit> = Result.success(Unit)
    override suspend fun driverArriveAtPickup(deliveryId: String, lat: Double, lng: Double): Result<Unit> = Result.success(Unit)
    override suspend fun driverConfirmCustomerShowed(deliveryId: String): Result<Unit> = Result.success(Unit)
    override suspend fun driverConfirmCargoLoaded(deliveryId: String): Result<Unit> = Result.success(Unit)
    override suspend fun driverSubmitPod(deliveryId: String, recipientName: String, photoBytes: ByteArray, signatureBytes: ByteArray): Result<Unit> = Result.success(Unit)
    override suspend fun addDeliveryExpense(deliveryId: String, category: String, amount: Double, receiptBytes: ByteArray?): Result<Unit> = Result.success(Unit)
    override suspend fun publishDriverLocation(deliveryId: String, lat: Double, lng: Double, heading: Double, speed: Double): Result<Unit> = Result.success(Unit)
    override fun observeDeliveryLocations(deliveryId: String): Flow<Pair<Double, Double>> = flowOf(Pair(15.4828, 120.9692))
    override suspend fun reportDeliveryIncident(deliveryId: String, shipmentId: String, category: IncidentCategory, description: String?, delayMinutes: Int?, lat: Double?, lng: Double?): Result<Unit> = Result.success(Unit)
    override fun observeDeliveryIncidents(shipmentId: String): Flow<Result<List<DeliveryIncidentItem>>> = flowOf(Result.success(emptyList()))
}
