package com.tanaw.app.di

import android.content.Context
import com.tanaw.app.BuildConfig
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.exception.NoSessionFoundException
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.json.Json
import java.net.URI

/** Never import a session from another Supabase project, including the old SDK default. */
class ProjectSessionManager(context: Context) : SessionManager {
    private val preferences = context.getSharedPreferences("auth_${URI(BuildConfig.SUPABASE_URL).host}", Context.MODE_PRIVATE)
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    override suspend fun saveSession(session: UserSession) {
        check(preferences.edit().putString("session", json.encodeToString(UserSession.serializer(), session)).commit())
    }
    override suspend fun loadSession(): UserSession = preferences.getString("session", null)
        ?.let { json.decodeFromString(UserSession.serializer(), it) } ?: throw NoSessionFoundException()
    override suspend fun deleteSession() { check(preferences.edit().clear().commit()) }
}
