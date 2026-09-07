plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.xtmanager"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.xtmanager"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "0.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    signingConfigs {
        create("fixedRelease") {
            val localProps = java.util.Properties()
            val localPropsFile = rootProject.file("local.properties")
            if (localPropsFile.exists()) {
                localPropsFile.inputStream().use { localProps.load(it) }
            }

            val customPath = System.getenv("RELEASE_KEYSTORE_PATH")
                ?: localProps.getProperty("RELEASE_KEYSTORE_PATH")

            val candidateFiles = mutableListOf<java.io.File>()
            if (!customPath.isNullOrBlank()) {
                candidateFiles.add(file(customPath))
            }
            candidateFiles.add(file("release.keystore"))
            candidateFiles.add(rootProject.file("release.keystore"))
            candidateFiles.add(file("/sdcard/Xt-Manager-Keystore/release.keystore"))
            candidateFiles.add(file("/storage/emulated/0/Xt-Manager-Keystore/release.keystore"))

            val targetKs = candidateFiles.firstOrNull { it.exists() }
            if (targetKs != null) {
                storeFile = targetKs
            }

            storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                ?: localProps.getProperty("RELEASE_KEYSTORE_PASSWORD")
                ?: ""

            keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                ?: localProps.getProperty("RELEASE_KEY_ALIAS")
                ?: ""

            keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
                ?: localProps.getProperty("RELEASE_KEY_PASSWORD")
                ?: ""
        }
    }

    buildTypes {
        debug {
            val config = signingConfigs.getByName("fixedRelease")
            if (config.storeFile?.exists() == true && config.storePassword.isNotEmpty()) {
                signingConfig = config
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        release {
            val config = signingConfigs.getByName("fixedRelease")
            if (config.storeFile?.exists() == true && config.storePassword.isNotEmpty()) {
                signingConfig = config
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
            pickFirsts.add("**/libtermux.so")
        }
    }
}

dependencies {
    implementation(project(":terminal-view"))
    implementation(project(":terminal-emulator"))
    implementation(libs.androidx.core.ktx)
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
