package com.tanaw.app.feature.workspace

import kotlinx.serialization.SerializationException
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
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
        assertTrue(MobileErrorClassifier.isNetworkError(IOException("Software caused connection abort")))
        assertTrue(MobileErrorClassifier.isNetworkError(IOException("connect timed out")))
        assertTrue(MobileErrorClassifier.isNetworkError(RuntimeException("Wrapped", UnknownHostException())))

        assertEquals(ErrorCategory.NETWORK, MobileErrorClassifier.classify(UnknownHostException()))
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
        assertTrue(MobileErrorClassifier.isContractError(Exception("PGRST202: Could not find function")))

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
    }
}
