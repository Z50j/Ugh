import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")         // Essential for Android app
    id("org.jetbrains.kotlin.android")    // Correct way to apply Kotlin to an Android project
    id("org.jetbrains.kotlin.plugin.compose") // Kotlin Compose Compiler plugin
    id("kotlin-kapt")                     // For Kapt (Glide annotation processor)
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
    composeOptions {
        kotlinCompilerExtensionVersion = "2.2.0" // Ensure this matches your Compose version
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core Compose dependencies
    implementation(platform("androidx.compose:compose-bom:2025.07.00")) // Use the latest stable BOM
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation(libs.androidx.activity.compose) // For ComponentActivity and rememberLauncherForActivityResult
    implementation(libs.androidx.lifecycle.runtime.ktx) // For viewModelScope
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2") // For by viewModels()

    // Image loading and GIF encoding (Glide)
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("com.github.bumptech.glide:gifencoder-integration:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0")

    // Kotlin Coroutines for asynchronous operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Test dependencies
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.08.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("com.google.accompanist:accompanist-systemuicontroller:0.36.0") // Check for the latest stable version if needed, as of July 2025 this or newer should be available.
}