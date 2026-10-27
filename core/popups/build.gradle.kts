plugins {
    alias(libs.plugins.modular.android.library)
    alias(libs.plugins.modular.compose)
}

android {
    namespace = "core.popups"
    testFixtures {
        enable = true
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":core:util"))
    implementation(libs.icons.fontawesome)
    implementation(libs.reorderable)
    testImplementation(testFixtures(project(":core:data")))
    testImplementation(libs.test.robolectric)
    // The compose compiler plugin also runs on testFixtures and needs the runtime.
    testFixturesImplementation(platform(libs.compose.bom))
    testFixturesImplementation(libs.compose.ui)
    testFixturesImplementation(project(":core:ui"))
    testFixturesImplementation(testFixtures(project(":core:data")))
    testFixturesImplementation(libs.kotlinx.coroutines.core)
    testFixturesImplementation(libs.test.junit)
    testFixturesImplementation(libs.test.kotlinx.coroutines.test)
}
