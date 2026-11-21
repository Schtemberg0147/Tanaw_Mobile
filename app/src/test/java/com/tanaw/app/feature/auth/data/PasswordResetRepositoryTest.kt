package com.tanaw.app.feature.auth.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordResetRepositoryTest {

    private class FakePasswordResetApi(
        var onSendOtp: (SendOtpRequest) -> SendOtpResponse = { SendOtpResponse(true, "Sent") },
        var onVerifyOtp: (VerifyOtpRequest) -> VerifyOtpResponse = { VerifyOtpResponse(true, "Verified") },
        var onReset: (ResetPasswordRequest) -> ResetPasswordResponse = { ResetPasswordResponse(true, "Updated") }
    ) : PasswordResetApi {
        var lastSendOtpRequest: SendOtpRequest? = null
        var lastVerifyOtpRequest: VerifyOtpRequest? = null
        var lastResetRequest: ResetPasswordRequest? = null

        override suspend fun sendOtp(request: SendOtpRequest): SendOtpResponse {
            lastSendOtpRequest = request
            return onSendOtp(request)
        }

        override suspend fun verifyOtp(request: VerifyOtpRequest): VerifyOtpResponse {
            lastVerifyOtpRequest = request
            return onVerifyOtp(request)
        }

        override suspend fun resetPassword(request: ResetPasswordRequest): ResetPasswordResponse {
            lastResetRequest = request
            return onReset(request)
        }
    }

    @Test
    fun sendOtpNormalizesEmailAndCallsEndpoint() = runTest {
        val fakeApi = FakePasswordResetApi()
        val repo = PasswordResetRepository(fakeApi)

        val result = repo.sendOtp("  User@Gmail.com ")

        assertTrue(result.isSuccess)
        assertEquals("user@gmail.com", fakeApi.lastSendOtpRequest?.email)
    }

    @Test
    fun sendOtpRejectsNonGmailBeforeCallingApi() = runTest {
        val fakeApi = FakePasswordResetApi()
        val repo = PasswordResetRepository(fakeApi)

        val result = repo.sendOtp("user@example.com")

        assertTrue(result.isFailure)
        assertEquals(null, fakeApi.lastSendOtpRequest)
    }

    @Test
    fun verifyOtpValidatesAndCallsEndpoint() = runTest {
        val fakeApi = FakePasswordResetApi()
        val repo = PasswordResetRepository(fakeApi)

        val invalid = repo.verifyOtp("user@gmail.com", "12345")
        assertTrue(invalid.isFailure)
        assertEquals(null, fakeApi.lastVerifyOtpRequest)

        val valid = repo.verifyOtp("  User@Gmail.com ", " 123456 ")
        assertTrue(valid.isSuccess)
        assertEquals("user@gmail.com", fakeApi.lastVerifyOtpRequest?.email)
        assertEquals("123456", fakeApi.lastVerifyOtpRequest?.otp)
    }

    @Test
    fun resetPasswordValidatesAndPropagatesApiErrors() = runTest {
        val fakeApi = FakePasswordResetApi(
            onReset = { throw PasswordResetApiException(400, "Invalid OTP. 4 attempt(s) remaining.") }
        )
        val repo = PasswordResetRepository(fakeApi)

        val invalidOtp = repo.resetPassword("user@gmail.com", "12345", "password123")
        assertTrue(invalidOtp.isFailure)
        assertEquals(null, fakeApi.lastResetRequest)

        val failedAttempt = repo.resetPassword("User@Gmail.com", " 123456 ", "password123")
        assertTrue(failedAttempt.isFailure)
        assertEquals("user@gmail.com", fakeApi.lastResetRequest?.email)
        assertEquals("123456", fakeApi.lastResetRequest?.otp)
        val exception = failedAttempt.exceptionOrNull() as PasswordResetApiException
        assertEquals(400, exception.statusCode)
        assertEquals("Invalid OTP. 4 attempt(s) remaining.", exception.message)
    }
}
