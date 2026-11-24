import java.util.Properties

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

rootProject.name = "kpakam-mobile"

include(":core")

// The Android app needs an Android SDK. Without one, only :core configures,
// so `./gradlew :core:test` still works on a plain JVM machine.
val localProperties = Properties().apply {
    val file = rootDir.resolve("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val sdkDir = localProperties.getProperty("sdk.dir")
    ?: System.getenv("ANDROID_HOME")
    ?: System.getenv("ANDROID_SDK_ROOT")

if (sdkDir != null && File(sdkDir).isDirectory) {
    include(":app")
} else {
    logger.lifecycle(
        "Kpakam: no Android SDK found (set sdk.dir in local.properties or ANDROID_HOME). " +
            "Only :core is included in this build.",
    )
}
