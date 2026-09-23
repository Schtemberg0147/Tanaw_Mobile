import java.util.Properties

val localProperties = Properties().apply {
    load(rootProject.file("local.properties").inputStream())
}

plugins {
    // 1. Android application initializes everything and auto-injects Kotlin natively under AGP 9.0+
    alias(libs.plugins.android.application)

    // 2. Load the specific Jetpack Compose compiler compiler
    alias(libs.plugins.kotlin.compose)

    // 3. Annotation processors and Hilt tools go last
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.dagger.hilt.android)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.tanaw.app"
    compileSdk = 36 // Required for stable compatibility with AGP 9+ dependencies

    defaultConfig {
        applicationId = "com.tanaw.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
    defaultConfig {
        buildConfigField("String", "SUPABASE_URL", "\"${localProperties["SUPABASE_URL"]}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProperties["SUPABASE_ANON_KEY"]}\"")
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    // Hilt Dependencies
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.1")
    implementation("androidx.compose.material:material-icons-extended")
    // Supabase Dependencies
    implementation(platform("io.github.jan-tennert.supabase:bom:3.7.0"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.ktor:ktor-client-android:3.5.2")

    // ViewModel + Compose integration
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.4")
    implementation("androidx.hilt:hilt-navigation-compose:1.4.0")

    // Base Compose UI Dependencies from your TOML catalog
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx) // <-- CHANGE DASH TO DOT HERE
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // Testing Blocks
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4) // <-- CHANGE DASHES TO DOTS HERE
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)    // <-- CHANGE DASH TO DOT HERE
}
