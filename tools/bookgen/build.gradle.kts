import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

application {
    mainClass.set("com.kregosh.chessopenings.bookgen.MainKt")
    applicationName = "bookgen"
}

dependencies {
    implementation(project(":core"))
    testImplementation(libs.kotlin.test)
}

tasks.test {
    useJUnitPlatform()
    // Engine integration test runs only when a Stockfish binary is given.
    System.getenv("STOCKFISH")?.let { environment("STOCKFISH", it) }
}
