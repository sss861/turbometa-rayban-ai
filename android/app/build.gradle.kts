plugins {
    id("com.android.application") version "8.6.0"
    id("org.jetbrains.kotlin.android") version "2.0.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0"
}

android {
    namespace = "com.tourmeta.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tourmeta.app"
        minSdk = 31
        targetSdk = 34
        versionCode = 4
        versionName = "1.5.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getProperty("user.home") + "/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Meta Wearables DAT SDK
    implementation("com.meta.wearable:mwdat-core:0.3.0")
    implementation("com.meta.wearable:mwdat-camera:0.3.0")

    // AndroidX Core
    implementation("androidx.core:core-ktx:1.13.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Compose (使用具体的版本号而非 BOM，以防索引失效)
    implementation("androidx.compose.ui:ui:1.7.0")
    implementation("androidx.compose.ui:ui-graphics:1.7.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.0")
    implementation("androidx.compose.material3:material3:1.3.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling:1.7.0")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Security
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // Image Loading
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Collections
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.7")

    // Picovoice Wake Word Detection
    implementation("ai.picovoice:porcupine-android:3.0.3")

    // RTMP Streaming
    implementation("com.github.pedroSG94.rtmp-rtsp-stream-client-java:rtplibrary:2.2.6")
}
