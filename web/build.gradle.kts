import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform") version "2.4.20"
    kotlin("plugin.compose") version "2.4.20"
    id("org.jetbrains.compose") version "1.12.1"
}

val copyBook by tasks.registering(Sync::class) {
    from(rootDir.resolve("../data/book.bin"))
    into(layout.buildDirectory.dir("generated/book"))
}

// The engine comes from the stockfish npm package that the Kotlin build installs.
val copyEngine by tasks.registering(Sync::class) {
    dependsOn("kotlinWasmNpmInstall")
    val src = layout.buildDirectory.dir("wasm/node_modules/stockfish/src")
    from(src) {
        include("stockfish-*-lite-single-*.js", "stockfish-*-lite-single-*.wasm")
        // Stable names; the script finds its .wasm by its own name.
        rename { name -> "stockfish." + name.substringAfterLast('.') }
        into("engine")
    }
    from(layout.buildDirectory.dir("wasm/node_modules/stockfish")) {
        include("Copying.txt")
        into("engine")
    }
    into(layout.buildDirectory.dir("generated/engine"))
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("chessopenings")
        browser {
            commonWebpackConfig { outputFileName = "chessopenings.js" }
        }
        binaries.executable()
    }

    sourceSets {
        val commonMain by getting {
            // Shared with the Android app: rules, book format and training logic.
            kotlin.srcDir("../chess/src/main/kotlin")
            kotlin.srcDir("../core/src/main/kotlin")
            // The screens, shared with the Android app.
            kotlin.srcDir("../ui/src/main/kotlin")
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            }
        }
        val wasmJsMain by getting {
            // The opening book and the Stockfish engine, served next to the app.
            resources.srcDir(copyBook)
            resources.srcDir(copyEngine)
            dependencies {
                // Stockfish.js (GPL-3.0): its single-threaded "lite" WASM build runs in a Web Worker.
                implementation(npm("stockfish", "17.1.0"))
            }
        }
    }
}

// Kotlin's browser tooling installs its own Karma fork straight from GitHub (codeload),
// which not every build environment can reach. Karma only runs browser tests, which this
// app doesn't have, so the regular npm release does.
plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsRootPlugin> {
    the<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsRootExtension>().versions.karma.version = "6.4.4"
}
