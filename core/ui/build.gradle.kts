plugins {
    alias(libs.plugins.modular.android.library)
    alias(libs.plugins.modular.compose)
}

android {
    namespace = "core.ui"
}

dependencies {
    implementation(project(":core:data"))
}
