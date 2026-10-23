plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Original v0.6.1 signing key: external/secret only, never committed to Git.
// Only the dedicated signed-update workflow sets these environment variables.
val originalKeyStorePath = System.getenv("KUROHA_ORIGINAL_KEYSTORE")
val originalStorePassword = System.getenv("KUROHA_STORE_PASSWORD")
val originalKeyAlias = System.getenv("KUROHA_KEY_ALIAS")
val originalKeyPassword = System.getenv("KUROHA_KEY_PASSWORD")
val hasOriginalSigningKey = listOf(
    originalKeyStorePath, originalStorePassword, originalKeyAlias, originalKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "com.azrael.pixivdumpsync"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.azrael.pixivdumpsync"
        minSdk = 29
        targetSdk = 35
        versionCode = 20
        versionName = "0.8.0"

        testInstrumentationRunner = "android.app.Instrumentation"
    }

    signingConfigs {
        if (hasOriginalSigningKey) {
            create("originalUpgradeKey") {
                storeFile = file(requireNotNull(originalKeyStorePath))
                storePassword = requireNotNull(originalStorePassword)
                keyAlias = requireNotNull(originalKeyAlias)
                keyPassword = requireNotNull(originalKeyPassword)
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasOriginalSigningKey) {
                signingConfig = signingConfigs.getByName("originalUpgradeKey")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("org.jsoup:jsoup:1.18.1")
    testImplementation("junit:junit:4.13.2")
}
