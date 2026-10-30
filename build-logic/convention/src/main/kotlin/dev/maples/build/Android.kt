package dev.maples.build

import androidx.baselineprofile.gradle.consumer.BaselineProfileConsumerExtension
import com.android.build.api.dsl.CommonExtension
import java.io.FileInputStream
import java.util.Properties
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.logging.LogLevel
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

private val javaVersion = JavaVersion.VERSION_17
private const val JVM_TOOLCHAIN_VERSION = 21
private const val COMPILE_SDK = 37
private const val MIN_SDK = 32
private const val UNIT_TEST_IMPLEMENTATION = "testImplementation"
private const val TEST_FIXTURES_IMPLEMENTATION = "testFixturesImplementation"
private const val MOCKK_ALIAS = "test.mockk"

// Robolectric's documented Java 17+ flag; API 36's ApplicationSharedMemory reads jdk.internal.access.
private const val ROBOLECTRIC_JVM_ARG = "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED"

private val sharedTestLibraryAliases = listOf(
    "test.junit",
    "test.kotlinx.coroutines.test",
    MOCKK_ALIAS,
)

private val sharedFixtureLibraryAliases = sharedTestLibraryAliases + listOf("kotlinx.coroutines.core", "util.timber")

private val unitTestLibraryAliases = sharedTestLibraryAliases + listOf(
    "test.robolectric",
    "test.turbine",
)

internal fun configureAndroid(target: Project, commonExtension: CommonExtension) {
    commonExtension.apply {
        compileSdk = COMPILE_SDK

        defaultConfig.apply {
            minSdk = MIN_SDK
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            vectorDrawables.useSupportLibrary = true
        }

        compileOptions.apply {
            sourceCompatibility = javaVersion
            targetCompatibility = javaVersion
        }

        buildFeatures.buildConfig = true

        testOptions.unitTests.all { test ->
            test.jvmArgs(ROBOLECTRIC_JVM_ARG)
        }

        target.extensions.configure<KotlinAndroidProjectExtension> {
            jvmToolchain(JVM_TOOLCHAIN_VERSION)
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17)
            }
        }

        try {
            val keystorePropertiesFile = target.rootProject.file("keystore.properties")
            val keystoreProperties = Properties()
            if (keystorePropertiesFile.exists()) {
                keystoreProperties.load(FileInputStream(keystorePropertiesFile))
                signingConfigs.apply {
                    buildTypes.names.forEach { variant ->
                        val prefix = if (keystoreProperties.keys.any { (it as String).contains(variant) }) "$variant." else ""
                        val config = when (variant) {
                            "debug" -> getByName(variant)
                            else -> create(variant)
                        }

                        config.apply {
                            keyAlias = keystoreProperties["${prefix}keyAlias"] as String
                            keyPassword = keystoreProperties["${prefix}keyPassword"] as String
                            storeFile = target.rootProject.file(keystoreProperties["${prefix}storeFile"] as String)
                            storePassword = keystoreProperties["${prefix}storePassword"] as String
                        }
                    }
                }
            }
        } catch (exception: Exception) {
            target.logger.log(LogLevel.WARN, "Failed to read keystore.properties, using default signing config", exception)
        }
    }
}

internal fun Project.configureAndroidBase() {
    val libs: VersionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
    dependencies {
        implementation(libs, "androidx.core.ktx")
        implementation(libs, "kotlinx.coroutines.core")
        implementation(libs, "kotlinx.coroutines.android")

        implementation(libs, "androidx.lifecycle.runtime.ktx")
        implementation(libs, "androidx.lifecycle.viewmodel.compose")
        implementation(libs, "androidx.lifecycle.service")
        annotationProcessor(libs, "androidx.lifecycle.compiler")

        add("implementation", platform(libs.findLibrary("koin.bom").get()))
        implementation(libs, "koin.android")
        implementation(libs, "util.timber")

        add("baselineProfile", project(":core:baselineprofile"))
    }

    configureUnitTestDependencies()

    extensions.configure<BaselineProfileConsumerExtension> {
        mergeIntoMain = true
        dexLayoutOptimization = true
        saveInSrc = true
    }
}

internal fun Project.configureUnitTestDependencies() {
    val libs: VersionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
    if (configurations.findByName(UNIT_TEST_IMPLEMENTATION) != null) {
        dependencies {
            unitTestLibraryAliases.forEach { testImplementation(libs, it) }
        }
    }
    configurations.matching { it.name == TEST_FIXTURES_IMPLEMENTATION }.configureEach {
        dependencies {
            sharedFixtureLibraryAliases.forEach { alias -> testFixturesImplementation(libs, alias) }
        }
    }
}
