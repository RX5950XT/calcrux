import com.android.build.gradle.internal.tasks.factory.dependsOn

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.hilt)
    // cargo-ndk: builds Rust .so for all ABIs and copies to jniLibs/
    id("com.github.willir.rust.cargo-ndk-android") version "0.3.4"
}

android {
    namespace = "com.calcrux"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.calcrux"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Filter to the four live ABIs. Prevents dead platforms (mips, mips64,
        // armeabi) bundled inside JNA's AAR from inflating the APK.
        ndk {
            abiFilters += setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true      // R8: shrinks + obfuscates DEX (65MB → ~9MB)
            isShrinkResources = true    // AAPT2: removes unused drawables / strings
            // Sign with debug keystore for local testing.
            // Replace with a real keystore (storeFile/storePassword/keyAlias/keyPassword) before Play Store.
            @Suppress("UNCHECKED_CAST")
            signingConfig = signingConfigs.getByName("debug") as com.android.build.api.dsl.ApkSigningConfig
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }

    // AGP 8.7 + Kotlin 2.0 has a known lint crash in NonNullableMutableLiveDataDetector.
    // Disable lint for release builds until AGP is updated.
    lint {
        checkReleaseBuilds = false
    }
}

// ── cargo-ndk ─────────────────────────────────────────────────────────────────
// Builds the Rust crate for all target ABIs.
cargoNdk {
    // Path to the Rust workspace (relative to android/ project root)
    module = ".."
    // Crate to build (name in Cargo.toml)
    librariesNames = arrayListOf("libcalcrux.so")
    // ABIs to target
    targets = arrayListOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
}

// ── UniFFI Kotlin bindings ────────────────────────────────────────────────────
// Generated from the Rust library.  Run:
//   ./gradlew generateUniFFIBindings
// to regenerate after changing the Rust API.
val generateUniFFIBindings by tasks.registering(Exec::class) {
    group = "uniffi"
    description = "Generate Kotlin bindings from the compiled Rust library"

    val libPath = layout.buildDirectory.file(
        "rustJniLibs/android/arm64-v8a/libcalcrux.so"
    )
    val outDir = layout.projectDirectory.dir(
        "src/main/java/com/calcrux/generated"
    )
    outputs.dir(outDir)
    inputs.file(libPath)

    commandLine(
        "cargo", "run", "--manifest-path",
        "../../Cargo.toml",
        "--bin", "uniffi-bindgen",
        "--",
        "generate",
        "--library", libPath.get().asFile.absolutePath,
        "--language", "kotlin",
        "--out-dir", outDir.asFile.absolutePath
    )
    dependsOn("buildCargoNdkDebug")
}

// ── dependencies ──────────────────────────────────────────────────────────────
dependencies {
    // Compose BOM
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.material)
    // JNA AAR is required for UniFFI — must use @aar to include libjnidispatch.so
    implementation("net.java.dev.jna:jna:5.14.0@aar")
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)

    // Lifecycle / ViewModel
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.ktx)

    // Room (history database)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore (preferences)
    implementation(libs.datastore.preferences)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Retrofit + Moshi (exchange rates)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.okhttp)
    implementation(libs.moshi.kotlin)

    // Kotlin coroutines
    implementation(libs.coroutines.android)

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
