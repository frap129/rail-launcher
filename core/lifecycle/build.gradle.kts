plugins {
    alias(libs.plugins.modular.android.library)
}

android {
    namespace = "core.lifecycle"
}
dependencies {
    implementation(project(":feature:launcher"))
    implementation(project(":core:data"))
    implementation(project(":feature:settings"))
}
