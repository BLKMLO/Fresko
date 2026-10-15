pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Fresko"

include(":app")
include(":core:model")
include(":core:network")
include(":core:source")
include(":core:domain")
include(":core:data")
include(":feature:checker")
include(":sources:asura")
include(":sources:demonic")
