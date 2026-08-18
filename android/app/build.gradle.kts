plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.srrotas.app"
    compileSdk = 36

    val oneSignalAppId = (System.getenv("ONESIGNAL_APP_ID") ?: "").trim()

    defaultConfig {
        applicationId = "com.srrotas.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 16
        versionName = "0.12.0-alpha"
        buildConfigField("String", "ONESIGNAL_APP_ID", "\"${oneSignalAppId.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
    }

    val keystorePath = System.getenv("KEYSTORE_PATH")
    val keystorePassword = System.getenv("KEYSTORE_PASSWORD")
    val keyAliasValue = System.getenv("KEY_ALIAS")
    val keyPasswordValue = System.getenv("KEY_PASSWORD")

    if (!keystorePath.isNullOrBlank() &&
        !keystorePassword.isNullOrBlank() &&
        !keyAliasValue.isNullOrBlank() &&
        !keyPasswordValue.isNullOrBlank()
    ) {
        signingConfigs {
            create("releaseEnv") {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            if (signingConfigs.findByName("releaseEnv") != null) {
                signingConfig = signingConfigs.getByName("releaseEnv")
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
        buildConfig = true
    }
}

dependencies {
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.onesignal:OneSignal:5.9.8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
}
