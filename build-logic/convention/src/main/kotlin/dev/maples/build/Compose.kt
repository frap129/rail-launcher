package dev.maples.build

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

private fun KotlinCompile.isTestCompilation(): Boolean =
    name.endsWith("UnitTestKotlin") || name.endsWith("AndroidTestKotlin")

internal fun Project.configureCompose(commonExtension: CommonExtension) {
    commonExtension.buildFeatures.compose = true

    val libs: VersionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

    extensions.configure<ComposeCompilerGradlePluginExtension> {
        stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("build-logic/compose_stability.conf"))
    }

    // Report names are module-based, so test compilations would overwrite the main report.
    tasks.withType<KotlinCompile>().configureEach {
        if (!isTestCompilation()) {
            compilerOptions.freeCompilerArgs.addAll(
                "-P",
                "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=" +
                    layout.buildDirectory.dir("compose_compiler").get().asFile.absolutePath
            )
        }
    }

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
