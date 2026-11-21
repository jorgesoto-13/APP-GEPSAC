plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

// En GitHub Actions el número de ejecución sube con cada compilación: se usa como versionCode.
// Así la app instalada sabe si hay una versión más nueva publicada. En local vale 1 y no busca actualizaciones.
val numeroCompilacion: Int? = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()

android {
    namespace = "com.gepsac.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.gepsac.app"
        minSdk = 24
        targetSdk = 34
        versionCode = numeroCompilacion ?: 1
        versionName = "1.0." + (numeroCompilacion ?: 0)
        buildConfigField("int", "NUMERO_COMPILACION", (numeroCompilacion ?: 0).toString())
        buildConfigField("boolean", "BUSCAR_ACTUALIZACIONES", (numeroCompilacion != null).toString())
        buildConfigField("String", "REPO_GITHUB", "\"jorgesoto-13/APP-GEPSAC\"")
    }

    // Clave de debug compartida (ver .gitignore): todos los APK del grupo quedan firmados igual.
    signingConfigs {
        getByName("debug") {
            storeFile = file("gepsac-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
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
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Firebase (las versiones las fija el BoM).
    // 34.12.0 y no uno más nuevo: desde BoM 34.13 las libs se compilan con metadata de Kotlin 2.3+ (35.x: 2.4)
    // y este proyecto usa Kotlin 2.2.x. Para subir el BoM hay que subir antes el plugin de Kotlin.
    implementation(platform("com.google.firebase:firebase-bom:34.12.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    // Usados por StorageService y CloudFunctionsService (ambos exigen el plan Blaze para funcionar de verdad)
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-functions")
    // Permite usar .await() sobre las tareas de Firebase dentro de corrutinas
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")

    // Ubicación actual para rellenar la dirección de entrega (no necesita clave de API)
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Mapa gratuito de OpenStreetMap para marcar el punto de entrega (sin clave de API ni cuenta de pago)
    implementation("org.osmdroid:osmdroid-android:6.1.20")
}
