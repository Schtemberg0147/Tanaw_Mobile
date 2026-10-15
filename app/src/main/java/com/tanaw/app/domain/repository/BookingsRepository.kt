package com.tanaw.app.domain.repository

import com.tanaw.app.domain.model.BookingItemUi
import com.tanaw.app.domain.model.BookingTabFilter
import kotlinx.coroutines.flow.Flow

interface BookingsRepository {
    fun observeBookings(
        customerId: String,
        tab: BookingTabFilter,
        page: Int,
        pageSize: Int
    ): Flow<Result<List<BookingItemUi>>>

    fun getCustomerId(userId: String): Flow<Result<String>>

    fun observeRealtimeShipmentChanges(customerId: String): Flow<Unit>
}
