package com.tanaw.app.feature.workspace

sealed class BackgroundRefreshOutcome {
    object MarkOffline : BackgroundRefreshOutcome()
    data class AccessRevoked(val message: String) : BackgroundRefreshOutcome()
    data class SessionExpired(val message: String) : BackgroundRefreshOutcome()
    data class ServerUnavailable(val message: String) : BackgroundRefreshOutcome()
    object Ignore : BackgroundRefreshOutcome()
}

object WorkspaceAutoRefreshRules {

    fun evaluateRefreshError(error: Throwable): BackgroundRefreshOutcome {
        return when {
            MobileErrorClassifier.isNetworkError(error) -> BackgroundRefreshOutcome.MarkOffline
            MobileErrorClassifier.isAuthError(error) -> {
                val msg = MobileErrorClassifier.friendlyMessage(error)
                val raw = error.message.orEmpty()
                if (raw.contains("ACTIVE_")) {
                    BackgroundRefreshOutcome.AccessRevoked(msg)
                } else {
                    BackgroundRefreshOutcome.SessionExpired(msg)
                }
            }
            MobileErrorClassifier.isContractError(error) -> {
                BackgroundRefreshOutcome.ServerUnavailable(MobileErrorClassifier.friendlyMessage(error))
            }
            else -> BackgroundRefreshOutcome.Ignore
        }
    }

    fun shouldAutoRefresh(
        isOnline: Boolean,
        page: String,
        isOffline: Boolean,
        isBusy: Boolean
    ): Boolean {
        return isOnline && page == "workspace" && isOffline && !isBusy
    }
}
