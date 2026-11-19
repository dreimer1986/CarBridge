// The split-screen code merged into a car-enabled app, see core's SplitScreenPatch. Built as an
// app only to get a shrunk dex: CarBridge adds it to the copy, CarBridge Patches compiles these
// sources as a Morphe extension.
plugins {
    id("com.android.application")
}

android {
    namespace = "org.carbridge.extension.splitscreen"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.carbridge.extension.splitscreen"
        // InMemoryDexClassLoader for the payload; Shizuku needs 24
        minSdk = 26
        targetSdk = 36
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            // Shrink the bundled libraries; names must stay intact, see proguard-rules.pro
            isMinifyEnabled = true
            proguardFiles("proguard-rules.pro")
        }
    }
}

dependencies {
    // Shizuku already brings this version
    compileOnly("androidx.annotation:annotation:1.3.0")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
