package com.tanaw.app.feature.workspace

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import org.junit.Assert.*
import org.junit.Test
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.ProtocolException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class MobileErrorClassifierTest {

    @Test
    fun transportExceptionsAreClassifiedAsNetworkErrors() {
        assertTrue(MobileErrorClassifier.isNetworkError(UnknownHostException("Unable to resolve host")))
        assertTrue(MobileErrorClassifier.isNetworkError(SocketTimeoutException("timeout")))
        assertTrue(MobileErrorClassifier.isNetworkError(ConnectException("Failed to connect to /1.2.3.4:443")))
        assertTrue(MobileErrorClassifier.isNetworkError(SocketException("Connection reset")))
        assertTrue(MobileErrorClassifier.isNetworkError(NoRouteToHostException("No route to host")))
        assertTrue(MobileErrorClassifier.isNetworkError(PortUnreachableException("Port unreachable")))
        assertTrue(MobileErrorClassifier.isNetworkError(SSLException("SSL handshake aborted")))
        assertTrue(MobileErrorClassifier.isNetworkError(EOFException("unexpected end of stream on https://api.supabase.co/...")))
        assertTrue(MobileErrorClassifier.isNetworkError(ProtocolException("unexpected end of stream")))
        assertTrue(MobileErrorClassifier.isNetworkError(IOException("stream was reset: CANCEL")))
        assertTrue(MobileErrorClassifier.isNetworkError(IOException("\\n not found: limit=0 content=…")))
        assertTrue(MobileErrorClassifier.isNetworkError(IOException("connection shutdown")))
        assertTrue(MobileErrorClassifier.isNetworkError(IOException("Software caused connection abort")))
        assertTrue(MobileErrorClassifier.isNetworkError(IOException("connect timed out")))
        assertTrue(MobileErrorClassifier.isNetworkError(RuntimeException("Wrapped", UnknownHostException())))

        val timeoutCancellation = runBlocking {
            runCatching {
                withTimeout(1) {
                    delay(50)
                }
            }.exceptionOrNull()!!
        }
        assertTrue(MobileErrorClassifier.isNetworkError(timeoutCancellation))

        assertEquals(ErrorCategory.NETWORK, MobileErrorClassifier.classify(UnknownHostException()))
    }

    @Test
    fun databaseStatementTimeoutIsNotNetworkError() {
        assertFalse(MobileErrorClassifier.isNetworkError(Exception("canceling statement due to statement timeout")))
        assertFalse(MobileErrorClassifier.isNetworkError(Exception("statement timeout on query")))
    }

    @Test
    fun numbersInBusinessErrorsAreNotContractOrAuthErrors() {
        // 1500 meters contains "500", but it is a domain validation error, not a 500 server error
        val arrivalError = IllegalArgumentException("ARRIVAL_TOO_FAR: Distance 1500 meters exceeds threshold")
        assertFalse(MobileErrorClassifier.isContractError(arrivalError))
        assertFalse(MobileErrorClassifier.isAuthError(arrivalError))
        assertEquals(ErrorCategory.VALIDATION, MobileErrorClassifier.classify(arrivalError))
        assertEquals(
            "Move within the pickup arrival radius and use a fresh accurate location.",
            MobileErrorClassifier.friendlyMessage(arrivalError)
        )

        // 5000 kg contains "500", but it is a domain validation error
        val capacityError = IllegalStateException("CAPACITY_EXCEEDED: 5000 kg exceeds vehicle limit")
        assertFalse(MobileErrorClassifier.isContractError(capacityError))
        assertFalse(MobileErrorClassifier.isAuthError(capacityError))
        assertEquals(ErrorCategory.VALIDATION, MobileErrorClassifier.classify(capacityError))
        assertEquals(
            "Cargo exceeds registered vehicle capacity. Choose another type or contact the office.",
            MobileErrorClassifier.friendlyMessage(capacityError)
        )

        // UUID containing 401 or 500
        val uuidError = Exception("Record 1401b500-1111-2222-3333-444455556666 not found")
        assertFalse(MobileErrorClassifier.isAuthError(uuidError))
        assertFalse(MobileErrorClassifier.isContractError(uuidError))
        assertEquals(ErrorCategory.UNKNOWN, MobileErrorClassifier.classify(uuidError))
    }

    @Test
    fun authExceptionsAreClassifiedAsAuthErrors() {
        assertTrue(MobileErrorClassifier.isAuthError(Exception("ACTIVE_ACCOUNT_REQUIRED")))
        assertTrue(MobileErrorClassifier.isAuthError(Exception("ACTIVE_DRIVER_REQUIRED")))
        assertTrue(MobileErrorClassifier.isAuthError(Exception("PGRST301: JWT expired")))
        assertTrue(MobileErrorClassifier.isAuthError(Exception("HTTP 401 Unauthorized")))
        assertTrue(MobileErrorClassifier.isAuthError(Exception("HTTP 403 Forbidden")))
        assertTrue(MobileErrorClassifier.isAuthError(Exception("Invalid login credentials")))
        assertTrue(MobileErrorClassifier.isAuthError(Exception("invalid_credentials")))
        assertTrue(MobileErrorClassifier.isAuthError(Exception("phone_not_confirmed")))

        assertEquals(ErrorCategory.AUTH, MobileErrorClassifier.classify(Exception("PGRST301: JWT expired")))
        assertFalse(MobileErrorClassifier.isNetworkError(Exception("PGRST301: JWT expired")))
    }

    @Test
    fun schemaAndServerErrorsAreClassifiedAsContractErrors() {
        assertTrue(MobileErrorClassifier.isContractError(SerializationException("Missing field: shipment_id")))
        assertTrue(MobileErrorClassifier.isContractError(Exception("HTTP 500 Internal Server Error")))
        assertTrue(MobileErrorClassifier.isContractError(Exception("HTTP 502 Bad Gateway")))
        assertTrue(MobileErrorClassifier.isContractError(Exception("HTTP 503 Service Unavailable")))
        assertTrue(MobileErrorClassifier.isContractError(Exception("HTTP 504 Gateway Timeout")))
        assertTrue(MobileErrorClassifier.isContractError(Exception("PGRST202: Could not find function")))
        assertTrue(MobileErrorClassifier.isContractError(Exception("PGRST200: Ambiguous function")))

        assertEquals(ErrorCategory.CONTRACT, MobileErrorClassifier.classify(SerializationException("Missing field")))
        assertFalse(MobileErrorClassifier.isNetworkError(SerializationException("Missing field")))
    }

    @Test
    fun validationErrorsAreClassifiedCorrectly() {
        assertEquals(ErrorCategory.VALIDATION, MobileErrorClassifier.classify(IllegalArgumentException("INVALID_PHONE")))
        assertEquals(ErrorCategory.VALIDATION, MobileErrorClassifier.classify(IllegalStateException("Bad state")))
        assertFalse(MobileErrorClassifier.isNetworkError(IllegalArgumentException("INVALID_PHONE")))
    }

    @Test
    fun cancellationFriendlyMessagesMatchDomainCodes() {
        val enRouteError = IllegalStateException("DRIVER_ALREADY_EN_ROUTE")
        assertEquals(
            "Cannot cancel shipment because the driver is already on the way.",
            MobileErrorClassifier.friendlyMessage(enRouteError)
        )

        val reasonError = IllegalArgumentException("CANCELLATION_REASON_REQUIRED")
        assertEquals(
            "Please enter a valid cancellation reason.",
            MobileErrorClassifier.friendlyMessage(reasonError)
        )

        val notCancellableError = IllegalStateException("SHIPMENT_NOT_CANCELLABLE")
        assertEquals(
            "This shipment can no longer be cancelled. Contact the office for assistance.",
            MobileErrorClassifier.friendlyMessage(notCancellableError)
        )
    }

    @Test
    fun nullHandlingIsSafe() {
        assertEquals(ErrorCategory.UNKNOWN, MobileErrorClassifier.classify(null))
        assertFalse(MobileErrorClassifier.isNetworkError(null))
        assertFalse(MobileErrorClassifier.isAuthError(null))
        assertFalse(MobileErrorClassifier.isContractError(null))
    }

    @Test
    fun networkFailureOnOtpRequestReturnsConnectionIssueNotInvalidCode() {
        val netOtpError = IOException("HTTP request to https://supabase.co/auth/v1/otp failed with message: Unable to resolve host")
        assertTrue(MobileErrorClassifier.isNetworkError(netOtpError))
        val msg = MobileErrorClassifier.friendlyMessage(netOtpError)
        assertEquals("Connection issue. Check your network connection and tap Refresh to retry.", msg)
    }

    @Test
    fun domainFriendlyMessagesAreExact() {
        assertEquals(
            "Move within the pickup arrival radius and use a fresh accurate location.",
            MobileErrorClassifier.friendlyMessage(Exception("ARRIVAL_TOO_FAR"))
        )
        assertEquals(
            "Cargo exceeds registered vehicle capacity. Choose another type or contact the office.",
            MobileErrorClassifier.friendlyMessage(Exception("CAPACITY_EXCEEDED"))
        )
        assertEquals(
            "Choose a future departure within the next year.",
            MobileErrorClassifier.friendlyMessage(Exception("INVALID_DEPARTURE"))
        )
        assertEquals(
            "This request was already saved. Refresh Active shipments before making another request.",
            MobileErrorClassifier.friendlyMessage(Exception("REQUEST_ALREADY_SAVED_REFRESH"))
        )
        assertEquals(
            "Your customer profile needs office attention before booking.",
            MobileErrorClassifier.friendlyMessage(Exception("CUSTOMER_PROFILE_REQUIRED"))
        )
        assertEquals(
            "This action is only available during operating hours.",
            MobileErrorClassifier.friendlyMessage(Exception("OUTSIDE_OPERATING_HOURS"))
        )
        assertEquals(
            "The pickup waiting period has not finished. The office decides any cancellation.",
            MobileErrorClassifier.friendlyMessage(Exception("WAIT_PERIOD_NOT_FINISHED"))
        )
        assertEquals(
            "This delivery has closed. Refresh its current status.",
            MobileErrorClassifier.friendlyMessage(Exception("DELIVERY_CLOSED"))
        )
        assertEquals(
            "Too many requests. Please wait before trying again.",
            MobileErrorClassifier.friendlyMessage(Exception("rate limit exceeded (429)"))
        )
    }

    @Test
    fun friendlyMessagesAreActionableAndDoNotLeakPrivateData() {
        val netMsg = MobileErrorClassifier.friendlyMessage(UnknownHostException("api.supabase.co"))
        assertTrue(netMsg.contains("network connection") || netMsg.contains("Connection issue"))

        val authMsg = MobileErrorClassifier.friendlyMessage(Exception("PGRST301: JWT expired payload=private"))
        assertTrue(authMsg.contains("session expired") || authMsg.contains("code is invalid"))
        assertFalse(authMsg.contains("private"))

        val inactiveMsg = MobileErrorClassifier.friendlyMessage(Exception("ACTIVE_ACCOUNT_REQUIRED"))
        assertTrue(inactiveMsg.contains("inactive or does not have access"))

        val serverMsg = MobileErrorClassifier.friendlyMessage(SerializationException("Missing field: secret_token"))
        assertTrue(serverMsg.contains("temporarily unavailable") || serverMsg.contains("contact the office"))
        assertFalse(serverMsg.contains("secret_token"))

        val phoneExistsMsg = MobileErrorClassifier.friendlyMessage(Exception("AuthApiError: phone_exists"))
        assertEquals("This phone number is already registered to another account.", phoneExistsMsg)

        val otpExpiredMsg = MobileErrorClassifier.friendlyMessage(Exception("invalid_token or token is expired"))
        assertEquals("The code is invalid or your session expired. Request another code or sign in again.", otpExpiredMsg)
    }
}
