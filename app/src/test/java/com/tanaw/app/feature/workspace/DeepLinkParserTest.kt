package com.tanaw.app.feature.workspace

import org.junit.Assert.*
import org.junit.Test

class DeepLinkParserTest {
    private val host = "test.supabase.co"
    private val validToken = "abcdefghijklmnopqrstuvwxyz12345"
    private val validAccessToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.token1234567890"
    private val validRefreshToken = "refresh_token_12345678901234567890"

    @Test
    fun parsesTanawCustomSchemeWithTokenHash() {
        val uri = "tanaw://auth/callback?token_hash=$validToken&type=recovery"
        val result = DeepLinkParser.parse(uri, host)
        assertTrue(result is DeepLinkResult.Otp)
        val otp = result as DeepLinkResult.Otp
        assertEquals("recovery", otp.type)
        assertEquals(validToken, otp.tokenHash)
        assertTrue(result.isRecovery)
        assertFalse(result.isSignup)
    }

    @Test
    fun parsesTanawCustomSchemeWithTokenForSignup() {
        val uri = "tanaw://auth/callback?token=$validToken&type=signup&email=user%40example.com"
        val result = DeepLinkParser.parse(uri, host)
        assertTrue(result is DeepLinkResult.Otp)
        val otp = result as DeepLinkResult.Otp
        assertEquals("signup", otp.type)
        assertEquals(validToken, otp.tokenHash)
        assertEquals("user@example.com", otp.email)
        assertFalse(result.isRecovery)
        assertTrue(result.isSignup)
    }

    @Test
    fun parsesImplicitFragmentSessionTokens() {
        val uri = "tanaw://auth/callback#access_token=$validAccessToken&refresh_token=$validRefreshToken&token_type=bearer&type=recovery"
        val result = DeepLinkParser.parse(uri, host)
        assertTrue(result is DeepLinkResult.Session)
        val session = result as DeepLinkResult.Session
        assertEquals("recovery", session.type)
        assertEquals(validAccessToken, session.accessToken)
        assertEquals(validRefreshToken, session.refreshToken)
        assertTrue(result.isRecovery)
    }

    @Test
    fun parsesDirectSupabaseVerifyUrl() {
        val uri = "https://$host/auth/v1/verify?token_hash=$validToken&type=recovery"
        val result = DeepLinkParser.parse(uri, host)
        assertTrue(result is DeepLinkResult.Otp)
        val otp = result as DeepLinkResult.Otp
        assertEquals("recovery", otp.type)
        assertEquals(validToken, otp.tokenHash)
    }

    @Test
    fun rejectsUntrustedHostForHttpsVerify() {
        val uri = "https://evil.example/auth/v1/verify?token=$validToken&type=recovery"
        val result = DeepLinkParser.parse(uri, host)
        assertEquals(DeepLinkResult.Invalid, result)
    }

    @Test
    fun rejectsInsecureHttpScheme() {
        val uri = "http://$host/auth/v1/verify?token=$validToken&type=recovery"
        val result = DeepLinkParser.parse(uri, host)
        assertEquals(DeepLinkResult.Invalid, result)
    }

    @Test
    fun rejectsWrongPathForSupabase() {
        val uri = "https://$host/other/path?token=$validToken&type=recovery"
        val result = DeepLinkParser.parse(uri, host)
        assertEquals(DeepLinkResult.Invalid, result)
    }

    @Test
    fun rejectsUntrustedCustomSchemeHostOrPath() {
        assertEquals(DeepLinkResult.Invalid, DeepLinkParser.parse("tanaw://evil/callback?token=$validToken", host))
        assertEquals(DeepLinkResult.Invalid, DeepLinkParser.parse("tanaw://auth/unsupported?token=$validToken", host))
        assertEquals(DeepLinkResult.Invalid, DeepLinkParser.parse("other://auth/callback?token=$validToken", host))
    }

    @Test
    fun parsesErrorQueryAndFragment() {
        val queryErrorUri = "tanaw://auth/callback?error=access_denied&error_description=Link+expired"
        val queryResult = DeepLinkParser.parse(queryErrorUri, host)
        assertTrue(queryResult is DeepLinkResult.Error)
        val error1 = queryResult as DeepLinkResult.Error
        assertEquals("access_denied", error1.error)
        assertEquals("Link expired", error1.description)

        val fragmentErrorUri = "tanaw://auth/callback#error=unauthorized_client&error_description=Invalid+token"
        val fragmentResult = DeepLinkParser.parse(fragmentErrorUri, host)
        assertTrue(fragmentResult is DeepLinkResult.Error)
        val error2 = fragmentResult as DeepLinkResult.Error
        assertEquals("unauthorized_client", error2.error)
        assertEquals("Invalid token", error2.description)
    }

    @Test
    fun parsesPkceAuthCode() {
        val uri = "tanaw://auth/callback?code=pkce_auth_code_1234567890&type=recovery"
        val result = DeepLinkParser.parse(uri, host)
        assertTrue(result is DeepLinkResult.Otp)
        val otp = result as DeepLinkResult.Otp
        assertEquals("pkce_auth_code_1234567890", otp.tokenHash)
        assertEquals("recovery", otp.type)
    }

    @Test
    fun rejectsMalformedAndEmptyInputs() {
        assertEquals(DeepLinkResult.Invalid, DeepLinkParser.parse("", host))
        assertEquals(DeepLinkResult.Invalid, DeepLinkParser.parse("   ", host))
        assertEquals(DeepLinkResult.Invalid, DeepLinkParser.parse(null, host))
        assertEquals(DeepLinkResult.Invalid, DeepLinkParser.parse("not a url :::", host))
    }
}
