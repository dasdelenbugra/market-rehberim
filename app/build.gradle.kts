import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)

    id("kotlin-kapt")
    id("kotlin-parcelize")
    id("com.google.dagger.hilt.android")
}

// Yayın imzalama bilgileri kök dizindeki keystore.properties'ten okunur (repoya
// eklenmez). Dosya yoksa release derlemesi geçici olarak debug imzasıyla imzalanır,
// böylece imzalama kurulmadan da minify/R8 doğrulanabilir.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) load(keystorePropsFile.inputStream())
}

android {
    namespace = "com.marketrehberim"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.marketrehberim"
        minSdk = 25
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Backend adresi. local.properties içindeki `backend.baseUrl` değerinden
        // okunur; yoksa emülatörün host makineye eriştiği varsayılan adres kullanılır.
        // (Android emülatöründe 10.0.2.2 = geliştirme makinesinin localhost'u.)
        val backendBaseUrl: String = gradleLocalProperties(rootDir, providers)
            .getProperty("backend.baseUrl", "http://10.0.2.2:5454/")
        buildConfigField("String", "BASE_URL", "\"$backendBaseUrl\"")
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
            signingConfig = if (keystorePropsFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

kapt {
    correctErrorTypes = true
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Jetpack Navigation
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    kapt(libs.androidx.lifecycle.compiler)

    // Hilt
    implementation(libs.hilt.android)
    kapt(libs.hilt.android.compiler)

    // Retrofit & Gson
    implementation(libs.retrofit)
    implementation(libs.converter.gson)

    // Glide
    implementation(libs.glide)

    // ML Kit — cihaz üstü görüntü etiketleme (nesne tanıma) + metin tanıma (OCR)
    // + barkod okuma. Barkod, paketli üründe tek güvenilir tanıma yolu:
    // varsayılan etiketleyicinin 447 etiketlik sözlüğünde ürün adı yok.
    implementation(libs.mlkit.image.labeling)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.barcode.scanning)

    // Room — yerel kalıcılık (favoriler, arama geçmişi)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    // Splash Screen API
    implementation(libs.androidx.core.splashscreen)

    // WorkManager — favori ürünler için arka planda fiyat takibi
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    kapt(libs.androidx.hilt.compiler)

    // Konum — cihaz konumundan şehir tespiti (FusedLocationProvider)
    implementation(libs.play.services.location)
}
