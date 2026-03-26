import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

fun apiKey(name: String): String =
    localProperties.getProperty(name)
        ?: System.getenv(name)
        ?: ""

android {
    namespace = "ai.neuron"
    compileSdk = 35

    defaultConfig {
        applicationId = "ai.neuron"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-alpha"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // SECURITY: Cloud API keys removed from BuildConfig (HEY-68).
        // All cloud LLM calls route through the server proxy.
        // Picovoice key is provisioned via EncryptedSharedPreferences at runtime.
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("NEURON_KEYSTORE_PATH") ?: ""
            if (keystorePath.isNotEmpty()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("NEURON_KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("NEURON_KEY_ALIAS") ?: "neuron"
                keyPassword = System.getenv("NEURON_KEY_PASSWORD") ?: ""
            }
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("sideload") {
            dimension = "distribution"
            buildConfigField("String", "DEFAULT_EXECUTION_MODE", "\"AUTONOMOUS\"")
        }
        create("playstore") {
            dimension = "distribution"
            buildConfigField("String", "DEFAULT_EXECUTION_MODE", "\"SUPERVISED\"")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            val keystorePath = System.getenv("NEURON_KEYSTORE_PATH") ?: ""
            if (keystorePath.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
        unitTests.isReturnDefaultValues = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

ktlint {
    android.set(true)
    outputToConsole.set(true)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.core.ktx)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.animation)
    implementation(libs.activity.compose)
    debugImplementation(libs.compose.ui.tooling)

    // Lifecycle
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Serialization
    implementation(libs.serialization.json)

    // Encrypted storage (Picovoice key, device token)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // SSE (Server-Sent Events) for streaming LLM proxy responses
    implementation("com.squareup.okhttp3:okhttp-sse:${libs.versions.okhttp.get()}")

    // Wake word detection
    implementation("ai.picovoice:porcupine-android:3.0.3")

    // Testing
    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.junit5.params)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation("com.squareup.okhttp3:mockwebserver:${libs.versions.okhttp.get()}")
}
