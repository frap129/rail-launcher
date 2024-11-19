plugins {
    alias(libs.plugins.kotlinSymbolProcessor)
    alias(libs.plugins.modular.android.library)
}

android {
    namespace = "core.data"
}
dependencies {
    implementation(project(":core:util"))
    implementation(libs.androidx.datastore)
    implementation(libs.coil.core)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)
}
