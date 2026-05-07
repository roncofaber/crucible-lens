plugins {
    id("com.android.application")
}

// Same M3 version force as :composeApp — moko transitive deps pull in an older M3.
configurations.configureEach {
    resolutionStrategy {
        force("androidx.compose.material3:material3:1.3.1")
    }
}

android {
    namespace = "crucible.lens.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "crucible.lens"
        minSdk = 26
        targetSdk = 36
        versionCode = (project.findProperty("app.versionCode") as String).toInt()
        versionName = project.findProperty("app.versionName") as String
    }

    signingConfigs {
        create("release") {
            // Set these via environment variables or local.properties — never hard-code.
            // CI: export KEYSTORE_PATH, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD
            // Local: define them in local.properties (already git-ignored)
            val keystorePath = System.getenv("KEYSTORE_PATH")
                ?: project.findProperty("keystore.path") as String?
            val keystorePassword = System.getenv("KEYSTORE_PASSWORD")
                ?: project.findProperty("keystore.password") as String?
            val keyAlias = System.getenv("KEY_ALIAS")
                ?: project.findProperty("key.alias") as String?
            val keyPassword = System.getenv("KEY_PASSWORD")
                ?: project.findProperty("key.password") as String?

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
