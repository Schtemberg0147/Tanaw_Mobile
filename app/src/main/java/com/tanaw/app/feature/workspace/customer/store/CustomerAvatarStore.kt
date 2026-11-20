package com.tanaw.app.feature.workspace.customer.store

import android.content.Context
import com.tanaw.app.feature.workspace.customer.rules.CustomerAvatarRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Local per-account profile photo cache so customer profile photos load immediately
 * across app restarts and offline sessions, while syncing with `public.users.avatar_url`.
 */
class CustomerAvatarStore(private val context: Context) {

    private fun avatarFile(userId: String): File {
        val safeId = userId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifBlank { "default" }
        val dir = File(context.filesDir, "avatars").apply { mkdirs() }
        return File(dir, "avatar_$safeId.jpg")
    }

    fun readBytes(userId: String): ByteArray? {
        if (userId.isBlank()) return null
        val file = avatarFile(userId)
        val length = file.length()
        if (!file.exists() || !file.isFile || length <= 0L || length > CustomerAvatarRules.MAX_ENCODED_AVATAR_BYTES) return null
        return runCatching { file.readBytes() }.getOrNull()
    }

    suspend fun readBytesAsync(userId: String): ByteArray? = withContext(Dispatchers.IO) {
        readBytes(userId)
    }

    fun readDataUri(userId: String): String? {
        val bytes = readBytes(userId) ?: return null
        return runCatching { CustomerAvatarRules.toDataUri(bytes) }.getOrNull()
    }

    suspend fun writeBytesAsync(userId: String, jpegBytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        if (userId.isBlank() || jpegBytes.isEmpty() || jpegBytes.size > CustomerAvatarRules.MAX_ENCODED_AVATAR_BYTES) return@withContext false
        runCatching {
            avatarFile(userId).writeBytes(jpegBytes)
            true
        }.getOrDefault(false)
    }

    fun clear(userId: String) {
        if (userId.isBlank()) return
        runCatching { avatarFile(userId).delete() }
    }

    suspend fun clearAsync(userId: String) = withContext(Dispatchers.IO) {
        clear(userId)
    }
}
