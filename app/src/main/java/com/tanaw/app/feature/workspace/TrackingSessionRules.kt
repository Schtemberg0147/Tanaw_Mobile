package com.tanaw.app.feature.workspace

enum class TrackingOwnerState {
    ACTIVE_AUTHENTICATED,
    OFFLINE_TOKEN_EXPIRED,
    DIFFERENT_OR_LOGGED_OUT
}

object TrackingSessionRules {

    fun evaluateOwner(
        expectedUid: String?,
        currentAuthUid: String?,
        storedSessionUid: String?,
        isExplicitlyLoggedOut: Boolean,
    ): TrackingOwnerState {
        if (expectedUid.isNullOrBlank() || isExplicitlyLoggedOut) {
            return TrackingOwnerState.DIFFERENT_OR_LOGGED_OUT
        }
        if (currentAuthUid != null) {
            return if (currentAuthUid == expectedUid) TrackingOwnerState.ACTIVE_AUTHENTICATED
            else TrackingOwnerState.DIFFERENT_OR_LOGGED_OUT
        }
        // When offline or token refresh fails in Auth 3.8, currentAuthUid is null,
        // but storedSessionUid preserves the signed-in driver's identity.
        if (storedSessionUid == expectedUid) {
            return TrackingOwnerState.OFFLINE_TOKEN_EXPIRED
        }
        return TrackingOwnerState.DIFFERENT_OR_LOGGED_OUT
    }

    fun shouldQueueFix(state: TrackingOwnerState): Boolean =
        (state == TrackingOwnerState.ACTIVE_AUTHENTICATED) || (state == TrackingOwnerState.OFFLINE_TOKEN_EXPIRED)

    fun shouldFlushNetwork(state: TrackingOwnerState): Boolean =
        state == TrackingOwnerState.ACTIVE_AUTHENTICATED

    fun shouldClearAndStopService(state: TrackingOwnerState): Boolean =
        state == TrackingOwnerState.DIFFERENT_OR_LOGGED_OUT
}
