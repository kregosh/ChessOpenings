// The browser app. A build of its own (like MtGLifeTracker's web/), so the JVM build of the
// other modules stays as it is; it compiles chess, core and ui from their folders.
pluginManagement {
    repositories {
        // Compose Multiplatform pulls AndroidX's multiplatform libraries from Google's repository.
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ChessOpeningsWeb"
