package dev.maples.build

import androidx.baselineprofile.gradle.consumer.BaselineProfileConsumerExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.gradle.BaseExtension
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
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension

private val javaVersion = JavaVersion.VERSION_17

internal fun configureAndroid(target: Project, commonExtension: CommonExtension<*, *, *, *, *, *>) {
    commonExtension.apply {
        compileSdk = 35

        defaultConfig {
            minSdk = 32
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

            vectorDrawables {
                useSupportLibrary = true
            }
        }

        compileOptions {
            sourceCompatibility = javaVersion
            targetCompatibility = javaVersion
        }

        target.kotlinExtension.jvmToolchain(javaVersion.ordinal + 1)

        buildFeatures {
            buildConfig = true
        }

        try {
            val keystorePropertiesFile = target.rootProject.file("keystore.properties")
            val keystoreProperties = Properties()
            if (keystorePropertiesFile.exists()) {
                signingConfigs {
                    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
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

internal fun Project.configureAndroidBase(commonExtension: BaseExtension) {
    commonExtension.apply {
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

        extensions.configure<BaselineProfileConsumerExtension> {
            mergeIntoMain = true
            dexLayoutOptimization = true
            saveInSrc = true
        }
    }
}
