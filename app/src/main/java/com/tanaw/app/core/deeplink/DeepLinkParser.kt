package com.tanaw.app.core.deeplink

import java.net.URI
import java.net.URLDecoder

sealed interface DeepLinkResult {
    data class Otp(
        val type: String,
        val tokenHash: String,
        val email: String? = null
    ) : DeepLinkResult

    data class Code(
        val type: String,
        val code: String,
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
        is Code -> type.equals("recovery", ignoreCase = true)
        is Session -> type.equals("recovery", ignoreCase = true)
        else -> false
    }

    val isSignup: Boolean get() = when (this) {
        is Otp -> type.equals("signup", ignoreCase = true)
        is Code -> type.equals("signup", ignoreCase = true)
        is Session -> type.equals("signup", ignoreCase = true)
        else -> false
    }
}

object DeepLinkParser {
    private val TOKEN_HASH_REGEX = Regex("^[A-Za-z0-9_-]{20,500}$")
    private val PKCE_CODE_REGEX = Regex("^[A-Za-z0-9._-]{8,500}$")

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
        val path = uri.path.orEmpty().trimEnd('/')

        val isCustomScheme = scheme == "tanaw" && host == "auth" && (path.equals("/callback", ignoreCase = true) || path.lowercase().startsWith("/callback/"))
        val isSupabaseVerify = scheme == "https" && projectHost.isNotBlank() &&
            host == projectHost.lowercase() && path.equals("/auth/v1/verify", ignoreCase = true)

        if (!isCustomScheme && !isSupabaseVerify) {
            return DeepLinkResult.Invalid
        }

        val queryParams = parseParams(uri.rawQuery)
        val fragmentParams = parseParams(uri.rawFragment)
        // Reject raw bearer tokens in URL query parameters; session tokens are only accepted in URL fragments.
        if ("access_token" in queryParams || "refresh_token" in queryParams) {
            return DeepLinkResult.Invalid
        }
        val allParams = queryParams + fragmentParams

        val error = allParams["error"] ?: allParams["error_code"]
        if (!error.isNullOrBlank()) {
            return DeepLinkResult.Error(
                error = error,
                description = allParams["error_description"] ?: error
            )
        }

        val type = allParams["type"].orEmpty().lowercase()

        // Case B: Session tokens in URL fragment only from implicit redirect
        val accessToken = fragmentParams["access_token"]
        val refreshToken = fragmentParams["refresh_token"]
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

        // Case C: Auth code (PKCE) — returned as distinct DeepLinkResult.Code, never as Otp tokenHash
        val code = allParams["code"]?.takeIf { PKCE_CODE_REGEX.matches(it) }
        if (code != null) {
            return DeepLinkResult.Code(
                type = type.ifBlank { "recovery" },
                code = code,
                email = allParams["email"]
            )
        }

        return DeepLinkResult.Invalid
    }

    /**
     * Decodes the payload of a JWT access token and extracts the `sub` and `email` claims
     * without trusting the token for authorization (used strictly to prevent cross-account
     * session overwrite before importing a recovery session).
     */
    fun extractJwtSubjectAndEmail(jwt: String): Pair<String?, String?> {
        val parts = jwt.split('.')
        if (parts.size != 3) return null to null
        val payloadJson = runCatching {
            val padded = parts[1].replace('-', '+').replace('_', '/').let { base64 ->
                when (base64.length % 4) {
                    2 -> "$base64=="
                    3 -> "$base64="
                    else -> base64
                }
            }
            String(java.util.Base64.getDecoder().decode(padded), Charsets.UTF_8)
        }.getOrNull() ?: return null to null

        val sub = Regex(""""sub"\s*:\s*"([^"]+)"""").find(payloadJson)?.groupValues?.getOrNull(1)
        val email = Regex(""""email"\s*:\s*"([^"]+)"""").find(payloadJson)?.groupValues?.getOrNull(1)
        return sub to email
    }

    private fun parseParams(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split('&').filter { it.isNotBlank() }.mapNotNull { item ->
            val pair = item.split('=', limit = 2)
            if (pair.size == 2) {
                runCatching {
                    URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair[1], "UTF-8")
                }.getOrNull()
            } else null
        }.toMap()
    }
}
