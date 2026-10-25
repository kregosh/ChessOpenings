// The Android app. A build of its own (like web/), so the JVM build of the other modules
// stays as it is; it compiles chess, core and ui from their folders.
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

rootProject.name = "ChessOpeningsAndroid"
