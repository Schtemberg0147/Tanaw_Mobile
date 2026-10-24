package com.tanaw.app.feature.tracking

import androidx.compose.runtime.Immutable
import com.tanaw.app.domain.model.TripAssignment

sealed interface TrackDetailUiState {
    data object Loading : TrackDetailUiState

    @Immutable
    data class Content(
        val bookingId: String,
        val trackingNumber: String,
        val driverName: String,
        val vehicleDetails: String,
        val currentStatusLabel: String,
        val currentLocationName: String,
        val formattedFee: String,
        val etaText: String,
        val driverLat: Double = 15.4828,
        val driverLng: Double = 120.9692,
        val pickupLat: Double = 15.4828,
        val pickupLng: Double = 120.9692,
        val dropoffLat: Double = 15.3180,
        val dropoffLng: Double = 120.9472,
        val currentStageIndex: Int = 2 // 0 to 6
    ) : TrackDetailUiState

    data class Error(val message: String) : TrackDetailUiState
}
