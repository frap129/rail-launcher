plugins {
    alias(libs.plugins.modular.android.library)
    alias(libs.plugins.modular.compose)
}

android {
    namespace = "core.widgets"
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:data"))
}
