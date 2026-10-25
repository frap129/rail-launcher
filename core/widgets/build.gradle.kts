plugins {
    alias(libs.plugins.modular.android.library)
    alias(libs.plugins.modular.compose)
}

android {
    namespace = "core.widgets"
    testFixtures {
        enable = true
    }
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:data"))
    implementation(project(":core:util"))
    implementation(libs.icons.fontawesome)
    testImplementation(testFixtures(project(":core:data")))
    testFixturesImplementation(project(":core:ui"))
    testFixturesImplementation(testFixtures(project(":core:data")))
    testFixturesImplementation(libs.androidx.lifecycle.viewmodel.compose)
}
