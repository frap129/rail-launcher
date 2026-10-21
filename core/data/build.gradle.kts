plugins {
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.kotlinSymbolProcessor)
    alias(libs.plugins.modular.android.library)
}

android {
    namespace = "core.data"
    testFixtures {
        enable = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":core:util"))
    implementation(libs.coil.core)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)
    testImplementation(libs.test.robolectric)
    testFixturesImplementation(libs.kotlinx.coroutines.core)
}
