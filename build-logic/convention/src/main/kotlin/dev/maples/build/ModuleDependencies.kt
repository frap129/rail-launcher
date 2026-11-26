package dev.maples.build

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ProjectDependency

private const val APP_MODULE_PATH: String = ":app"
private const val LIFECYCLE_MODULE_PATH: String = ":core:lifecycle"
internal const val BASELINE_PROFILE_MODULE_PATH: String = ":core:baselineprofile"
private const val FEATURE_MODULE_PREFIX: String = ":feature:"
private const val CORE_MODULE_PREFIX: String = ":core:"

private enum class ModuleLayer {
    APP,
    LIFECYCLE,
    FEATURE,
    CORE,
    BASELINE_PROFILE,
}

internal fun Project.configureModuleDependencyGuard() {
    val sourceLayer: ModuleLayer = findModuleLayer(path)
        ?: throw GradleException("Module layer check cannot classify source project $path")
    afterEvaluate {
        configurations.forEach { configuration: Configuration ->
            configuration.dependencies
                .withType(ProjectDependency::class.java)
                .forEach { dependency: ProjectDependency ->
                    val dependencyLayer: ModuleLayer = findModuleLayer(dependency.path)
                        ?: throw GradleException(
                            "Module layer check cannot classify dependency ${dependency.path} declared by $path"
                        )
                    if (dependency.path == path) {
                        return@forEach
                    }
                    if (!sourceLayer.canDependOn(dependencyLayer)) {
                        throw GradleException(
                            "Module dependency violates the layer rules: $path depends on ${dependency.path}"
                        )
                    }
                }
        }
    }
}

private fun findModuleLayer(modulePath: String): ModuleLayer? = when {
    modulePath == APP_MODULE_PATH -> ModuleLayer.APP
    modulePath == LIFECYCLE_MODULE_PATH -> ModuleLayer.LIFECYCLE
    modulePath == BASELINE_PROFILE_MODULE_PATH -> ModuleLayer.BASELINE_PROFILE
    modulePath.startsWith(FEATURE_MODULE_PREFIX) -> ModuleLayer.FEATURE
    modulePath.startsWith(CORE_MODULE_PREFIX) -> ModuleLayer.CORE
    else -> null
}

private fun ModuleLayer.canDependOn(dependencyLayer: ModuleLayer): Boolean =
    ordinal <= dependencyLayer.ordinal && (this != ModuleLayer.FEATURE || dependencyLayer != ModuleLayer.FEATURE)
