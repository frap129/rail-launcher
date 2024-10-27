plugins {
    alias(libs.plugins.modular.android.library)
}

android {
    namespace = "core.prefsrepo"
}

dependencies {
    implementation(libs.androidx.datastore)
}
