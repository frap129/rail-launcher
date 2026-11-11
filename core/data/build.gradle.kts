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
    sourceSets {
        getByName("test") {
            resources.srcDir("$projectDir/schemas")
        }
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
    testFixturesImplementation(project(":core:util"))
    testFixturesImplementation(libs.androidx.room.runtime)
}
