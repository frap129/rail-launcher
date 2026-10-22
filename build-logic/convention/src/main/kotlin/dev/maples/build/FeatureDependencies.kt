package dev.maples.build

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency

private const val FEATURE_MODULE_PREFIX: String = ":feature:"

internal fun Project.configureFeatureDependencyGuard() {
    if (!path.startsWith(FEATURE_MODULE_PREFIX)) {
        return
    }
    afterEvaluate {
        configurations.forEach { configuration ->
            configuration.dependencies.withType(ProjectDependency::class.java).forEach { dependency ->
                if (dependency.path.startsWith(FEATURE_MODULE_PREFIX) && dependency.path != path) {
                    throw GradleException(
                        "Feature modules must not depend on other feature modules: $path depends on ${dependency.path}"
                    )
                }
            }
        }
    }
}
