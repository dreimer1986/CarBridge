// Loaded at runtime by the host's PayloadClassLoader, isolated from the app's classes, so the
// app's own Car App Library can't replace ours. Ships as an asset, not merged; see the host.
plugins {
    id("com.android.application")
}

android {
    namespace = "org.carbridge.extension.splitscreen.payload"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.carbridge.extension.splitscreen.payload"
        // Detached SurfaceTexture for the compositor
        minSdk = 26
        targetSdk = 36
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles("proguard-rules.pro")
        }
    }
}

dependencies {
    implementation("androidx.car.app:app:1.7.0")
    // The app's merged copy is used at runtime: Shizuku hands its binder to that one
    compileOnly("dev.rikka.shizuku:api:13.1.5")
}
