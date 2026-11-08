plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.gepsac.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.gepsac.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    buildFeatures { viewBinding = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.fragment:fragment-ktx:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.4")

    // Firebase (las versiones las fija el BoM).
    // 34.12.0 y no uno más nuevo: desde BoM 34.13 las libs se compilan con metadata de Kotlin 2.3+ (35.x: 2.4)
    // y este proyecto usa Kotlin 2.2.x. Para subir el BoM hay que subir antes el plugin de Kotlin.
    implementation(platform("com.google.firebase:firebase-bom:34.12.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    // Permite usar .await() sobre las tareas de Firebase dentro de corrutinas
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")
}
