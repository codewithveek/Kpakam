plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Build with JDK 21 but emit Java 17 bytecode, so the Android app (Java 17) can inline core code.
java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    targetCompatibility = JavaVersion.VERSION_17
    sourceCompatibility = JavaVersion.VERSION_17
}

kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(libs.kotlinx.serialization.json)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val contractDir = rootProject.extra["contractDir"] as File

tasks.test {
    useJUnitPlatform()
    systemProperty("contractDir", contractDir.absolutePath)
    // Re-run the contract tests whenever a contract file changes.
    inputs.dir(contractDir).withPropertyName("contract").withPathSensitivity(PathSensitivity.RELATIVE)
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
