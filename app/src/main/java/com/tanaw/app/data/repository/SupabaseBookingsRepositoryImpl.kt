package com.tanaw.app.data.repository

import com.tanaw.app.data.dto.DbShipmentSummaryDto
import com.tanaw.app.data.mapper.toUiModel
import com.tanaw.app.domain.model.BookingItemUi
import com.tanaw.app.domain.model.BookingTabFilter
import com.tanaw.app.domain.repository.BookingsRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseBookingsRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient
) : BookingsRepository {

    override fun getCustomerId(userId: String): Flow<Result<String>> = flow {
        try {
            val customerId = try {
                supabase.from("customers")
                    .select { filter { eq("user_id", userId) } }
                    .decodeList<Map<String, String>>()
                    .firstOrNull()?.get("id")
            } catch (_: Exception) {
                null
            } ?: userId

            emit(Result.success(customerId))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    override fun observeBookings(
        customerId: String,
        tab: BookingTabFilter,
        page: Int,
        pageSize: Int
    ): Flow<Result<List<BookingItemUi>>> = flow {
        try {
            val startRange = (page - 1) * pageSize
            val endRange = page * pageSize - 1

            val list = try {
                supabase.from("shipments")
                    .select(
                        Columns.raw(
                            """
                            *,
                            pickup_location:locations!pickup_location_id(*),
                            dropoff_location:locations!dropoff_location_id(*),
                            requested_vehicle_type:vehicle_types!requested_vehicle_type_id(*),
                            delivery_documents(id)
                            """.trimIndent()
                        )
                    ) {
                        filter {
                            if (customerId.isNotBlank()) {
                                eq("customer_id", customerId)
                            }
                            when (tab) {
                                BookingTabFilter.ACTIVE -> isIn("status", listOf("booked", "assigned", "in_transit", "en_route", "picked_up"))
                                BookingTabFilter.COMPLETED -> eq("status", "delivered")
                                BookingTabFilter.CANCELLED -> isIn("status", listOf("cancelled", "rejected"))
                                BookingTabFilter.ALL -> {}
                            }
                        }
                        order("created_at", Order.DESCENDING)
                        range(startRange.toLong(), endRange.toLong())
                    }
                    .decodeList<DbShipmentSummaryDto>()
            } catch (_: Exception) {
                supabase.from("shipments")
                    .select {
                        filter {
                            if (customerId.isNotBlank()) {
                                eq("customer_id", customerId)
                            }
                            when (tab) {
                                BookingTabFilter.ACTIVE -> isIn("status", listOf("booked", "assigned", "in_transit", "en_route", "picked_up"))
                                BookingTabFilter.COMPLETED -> eq("status", "delivered")
                                BookingTabFilter.CANCELLED -> isIn("status", listOf("cancelled", "rejected"))
                                BookingTabFilter.ALL -> {}
                            }
                        }
                        order("created_at", Order.DESCENDING)
                        range(startRange.toLong(), endRange.toLong())
                    }
                    .decodeList<DbShipmentSummaryDto>()
            }

            val uiModels = list.map { it.toUiModel() }
            emit(Result.success(uiModels))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    override fun observeRealtimeShipmentChanges(customerId: String): Flow<Unit> = channelFlow {
        val channel = supabase.channel("customer-shipments-$customerId")
        try {
            val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "shipments"
                if (customerId.isNotBlank()) {
                    filter = "customer_id=eq.$customerId"
                }
            }

            channel.subscribe()

            changeFlow.collect {
                send(Unit)
            }
        } catch (_: Exception) {
            // Non-critical if realtime fails
        } finally {
            try {
                supabase.realtime.removeChannel(channel)
            } catch (_: Exception) {}
        }

        awaitClose {
            // Clean up on cancel
        }
    }
}
