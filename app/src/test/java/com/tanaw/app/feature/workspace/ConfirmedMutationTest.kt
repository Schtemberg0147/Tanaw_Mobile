package com.tanaw.app.feature.workspace

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ConfirmedMutationTest {
    @Test fun confirmedStartAcknowledgesBeforeFailedRefreshWithoutRepeatingMutation()=runTest {
        val events=mutableListOf<String>()
        ConfirmedMutation.run({events.add("start")},{true},{events.add("GPS")},
            {events.add("refresh");error("network")},{events.add("saved but stale")})
        assertEquals(listOf("start","GPS","refresh","saved but stale"),events)
    }
    @Test fun rejectedMutationNeverAcknowledgesOrRefreshes()=runTest {
        var touched=false
        assertTrue(runCatching {ConfirmedMutation.run({error("denied")},{true},{touched=true},{touched=true},{touched=true})}.isFailure)
        assertFalse(touched)
    }
    @Test fun accountChangeCannotAcknowledgePreviousUsersCommand()=runTest {
        var touched=false
        ConfirmedMutation.run({},{false},{touched=true},{touched=true},{touched=true})
        assertFalse(touched)
    }
    @Test fun successfulRefreshDoesNotInvokeFailureAndCancellationPropagates()=runTest {
        var acknowledged=0
        ConfirmedMutation.run({},{true},{acknowledged++},{},{fail("refresh succeeded")})
        assertEquals(1,acknowledged)
        assertTrue(runCatching {ConfirmedMutation.run({},{true},{},{throw CancellationException()},{fail("cancelled")})}.exceptionOrNull() is CancellationException)
    }
    @Test fun refreshFailureAfterAccountChangeDoesNotWriteIntoNewSession()=runTest {
        var current=true
        var acknowledged=false
        ConfirmedMutation.run({},{current},{acknowledged=true},
            {current=false;error("old read failed")},{fail("Old session must not update new session")})
        assertTrue(acknowledged)
    }
}
