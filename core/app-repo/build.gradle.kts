plugins {
    alias(libs.plugins.modular.android.library)
}

android {
    namespace = "core.apprepo"
}
dependencies {
    implementation(project(":core:util"))
}
