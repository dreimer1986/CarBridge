plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "org.carbridge"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.carbridge"
        minSdk = 35
        targetSdk = 36
        versionCode = 8
        versionName = "0.7.1"
    }

    // Leave out the Google-encrypted dependency block, which only Google can read
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildFeatures {
        compose = true
        aidl = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Release key lives outside the repo; set CARBRIDGE_* in ~/.gradle/gradle.properties
    val releaseStore = providers.gradleProperty("CARBRIDGE_STORE_FILE").orNull
    signingConfigs {
        if (releaseStore != null) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = providers.gradleProperty("CARBRIDGE_STORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("CARBRIDGE_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("CARBRIDGE_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        // Installs next to the release build, which is signed with a different key
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Without the release key (e.g. a fresh clone), fall back to debug signing for local tests
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        // Pre-release builds from the dev branch; install next to the release build as "CarBridge Dev"
        create("dev") {
            initWith(getByName("release"))
            applicationIdSuffix = ".dev"
            matchingFallbacks += "release"
        }
    }

    packaging {
        resources.excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*")
    }
}

kotlin {
    jvmToolchain(17)
}

// Each dev build needs a higher versionCode to update in place: -PdevBuild=<CI run number>
val devBuild = providers.gradleProperty("devBuild").map(String::toInt).orElse(0)
androidComponents {
    onVariants(selector().withBuildType("dev")) { variant ->
        variant.outputs.forEach { output ->
            output.versionCode.set(devBuild)
            output.versionName.set(devBuild.map { "${android.defaultConfig.versionName}-dev.$it" })
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation("io.github.reandroid:ARSCLib:1.4.0")
    implementation("com.android.tools.build:apksig:9.5.1")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.car.app:app:1.7.0")
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.withType<Test>().configureEach {
    // Optional real-APK fixture: ./gradlew :patcher:testDebugUnitTest -PpatchFixture=/path/app.apk
    project.findProperty("patchFixture")?.let { systemProperty("patchFixture", it) }
    // Mirror a phone's heap cap to catch out-of-memory patches: -PpatchHeap=256m
    project.findProperty("patchHeap")?.let { maxHeapSize = it.toString() }
    systemProperty("patchOut", layout.buildDirectory.file("patch-test/out.apk").get().asFile.path)
}
