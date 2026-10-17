import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.sukot.vpn"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sukot.vpn"
        minSdk = 24
        targetSdk = 35
        versionCode = 4
        versionName = "1.0.3"
    }

    val ciStoreFile = (project.findProperty("sukot.store.file") as String?) ?: System.getenv("SUKOT_STORE_FILE")
    val ciStorePass = (project.findProperty("sukot.store.password") as String?) ?: System.getenv("SUKOT_STORE_PASSWORD") ?: "sukot123456"
    val ciKeyAlias = (project.findProperty("sukot.key.alias") as String?) ?: System.getenv("SUKOT_KEY_ALIAS") ?: "sukot"
    val ciKeyPass = (project.findProperty("sukot.key.password") as String?) ?: System.getenv("SUKOT_KEY_PASSWORD") ?: "sukot123456"
    val hasCiKeystore = !ciStoreFile.isNullOrBlank() && File(ciStoreFile).exists()

    signingConfigs {
        if (hasCiKeystore) {
            create("ci") {
                storeFile = File(ciStoreFile!!)
                storePassword = ciStorePass
                keyAlias = ciKeyAlias
                keyPassword = ciKeyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Sign with CI keystore when provided; fall back to debug key so the APK stays installable
            signingConfig = signingConfigs.getByName(if (hasCiKeystore) "ci" else "debug")
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
        compose = true
        buildConfig = true
    }
    packaging {
        jniLibs {
            // Compress native libraries -> ~100MB smaller APK download
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.zxing.android.embedded)
    implementation(libs.zxing.core)
    debugImplementation(libs.androidx.ui.tooling)
    implementation(files("libs/libv2ray.aar"))
}
