plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.parcelize")
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
}

android {
    namespace = "com.chaomixian.vflow"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.chaomixian.vflow"
        minSdk = 29
        targetSdk = 36
        versionCode = 22
        versionName = "1.3.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    // 启用 ViewBinding，可以更安全地访问视图
    buildFeatures {
        viewBinding = true
        aidl = true           // 启用aidl
        compose = true
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }
}

dependencies {

    val composeBom = platform("androidx.compose:compose-bom:2025.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // 扩展图标库
    implementation("androidx.compose.material:material-icons-extended")

    // 核心 UI 库
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    // 导航库
    implementation("androidx.navigation:navigation-fragment-ktx:2.9.6")
    implementation("androidx.navigation:navigation-ui-ktx:2.9.6")
    implementation("androidx.navigation:navigation-compose:2.9.6")

    // SwipeRefreshLayout
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0")

    // JSON 解析库，用于保存和读取工作流
    implementation("com.google.code.gson:gson:2.13.2")

    // Lua 脚本解释器引擎
    implementation("org.luaj:luaj-jse:3.0.1")

    // Shizuku API
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation(libs.androidx.scenecore)

    // 图像处理
    implementation("io.coil-kt:coil:2.7.0")

    // 测试库
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

    // 网络库
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Google ML Kit 文本识别库 (中文和英文)
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    implementation("com.google.mlkit:text-recognition:16.0.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
}