plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Read version from git tag when building for release; fall back to a dev version for local builds.
// Tag format: v<major>.<minor>.<patch> (e.g. v1.2.3). Set by CI as RELEASE_VERSION env var.
fun versionFromTag(): Pair<String, Int> {
    val tag = System.getenv("RELEASE_VERSION")
        ?: return "1.0.0-dev" to 1
    val semver = tag.removePrefix("v")
    val (major, minor, patch) = semver.split(".").map { it.toInt() }
    require(major in 0..99 && minor in 0..99 && patch in 0..99) {
        "Version components must be 0–99 (got $semver)"
    }
    val code = major * 10_000 + minor * 100 + patch
    return semver to code
}

val (appVersionName, appVersionCode) = versionFromTag()

android {
    namespace = "com.princeyadav.grayout"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.princeyadav.grayout"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            // Populated from env vars in CI; left null locally so release builds fail fast
            // instead of silently producing an unsigned or debug-signed APK.
            storeFile = System.getenv("SIGNING_KEYSTORE_PATH")?.let { file(it) }
            storePassword = System.getenv("SIGNING_STORE_PASSWORD")
            keyAlias = System.getenv("SIGNING_KEY_ALIAS")
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

tasks.register("preReleaseCheck") {
    group = "verification"
    description = "Run all automated tests before release. Requires a connected device/emulator for instrumentation tests."
    dependsOn("testDebugUnitTest", "connectedDebugAndroidTest")
    doLast {
        println("")
        println("✓ Automated tests passed.")
        println("→ Next: walk docs/superpowers/specs/2026-04-11-testing-manual-checklist.md on target device.")
        println("  Tier A is mandatory. Tier B is required if you touched the relevant area this release.")
    }
}