plugins {
    alias(libs.plugins.modular.android.library)
    alias(libs.plugins.modular.compose)
}

android {
    namespace = "feature.settings"
}

dependencies {
    implementation(libs.compose.activity)
    implementation(project(":core:ui"))
    implementation(project(":core:data"))
    implementation(project(":core:util"))
    implementation(libs.compose.material.icons.core)
    implementation(libs.icons.fontawesome)
    testImplementation(testFixtures(project(":core:data")))
    testImplementation(libs.test.robolectric)
}
