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
        versionCode = 4
        versionName = "0.3.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
