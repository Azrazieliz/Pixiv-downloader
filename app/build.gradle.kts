plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.azrael.pixivdumpsync"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.azrael.pixivdumpsync"
        minSdk = 29
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"

        testInstrumentationRunner = "android.app.Instrumentation"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
