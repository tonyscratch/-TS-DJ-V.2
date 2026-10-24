plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.tonyscratch.tsdj"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tonyscratch.tsdj"
        // Android 13 or newer. The Galaxy S23 Ultra shipped with Android 13.
        minSdk = 33
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
    }

    // One fixed signing key for every build, so the app's fingerprint never changes.
    // Google sign-in for YouTube needs this fingerprint (see README, "YouTube setup").
    // This key is for testing on your own phone. Keep the project private.
    signingConfigs {
        create("tsdj") {
            storeFile = file("tsdj-dev.jks")
            storePassword = "tsdjdev123"
            keyAlias = "tsdj"
            keyPassword = "tsdjdev123"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("tsdj")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("tsdj")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Google sign-in (authorization) for reading your YouTube playlists.
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // YouTube's official web player wrapped for Android (handles the embed rules).
    implementation("com.pierfrancescosoffritti.androidyoutubeplayer:core:13.0.0")
}
