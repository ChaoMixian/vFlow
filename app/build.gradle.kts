plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // 新增：启用 Parcelize 功能，用于在组件间传递我们新的数据模型（如 Workflow）
    id("org.jetbrains.kotlin.plugin.parcelize")
}

android {
    namespace = "com.chaomixian.vflow"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.chaomixian.vflow"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        // 安卓开发标准通常使用 1.8 版本，兼容性更广
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    // 启用 ViewBinding，可以更安全地访问视图
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    // 核心 UI 库
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    // 导航库
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")

    // 新增：JSON 解析库，用于保存和读取工作流
    implementation("com.google.code.gson:gson:2.10.1")

    // 新增：Lua 脚本解释器引擎
    implementation("org.luaj:luaj-jse:3.0.1")

    // 新增：Shizuku API (未来重新引入高级功能时需要)
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation(libs.androidx.scenecore)

    // 测试库
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}