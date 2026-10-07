plugins {
    alias(libs.plugins.android.application)
    // AGP 9 内置 Kotlin，无需应用 org.jetbrains.kotlin.android
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.ican.tvplay"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ican.tvplay"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = "icanyunying2026"
            keyAlias = "icanyunying"
            keyPassword = "icanyunying2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
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

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    // Jetpack Compose for TV
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.tv.foundation)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Room：收藏与播放历史
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Media3 / ExoPlayer：视频播放
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)

    // miuix-blur：液态玻璃悬浮底栏的 Backdrop 模糊 / 折射
    implementation(libs.miuix.blur)

    // Coil 3：网络封面加载
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // HiddenApiBypass：运行时切换系统预测性返回（反射 ApplicationInfo.setEnableOnBackInvokedCallback）
    implementation(libs.hiddenapibypass)
}
