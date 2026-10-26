plugins {
    // com.android.test：baseline profile 生成模块专用插件
    id("com.android.test")
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.ican.tvplay.baselineprofile"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        // baseline profile 生成设备需要 API 24+，target 与 app 对齐
        targetSdk = 37

        // 目标应用模块（必须是 application 模块）
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 指定 profile 生成的目标 app 模块
    targetProjectPath = ":app"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    // Macrobenchmark：BaselineProfileRule 的来源
    implementation(libs.androidx.benchmark.macro.junit4)
    // AndroidJUnit4 runner + UiAutomator
    implementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.test:runner:1.6.2")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
