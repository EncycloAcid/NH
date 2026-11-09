import java.util.Properties
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeFeatureFlag

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.nh.hoopscoach"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nh.hoopscoach.pro"
        minSdk = 26
        targetSdk = 35
        versionCode = 132
        versionName = "3.4-pro.111"
    }

    // Release signing comes from keystore.properties (kept out of git) or from
    // HD_KEYSTORE* environment variables in CI. Without either, release builds
    // fall back to the debug key so the project still builds anywhere.
    val keyProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    val keyPath = System.getenv("HD_KEYSTORE") ?: keyProps.getProperty("storeFile")
    val hasReleaseKey = keyPath != null && file(keyPath).exists()
    signingConfigs {
        if (hasReleaseKey) create("release") {
            storeFile = file(keyPath!!)
            storePassword = System.getenv("HD_KEYSTORE_PASSWORD") ?: keyProps.getProperty("storePassword")
            keyAlias = System.getenv("HD_KEY_ALIAS") ?: keyProps.getProperty("keyAlias")
            keyPassword = System.getenv("HD_KEY_PASSWORD") ?: keyProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (hasReleaseKey) "release" else "debug")
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
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.maxHeapSize = "3g"
                it.systemProperty("screenshots.dir", rootProject.file("build/screenshots").absolutePath)
            }
        }
    }
}

// Screens read game state that lives in plain (non-Compose) objects, so they must
// recompose whenever their parent does instead of being skipped.
composeCompiler {
    featureFlags = setOf(ComposeFeatureFlag.StrongSkipping.disabled())
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation(composeBom)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
