plugins {
    alias(libs.plugins.modular.android.library)
    alias(libs.plugins.modular.compose)
}

android {
    namespace = "feature.settings"
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:data"))
    implementation(libs.compose.material.icons.core)
    implementation(libs.icons.fontawesome)
    testImplementation(testFixtures(project(":core:data")))
}
