plugins {
    alias(libs.plugins.modular.android.library)
    alias(libs.plugins.modular.compose)
}

android {
    namespace = "feature.launcher"
    testOptions.unitTests.isIncludeAndroidResources = true
}
dependencies {
    implementation(libs.compose.activity)
    implementation(project(":core:ui"))
    implementation(project(":core:data"))
    implementation(project(":core:util"))
    implementation(libs.icons.fontawesome)
    implementation(libs.reorderable)
    testImplementation(testFixtures(project(":core:data")))
    testImplementation(testFixtures(project(":core:ui")))
}
