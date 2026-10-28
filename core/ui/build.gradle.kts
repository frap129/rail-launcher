plugins {
    alias(libs.plugins.modular.android.library)
    alias(libs.plugins.modular.compose)
}

android {
    namespace = "core.ui"
    testFixtures {
        enable = true
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:util"))
    implementation(libs.icons.fontawesome)
    implementation(libs.reorderable)
    testImplementation(testFixtures(project(":core:data")))
    // The compose compiler plugin also runs on testFixtures and needs the runtime.
    testFixturesImplementation(platform(libs.compose.bom))
    testFixturesImplementation(libs.compose.ui)
    testFixturesImplementation(testFixtures(project(":core:data")))
}
