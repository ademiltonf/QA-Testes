plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.oxe.iptvplayer"
    compileSdk = 35

    defaultConfig {
        minSdk = 21          // Android 5.0+ (cobre TV Box antigas)
        targetSdk = 35
    }

    buildFeatures {
        buildConfig = true
    }

    // Os dois apps saem do mesmo código:
    //  - player: OXEIPTVPLAYER (gratuito)
    //  - pro:    OXEIPTVPRO (com ativação paga)
    flavorDimensions += "edition"
    productFlavors {
        create("player") {
            dimension = "edition"
            applicationId = "com.oxe.iptvplayer"
            versionCode = 7
            versionName = "6.1"
            buildConfigField("boolean", "PRO", "false")
        }
        create("pro") {
            dimension = "edition"
            applicationId = "com.oxe.iptvpro"
            versionCode = 5
            versionName = "1.4 PRO"
            buildConfigField("boolean", "PRO", "true")
        }
    }

    // Assinatura fixa: as atualizações instalam por cima sem perder nada.
    // A senha fica no segredo SIGNING_PASSWORD do GitHub (nunca no código).
    val signingPassword = System.getenv("SIGNING_PASSWORD")
    signingConfigs {
        create("oxe") {
            storeFile = rootProject.file("keystore/oxe-release.jks")
            storePassword = signingPassword
            keyAlias = "oxe"
            keyPassword = signingPassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (!signingPassword.isNullOrEmpty()) signingConfig = signingConfigs.getByName("oxe")
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
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // QR Code do pagamento
    implementation("com.google.zxing:core:3.5.3")
}
