package com.tanaw.app.domain.repository

import com.tanaw.app.domain.model.ComplianceDocumentItem
import com.tanaw.app.domain.model.DriverProfile
import com.tanaw.app.domain.model.DriverProfileDetails
import com.tanaw.app.domain.model.DriverTripItem
import com.tanaw.app.domain.model.DriverTripStats
import com.tanaw.app.domain.model.TripAssignment
import com.tanaw.app.domain.model.TripPeriod
import com.tanaw.app.domain.model.TripTabFilter
import kotlinx.coroutines.flow.Flow

interface DriverRepository {
    fun observeActiveAssignment(driverId: String): Flow<Result<TripAssignment?>>
    fun observeDriverProfile(userId: String): Flow<Result<DriverProfile>>
    fun observeUnreadNotificationCount(userId: String): Flow<Int>

    suspend fun getDriverIdForCurrentUser(): String?

    fun observeDriverProfileDetails(): Flow<Result<DriverProfileDetails>>
    fun observeComplianceDocuments(): Flow<Result<List<ComplianceDocumentItem>>>

    suspend fun getDriverTripStats(period: TripPeriod): Result<DriverTripStats>
    fun observeDriverTrips(
        tab: TripTabFilter,
        period: TripPeriod,
        page: Int,
        pageSize: Int
    ): Flow<Result<List<DriverTripItem>>>

    fun observeRealtimeTripChanges(): Flow<Unit>

    suspend fun signOut(): Result<Unit>
}
