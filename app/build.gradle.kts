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
        versionCode = 13
        versionName = "3.1"
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

dependencies {
    // QR-Code erzeugen (reine Java-Bibliothek)
    implementation("com.google.zxing:core:3.5.3")
    // QR-Code scannen ueber den Google-Scanner (braucht keine Kamera-Berechtigung)
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
}
