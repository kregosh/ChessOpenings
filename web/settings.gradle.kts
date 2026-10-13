// The browser app. A build of its own (like MtGLifeTracker's web/), so the JVM build of the
// other modules stays as it is; it compiles chess, core and ui from their folders.
pluginManagement {
    repositories {
        // Google's repository, for AndroidX's multiplatform libraries that Compose
        // Multiplatform uses (maven.google.com is the same repository as google()).
        maven("https://maven.google.com") {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("com\\.android.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        // Google's repository, for AndroidX's multiplatform libraries that Compose
        // Multiplatform uses (maven.google.com is the same repository as google()).
        maven("https://maven.google.com") {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("com\\.android.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "ChessOpeningsWeb"
