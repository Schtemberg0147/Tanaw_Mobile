import java.util.Properties
import java.net.URI
import groovy.json.JsonSlurper

// Public shared backend settings travel with the repository; SDK paths and secrets do not.
val sharedClientProperties = Properties().apply {
    rootProject.file("client-config.properties").inputStream().use { load(it) }
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun requiredClientSetting(name: String): String {
    val value = (System.getenv(name) ?: localProperties.getProperty(name)
        ?: sharedClientProperties.getProperty(name))?.trim()
    require(!value.isNullOrBlank() && !value.equals("null", ignoreCase = true)) {
        "Missing $name. Set it in local.properties or the environment before syncing Gradle."
    }
    require(value.none { it == '\n' || it == '\r' || it == '"' || it == '\\' }) {
        "Invalid characters in $name."
    }
    return value
}

val supabaseUrl = requiredClientSetting("SUPABASE_URL").also {
    val uri = URI(it)
    require(uri.scheme == "https" && !uri.host.isNullOrBlank()) {
        "SUPABASE_URL must be a valid HTTPS project URL."
    }
    require(it == sharedClientProperties.getProperty("SUPABASE_URL")) {
        "Wrong Tanaw backend: ${uri.host}. This branch uses the shared Tanaw Dev project. " +
            "Remove stale SUPABASE_URL and SUPABASE_ANON_KEY overrides from local.properties/environment, " +
            "sync Gradle and rebuild. See README.md."
    }
}
val supabaseKey = requiredClientSetting("SUPABASE_ANON_KEY").also {
    require(it.startsWith("sb_publishable_")) {
        "SUPABASE_ANON_KEY must be a public sb_publishable_ key for the Android client."
    }
    require(it == sharedClientProperties.getProperty("SUPABASE_ANON_KEY")) {
        "Tanaw Dev client key does not match this branch. Remove stale SUPABASE_ANON_KEY overrides " +
            "from local.properties/environment, sync Gradle and rebuild. See README.md."
    }
}

tasks.register("verifyPhoneSignup") {
    group = "verification"
    description = "Read the configured backend's public phone-signup settings without creating an account or sending SMS."
    doLast {
        val connection = URI("$supabaseUrl/auth/v1/settings").toURL().openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.setRequestProperty("apikey", supabaseKey)
        try {
            check(connection.responseCode == 200) { "Cannot read public Auth settings for ${URI(supabaseUrl).host}; HTTP ${connection.responseCode}." }
            val settings = connection.inputStream.bufferedReader().use { JsonSlurper().parseText(it.readText()) } as Map<*, *>
            val external = settings["external"] as? Map<*, *>
            check(external?.get("phone") == true && settings["disable_signup"] != true) {
                "Phone signup is disabled on ${URI(supabaseUrl).host}. The project administrator must check Auth providers."
            }
            println("Tanaw backend: ${URI(supabaseUrl).host}; phone signup enabled. No signup or SMS request sent.")
        } finally { connection.disconnect() }
    }
}

// Public Web OAuth client ID; the client secret belongs only in Supabase.
val googleWebClientId = (System.getenv("GOOGLE_WEB_CLIENT_ID")
    ?: localProperties.getProperty("GOOGLE_WEB_CLIENT_ID", "")).trim().also {
    require(it.isEmpty() || it.matches(Regex("[A-Za-z0-9_-]+\\.apps\\.googleusercontent\\.com"))) {
        "GOOGLE_WEB_CLIENT_ID must be a Google Web OAuth client ID."
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.dagger.hilt.android)
}

android {
    namespace = "com.tanaw.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.tanaw.app"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseKey\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
        buildConfigField("String", "MAPTILER_KEY", "\"${localProperties.getProperty("MAPTILER_KEY", "").trim()}\"")
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.2.1")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    // Hilt & KSP Metadata
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Compose extras
    implementation(libs.androidx.compose.material.icons.extended)

    // Supabase
    implementation(platform(libs.bom))
    implementation(libs.auth.kt)
    implementation(libs.postgrest.kt)
    implementation("io.github.jan-tennert.supabase:storage-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation("org.maplibre.gl:android-sdk:13.7.1")
    implementation(libs.ktor.client.android)

    // Lifecycle / ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.hilt.navigation.compose)

    // Core / Activity
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.play.services.location)
    implementation(libs.maplibre.android.sdk)

    // Compose BOM + UI
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    // Instrumented tests
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    // Debug-only tooling
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
