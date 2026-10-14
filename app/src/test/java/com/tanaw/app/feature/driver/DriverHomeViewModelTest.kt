package com.tanaw.app.feature.driver

import app.cash.turbine.test
import com.tanaw.app.domain.model.CargoDetails
import com.tanaw.app.domain.model.ComplianceDocumentItem
import com.tanaw.app.domain.model.DriverProfile
import com.tanaw.app.domain.model.DriverProfileDetails
import com.tanaw.app.domain.model.DriverStatus
import com.tanaw.app.domain.model.DriverTripItem
import com.tanaw.app.domain.model.DriverTripStats
import com.tanaw.app.domain.model.RoutePoint
import com.tanaw.app.domain.model.TripAssignment
import com.tanaw.app.domain.model.TripMeta
import com.tanaw.app.domain.model.TripPeriod
import com.tanaw.app.domain.model.TripRoute
import com.tanaw.app.domain.model.TripTabFilter
import com.tanaw.app.domain.repository.DriverRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
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
class DriverHomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val sampleProfile = DriverProfile(
        id = "drv-1",
        userId = "usr-1",
        fullName = "Ramon Cruz",
        vehicleTypeName = "10-Wheeler Wing Van",
        status = DriverStatus.AVAILABLE
    )

    private val sampleTrip = TripAssignment(
        deliveryId = "del-1",
        shipmentId = "shp-1",
        trackingNumber = "SHP-NE-8063",
        cargo = CargoDetails("Bulk", "6,000 kg", "KEEP DRY"),
        route = TripRoute(
            pickup = RoutePoint("Pickup Hub", "Address 1"),
            destination = RoutePoint("Dest Hub", "Address 2")
        ),
        meta = TripMeta(38.0, "38 km", "1h 15min", true),
        formattedAllowance = "₱500",
        notes = "Handle with care"
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
    fun `uiState emits Empty state when no trip is assigned`() = runTest {
        val fakeRepo = FakeDriverRepository(
            profile = sampleProfile,
            assignmentFlow = MutableStateFlow(Result.success(null))
        )

        val viewModel = DriverHomeViewModel(
            driverRepository = fakeRepo,
            supabase = createFakeSupabaseClient(),
            ioDispatcher = testDispatcher
        )

        viewModel.uiState.test {
            var item = awaitItem()
            if (item is DriverHomeUiState.Loading) {
                item = awaitItem()
            }
            assertTrue("Expected Empty state but was $item", item is DriverHomeUiState.Empty)
            val emptyState = item as DriverHomeUiState.Empty
            assertEquals("Ramon Cruz", emptyState.driver.fullName)
            assertEquals(1, emptyState.unreadNotificationCount)
        }
    }

    @Test
    fun `uiState emits Assigned state when active trip is returned`() = runTest {
        val fakeRepo = FakeDriverRepository(
            profile = sampleProfile,
            assignmentFlow = MutableStateFlow(Result.success(sampleTrip))
        )

        val viewModel = DriverHomeViewModel(
            driverRepository = fakeRepo,
            supabase = createFakeSupabaseClient(),
            ioDispatcher = testDispatcher
        )

        viewModel.uiState.test {
            var item = awaitItem()
            if (item is DriverHomeUiState.Loading) {
                item = awaitItem()
            }
            assertTrue("Expected Assigned state but was $item", item is DriverHomeUiState.Assigned)
            val assignedState = item as DriverHomeUiState.Assigned
            assertEquals("SHP-NE-8063", assignedState.trip.trackingNumber)
            assertEquals(DriverStatus.ON_TRIP, assignedState.driver.status)
        }
    }

    @Test
    fun `uiState transitions live from Empty to Assigned when realtime event triggers`() = runTest {
        val assignmentStateFlow = MutableStateFlow<Result<TripAssignment?>>(Result.success(null))
        val fakeRepo = FakeDriverRepository(
            profile = sampleProfile,
            assignmentFlow = assignmentStateFlow
        )

        val viewModel = DriverHomeViewModel(
            driverRepository = fakeRepo,
            supabase = createFakeSupabaseClient(),
            ioDispatcher = testDispatcher
        )

        viewModel.uiState.test {
            var item = awaitItem()
            if (item is DriverHomeUiState.Loading) {
                item = awaitItem()
            }
            assertTrue("Expected Empty state but was $item", item is DriverHomeUiState.Empty)

            // Simulate realtime insert event assigning trip
            assignmentStateFlow.value = Result.success(sampleTrip)

            val updatedAssigned = awaitItem()
            assertTrue("Expected Assigned state but was $updatedAssigned", updatedAssigned is DriverHomeUiState.Assigned)
            assertEquals("SHP-NE-8063", (updatedAssigned as DriverHomeUiState.Assigned).trip.trackingNumber)
        }
    }

    private fun createFakeSupabaseClient(): SupabaseClient {
        return createSupabaseClient("https://fake.supabase.co", "fake-key") {}
    }
}

private class FakeDriverRepository(
    private val profile: DriverProfile,
    private val assignmentFlow: MutableStateFlow<Result<TripAssignment?>>
) : DriverRepository {

    override fun observeDriverProfile(userId: String): Flow<Result<DriverProfile>> {
        return flowOf(Result.success(profile))
    }

    override fun observeActiveAssignment(driverId: String): Flow<Result<TripAssignment?>> {
        return assignmentFlow
    }

    override fun observeUnreadNotificationCount(userId: String): Flow<Int> {
        return flowOf(1)
    }

    override suspend fun getDriverIdForCurrentUser(): String? = "drv-1"

    override fun observeDriverProfileDetails(): Flow<Result<DriverProfileDetails>> = flowOf(
        Result.success(DriverProfileDetails("d1", "Ramon Cruz", "RC", "10-Wheeler · ABC-1234", "Driver since Jan 2022", "ramon@tanaw.ph", "09995130476"))
    )

    override fun observeComplianceDocuments(): Flow<Result<List<ComplianceDocumentItem>>> = flowOf(Result.success(emptyList()))

    override suspend fun getDriverTripStats(period: TripPeriod): Result<DriverTripStats> = Result.success(
        DriverTripStats(24, 12000.0, "₱12,000", 96)
    )

    override fun observeDriverTrips(
        tab: TripTabFilter,
        period: TripPeriod,
        page: Int,
        pageSize: Int
    ): Flow<Result<List<DriverTripItem>>> = flowOf(Result.success(emptyList()))

    override fun observeRealtimeTripChanges(): Flow<Unit> = flowOf(Unit)

    override suspend fun signOut(): Result<Unit> = Result.success(Unit)
}
