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
    @Test fun providersPrioritizesFusedGpsAndNetworkAndFailsWhenAllDisabled() {
        assertEquals(listOf("fused","gps","network"),TrackingSafety.providers {true})
        assertEquals(listOf("fused","network"),TrackingSafety.providers {it!="gps"})
        assertEquals(listOf("network"),TrackingSafety.providers {it=="network"})
        val failure = runCatching {TrackingSafety.providers {false}}.exceptionOrNull()
        assertNotNull(failure)
        assertTrue(failure?.message.orEmpty().contains("Enable location"))
        assertTrue(runCatching {TrackingSafety.providers {throw IllegalArgumentException()}}.isFailure)
    }
    @Test fun chooseBestFixFiltersInvalidAndPicksMostAccurateAndFreshest() {
        val now = 100_000L
        val candidates = listOf(
            FixCandidate("stale",14.6,120.9,5f,now - 20_000L),
            FixCandidate("inaccurate",14.6,120.9,150f,now - 2_000L),
            FixCandidate("network_ok",14.6,120.9,35f,now - 3_000L),
            FixCandidate("fused_accurate",14.6,120.9,12f,now - 5_000L),
            FixCandidate("fused_fresher_tie",14.6,120.9,12f,now - 1_000L),
        )
        assertEquals("fused_fresher_tie",TrackingSafety.chooseBestFix(candidates,now,maxAgeMs=15_000L))
        assertNull(TrackingSafety.chooseBestFix(listOf(FixCandidate("too_old",14.6,120.9,8f,now - 16_000L)),now,maxAgeMs=15_000L))
    }
}
