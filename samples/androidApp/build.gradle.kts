plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    kotlin("android")
}

android {
    namespace = "dev.deviceai.demo"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.deviceai.demo"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.3.0"
    }

    buildTypes {
        release { isMinifyEnabled = false }
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
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            pickFirsts += "lib/**/libggml*.so"
            pickFirsts += "lib/**/libllama*.so"
        }
    }
}

dependencies {
    // DeviceAI SDK
    implementation(project(":kotlin:core"))
    implementation(project(":kotlin:speech"))
    implementation(project(":kotlin:llm"))

    // Compose (via JetBrains Compose plugin — resolves all Compose artifacts)
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.ui)
    implementation(compose.materialIconsExtended)
    implementation(libs.androidx.activity.compose)

    // DI
    implementation(libs.koin.core)
    implementation(libs.koin.compose)

    // Navigation
    implementation(libs.voyager.navigator)
    implementation(libs.voyager.transitions)
}
