package com.tanaw.app.feature.workspace

import kotlinx.serialization.SerializationException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

enum class ErrorCategory {
    NETWORK,
    AUTH,
    CONTRACT,
    VALIDATION,
    UNKNOWN
}

object MobileErrorClassifier {

    fun isNetworkError(error: Throwable?): Boolean {
        if (error == null) return false
        var current: Throwable? = error
        while (current != null) {
            when (current) {
                is UnknownHostException,
                is SocketTimeoutException,
                is ConnectException,
                is NoRouteToHostException,
                is PortUnreachableException,
                is SocketException,
                is SSLException -> return true
                is IOException -> {
                    val msg = current.message.orEmpty()
                    if (containsNetworkHint(msg)) return true
                }
            }
            val className = current.javaClass.name
            if (className.contains("HttpRequestException") ||
                className.contains("SocketTimeoutException") ||
                className.contains("ConnectTimeoutException") ||
                className.contains("HttpRequestTimeoutException")
            ) {
                return true
            }
            if (containsNetworkHint(current.message.orEmpty())) return true
            current = current.cause
        }
        return false
    }

    fun isAuthError(error: Throwable?): Boolean {
        if (error == null || error is SerializationException) return false
        var current: Throwable? = error
        while (current != null) {
            val msg = current.message.orEmpty()
            if (msg.contains("ACTIVE_") ||
                msg.contains("JWT", ignoreCase = true) ||
                msg.contains("PGRST301") ||
                msg.contains("401") ||
                msg.contains("403") ||
                msg.contains("unauthorized", ignoreCase = true) ||
                msg.contains("invalid_credentials", ignoreCase = true) ||
                msg.contains("Invalid login", ignoreCase = true) ||
                msg.contains("invalid_token", ignoreCase = true) ||
                msg.contains("token is expired", ignoreCase = true) ||
                msg.contains("token has expired", ignoreCase = true) ||
                msg.contains("expired token", ignoreCase = true) ||
                msg.contains("otp_expired", ignoreCase = true) ||
                msg.contains("otp_disabled", ignoreCase = true) ||
                MobileAuthErrors.message(msg) != null
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    fun isContractError(error: Throwable?): Boolean {
        if (error == null) return false
        var current: Throwable? = error
        while (current != null) {
            if (current is SerializationException) return true
            val msg = current.message.orEmpty()
            if (msg.contains("500") || msg.contains("502") || msg.contains("503") || msg.contains("504") ||
                msg.contains("Internal Server Error", ignoreCase = true) ||
                msg.contains("PGRST202") || // Function not found
                msg.contains("PGRST200") // Ambiguous function
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    fun classify(error: Throwable?): ErrorCategory {
        if (error == null) return ErrorCategory.UNKNOWN
        return when {
            isContractError(error) -> ErrorCategory.CONTRACT
            isNetworkError(error) -> ErrorCategory.NETWORK
            isAuthError(error) -> ErrorCategory.AUTH
            error is IllegalArgumentException || error is IllegalStateException -> ErrorCategory.VALIDATION
            else -> ErrorCategory.UNKNOWN
        }
    }

    fun friendlyMessage(error: Throwable): String {
        if (isContractError(error)) {
            return "Service temporarily unavailable. Please try again later or contact the office."
        }
        val detail = error.message.orEmpty()
        MobileAuthErrors.message(detail)?.let { return it }

        return when {
            detail.contains("ACTIVE_") -> "This account is inactive or does not have access. Contact the office."
            detail.contains("Invalid login", ignoreCase = true) || detail.contains("invalid_credentials", ignoreCase = true) -> "Email or password is incorrect."
            detail.contains("email_not_confirmed", ignoreCase = true) -> "Verify your email before signing in."
            detail.contains("CAPACITY_EXCEEDED") -> "Cargo exceeds registered vehicle capacity. Choose another type or contact the office."
            detail.contains("INVALID_DEPARTURE") -> "Choose a future departure within the next year."
            detail.contains("REQUEST_ALREADY_SAVED_REFRESH") -> "This request was already saved. Refresh Active shipments before making another request."
            detail.contains("CUSTOMER_PROFILE_REQUIRED") -> "Your customer profile needs office attention before booking."
            detail.contains("rate", ignoreCase = true) || detail.contains("429") -> "Too many requests. Please wait before trying again."
            detail.contains("otp", ignoreCase = true) || detail.contains("invalid_token", ignoreCase = true) || detail.contains("PGRST301") ->
                "The code is invalid or your session expired. Request another code or sign in again."
            detail.contains("OUTSIDE_OPERATING_HOURS") -> "This action is only available during operating hours."
            detail.contains("ARRIVAL_TOO_FAR") -> "Move within the pickup arrival radius and use a fresh accurate location."
            detail.contains("WAIT_PERIOD_NOT_FINISHED") -> "The pickup waiting period has not finished. The office decides any cancellation."
            detail.contains("DELIVERY_CLOSED") -> "This delivery has closed. Refresh its current status."
            detail.contains("INVALID_") || error is IllegalArgumentException -> "Check the entered details and try again."
            isNetworkError(error) -> "Connection issue. Check your network connection and tap Refresh to retry."
            isAuthError(error) -> "Your session could not be verified. Sign in again to verify access."
            else -> "Unable to complete the request. Check your connection, refresh and try again."
        }
    }

    private fun containsNetworkHint(msg: String): Boolean {
        if (msg.isBlank()) return false
        val lower = msg.lowercase()
        return lower.contains("unable to resolve host") ||
                lower.contains("failed to connect") ||
                lower.contains("connection refused") ||
                lower.contains("network is unreachable") ||
                lower.contains("connection reset") ||
                lower.contains("broken pipe") ||
                lower.contains("software caused connection abort") ||
                lower.contains("connect timed out") ||
                lower.contains("timeout") ||
                lower.contains("no address associated with hostname")
    }
}
