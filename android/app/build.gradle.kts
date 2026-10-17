plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "local.roselink.lite"
    compileSdk = 34
    defaultConfig { applicationId = "local.roselink.lite"; minSdk = 26; targetSdk = 34; versionCode = 9; versionName = "0.8.1" }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_1_8; targetCompatibility = JavaVersion.VERSION_1_8 }
    kotlinOptions { jvmTarget = "1.8" }
    signingConfigs {
        create("local") {
            val password = rootProject.file(".local/signing-password.txt")
            val key = rootProject.file(".local/local-test.p12")
            if (password.exists() && key.exists()) {
                storeFile = key; storePassword = password.readText().trim()
                keyAlias = "roselink-lite-local"; keyPassword = storePassword
                storeType = "PKCS12"
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true; isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (rootProject.file(".local/local-test.p12").exists()) signingConfig = signingConfigs.getByName("local")
        }
    }
    packaging { resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1") }
}
dependencies {
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-android:0.2.9")
    implementation("androidx.compose.ui:ui:1.7.0")
    implementation("androidx.compose.foundation:foundation:1.7.0")
}
