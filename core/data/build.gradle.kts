plugins {
    alias(libs.plugins.modular.android.library)
}

android {
    namespace = "core.data"
}
dependencies {
    implementation(project(":core:util"))
    implementation(libs.androidx.datastore)
}
