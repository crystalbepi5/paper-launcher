plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "dev.paperlauncher.diagnostic"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.paperlauncher.diagnostic"
        minSdk = 31
        targetSdk = 35
        versionCode = 3
        versionName = "0.3"
    }
    signingConfigs {
        System.getenv("PAPER_KEYSTORE")?.let { path ->
            create("paper") {
                storeFile = file(path)
                storePassword = System.getenv("PAPER_KEYSTORE_PASSWORD")
                keyAlias = "paper"
                keyPassword = System.getenv("PAPER_KEYSTORE_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("debug") {
            signingConfigs.findByName("paper")?.let { signingConfig = it }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.window:window:1.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
