plugins {
    alias(libs.plugins.modular.android.library)
}

android {
    namespace = "core.lifecycle"
}
dependencies {
    implementation(project(":feature:launcher"))
    implementation(project(":core:app-repo"))
    implementation(project(":core:prefs-repo"))
}
