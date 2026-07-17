import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        // expect/actual classes are standard KMP usage; suppress the Beta warning
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    val xcf = XCFramework("ComposeApp")
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            xcf.add(this)
        }
    }

    sourceSets {
        // Android-specific source — existing code lives here initially
        androidMain {
            kotlin.srcDirs("src/main/java")
            dependencies {
                implementation("androidx.core:core-ktx:1.13.1")
                implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
                implementation("androidx.activity:activity-compose:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

                // Ktor Android engine
                implementation("io.ktor:ktor-client-okhttp:3.0.3")
                implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

                // CameraX
                implementation("androidx.camera:camera-camera2:1.3.3")
                implementation("androidx.camera:camera-lifecycle:1.3.3")
                implementation("androidx.camera:camera-view:1.3.3")

                // ML Kit
                implementation("com.google.mlkit:barcode-scanning:17.2.0")

                // ZXing
                implementation("com.google.zxing:core:3.5.3")

                // DataStore
                implementation("androidx.datastore:datastore-preferences:1.1.1")

                // Splash Screen
                implementation("androidx.core:core-splashscreen:1.0.1")

                // Coil SVG (Android only)
                implementation("io.coil-kt:coil-svg:2.6.0")
                implementation("com.caverock:androidsvg-aar:1.4")

                // FileProvider
                implementation("androidx.core:core:1.13.1")
            }
        }

        commonMain {
            dependencies {
                // Compose Multiplatform
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.components.resources)
                implementation(compose.components.uiToolingPreview)

                // Ktor (multiplatform)
                implementation("io.ktor:ktor-client-core:3.0.3")
                implementation("io.ktor:ktor-client-content-negotiation:3.0.3")
                implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")
                implementation("io.ktor:ktor-client-logging:3.0.3")

                // Serialization
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

                // Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

                // Date/time
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")

                // Multiplatform Settings
                implementation("com.russhwolf:multiplatform-settings:1.2.0")
                implementation("com.russhwolf:multiplatform-settings-coroutines:1.2.0")

                // Coil 3 (multiplatform)
                implementation("io.coil-kt.coil3:coil-compose:3.0.4")
                implementation("io.coil-kt.coil3:coil-network-ktor3:3.0.4")
                implementation("io.coil-kt.coil3:coil-svg:3.0.4")

                // Lifecycle / ViewModel
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.9.1")

                // Navigation (multiplatform, 2.8+)
                implementation("org.jetbrains.androidx.navigation:navigation-compose:2.9.2")

                // Multiplatform WebView (ORCID login)
                api("io.github.kevinnzou:compose-webview-multiplatform:2.0.3")

                // QR code scanning (EasyQRScan - KMP, handles lifecycle correctly)
                implementation("io.github.kalinjul.easyqrscan:scanner:0.7.1")

                // QR code display/generation (qr-kit painter only)
                implementation("network.chaintech:qr-kit:3.1.3")
            }
        }

        iosMain {
            dependencies {
                // Ktor iOS engine
                implementation("io.ktor:ktor-client-darwin:3.0.3")
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

compose.resources {
    packageOfResClass = "crucible.lens.composeapp.generated.resources"
}

// easyqrscan 0.7.1 was compiled against M3 1.4.x and pulls it in transitively.
// CMP 1.7.3 compiles against M3 1.3.x — the mangled Compose function names differ between
// 1.3.x and 1.4.x, so letting 1.4.x win at runtime would break our compiled code.
// Force 1.3.1 so only the version our code was compiled against is in the final APK.
// easyqrscan is therefore only used on iOS (its native stack has no AndroidX M3 dependency).
configurations.configureEach {
    resolutionStrategy {
        force("androidx.compose.material3:material3:1.3.1")
    }
}

android {
    namespace = "crucible.lens"
    compileSdk = 36

    defaultConfig {
        minSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "VERSION_NAME", "\"" + (project.findProperty("app.versionName") as? String ?: "0.0.0") + "\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}
