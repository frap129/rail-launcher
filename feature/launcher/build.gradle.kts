plugins {
    alias(libs.plugins.modular.android.library)
    alias(libs.plugins.modular.compose)
}

android {
    namespace = "feature.launcher"
}
dependencies {
    implementation(libs.compose.activity)
    implementation(project(":core:ui"))
    implementation(project(":core:data"))
    implementation(project(":core:util"))
    implementation(project(":core:widgets"))
    implementation(project(":core:popups"))
    implementation(libs.icons.fontawesome)
    implementation(libs.reorderable)
    testImplementation(testFixtures(project(":core:data")))
    testImplementation(testFixtures(project(":core:popups")))
    testImplementation(testFixtures(project(":core:widgets")))
    testImplementation(libs.test.robolectric)
}
