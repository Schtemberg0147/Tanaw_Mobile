package com.tanaw.app.feature.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingSessionRulesTest {

    @Test
    fun evaluateOwnerIdentifiesActiveAuthenticatedUser() {
        val state = TrackingSessionRules.evaluateOwner(
            expectedUid = "driver-1",
            currentAuthUid = "driver-1",
            storedSessionUid = "driver-1",
            isExplicitlyLoggedOut = false
        )
        assertEquals(TrackingOwnerState.ACTIVE_AUTHENTICATED, state)
        assertTrue(TrackingSessionRules.shouldQueueFix(state))
        assertTrue(TrackingSessionRules.shouldFlushNetwork(state))
        assertFalse(TrackingSessionRules.shouldClearAndStopService(state))
    }

    @Test
    fun evaluateOwnerPreservesOfflineExpiredSession() {
        val state = TrackingSessionRules.evaluateOwner(
            expectedUid = "driver-1",
            currentAuthUid = null,
            storedSessionUid = "driver-1",
            isExplicitlyLoggedOut = false
        )
        assertEquals(TrackingOwnerState.OFFLINE_TOKEN_EXPIRED, state)
        assertTrue(TrackingSessionRules.shouldQueueFix(state))
        assertFalse(TrackingSessionRules.shouldFlushNetwork(state))
        assertFalse(TrackingSessionRules.shouldClearAndStopService(state))
    }

    @Test
    fun evaluateOwnerStopsOnExplicitLogout() {
        val state = TrackingSessionRules.evaluateOwner(
            expectedUid = "driver-1",
            currentAuthUid = "driver-1",
            storedSessionUid = "driver-1",
            isExplicitlyLoggedOut = true
        )
        assertEquals(TrackingOwnerState.DIFFERENT_OR_LOGGED_OUT, state)
        assertFalse(TrackingSessionRules.shouldQueueFix(state))
        assertFalse(TrackingSessionRules.shouldFlushNetwork(state))
        assertTrue(TrackingSessionRules.shouldClearAndStopService(state))
    }

    @Test
    fun evaluateOwnerStopsOnDifferentUser() {
        val state = TrackingSessionRules.evaluateOwner(
            expectedUid = "driver-1",
            currentAuthUid = "driver-2",
            storedSessionUid = "driver-1",
            isExplicitlyLoggedOut = false
        )
        assertEquals(TrackingOwnerState.DIFFERENT_OR_LOGGED_OUT, state)
        assertFalse(TrackingSessionRules.shouldQueueFix(state))
        assertFalse(TrackingSessionRules.shouldFlushNetwork(state))
        assertTrue(TrackingSessionRules.shouldClearAndStopService(state))
    }

    @Test
    fun evaluateOwnerStopsWhenStoredSessionDiffersOrMissing() {
        val missingStored = TrackingSessionRules.evaluateOwner(
            expectedUid = "driver-1",
            currentAuthUid = null,
            storedSessionUid = null,
            isExplicitlyLoggedOut = false
        )
        assertEquals(TrackingOwnerState.DIFFERENT_OR_LOGGED_OUT, missingStored)

        val mismatchedStored = TrackingSessionRules.evaluateOwner(
            expectedUid = "driver-1",
            currentAuthUid = null,
            storedSessionUid = "driver-other",
            isExplicitlyLoggedOut = false
        )
        assertEquals(TrackingOwnerState.DIFFERENT_OR_LOGGED_OUT, mismatchedStored)
    }

    @Test
    fun evaluateOwnerStopsOnBlankOrNullExpectedUid() {
        assertEquals(
            TrackingOwnerState.DIFFERENT_OR_LOGGED_OUT,
            TrackingSessionRules.evaluateOwner(
                expectedUid = null,
                currentAuthUid = "driver-1",
                storedSessionUid = "driver-1",
                isExplicitlyLoggedOut = false
            )
        )
        assertEquals(
            TrackingOwnerState.DIFFERENT_OR_LOGGED_OUT,
            TrackingSessionRules.evaluateOwner(
                expectedUid = "",
                currentAuthUid = "driver-1",
                storedSessionUid = "driver-1",
                isExplicitlyLoggedOut = false
            )
        )
    }
}
