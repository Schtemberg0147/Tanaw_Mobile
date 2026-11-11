package com.tanaw.app.feature.workspace

import kotlinx.serialization.SerializationException
import org.junit.Assert.*
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class WorkspaceAutoRefreshRulesTest {

    @Test
    fun networkErrorsEvaluateToMarkOffline() {
        val outcome1 = WorkspaceAutoRefreshRules.evaluateRefreshError(UnknownHostException("Host unresolvable"))
        assertEquals(BackgroundRefreshOutcome.MarkOffline, outcome1)

        val outcome2 = WorkspaceAutoRefreshRules.evaluateRefreshError(SocketTimeoutException("Read timed out"))
        assertEquals(BackgroundRefreshOutcome.MarkOffline, outcome2)

        val outcome3 = WorkspaceAutoRefreshRules.evaluateRefreshError(ConnectException("Connection refused"))
        assertEquals(BackgroundRefreshOutcome.MarkOffline, outcome3)
    }

    @Test
    fun inactiveAccountEvaluatesToAccessRevoked() {
        val outcome = WorkspaceAutoRefreshRules.evaluateRefreshError(Exception("ACTIVE_ACCOUNT_REQUIRED"))
        assertTrue(outcome is BackgroundRefreshOutcome.AccessRevoked)
        val revoked = outcome as BackgroundRefreshOutcome.AccessRevoked
        assertTrue(revoked.message.contains("inactive or does not have access"))
    }

    @Test
    fun expiredAuthEvaluatesToSessionExpired() {
        val outcome1 = WorkspaceAutoRefreshRules.evaluateRefreshError(Exception("PGRST301: JWT expired"))
        assertTrue(outcome1 is BackgroundRefreshOutcome.SessionExpired)
        val expired1 = outcome1 as BackgroundRefreshOutcome.SessionExpired
        assertTrue(expired1.message.contains("session expired") || expired1.message.contains("Sign in again"))

        val outcome2 = WorkspaceAutoRefreshRules.evaluateRefreshError(Exception("HTTP 401 Unauthorized"))
        assertTrue(outcome2 is BackgroundRefreshOutcome.SessionExpired)
    }

    @Test
    fun serverAndContractErrorsEvaluateToServerUnavailable() {
        val outcome1 = WorkspaceAutoRefreshRules.evaluateRefreshError(SerializationException("Missing field"))
        assertTrue(outcome1 is BackgroundRefreshOutcome.ServerUnavailable)
        val server1 = outcome1 as BackgroundRefreshOutcome.ServerUnavailable
        assertTrue(server1.message.contains("temporarily unavailable") || server1.message.contains("contact the office"))

        val outcome2 = WorkspaceAutoRefreshRules.evaluateRefreshError(Exception("HTTP 500 Internal Server Error"))
        assertTrue(outcome2 is BackgroundRefreshOutcome.ServerUnavailable)

        val outcome3 = WorkspaceAutoRefreshRules.evaluateRefreshError(Exception("HTTP 503 Service Unavailable"))
        assertTrue(outcome3 is BackgroundRefreshOutcome.ServerUnavailable)
    }

    @Test
    fun unknownErrorsEvaluateToIgnore() {
        val outcome = WorkspaceAutoRefreshRules.evaluateRefreshError(Exception("Some random unhandled message"))
        assertEquals(BackgroundRefreshOutcome.Ignore, outcome)
    }

    @Test
    fun shouldAutoRefreshRulesEnforced() {
        // Can only auto-refresh when online, on workspace page, currently offline, and not busy
        assertTrue(WorkspaceAutoRefreshRules.shouldAutoRefresh(isOnline = true, page = "workspace", isOffline = true, isBusy = false))

        // Not online
        assertFalse(WorkspaceAutoRefreshRules.shouldAutoRefresh(isOnline = false, page = "workspace", isOffline = true, isBusy = false))

        // Not on workspace page
        assertFalse(WorkspaceAutoRefreshRules.shouldAutoRefresh(isOnline = true, page = "login", isOffline = true, isBusy = false))
        assertFalse(WorkspaceAutoRefreshRules.shouldAutoRefresh(isOnline = true, page = "access", isOffline = true, isBusy = false))

        // Not offline (already synchronized)
        assertFalse(WorkspaceAutoRefreshRules.shouldAutoRefresh(isOnline = true, page = "workspace", isOffline = false, isBusy = false))

        // Currently busy performing an operation
        assertFalse(WorkspaceAutoRefreshRules.shouldAutoRefresh(isOnline = true, page = "workspace", isOffline = true, isBusy = true))
    }
}
