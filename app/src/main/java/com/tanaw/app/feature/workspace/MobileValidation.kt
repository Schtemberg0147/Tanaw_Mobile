package com.tanaw.app.feature.workspace

object MobileValidation {
    fun linkToken(raw: String, projectHost: String, type: String): String? = try {
        when (val result = DeepLinkParser.parse(raw, projectHost)) {
            is DeepLinkResult.Otp -> if (result.type.equals(type, ignoreCase = true)) result.tokenHash else null
            is DeepLinkResult.Session -> if (result.type.equals(type, ignoreCase = true)) result.accessToken else null
            else -> null
        }
    } catch (_: Exception) { null }
    fun phone(raw: String): String? {
        val cleaned = raw.trim().replace(Regex("[ ()-]"), "")
        val result = when {
            cleaned.matches(Regex("09[0-9]{9}")) -> "+63" + cleaned.drop(1)
            cleaned.matches(Regex("9[0-9]{9}")) -> "+63$cleaned"
            cleaned.matches(Regex("639[0-9]{9}")) -> "+$cleaned"
            else -> cleaned
        }
        return result.takeIf { it.matches(Regex("\\+639[0-9]{9}")) }
    }
    fun weight(raw: String): Double? = raw.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 && it <= 100000 }
    fun email(raw: String): Boolean = raw.trim().matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
    fun password(value: String, confirmation: String): Boolean = value.length >= 8 && value == confirmation
}
