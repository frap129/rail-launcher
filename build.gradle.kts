import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.dokka.gradle.DokkaTaskPartial

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.androidApplication) apply false
    // AGP 9 built-in Kotlin uses the KGP version on the buildscript classpath.
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.kotlinSymbolProcessor) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.dokka)
    alias(libs.plugins.androidTest) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.androidx.room) apply false
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    // Dokka configures Android source sets when it is applied, so apply it after the Android plugin.
    pluginManager.withPlugin("com.android.application") { apply(plugin = "org.jetbrains.dokka") }
    pluginManager.withPlugin("com.android.library") { apply(plugin = "org.jetbrains.dokka") }
    pluginManager.withPlugin("com.android.test") { apply(plugin = "org.jetbrains.dokka") }

    tasks.withType<DokkaTask>().configureEach {
        notCompatibleWithConfigurationCache("https://github.com/Kotlin/dokka/issues/2231")
    }

    // AGP 9's built-in Kotlin does not register Kotlin source sets with Dokka 1.x.
    tasks.withType<DokkaTaskPartial>().configureEach {
        notCompatibleWithConfigurationCache("https://github.com/Kotlin/dokka/issues/2231")
        dokkaSourceSets.maybeCreate("main").sourceRoots.from(project.file("src/main/java"), project.file("src/main/kotlin"))
    }
}

tasks.register("generateDocs") {}

tasks.getByName("generateDocs") {
    dependsOn(tasks.dokkaHtmlMultiModule)
    dependsOn(tasks.dokkaGfmMultiModule)
}

tasks.dokkaHtmlMultiModule {
    outputDirectory.set(rootProject.file("docs/html"))
}

tasks.dokkaGfmMultiModule {
    outputDirectory.set(rootProject.file("docs/markdown"))
}
