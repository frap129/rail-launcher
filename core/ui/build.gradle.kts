plugins {
    alias(libs.plugins.modular.android.library)
    alias(libs.plugins.modular.compose)
}

android {
    namespace = "core.ui"
    testFixtures {
        enable = true
    }
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:util"))
    implementation(libs.compose.activity)
    implementation(libs.icons.fontawesome)
    implementation(libs.reorderable)
    testImplementation(testFixtures(project(":core:data")))
    testImplementation(testFixtures(project(":core:ui")))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.test.compose.ui.testManifest)
    // The compose compiler plugin also runs on testFixtures and needs the runtime.
    testFixturesImplementation(platform(libs.compose.bom))
    testFixturesImplementation(libs.compose.foundation.android)
    testFixturesImplementation(libs.compose.material3)
    testFixturesImplementation(libs.compose.ui)
    testFixturesImplementation(libs.compose.ui.test.junit4)
    testFixturesImplementation(testFixtures(project(":core:data")))
}
