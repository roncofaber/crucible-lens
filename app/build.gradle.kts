import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val versionNameProp = project.findProperty("app.versionName") as? String ?: "0.0.0"

// Generate AppBuildConfig.kt for build-time constants unavailable in the new KMP plugin
val generateAppBuildConfig by tasks.registering {
    val isDebug = gradle.startParameter.taskNames.any { it.contains("debug", ignoreCase = true) }
    val outputDir = layout.buildDirectory.dir("generated/appBuildConfig/kotlin")
    outputs.dir(outputDir)
    inputs.property("versionName", versionNameProp)
    inputs.property("isDebug", isDebug)
    doLast {
        val dir = outputDir.get().asFile.resolve("crucible/lens")
        dir.mkdirs()
        dir.resolve("AppBuildConfig.kt").writeText("""
package crucible.lens

internal object AppBuildConfig {
    const val VERSION_NAME: String = "$versionNameProp"
    const val DEBUG: Boolean = $isDebug
}
""".trimIndent())
    }
}

kotlin {
    android {
        namespace = "crucible.lens"
        compileSdk = 36
        minSdk = 26

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }

        androidResources {
            enable = true
        }

        withHostTestBuilder {}.configure {}
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
        androidMain {
            kotlin.srcDirs(generateAppBuildConfig)
            dependencies {
                implementation(libs.androidx.core.ktx)
                implementation(libs.androidx.lifecycle.runtime.ktx)
                implementation(libs.androidx.activity.compose)
                implementation(libs.kotlinx.coroutines.android)

                // Ktor Android engine
                implementation(libs.ktor.client.okhttp)
                implementation(libs.okhttp.logging.interceptor)

                // CameraX
                implementation(libs.androidx.camera.camera2)
                implementation(libs.androidx.camera.lifecycle)
                implementation(libs.androidx.camera.view)

                // ML Kit
                implementation(libs.mlkit.barcode.scanning)

                // ZXing
                implementation(libs.zxing.core)

                // DataStore
                implementation(libs.androidx.datastore.preferences)

                // Splash Screen
                implementation(libs.androidx.core.splashscreen)

                // Coil SVG (Android only)
                implementation(libs.coil.svg.v2)
                implementation(libs.androidsvg.aar)

                // FileProvider
                implementation(libs.androidx.core)
            }
        }

        commonMain {
            dependencies {
                // Compose Multiplatform
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.components.resources)
                implementation(compose.components.uiToolingPreview)

                // Ktor (multiplatform)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.ktor.client.logging)

                // Serialization
                implementation(libs.kotlinx.serialization.json)

                // Coroutines
                implementation(libs.kotlinx.coroutines.core)

                // Date/time
                implementation(libs.kotlinx.datetime)

                // Multiplatform Settings
                implementation(libs.multiplatform.settings)
                implementation(libs.multiplatform.settings.coroutines)

                // Coil 3 (multiplatform)
                implementation(libs.coil3.compose)
                implementation(libs.coil3.network.ktor3)
                implementation(libs.coil3.svg)

                // Lifecycle / ViewModel
                implementation(libs.androidx.lifecycle.viewmodel.compose)
                implementation(libs.androidx.lifecycle.runtime.compose)

                // Navigation (multiplatform, 2.8+)
                implementation(libs.androidx.navigation.compose)

                // Multiplatform WebView (ORCID login)
                api(libs.compose.webview.multiplatform)

                // QR code scanning (EasyQRScan - KMP, handles lifecycle correctly)
                implementation(libs.easyqrscan.scanner)

                // QR code display/generation (qr-kit painter only)
                implementation(libs.qr.kit)

                // Dependency injection
                implementation(libs.koin.core)
                implementation(libs.koin.compose)
                implementation(libs.koin.compose.viewmodel)
            }
        }

        iosMain {
            dependencies {
                // Ktor iOS engine
                implementation(libs.ktor.client.darwin)
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

compose.resources {
    packageOfResClass = "crucible.lens.composeapp.generated.resources"
}
