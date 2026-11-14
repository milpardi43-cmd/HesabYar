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

rootProject.name = "HesabYar"
include(":app")
include(":core:common")
include(":core:calendar")
include(":core:currency")
include(":core:validation")
include(":core:design")
include(":core:database")
include(":domain")
include(":data")
include(":feature")
include(":billing")
