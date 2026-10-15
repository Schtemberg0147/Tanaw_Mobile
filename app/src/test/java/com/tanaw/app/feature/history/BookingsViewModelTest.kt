package com.tanaw.app.feature.history

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.tanaw.app.data.mapper.mapBookingCardStyle
import com.tanaw.app.domain.model.BookingItemUi
import com.tanaw.app.domain.model.BookingStatusEnum
import com.tanaw.app.domain.model.BookingTabFilter
import com.tanaw.app.domain.repository.BookingsRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
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
class BookingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val sampleActiveBooking = BookingItemUi(
        id = "SHP-NE-8063",
        shipmentId = "shp-1",
        pickupCity = "Cabanatuan City",
        destinationCity = "Gapan City",
        formattedDateAndVehicle = "Oct 24 · 10-Wheeler Wing Van",
        formattedPrice = "₱3,530",
        status = BookingStatusEnum.IN_TRANSIT,
        cardStyle = mapBookingCardStyle(BookingStatusEnum.IN_TRANSIT),
        hasPod = false
    )

    private val sampleCancelledBooking = BookingItemUi(
        id = "SHP-NE-7203",
        shipmentId = "shp-3",
        pickupCity = "Palayan",
        destinationCity = "Cabanatuan",
        formattedDateAndVehicle = "Oct 10 · 10-Wheeler Flatbed",
        formattedPrice = "₱3,040",
        status = BookingStatusEnum.CANCELLED,
        cardStyle = mapBookingCardStyle(BookingStatusEnum.CANCELLED),
        hasPod = false
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
    fun `uiState emits Content state when repository returns list`() = runTest {
        val fakeRepo = FakeBookingsRepository(
            bookingsMap = mapOf(
                BookingTabFilter.ALL to listOf(sampleActiveBooking, sampleCancelledBooking)
            )
        )

        val viewModel = BookingsViewModel(
            bookingsRepository = fakeRepo,
            supabase = createFakeSupabaseClient(),
            savedStateHandle = SavedStateHandle(),
            ioDispatcher = testDispatcher
        )

        viewModel.uiState.test {
            var item = awaitItem()
            if (item is BookingsUiState.Loading) {
                item = awaitItem()
            }
            assertTrue(item is BookingsUiState.Content)
            val contentState = item as BookingsUiState.Content
            assertEquals(2, contentState.bookings.size)
            assertEquals("SHP-NE-8063", contentState.bookings[0].id)
        }
    }

    @Test
    fun `uiState updates when tab changes`() = runTest {
        val fakeRepo = FakeBookingsRepository(
            bookingsMap = mapOf(
                BookingTabFilter.ALL to listOf(sampleActiveBooking, sampleCancelledBooking),
                BookingTabFilter.CANCELLED to listOf(sampleCancelledBooking)
            )
        )

        val viewModel = BookingsViewModel(
            bookingsRepository = fakeRepo,
            supabase = createFakeSupabaseClient(),
            savedStateHandle = SavedStateHandle(),
            ioDispatcher = testDispatcher
        )

        viewModel.uiState.test {
            var item = awaitItem()
            if (item is BookingsUiState.Loading) {
                item = awaitItem()
            }
            assertEquals(BookingTabFilter.ALL, item.selectedTab)

            viewModel.onEvent(BookingsEvent.TabSelected(3))

            var cancelledItem = awaitItem()
            if (cancelledItem is BookingsUiState.Loading) {
                cancelledItem = awaitItem()
            }
            assertEquals(BookingTabFilter.CANCELLED, cancelledItem.selectedTab)
            assertTrue(cancelledItem is BookingsUiState.Content)
            assertEquals(1, (cancelledItem as BookingsUiState.Content).bookings.size)
            assertEquals(BookingStatusEnum.CANCELLED, cancelledItem.bookings[0].status)
        }
    }

    private fun createFakeSupabaseClient(): SupabaseClient {
        return createSupabaseClient("https://fake.supabase.co", "fake-key") {}
    }
}

private class FakeBookingsRepository(
    private val bookingsMap: Map<BookingTabFilter, List<BookingItemUi>>
) : BookingsRepository {

    override fun observeBookings(
        customerId: String,
        tab: BookingTabFilter,
        page: Int,
        pageSize: Int
    ): Flow<Result<List<BookingItemUi>>> {
        val list = bookingsMap[tab] ?: emptyList()
        return flowOf(Result.success(list))
    }

    override fun getCustomerId(userId: String): Flow<Result<String>> {
        return flowOf(Result.success("cust-1"))
    }

    override fun observeRealtimeShipmentChanges(customerId: String): Flow<Unit> {
        return flowOf(Unit)
    }
}
