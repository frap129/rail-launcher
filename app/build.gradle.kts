plugins {
    alias(libs.plugins.modular.android.application)
    alias(libs.plugins.modular.compose)
}

android {
    namespace = "dev.maples.rail"

    defaultConfig {
        applicationId = "dev.maples.rail"
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    implementation(libs.compose.activity)
    implementation(project(":core:ui"))
    implementation(project(":core:lifecycle"))
    implementation(project(":feature:launcher"))
}
