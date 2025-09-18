plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.dice_eye_app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.dice_eye_app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        // Add compiler arguments to help with compatibility
        freeCompilerArgs += listOf(
            "-Xjvm-default=all",
            "-opt-in=kotlin.RequiresOptIn"
        )
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Navigation - direct implementation with fixed version
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // CameraX - direct implementation with fixed versions
    implementation("androidx.camera:camera-core:1.3.0-beta02")
    implementation("androidx.camera:camera-camera2:1.3.0-beta02")
    implementation("androidx.camera:camera-lifecycle:1.3.0-beta02")
    implementation("androidx.camera:camera-view:1.3.0-beta02")
    implementation("androidx.camera:camera-extensions:1.3.0-beta02")

    // Lifecycle for camera
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.2")

    // Permissions handling
    implementation("com.google.accompanist:accompanist-permissions:0.30.1")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}