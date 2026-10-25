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
    implementation(libs.icons.fontawesome)
    implementation(libs.reorderable)
    testImplementation(testFixtures(project(":core:data")))
    testImplementation(testFixtures(project(":core:widgets")))
    testImplementation(libs.test.robolectric)
}
