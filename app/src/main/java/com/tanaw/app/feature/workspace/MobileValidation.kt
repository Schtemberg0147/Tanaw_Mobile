package com.tanaw.app.feature.workspace

object MobileValidation {
    fun linkToken(raw: String, projectHost: String, type: String): String? = try {
        val uri=java.net.URI(raw.trim())
        val query=uri.rawQuery.orEmpty().split('&').mapNotNull { item ->
            val pair=item.split('=',limit=2)
            if(pair.size==2) java.net.URLDecoder.decode(pair[0],"UTF-8") to java.net.URLDecoder.decode(pair[1],"UTF-8") else null
        }.toMap()
        if(uri.scheme!="https" || uri.host!=projectHost || uri.path!="/auth/v1/verify" || query["type"]!=type) null
        else (query["token_hash"] ?: query["token"])?.takeIf{it.matches(Regex("[A-Za-z0-9_-]{20,500}"))}
    } catch (_: Exception) { null }
    fun phone(raw: String): String? {
        val cleaned = raw.trim().replace(Regex("[ ()-]"), "")
        val result = when {
            cleaned.matches(Regex("09[0-9]{9}")) -> "+63" + cleaned.drop(1)
            cleaned.matches(Regex("9[0-9]{9}")) -> "+63$cleaned"
            else -> cleaned
        }
        return result.takeIf { it.matches(Regex("\\+639[0-9]{9}")) }
    }
    fun weight(raw: String): Double? = raw.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 && it <= 100000 }
    fun email(raw: String): Boolean = raw.trim().matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
    fun password(value: String, confirmation: String): Boolean = value.length >= 8 && value == confirmation
}
