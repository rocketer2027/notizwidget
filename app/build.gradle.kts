plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.notizwidget"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.notizwidget"
        minSdk = 29
        targetSdk = 34
        versionCode = 7
        versionName = "2.5"
    }

    // Feste Signatur: Dadurch lassen sich spaetere Updates direkt ueber die alte App installieren.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
