plugins {
    id("com.android.application")
}

android {
    namespace = "com.azrael.pixivdumpsync"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.azrael.pixivdumpsync"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

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
