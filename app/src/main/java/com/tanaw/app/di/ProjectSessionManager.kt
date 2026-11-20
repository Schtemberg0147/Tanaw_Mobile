package com.tanaw.app.di

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import androidx.core.content.edit
import com.tanaw.app.BuildConfig
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.exception.NoSessionFoundException
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware-backed Android Keystore AES-256-GCM session storage in `noBackupFilesDir`.
 * Never imports a session from another Supabase project, and automatically migrates/wipes
 * any legacy cleartext SharedPreferences file (`auth_<host>.xml`).
 */
class ProjectSessionManager(private val context: Context) : SessionManager {
    private val host = URI(BuildConfig.SUPABASE_URL).host.orEmpty()
    private val legacyPrefsName = "auth_$host"
    private val aad = "session:$host".toByteArray(Charsets.UTF_8)
    private val root = File(context.noBackupFilesDir, "auth-session").apply { mkdirs() }
    private val fileName = MessageDigest.getInstance("SHA-256")
        .digest(aad)
        .joinToString("") { "%02x".format(it) }
    private val sessionFile = AtomicFile(File(root, fileName))
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val mutex = Mutex()

    private fun key(): SecretKey = synchronized(KEYSTORE_LOCK) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)
            ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
                init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build()
                )
                generateKey()
            }
    }

    private fun encryptAndWriteLocked(rawJson: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key())
            updateAAD(aad)
        }
        val payload = cipher.iv + cipher.doFinal(rawJson.toByteArray(Charsets.UTF_8))
        require(payload.size <= MAX_SESSION_BYTES) { "Session payload exceeds limit." }
        val stream = sessionFile.startWrite()
        try {
            stream.write(payload)
            sessionFile.finishWrite(stream)
        } catch (e: Exception) {
            sessionFile.failWrite(stream)
            throw e
        }
    }

    private fun readAndDecryptLocked(): String? {
        if (!sessionFile.baseFile.exists() && !File(sessionFile.baseFile.path + ".bak").exists()) {
            return null
        }
        return runCatching {
            val bytes = sessionFile.openRead().use { input ->
                val buffer = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(4096)
                var total = 0
                while (true) {
                    val read = input.read(chunk)
                    if (read < 0) break
                    total += read
                    require(total <= MAX_SESSION_BYTES) { "Session file exceeds maximum size." }
                    buffer.write(chunk, 0, read)
                }
                buffer.toByteArray()
            }
            require(bytes.size > (GCM_IV_BYTES + GCM_TAG_BYTES)) { "Truncated session ciphertext." }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(
                    Cipher.DECRYPT_MODE,
                    key(),
                    GCMParameterSpec(GCM_TAG_BITS, bytes, 0, GCM_IV_BYTES),
                )
                updateAAD(aad)
            }
            String(cipher.doFinal(bytes, GCM_IV_BYTES, bytes.size - GCM_IV_BYTES), Charsets.UTF_8)
        }.getOrElse {
            sessionFile.delete()
            null
        }
    }

    private fun wipeLegacyCleartextLocked(): String? {
        val prefs = context.getSharedPreferences(legacyPrefsName, Context.MODE_PRIVATE)
        val legacyValue = prefs.getString("session", null)
        if (legacyValue != null || prefs.all.isNotEmpty()) {
            prefs.edit { clear() }
        }
        runCatching { context.deleteSharedPreferences(legacyPrefsName) }
        return legacyValue
    }

    override suspend fun saveSession(session: UserSession): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            val encoded = json.encodeToString(UserSession.serializer(), session)
            encryptAndWriteLocked(encoded)
            wipeLegacyCleartextLocked()
        }
    }

    override suspend fun loadSession(): UserSession = withContext(Dispatchers.IO) {
        mutex.withLock {
            val raw = readAndDecryptLocked() ?: wipeLegacyCleartextLocked()?.also { migrated ->
                runCatching { encryptAndWriteLocked(migrated) }
            } ?: throw NoSessionFoundException()

            runCatching {
                json.decodeFromString(UserSession.serializer(), raw)
            }.getOrElse {
                sessionFile.delete()
                throw NoSessionFoundException()
            }
        }
    }

    override suspend fun deleteSession(): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            sessionFile.delete()
            wipeLegacyCleartextLocked()
        }
    }

    private companion object {
        private const val KEY_ALIAS = "tanaw-auth-session-v1"
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BYTES = 16
        private const val GCM_TAG_BITS = 128
        private const val MAX_SESSION_BYTES = 256 * 1024
        private val KEYSTORE_LOCK = Any()
    }
}

