package dev.maples.build

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

internal fun Project.configureCompose(commonExtension: CommonExtension) {
    commonExtension.buildFeatures.compose = true

    val libs: VersionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

    dependencies {
        add("implementation", platform(libs.findLibrary("compose.bom").get()))
        implementation(libs, "coil.compose")
        implementation(libs, "compose.ui")
        implementation(libs, "compose.foundation-android")
        implementation(libs, "compose.ui.graphics")
        implementation(libs, "compose.ui.tooling.preview")
        implementation(libs, "compose.material3")
        implementation(libs, "compose.drawablepainter")
        implementation(libs, "compose.unstyled.core")
        implementation(libs, "androidx.navigation.compose")
        implementation(libs, "koin.compose")
        implementation(libs, "koin.androidx.compose")
    }
}
