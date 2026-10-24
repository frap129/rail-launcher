plugins {
    alias(libs.plugins.modular.android.library)
    alias(libs.plugins.modular.compose)
}

android {
    namespace = "core.popups"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":core:util"))
    implementation(project(":core:widgets"))
    testImplementation(testFixtures(project(":core:data")))
    testImplementation(libs.test.robolectric)
}
