plugins {
    alias(libs.plugins.modular.android.library)
    alias(libs.plugins.modular.compose)
}

android {
    namespace = "feature.launcher"
}
dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:app-repo"))
}
