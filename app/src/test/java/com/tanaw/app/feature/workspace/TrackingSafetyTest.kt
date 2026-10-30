package com.tanaw.app.feature.workspace

import org.junit.Assert.*
import org.junit.Test

class TrackingSafetyTest {
    @Test fun disabledAndMissingProvidersRequireExplicitRecovery() {
        assertEquals("network",TrackingSafety.provider {it=="network"})
        assertEquals("gps",TrackingSafety.provider {true})
        assertTrue(runCatching {TrackingSafety.provider {false}}.isFailure)
        assertTrue(runCatching {TrackingSafety.provider {throw IllegalArgumentException()}}.isFailure)
    }
    @Test fun onlyFreshAccurateFiniteFixesAreQueued() {
        assertTrue(TrackingSafety.valid(0.0,0.0,10f,0))
        assertFalse(TrackingSafety.valid(Double.NaN,0.0,10f,0))
        assertFalse(TrackingSafety.valid(91.0,0.0,10f,0))
        assertFalse(TrackingSafety.valid(0.0,0.0,Float.NaN,0))
        assertFalse(TrackingSafety.valid(0.0,0.0,101f,0))
        assertFalse(TrackingSafety.valid(0.0,0.0,10f,60001))
        assertFalse(TrackingSafety.valid(0.0,0.0,10f,-1))
    }
}
