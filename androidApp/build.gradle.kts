import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// local.properties is not auto-exposed via project.findProperty() (that only reads Gradle
// properties — gradle.properties, -P flags, ~/.gradle/gradle.properties). It must be parsed
// explicitly to read arbitrary custom keys like the ones below.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "crucible.lens.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "gov.lbl.crucible"
        minSdk = 26
        targetSdk = 36
        versionCode = (project.findProperty("app.versionCode") as String).toInt()
        versionName = project.findProperty("app.versionName") as String
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        create("release") {
            // Set these via environment variables or local.properties — never hard-code.
            // CI: export KEYSTORE_PATH, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD
            // Local: define them in local.properties (already git-ignored)
            val keystorePath = System.getenv("KEYSTORE_PATH")
                ?: localProperties.getProperty("keystore.path")
            val keystorePassword = System.getenv("KEYSTORE_PASSWORD")
                ?: localProperties.getProperty("keystore.password")
            val keyAlias = System.getenv("KEY_ALIAS")
                ?: localProperties.getProperty("key.alias")
            val keyPassword = System.getenv("KEY_PASSWORD")
                ?: localProperties.getProperty("key.password")

            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) {
                signingConfig = releaseSigning
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":composeApp"))
}
