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

    @Test
    fun parsesMixedCaseAndTrailingSlashes() {
        val mixedCustom = "TANAW://AUTH/CALLBACK/?token_hash=$validToken&type=RECOVERY"
        val res1 = DeepLinkParser.parse(mixedCustom, host)
        assertTrue(res1 is DeepLinkResult.Otp)
        assertEquals(validToken, (res1 as DeepLinkResult.Otp).tokenHash)
        assertTrue(res1.isRecovery)

        val mixedHttps = "HTTPS://$host/AUTH/V1/VERIFY/?token_hash=$validToken&type=SIGNUP"
        val res2 = DeepLinkParser.parse(mixedHttps, host)
        assertTrue(res2 is DeepLinkResult.Otp)
        assertEquals(validToken, (res2 as DeepLinkResult.Otp).tokenHash)
        assertTrue(res2.isSignup)
    }

    @Test
    fun parsesErrorWithoutDescriptionFallbackToError() {
        val uri = "tanaw://auth/callback?error=access_denied"
        val result = DeepLinkParser.parse(uri, host)
        assertTrue(result is DeepLinkResult.Error)
        val err = result as DeepLinkResult.Error
        assertEquals("access_denied", err.error)
        assertEquals("access_denied", err.description)
    }

    @Test
    fun handlesEmptyAndConsecutiveAmpersandsInParams() {
        val uri = "tanaw://auth/callback?&&token_hash=$validToken&&type=recovery&"
        val result = DeepLinkParser.parse(uri, host)
        assertTrue(result is DeepLinkResult.Otp)
        assertEquals(validToken, (result as DeepLinkResult.Otp).tokenHash)
    }

    @Test
    fun vulnerability_tokenHashLengthBoundaryChecks() {
        // 19 chars -> rejected (below 20 chars minimum)
        val shortToken = "a".repeat(19)
        assertEquals(DeepLinkResult.Invalid, DeepLinkParser.parse("tanaw://auth/callback?token_hash=$shortToken&type=recovery", host))

        // 20 chars -> accepted
        val minToken = "a".repeat(20)
        assertTrue(DeepLinkParser.parse("tanaw://auth/callback?token_hash=$minToken&type=recovery", host) is DeepLinkResult.Otp)

        // 500 chars -> accepted
        val maxToken = "a".repeat(500)
        assertTrue(DeepLinkParser.parse("tanaw://auth/callback?token_hash=$maxToken&type=recovery", host) is DeepLinkResult.Otp)

        // 501 chars -> rejected (exceeds 500 chars maximum)
        val excessToken = "a".repeat(501)
        assertEquals(DeepLinkResult.Invalid, DeepLinkParser.parse("tanaw://auth/callback?token_hash=$excessToken&type=recovery", host))
    }

    @Test
    fun vulnerability_rejectsSpecialCharactersAndScriptInjectionInTokens() {
        val tokensWithPayloads = listOf(
            "<script>alert(1)</script>",
            "token with spaces 1234567890",
            "token_with_quotes' OR '1'='1",
            "token_with_semicolon;DROP TABLE",
            "token!@#$%^&*()1234567890"
        )
        for (token in tokensWithPayloads) {
            val uri = "tanaw://auth/callback?token_hash=$token&type=recovery"
            assertEquals(DeepLinkResult.Invalid, DeepLinkParser.parse(uri, host))
        }
    }

    @Test
    fun vulnerability_rejectsSubdomainAndDomainSpoofing() {
        // Attacker attempting to prefix or suffix project host
        val spoofedHosts = listOf(
            "$host.attacker.com",
            "attacker-$host",
            "$host@attacker.com",
            "sub.$host"
        )
        for (spoofed in spoofedHosts) {
            val uri = "https://$spoofed/auth/v1/verify?token_hash=$validToken&type=recovery"
            assertEquals(DeepLinkResult.Invalid, DeepLinkParser.parse(uri, host))
        }
    }
}
