import java.security.MessageDigest

plugins {
    id("com.android.application") version "9.3.3"
    kotlin("plugin.compose") version "2.4.20"
}

// AGP compiles Kotlin itself; this pins the Kotlin version to the one the rest of the project uses.
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
}

// Stockfish.js 17.1 (GPL-3.0), the same "lite single-threaded" WASM build as the web app,
// straight from npm and checked against a pinned hash.
val stockfishVersion = "17.1.0"
val stockfishSha256 = "f6473919fcd9dc1a3e6a8f30fb5e3b7e6a57d966b36c8ad0959fce7aca7ec5fc"

val downloadStockfish by tasks.registering {
    val out = layout.buildDirectory.file("downloads/stockfish-$stockfishVersion.tgz")
    outputs.file(out)
    doLast {
        val file = out.get().asFile
        if (!file.exists()) {
            file.parentFile.mkdirs()
            uri("https://registry.npmjs.org/stockfish/-/stockfish-$stockfishVersion.tgz").toURL().openStream().use { input ->
                file.outputStream().use { input.copyTo(it) }
            }
        }
        val hash = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        if (hash != stockfishSha256) {
            file.delete()
            error("Stockfish download has hash $hash, expected $stockfishSha256")
        }
    }
}

// Everything the app loads at run time: the opening book, the background picture and the engine.
val appAssets by tasks.registering(Sync::class) {
    dependsOn(downloadStockfish)
    from(rootDir.resolve("../data/book.bin"))
    from(rootDir.resolve("../web/src/wasmJsMain/resources/background.jpg"))
    from(tarTree(resources.gzip(layout.buildDirectory.file("downloads/stockfish-$stockfishVersion.tgz")))) {
        include("package/src/stockfish-*-lite-single-*.js", "package/src/stockfish-*-lite-single-*.wasm", "package/Copying.txt")
        // Stable names; the script finds its .wasm by its own name.
        eachFile {
            path = "engine/" + if (name == "Copying.txt") name else "stockfish." + name.substringAfterLast('.')
        }
        includeEmptyDirs = false
    }
    into(layout.buildDirectory.dir("generated/assets"))
}

android {
    namespace = "com.kregosh.chessopenings"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.kregosh.chessopenings"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    sourceSets {
        getByName("main") {
            // Shared with the web app: rules, book format, training logic and the screens.
            kotlin.directories += listOf("../chess/src/main/kotlin", "../core/src/main/kotlin", "../ui/src/main/kotlin")
            assets.directories += appAssets.get().destinationDir.path
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // The WASM engine is already compressed; storing it uncompressed keeps loading fast.
    androidResources {
        noCompress += "wasm"
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        disable += setOf(
            // Versions are kept in step with the web build (Kotlin 2.4.20, Compose 1.12.1)
            // and with Gradle 9.5.1, which AGP 9.4 would need upgraded; bumped on purpose.
            "NewerVersionAvailable",
            "AndroidGradlePluginVersion",
            "GradleDependency",
            // Like the web build, this build lists its few dependencies inline.
            "UseTomlInstead",
        )
    }
}

tasks.named("preBuild") { dependsOn(appAssets) }

dependencies {
    // Compose Multiplatform 1.12.1, as in the web app; on Android it maps to Jetpack Compose.
    implementation("org.jetbrains.compose.runtime:runtime:1.12.1")
    implementation("org.jetbrains.compose.foundation:foundation:1.12.1")
    implementation("org.jetbrains.compose.material3:material3:1.9.0")
    implementation("org.jetbrains.compose.ui:ui:1.12.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    // Serves the engine files to the WebView from the app's assets.
    implementation("androidx.webkit:webkit:1.18.0")
}
