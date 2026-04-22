import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.parcelize")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20"
}

android {
    namespace = "com.chaomixian.vflow"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.chaomixian.vflow"
        minSdk = 29
        targetSdk = 36
        versionCode = 46
        versionName = "1.5.0-pr2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        getByName("debug") {
            val keystoreFile = rootProject.file("debug.keystore")
            val signingPropsFile = rootProject.file("debug-signing.properties")

            if (keystoreFile.exists()) {
                val props = Properties()
                if (signingPropsFile.exists()) {
                    props.load(FileInputStream(signingPropsFile))
                }

                storeFile = keystoreFile
                storePassword = props.getProperty("KEYSTORE_PASSWORD", "android")
                keyAlias = props.getProperty("KEYSTORE_ALIAS", "androiddebugkey")
                keyPassword = props.getProperty("KEY_PASSWORD", "android")
            } else {
                println("ℹ️ Debug 签名文件未找到，继续使用 AGP 默认 Debug 签名")
            }
        }

        create("release") {
            val keystoreFile = rootProject.file("vFlow.jks")
            val signingPropsFile = rootProject.file("signing.properties")

            if (keystoreFile.exists() && signingPropsFile.exists()) {
                val props = Properties()
                props.load(FileInputStream(signingPropsFile))

                storeFile = keystoreFile
                storePassword = props.getProperty("KEYSTORE_PASSWORD")
                keyAlias = props.getProperty("KEYSTORE_ALIAS")
                keyPassword = props.getProperty("KEY_PASSWORD")
            } else {
                println("⚠️ Release 签名文件未找到")
            }
        }
    }

    buildTypes {
        debug {
            if (rootProject.file("debug.keystore").exists()) {
                signingConfig = signingConfigs.getByName("debug")
            }
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (rootProject.file("vFlow.jks").exists() && rootProject.file("signing.properties").exists()) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                println("⚠️ Release 签名文件未找到")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
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

    packaging {
        resources {
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
            excludes += "META-INF/DEPENDENCIES"
        }
    }
}

dependencies {

    implementation(libs.androidx.foundation)
    implementation(libs.androidx.foundation.layout)
    val composeBom = platform("androidx.compose:compose-bom:2025.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.5.0-alpha17")
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.backdrop)
    implementation(libs.reorderable)

    // 扩展图标库
    implementation("androidx.compose.material:material-icons-extended")

    // Markdown 渲染
    implementation(libs.multiplatform.markdown.renderer.android)
    implementation(libs.multiplatform.markdown.renderer.m3)
    implementation(libs.multiplatform.markdown.renderer.code)

    // 核心 UI 库
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    implementation("com.google.android.material:material:1.14.0-beta01")

    // 导航库
    implementation("androidx.navigation:navigation-fragment-ktx:2.9.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.9.7")
    implementation("androidx.navigation:navigation-compose:2.9.7")

    // SwipeRefreshLayout
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0")

    // JSON 解析库，用于保存和读取工作流
    implementation("com.google.code.gson:gson:2.13.2")

    // JSON5 解析库，用于解析 gkd 订阅规则
    implementation("li.songe:json5:0.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
    implementation("ai.koog:koog-agents:0.8.0")

    // Lua 脚本解释器引擎
    implementation("org.luaj:luaj-jse:3.0.1")

    // Rhino JavaScript 引擎
    implementation("org.mozilla:rhino:1.9.0")

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
    implementation("org.apache.commons:commons-compress:1.27.1")

    // Web 服务器
    implementation("org.nanohttpd:nanohttpd-webserver:2.3.1")
    implementation("org.nanohttpd:nanohttpd-websocket:2.3.1")

    // Google ML Kit 文本识别库 (中文和英文)
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    implementation("com.google.mlkit:text-recognition:16.0.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

    // OpenCV for image matching
    implementation(libs.opencv)
}

afterEvaluate {
    // 确保 core 模块变化时重建 DEX
    tasks.named("preBuild").configure {
        dependsOn(":core:buildDex")
    }

    // 让 mergeDebugAssets 依赖 buildDex，确保 assets 最新
    tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
        dependsOn(":core:buildDex")
    }
}
