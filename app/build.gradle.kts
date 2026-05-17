plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.example.asea"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.asea"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // local.properties'ten API anahtarlarını BuildConfig'e aktar
        val geminiKey = project.findProperty("GEMINI_API_KEY")?.toString() ?: ""
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiKey\"")

        val claudeKey = project.findProperty("CLAUDE_API_KEY")?.toString() ?: ""
        buildConfigField("String", "CLAUDE_API_KEY", "\"$claudeKey\"")

        val perplexityKey = project.findProperty("PERPLEXITY_API_KEY")?.toString() ?: ""
        buildConfigField("String", "PERPLEXITY_API_KEY", "\"$perplexityKey\"")

        val deeplKey = project.findProperty("DEEPL_API_KEY")?.toString() ?: ""
        buildConfigField("String", "DEEPL_API_KEY", "\"$deeplKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }
    packaging {
        jniLibs {
            // Vosk native kütüphaneleri için çakışmaları önle
pickFirsts += listOf("**/libkaldi-android.so")        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")

    // Jetpack Compose bom and core packages
    val composeBom = platform("androidx.compose:compose-bom:2023.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.8.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // Room
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    ksp("androidx.room:room-compiler:$room_version")

    // SQLCipher — Şifreli Room DB
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")

    // Vosk — Offline Wake-Word / Speech Recognition
    implementation("com.alphacephei:vosk-android:0.3.47")

    // Coroutines — Service & DB işlemleri için
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Lifecycle & ViewModel (servis durum yönetimi)
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // Security — EncryptedSharedPreferences (DB parola yönetimi)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Gemini AI SDK — Naturally Language Processing / LLM entegrasyonu
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")

    // Kotlin Serialization — Gemini yapılandırılmış çıktı işleme için
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Play Services Location — GPS / Konum tabanlı acil durum SMS için (F-07 ✅)
    implementation("com.google.android.gms:play-services-location:21.2.0")
}
