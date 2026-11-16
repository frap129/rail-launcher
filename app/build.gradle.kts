plugins {
    alias(libs.plugins.modular.android.application)
    alias(libs.plugins.modular.compose)
    alias(libs.plugins.androidApplication)
}

android {
    namespace = "dev.maples.rail"

    defaultConfig {
        applicationId = "dev.maples.rail"
        versionCode = providers.gradleProperty("versionCode").map(String::toInt).getOrElse(1)
        versionName = providers.gradleProperty("versionName").getOrElse("0.1")
        targetSdk = 37
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }

        release {
            isMinifyEnabled = true
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
    implementation(project(":feature:settings"))
    implementation(libs.androidx.profileinstaller)
}
