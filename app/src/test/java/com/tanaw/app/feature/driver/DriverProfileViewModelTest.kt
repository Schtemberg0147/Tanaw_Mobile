package com.tanaw.app.feature.driver

import app.cash.turbine.test
import com.tanaw.app.domain.model.ComplianceDocumentItem
import com.tanaw.app.domain.model.ComplianceStatus
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class DriverProfileViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val sampleDetails = DriverProfileDetails(
        driverId = "drv-1",
        fullName = "Ramon Cruz",
        initials = "RC",
        vehicleTypeNameAndPlate = "10-Wheeler Wing Van · ABC-1234",
        driverSinceText = "Driver since January 2022",
        email = "ramon.cruz@tanaw.ph",
        contactNumber = "09995130476"
    )

    private val sampleDocs = listOf(
        ComplianceDocumentItem(
            id = "doc-1",
            title = "Professional Driver's License",
            docType = "license",
            documentNumber = "N01-12-345678",
            expiryDate = LocalDate.of(2025, 12, 31),
            status = ComplianceStatus.from(LocalDate.of(2025, 12, 31), LocalDate.of(2024, 10, 24))
        )
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
    fun `loads profile details and compliance docs successfully in parallel`() = runTest {
        val fakeRepo = FakeProfileDriverRepository(
            detailsResult = Result.success(sampleDetails),
            docsResult = Result.success(sampleDocs)
        )

        val viewModel = DriverProfileViewModel(
            driverRepository = fakeRepo,
            ioDispatcher = testDispatcher
        )

        viewModel.uiState.test {
            var item = awaitItem()
            if (item is DriverProfileUiState.Loading) {
                item = awaitItem()
            }

            assertTrue("Expected Content state", item is DriverProfileUiState.Content)
            val content = item as DriverProfileUiState.Content
            assertEquals("Ramon Cruz", content.details.fullName)
            assertEquals("RC", content.details.initials)
            assertEquals(1, content.complianceDocs.size)
            assertEquals("Professional Driver's License", content.complianceDocs[0].title)
        }
    }

    @Test
    fun `when compliance docs fail header content still displays with docsErrorMessage`() = runTest {
        val fakeRepo = FakeProfileDriverRepository(
            detailsResult = Result.success(sampleDetails),
            docsResult = Result.failure(RuntimeException("Network error fetching docs"))
        )

        val viewModel = DriverProfileViewModel(
            driverRepository = fakeRepo,
            ioDispatcher = testDispatcher
        )

        viewModel.uiState.test {
            var item = awaitItem()
            if (item is DriverProfileUiState.Loading) {
                item = awaitItem()
            }

            assertTrue("Expected Content state despite docs error", item is DriverProfileUiState.Content)
            val content = item as DriverProfileUiState.Content
            assertEquals("Ramon Cruz", content.details.fullName)
            assertTrue(content.complianceDocs.isEmpty())
            assertNotNull(content.docsErrorMessage)
        }
    }

    @Test
    fun `logout triggers NavigateToLogin side effect`() = runTest {
        val fakeRepo = FakeProfileDriverRepository(
            detailsResult = Result.success(sampleDetails),
            docsResult = Result.success(sampleDocs)
        )

        val viewModel = DriverProfileViewModel(
            driverRepository = fakeRepo,
            ioDispatcher = testDispatcher
        )

        viewModel.sideEffect.test {
            viewModel.onEvent(DriverProfileEvent.LogoutClicked)
            val effect = awaitItem()
            assertEquals(DriverProfileSideEffect.NavigateToLogin, effect)
        }
    }
}

private class FakeProfileDriverRepository(
    private val detailsResult: Result<DriverProfileDetails>,
    private val docsResult: Result<List<ComplianceDocumentItem>>
) : DriverRepository {

    override fun observeActiveAssignment(driverId: String): Flow<Result<TripAssignment?>> = flowOf(Result.success(null))

    override fun observeDriverProfile(userId: String): Flow<Result<DriverProfile>> = flowOf(
        Result.success(DriverProfile("d1", "u1", "Ramon Cruz", "10-Wheeler", DriverStatus.AVAILABLE))
    )

    override fun observeUnreadNotificationCount(userId: String): Flow<Int> = flowOf(1)

    override suspend fun getDriverIdForCurrentUser(): String = "drv-1"

    override fun observeDriverProfileDetails(): Flow<Result<DriverProfileDetails>> = flowOf(detailsResult)

    override fun observeComplianceDocuments(): Flow<Result<List<ComplianceDocumentItem>>> = flowOf(docsResult)

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
