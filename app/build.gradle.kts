plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "it.lodaci.soundboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "it.lodaci.soundboard"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        ndk {
            // yt-dlp porta con sé un interprete Python nativo: teniamo solo le ABI dei telefoni reali.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        // Chiave fissa (non segreta) così ogni APK compilato dalla CI si installa sopra il precedente.
        create("shared") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true // richiesto da NewPipeExtractor
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        // richiesto da youtubedl-android (estrae Python dalle librerie native)
        jniLibs { useLegacyPackaging = true }
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    implementation(libs.media3.exoplayer)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.youtubedl.library)
    implementation(libs.coroutines.android)
    implementation(libs.newpipe.extractor)
    implementation(libs.okhttp)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit)
}

// Test di rete (YouTube reale): ./gradlew testDebugUnitTest -PnetworkTests=true
tasks.withType<Test>().configureEach {
    systemProperty("networkTests", (project.findProperty("networkTests") ?: "false").toString())
}
