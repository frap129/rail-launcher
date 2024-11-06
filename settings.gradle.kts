pluginManagement {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }
    includeBuild("build-logic")
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Rail"
include(":app")
include(":core:ui")
include(":core:util")
include(":core:lifecycle")
include(":feature:launcher")
include(":feature:settings")
include(":core:data")
