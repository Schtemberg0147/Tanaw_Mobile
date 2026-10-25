import java.util.Properties
import java.net.URI

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun requiredClientSetting(name: String): String {
    val value = (System.getenv(name) ?: localProperties.getProperty(name))?.trim()
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
}
val supabaseKey = requiredClientSetting("SUPABASE_ANON_KEY").also {
    require(it.startsWith("sb_publishable_")) {
        "SUPABASE_ANON_KEY must be a public sb_publishable_ key for the Android client."
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
