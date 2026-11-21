package com.tanaw.app.feature.auth.data

import com.tanaw.app.feature.auth.rules.GmailOtpPasswordRules
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SendOtpRequest(
    val email: String
)

@Serializable
data class SendOtpResponse(
    val success: Boolean = true,
    val message: String = ""
)

@Serializable
data class VerifyOtpRequest(
    val email: String,
    val otp: String
)

@Serializable
data class VerifyOtpResponse(
    val success: Boolean = true,
    val message: String = ""
)

@Serializable
data class ResetPasswordRequest(
    val email: String,
    val otp: String,
    val newPassword: String
)

@Serializable
data class ResetPasswordResponse(
    val success: Boolean = true,
    val message: String = ""
)

@Serializable
data class PasswordApiErrorResponse(
    val error: String? = null,
    val message: String? = null
)

interface PasswordResetApi {
    suspend fun sendOtp(request: SendOtpRequest): SendOtpResponse
    suspend fun verifyOtp(request: VerifyOtpRequest): VerifyOtpResponse
    suspend fun resetPassword(request: ResetPasswordRequest): ResetPasswordResponse
}

class PasswordResetApiException(
    val statusCode: Int,
    override val message: String
) : Exception(message)

internal class KtorPasswordResetApi(
    private val baseUrl: String,
    private val httpClient: HttpClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 30_000
            requestTimeoutMillis = 30_000
        }
    }
) : PasswordResetApi {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun sendOtp(request: SendOtpRequest): SendOtpResponse =
        postJson("/api/password/send-otp", request)

    override suspend fun verifyOtp(request: VerifyOtpRequest): VerifyOtpResponse =
        postJson("/api/password/verify-otp", request)

    override suspend fun resetPassword(request: ResetPasswordRequest): ResetPasswordResponse =
        postJson("/api/password/reset", request)

    private suspend inline fun <reified Req, reified Res> postJson(
        path: String,
        payload: Req
    ): Res {
        val cleanBase = baseUrl.trim().trimEnd('/')
        check(cleanBase.isNotBlank()) { "PASSWORD_API_BASE_URL is not configured." }
        val response = httpClient.post("$cleanBase$path") {
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            header("ngrok-skip-browser-warning", "true")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(payload))
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            val apiError = runCatching {
                json.decodeFromString<PasswordApiErrorResponse>(body)
            }.getOrNull()
            val message = apiError?.error?.takeIf { it.isNotBlank() }
                ?: apiError?.message?.takeIf { it.isNotBlank() }
                ?: if (response.status.value in listOf(404, 405, 501) && path == "/api/password/verify-otp" && body.contains("Cannot POST", ignoreCase = true)) {
                    "Code verification endpoint is not enabled on the password server yet."
                } else {
                    defaultStatusMessage(response.status.value)
                }
            throw PasswordResetApiException(response.status.value, message)
        }
        return json.decodeFromString(body)
    }

    private fun defaultStatusMessage(statusCode: Int): String = when (statusCode) {
        400 -> "Check the entered email, OTP, or password and try again."
        404 -> "No account found for this Gmail address."
        410 -> "Verification code expired. Request a new code."
        429 -> "Too many attempts. Please wait before trying again."
        in 500..599 -> "Service temporarily unavailable. Please try again later."
        else -> "Request failed ($statusCode)."
    }
}

internal class PasswordResetRepository(
    private val api: PasswordResetApi
) {
    suspend fun sendOtp(email: String): Result<SendOtpResponse> = runCatching {
        val normalizedEmail = GmailOtpPasswordRules.normalizeEmail(email)
        require(GmailOtpPasswordRules.isGmail(normalizedEmail)) {
            "Email must be a valid @gmail.com address."
        }
        api.sendOtp(SendOtpRequest(email = normalizedEmail))
    }

    suspend fun verifyOtp(email: String, otp: String): Result<VerifyOtpResponse> = runCatching {
        val normalizedEmail = GmailOtpPasswordRules.normalizeEmail(email)
        val trimmedOtp = otp.trim()
        require(GmailOtpPasswordRules.isGmail(normalizedEmail)) {
            "Email must be a valid @gmail.com address."
        }
        require(GmailOtpPasswordRules.isValidOtp(trimmedOtp)) {
            "OTP must be a 6-digit code."
        }
        api.verifyOtp(VerifyOtpRequest(email = normalizedEmail, otp = trimmedOtp))
    }

    suspend fun resetPassword(
        email: String,
        otp: String,
        newPassword: String
    ): Result<ResetPasswordResponse> = runCatching {
        val normalizedEmail = GmailOtpPasswordRules.normalizeEmail(email)
        val trimmedOtp = otp.trim()
        require(GmailOtpPasswordRules.isGmail(normalizedEmail)) {
            "Email must be a valid @gmail.com address."
        }
        require(GmailOtpPasswordRules.isValidOtp(trimmedOtp)) {
            "OTP must be a 6-digit code."
        }
        require(newPassword.length >= GmailOtpPasswordRules.MIN_PASSWORD_LENGTH) {
            "New password must be at least ${GmailOtpPasswordRules.MIN_PASSWORD_LENGTH} characters."
        }
        api.resetPassword(
            ResetPasswordRequest(
                email = normalizedEmail,
                otp = trimmedOtp,
                newPassword = newPassword
            )
        )
    }
}
