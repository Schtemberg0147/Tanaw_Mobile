package com.tanaw.app.feature.booking

import app.cash.turbine.test
import com.tanaw.app.data.model.MockData
import com.tanaw.app.domain.model.BookingError
import com.tanaw.app.domain.model.BookingItemUi
import com.tanaw.app.domain.model.BookingRequest
import com.tanaw.app.domain.model.BookingTabFilter
import com.tanaw.app.domain.model.CreatedShipment
import com.tanaw.app.domain.repository.BookingsRepository
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
class BookingViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val sampleVehicle = MockData.vehicles.first()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `successful submission updates state to Success and sets reference`() = runTest {
        val createdShipment = CreatedShipment(
            id = "shp-8063",
            trackingNumber = "SHP-NE-8063",
            status = "pending",
            quotedAmount = 2500.0
        )
        val fakeRepo = FakeBookingsRepository(createResult = Result.success(createdShipment))
        val viewModel = BookingViewModel(fakeRepo)

        viewModel.selectedVehicle = sampleVehicle
        viewModel.vehicleTypeId = sampleVehicle.id
        viewModel.pickupName = "Cabanatuan Hub"
        viewModel.destinationName = "Gapan Center"
        viewModel.weightKg = "2000"

        viewModel.uiState.test {
            assertEquals(BookingUiState.Idle, awaitItem())

            viewModel.submit()

            var state = awaitItem()
            if (state is BookingUiState.Submitting) {
                state = awaitItem()
            }

            assertTrue("Expected Success state but got $state", state is BookingUiState.Success)
            val successState = state as BookingUiState.Success
            assertEquals("SHP-NE-8063", successState.trackingNumber)
            assertEquals("SHP-NE-8063", viewModel.bookingReference)
            assertEquals(2500, viewModel.totalFee)
        }
    }

    @Test
    fun `outside operating hours error updates state to Error`() = runTest {
        val error = BookingError.OutsideOperatingHours("06:00 AM tomorrow")
        val fakeRepo = FakeBookingsRepository(createResult = Result.failure(error))
        val viewModel = BookingViewModel(fakeRepo)

        viewModel.selectedVehicle = sampleVehicle
        viewModel.vehicleTypeId = sampleVehicle.id
        viewModel.pickupName = "Cabanatuan Hub"
        viewModel.destinationName = "Gapan Center"
        viewModel.weightKg = "2000"

        viewModel.uiState.test {
            assertEquals(BookingUiState.Idle, awaitItem())

            viewModel.submit()

            var state = awaitItem()
            if (state is BookingUiState.Submitting) {
                state = awaitItem()
            }

            assertTrue("Expected Error state but got $state", state is BookingUiState.Error)
            val errorState = state as BookingUiState.Error
            assertTrue("Expected OutsideOperatingHours error", errorState.error is BookingError.OutsideOperatingHours)
            val optError = errorState.error as BookingError.OutsideOperatingHours
            assertEquals("06:00 AM tomorrow", optError.nextWindowStart)
        }
    }

    @Test
    fun `customer blocked error updates state to Error`() = runTest {
        val error = BookingError.CustomerBlocked("2025-02-26T12:00:00Z")
        val fakeRepo = FakeBookingsRepository(createResult = Result.failure(error))
        val viewModel = BookingViewModel(fakeRepo)

        viewModel.selectedVehicle = sampleVehicle
        viewModel.vehicleTypeId = sampleVehicle.id
        viewModel.pickupName = "Cabanatuan Hub"
        viewModel.destinationName = "Gapan Center"
        viewModel.weightKg = "2000"

        viewModel.uiState.test {
            assertEquals(BookingUiState.Idle, awaitItem())

            viewModel.submit()

            var state = awaitItem()
            if (state is BookingUiState.Submitting) {
                state = awaitItem()
            }

            assertTrue("Expected Error state but got $state", state is BookingUiState.Error)
            val errorState = state as BookingUiState.Error
            assertTrue("Expected CustomerBlocked error", errorState.error is BookingError.CustomerBlocked)
            val blockedError = errorState.error as BookingError.CustomerBlocked
            assertEquals("2025-02-26T12:00:00Z", blockedError.blockedUntil)
        }
    }

    @Test
    fun `consumeError resets error state back to Idle`() = runTest {
        val error = BookingError.NoPricingRate()
        val fakeRepo = FakeBookingsRepository(createResult = Result.failure(error))
        val viewModel = BookingViewModel(fakeRepo)

        viewModel.selectedVehicle = sampleVehicle
        viewModel.vehicleTypeId = sampleVehicle.id

        viewModel.uiState.test {
            assertEquals(BookingUiState.Idle, awaitItem())

            viewModel.submit()

            var state = awaitItem()
            if (state is BookingUiState.Submitting) {
                state = awaitItem()
            }

            assertTrue(state is BookingUiState.Error)

            viewModel.consumeError()

            assertEquals(BookingUiState.Idle, awaitItem())
        }
    }
}

private class FakeBookingsRepository(
    private val createResult: Result<CreatedShipment>
) : BookingsRepository {

    override fun observeBookings(
        customerId: String,
        tab: BookingTabFilter,
        page: Int,
        pageSize: Int
    ): Flow<Result<List<BookingItemUi>>> = flowOf(Result.success(emptyList()))

    override fun getCustomerId(userId: String): Flow<Result<String>> = flowOf(Result.success("cust-1"))

    override fun observeRealtimeShipmentChanges(customerId: String): Flow<Unit> = flowOf(Unit)

    override suspend fun createShipment(request: BookingRequest): Result<CreatedShipment> = createResult
}
