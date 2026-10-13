plugins {
    alias(libs.plugins.android.application)
    // AGP 9 内置 Kotlin，无需应用 org.jetbrains.kotlin.android
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    // AGP 9 内置 Kotlin 已在 classpath 提供 parcelize，无版本直接应用（KernelSU Route 需要 @Parcelize）
    id("org.jetbrains.kotlin.plugin.parcelize")
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
        // miuix-nav 字节码为 JVM 21（与 KernelSU 一致），target 必须对齐
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
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

    // Room：收藏与播放历史
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Media3 / ExoPlayer：视频播放
    implementation(libs.androidx.media3.exoplayer)
    // HLS(m3u8)/DASH(mpd)/SmoothStreaming：TVBox 站源绝大多数为 m3u8，
    // DefaultMediaSourceFactory 靠反射加载这三个模块的 MediaSource.Factory，缺失即 ClassNotFoundException 闪退
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.exoplayer.smoothstreaming)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)
    // MediaSession + MediaStyle 通知栏控制（后台续播）
    implementation(libs.androidx.media3.session)
    // ffmpeg 软解扩展：fongmi 预编译 AAR（含 native so + Java 类，
    // media3 官方 maven 不发布解码器扩展；DefaultRenderersFactory 反射加载，无需编译期引用）
    implementation(fileTree("libs") { include("*.aar") })

    // miuix-blur：液态玻璃悬浮底栏的 Backdrop 模糊 / 折射
    implementation(libs.miuix.blur)

    // miuix UI + preference + icons + nav：KernelSU 同款下拉弹窗（OverlayDropdownPopup，同窗口覆盖层渲染）、
    // 顶栏折叠（TopAppBar/MiuixScrollBehavior）、裸图标（MiuixIcons.Back）
    // 与导航（NavDisplay：边缘滑动返回 + 预测性返回圆角裁剪动画）
    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.nav)
    implementation(libs.hiddenapibypass)

    // Coil 3：网络封面加载
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // TVBox 接口：OkHttp + kotlinx.serialization
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    // Gson：spider jar 内解密出的真实站点类依赖宿主提供（与 fongmi catvod 模块一致）
    implementation(libs.gson)

    // NanoHTTPD：本地代理服务器（spider jar 返回 127.0.0.1/proxy 地址时由本服务中转拉流，fongmi 同款）
    implementation(libs.nanohttpd)
}
