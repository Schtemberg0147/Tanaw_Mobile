package com.tanaw.app.feature.workspace

import java.net.URI
import java.net.URLDecoder

sealed interface DeepLinkResult {
    data class Otp(
        val type: String,
        val tokenHash: String,
        val email: String? = null
    ) : DeepLinkResult

    data class Session(
        val type: String,
        val accessToken: String,
        val refreshToken: String
    ) : DeepLinkResult

    data class Error(
        val error: String,
        val description: String? = null
    ) : DeepLinkResult

    object Invalid : DeepLinkResult

    val isRecovery: Boolean get() = when (this) {
        is Otp -> type.equals("recovery", ignoreCase = true)
        is Session -> type.equals("recovery", ignoreCase = true)
        else -> false
    }

    val isSignup: Boolean get() = when (this) {
        is Otp -> type.equals("signup", ignoreCase = true)
        is Session -> type.equals("signup", ignoreCase = true)
        else -> false
    }
}

object DeepLinkParser {
    private val TOKEN_HASH_REGEX = Regex("^[A-Za-z0-9_-]{20,500}$")

    fun parse(raw: String?, projectHost: String = ""): DeepLinkResult {
        if (raw.isNullOrBlank()) return DeepLinkResult.Invalid
        val trimmed = raw.trim()
        val uri = try {
            URI(trimmed)
        } catch (_: Exception) {
            return DeepLinkResult.Invalid
        }

        val scheme = uri.scheme?.lowercase() ?: return DeepLinkResult.Invalid
        val host = uri.host?.lowercase().orEmpty()
        val path = uri.path.orEmpty()

        val isCustomScheme = scheme == "tanaw" && host == "auth" && (path == "/callback" || path.startsWith("/callback/"))
        val isSupabaseVerify = scheme == "https" && projectHost.isNotBlank() &&
            host == projectHost.lowercase() && path == "/auth/v1/verify"

        if (!isCustomScheme && !isSupabaseVerify) {
            return DeepLinkResult.Invalid
        }

        val queryParams = parseParams(uri.rawQuery)
        val fragmentParams = parseParams(uri.rawFragment)
        val allParams = queryParams + fragmentParams

        val error = allParams["error"] ?: allParams["error_code"]
        if (!error.isNullOrBlank()) {
            return DeepLinkResult.Error(
                error = error,
                description = allParams["error_description"] ?: error
            )
        }

        val type = allParams["type"].orEmpty().lowercase()

        // Case B: Session tokens in fragment (or query) from implicit redirect
        val accessToken = allParams["access_token"]
        val refreshToken = allParams["refresh_token"]
        if (!accessToken.isNullOrBlank() && !refreshToken.isNullOrBlank()) {
            return DeepLinkResult.Session(
                type = type.ifBlank { "recovery" },
                accessToken = accessToken,
                refreshToken = refreshToken
            )
        }

        // Case A: token_hash or token OTP
        val tokenCandidate = allParams["token_hash"] ?: allParams["token"]
        val tokenHash = tokenCandidate?.takeIf { TOKEN_HASH_REGEX.matches(it) }
        if (!tokenHash.isNullOrBlank()) {
            return DeepLinkResult.Otp(
                type = type.ifBlank { "recovery" },
                tokenHash = tokenHash,
                email = allParams["email"]
            )
        }

        // Case C: Auth code (PKCE)
        val code = allParams["code"]?.takeIf { it.isNotBlank() }
        if (code != null) {
            return DeepLinkResult.Otp(
                type = type.ifBlank { "recovery" },
                tokenHash = code,
                email = allParams["email"]
            )
        }

        return DeepLinkResult.Invalid
    }

    private fun parseParams(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split('&').mapNotNull { item ->
            val pair = item.split('=', limit = 2)
            if (pair.size == 2) {
                runCatching {
                    URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair[1], "UTF-8")
                }.getOrNull()
            } else null
        }.toMap()
    }
}
