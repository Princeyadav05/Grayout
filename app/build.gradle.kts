plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

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
        // Bump both in a release commit before tagging (see RELEASING.md). F-Droid reads these
        // literals from the tagged source, so they must stay plain numbers and strings here.
        versionCode = 10403
        versionName = "1.4.3"

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
            isMinifyEnabled = true
            isShrinkResources = true
            // Android entry points and AndroidX reflection use the platform/library rules.
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Keep this line and the signingConfigs block in this exact shape: F-Droid's build
            // server removes both with a line regex before building, then signs with its own key.
            signingConfig = signingConfigs.getByName("release")
        }
    }
    // The dependency-info block is encrypted for Google only; F-Droid and Izzy scanners flag it.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

// CI sets RELEASE_VERSION to the pushed tag. Refuse to build a release whose tag does not
// match the committed version, or whose versionCode breaks the M*10000 + m*100 + p scheme.
System.getenv("RELEASE_VERSION")?.let { tag ->
    val name = checkNotNull(android.defaultConfig.versionName)
    val code = android.defaultConfig.versionCode
    require(tag == "v$name") { "Tag $tag does not match versionName $name. Bump app/build.gradle.kts first." }
    val (major, minor, patch) = name.split(".").map { it.toInt() }
    require(minor in 0..99 && patch in 0..99 && code == major * 10_000 + minor * 100 + patch) {
        "versionCode $code does not match versionName $name (expected M*10000 + m*100 + p)."
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
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material3)
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
        println("→ Next: build and smoke-test the optimized APK using RELEASING.md.")
    }
}
