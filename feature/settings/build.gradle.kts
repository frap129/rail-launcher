plugins {
    alias(libs.plugins.modular.android.library)
    alias(libs.plugins.modular.compose)
}

android {
    namespace = "feature.settings"
}
dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:prefs-repo"))
}
