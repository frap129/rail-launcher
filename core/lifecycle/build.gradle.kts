plugins {
    alias(libs.plugins.modular.android.library)
}

android {
    namespace = "core.lifecycle"
}
dependencies {
    implementation(project(":feature:launcher"))
    implementation(project(":core:ui"))
    implementation(project(":core:widgets"))
    implementation(project(":core:popups"))
    implementation(project(":core:data"))
    implementation(project(":core:util"))
    implementation(project(":feature:settings"))
    implementation(libs.coil)
    implementation(libs.androidx.room.ktx)
}
