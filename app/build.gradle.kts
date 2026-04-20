plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.voiceos"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.voiceos"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true        // ← Enable Jetpack Compose
        viewBinding = true    // ← Keep for FloatingWidgetService + MacrosActivity
    }

    composeOptions {
        // Must match Kotlin 1.9.23  →  compiler extension 1.5.13
        kotlinCompilerExtensionVersion = "1.5.13"
    }
}

dependencies {
    // AndroidX core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)                            // for MacrosActivity XML
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)

    // ── Jetpack Compose ─────────────────────────────────────────────
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.animation)

    // Activity + ViewModel integration
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // Compose tooling (debug only)
    debugImplementation(libs.compose.ui.tooling)

    // ── Networking (Retrofit, OkHttp, Gson) ─────────────────────────
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // ── WebSockets (Socket.IO) ──────────────────────────────────────
    implementation(libs.socketio.client)
}
