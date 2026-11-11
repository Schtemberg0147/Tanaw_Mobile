package com.tanaw.app.di

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.realtime.Realtime
import io.ktor.client.engine.HttpClientEngineCapability
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.plugins.websocket.WebSocketCapability
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseEngineCapabilityTest {

    @Test
    fun okHttpEngineSupportsWebSocketAndTimeoutCapabilities() {
        val engine = OkHttp.create()
        val capabilities: Set<HttpClientEngineCapability<*>> = engine.supportedCapabilities

        // D01-B1 regression guard: ktor-client-android failed because it lacked WebSocketCapability
        assertTrue(
            "OkHttp engine must support WebSocketCapability for Supabase Realtime",
            capabilities.contains(WebSocketCapability)
        )
        assertTrue(
            "OkHttp engine must support HttpTimeoutCapability for configurable per-request timeouts",
            capabilities.contains(HttpTimeoutCapability)
        )
    }

    @Test
    fun supabaseClientInstallsRealtimePluginWithOkHttpEngine() {
        val client = createSupabaseClient(
            supabaseUrl = "https://example.supabase.co",
            supabaseKey = "sb_publishable_test_key"
        ) {
            httpEngine = OkHttp.create()
            install(Realtime)
        }

        assertNotNull(client)
        assertNotNull(client.pluginManager.getPluginOrNull(Realtime))
    }
}
