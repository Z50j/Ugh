plugins {
    id("com.android.application")         // Essential for Android app
    id("org.jetbrains.kotlin.android")    // Correct way to apply Kotlin to an Android project
    id("org.jetbrains.kotlin.plugin.compose") // Kotlin Compose Compiler plugin
    id("com.google.devtools.ksp")         // For KSP (if you use it, like Room, Hilt, etc.)
}

android {
    namespace = "com.example.ugh"
    compileSdk = 35 // Target Android SDK 34

    defaultConfig {
        applicationId = "com.example.ugh"
        minSdk = 26 // Minimum Android SDK 24 (Android 7.0 Nougat)
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // Core Compose dependencies
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.material3)
    implementation(libs.androidx.activity.compose) // For ComponentActivity and rememberLauncherForActivityResult
    implementation(libs.androidx.lifecycle.runtime.ktx) // For viewModelScope
    implementation(libs.androidx.lifecycle.viewmodel.compose) // For by viewModels()

    // Image loading and GIF encoding (Glide)
    implementation(libs.glide)
    implementation(libs.gifencoder.integration)

    // Kotlin Coroutines for asynchronous operations
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.appcompat)

    // Test dependencies
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit.v130)
    androidTestImplementation(libs.androidx.espresso.core.v351)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)

    implementation(libs.accompanist.systemuicontroller)

    implementation(libs.androidx.core.splashscreen)
}