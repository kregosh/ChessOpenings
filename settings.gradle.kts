pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "ChessOpenings"
include(":chess")
include(":core")
include(":bookgen")
project(":bookgen").projectDir = file("tools/bookgen")
