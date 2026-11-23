plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.oxe.iptvplayer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.oxe.iptvpro"
        minSdk = 21          // Android 5.0+ (cobre TV Box antigas)
        targetSdk = 35
        versionCode = 1
        versionName = "1.0 PRO"
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
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    // Player de vídeo (HLS/.m3u8, TS, MP4, MKV...)
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")

    // Logos e pôsteres
    implementation("io.coil-kt:coil:2.7.0")

    // QR Code do pagamento
    implementation("com.google.zxing:core:3.5.3")
}
