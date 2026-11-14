import java.util.Properties
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeFeatureFlag

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.application")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    // Compile against JDK 17 APIs even when the agent/CI host is on a newer JDK,
    // so calls like SortedSet.reversed() (Java 21) are not linked by mistake.
    jvmToolchain(17)

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("androidx.activity:activity-compose:1.9.3")
                implementation("androidx.core:core-ktx:1.15.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
            }
        }
        val desktopMain by getting {
            // School logos + character sprites live in the Android assets folder; reuse them on PC.
            resources.srcDir("src/main/assets")
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
            }
        }
        val androidUnitTest by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation("junit:junit:4.13.2")
                implementation("org.robolectric:robolectric:4.14.1")
                implementation("androidx.compose.ui:ui-test-junit4:1.7.6")
                implementation("androidx.compose.ui:ui-test-manifest:1.7.6")
            }
        }
    }
}

android {
    namespace = "com.nh.hoopscoach"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nh.hoopscoach.pro"
        minSdk = 26
        targetSdk = 35
        versionCode = 214
        versionName = "3.4-pro.193"
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
    buildFeatures {
        compose = true
    }
    // Lifecycle lint detector crashes against Kotlin 2.1 analysis APIs used by CMP.
    lint {
        disable += "NullSafeMutableLiveData"
        checkReleaseBuilds = false
    }
    sourceSets["main"].apply {
        manifest.srcFile("src/main/AndroidManifest.xml")
        res.srcDirs("src/main/res")
        resources.srcDirs("src/commonMain/resources")
        resources.srcDirs("src/main/resources")
        assets.srcDirs("src/main/assets")
    }
    // Unit tests stay in the classic AGP location.
    sourceSets["test"].java.srcDirs("src/test/java")
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

compose.desktop {
    application {
        mainClass = "com.nh.hoopscoach.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb)
            packageName = "HoopsDynasty"
            // jpackage requires a numeric dotted version; UI still shows 3.4-pro.193 via PlatformContext.
            packageVersion = "3.4.193"
            description = "College basketball coaching dynasty (Pro)"
            vendor = "DeadBunny Studios"
            copyright = "© 2026 DeadBunny Studios"
            linux {
                packageName = "hoops-dynasty"
            }
            windows {
                menuGroup = "Hoops Dynasty"
                upgradeUuid = "b8d4f0a2-5c3e-4f9b-ad2e-7e6f5c4b3d21"
                shortcut = true
                dirChooser = true
                perUserInstall = true
            }
            macOS {
                bundleID = "com.nh.hoopscoach.pro"
            }
        }
    }
}
