import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.medmate.personal"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.medmate.personal"
        minSdk = 26
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 13
        versionName = "0.9.1-local-study-drafts"
    }
    signingConfigs {
        getByName("debug") {
            // Existing user's private debug key is optional for upgrade continuity.
            // Public-source and CI builds use the Android Gradle Plugin's default
            // debug keystore; never ship the original signing key in GitHub.
            val continuityKey = rootProject.file("signing/medmate-debug.keystore")
            if (continuityKey.isFile) {
                storeFile = continuityKey
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    // Experimental, pinned MIT-licensed native llama.cpp AAR; ARM64 only, no bundled weights.
    implementation("dev.ffmpegkit-maintained:llama-android:0.1.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.robolectric:robolectric:4.16.1")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}

// Robolectric may resolve its matching Android runtime lazily during a test.
// Forward a CI/workspace HTTPS proxy when one is present; ordinary local builds are unchanged.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    System.getenv("HTTPS_PROXY")?.takeIf { it.isNotBlank() }?.let { value ->
        val proxy = URI(value)
        proxy.host?.let { host ->
            systemProperty("http.proxyHost", host)
            systemProperty("https.proxyHost", host)
        }
        if (proxy.port > 0) {
            systemProperty("http.proxyPort", proxy.port)
            systemProperty("https.proxyPort", proxy.port)
        }
    }
}
