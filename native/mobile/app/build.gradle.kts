import groovy.json.JsonSlurper
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// google-services.json is per developer and never committed. Without it the
// project still configures and compiles; the app shows a "set up Firebase" screen.
val hasFirebaseConfig = file("google-services.json").exists()
if (hasFirebaseConfig) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
} else {
    logger.lifecycle("Kpakam: app/google-services.json not found. Firebase plugins are not applied.")
}

val localProperties =
    Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }

// Read from local.properties or the environment; never commit a debug token.
val appCheckDebugToken: String =
    localProperties.getProperty("APP_CHECK_DEBUG_TOKEN")
        ?: System.getenv("APP_CHECK_DEBUG_TOKEN")
        ?: ""

val contractRoot = rootProject.extra["contractDir"] as File

android {
    namespace = "app.kpakam"
    // AndroidX Compose 1.13 needs API 37.1 or later; 37.2 is the current minor release.
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "app.kpakam"
        minSdk = 26 // firebase-ai-ondevice needs API 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["firebaseAppCheckDebugSecret"] = appCheckDebugToken
        if (appCheckDebugToken.isNotEmpty()) {
            testInstrumentationRunnerArguments["firebaseAppCheckDebugSecret"] = appCheckDebugToken
        }
    }

    buildTypes {
        debug {
            // firebase-appcheck-debug reads R.string.firebase_app_check_debug_token. The value comes
            // from local.properties or the environment (never committed); release builds ignore it.
            if (appCheckDebugToken.isNotEmpty()) {
                resValue("string", "firebase_app_check_debug_token", appCheckDebugToken)
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        optIn.add("kotlin.RequiresOptIn")
    }
}

/** Writes res/xml/remote_config_defaults.xml from the contract's remote-config.defaults.json. */
abstract class GenerateRemoteConfigDefaults : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val defaultsJson: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val values = JsonSlurper().parse(defaultsJson.get().asFile) as Map<*, *>
        val entries =
            values.entries.joinToString("\n") { (key, value) ->
                val text =
                    value
                        .toString()
                        .replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                "    <entry>\n        <key>$key</key>\n        <value>$text</value>\n    </entry>"
            }
        val file =
            outputDir
                .get()
                .dir("xml")
                .file("remote_config_defaults.xml")
                .asFile
        file.parentFile.mkdirs()
        file.writeText(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<!-- Generated from packages/contract/remote-config.defaults.json. Do not edit. -->\n" +
                "<defaultsMap>\n$entries\n</defaultsMap>\n",
        )
    }
}

/** Copies the prompts, the draft schema, and the lexicon from the contract into assets/contract. */
abstract class CopyContractAssets : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val contractDir: DirectoryProperty

    @get:Input
    abstract val paths: ListProperty<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val target = outputDir.get().dir("contract").asFile
        target.deleteRecursively()
        paths.get().forEach { path ->
            val source = contractDir.get().file(path).asFile
            check(source.isFile) { "Contract file missing: $source" }
            source.copyTo(target.resolve(path), overwrite = true)
        }
    }
}

val generateRemoteConfigDefaults =
    tasks.register<GenerateRemoteConfigDefaults>("generateRemoteConfigDefaults") {
        defaultsJson.set(contractRoot.resolve("remote-config.defaults.json"))
        outputDir.set(layout.buildDirectory.dir("generated/kpakam/res"))
    }

val copyContractAssets =
    tasks.register<CopyContractAssets>("copyContractAssets") {
        contractDir.set(contractRoot)
        paths.set(
            listOf(
                "prompts/parse-v1.md",
                "prompts/parse-retry-v1.md",
                "prompts/voice-v1.md",
                "prompts/receipt-v1.md",
                "prompts/summary-v1.md",
                "prompts/insights-v1.md",
                "schemas/draft.schema.json",
                "language-lexicon.json",
                "remote-config.defaults.json",
            ),
        )
        outputDir.set(layout.buildDirectory.dir("generated/kpakam/assets"))
    }

// The app's JVM tests read prompts and schemas straight from the contract, like :core.
tasks.withType<Test>().configureEach {
    systemProperty("contractDir", contractRoot.absolutePath)
}

androidComponents {
    onVariants { variant ->
        variant.sources.res?.addGeneratedSourceDirectory(
            generateRemoteConfigDefaults,
            GenerateRemoteConfigDefaults::outputDir,
        )
        variant.sources.assets?.addGeneratedSourceDirectory(copyContractAssets, CopyContractAssets::outputDir)
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime.ktx)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.config)
    implementation(libs.firebase.appcheck.playintegrity)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.ai)
    implementation(libs.firebase.ai.ondevice)
    debugImplementation(libs.firebase.appcheck.debug)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}
