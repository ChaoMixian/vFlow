import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("java-library")
    kotlin("jvm") // 使用标准 Kotlin JVM 插件
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}
val sdkDir = localProperties.getProperty("sdk.dir") ?: System.getenv("ANDROID_HOME")
checkNotNull(sdkDir) { "未找到 Android SDK 路径，请在 local.properties 中设置 sdk.dir" }

// 指定编译用的 android.jar (仅用于存根，不打包)
val androidJar = "$sdkDir/platforms/android-36/android.jar"

// 指定构建工具版本 (d8 所在位置)
val buildToolsVersion = "36.1.0"
val d8Path = "$sdkDir/build-tools/$buildToolsVersion/d8" +
        if (System.getProperty("os.name").lowercase().contains("windows")) ".bat" else ""

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
}

dependencies {
    // 仅编译时依赖 android.jar (运行时由系统提供)
    compileOnly(files(androidJar))

    // JSON 解析库 (运行时需要，会被打入 dex)
    implementation("org.json:json:20251224")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.chaomixian.vflow.server.VFlowCore"
    }
    // 包含源码编译结果
    from(sourceSets.main.get().output)

    // 包含依赖库 (排除 android.jar 等 compileOnly 依赖)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })

    // 处理重复文件策略
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Dex 构建任务
tasks.register<Exec>("buildDex") {
    group = "build"
    description = "将 Jar 编译为 Dex 并复制到 App assets"

    // 依赖 jar 任务先执行
    dependsOn("jar")

    val inputJar = tasks.jar.get().archiveFile.get().asFile
    // 临时输出目录
    val tempDexDir = layout.buildDirectory.dir("dex").get().asFile
    // 最终目标目录 (App 模块的 assets)
    val appAssetsDir = rootProject.file("app/src/main/assets")
    val targetDex = File(appAssetsDir, "vFlowCore.dex")

    // 声明输入输出，让 Gradle 能正确追踪变化
    inputs.file(inputJar)
    outputs.file(targetDex)

    // 确保目录存在
    doFirst {
        if (!tempDexDir.exists()) tempDexDir.mkdirs()
        if (!appAssetsDir.exists()) appAssetsDir.mkdirs()
    }

    // 执行 d8 命令
    // d8 --lib <android.jar> --output <dir> <input.jar>
    commandLine(
        d8Path,
        "--lib", androidJar,
        "--output", tempDexDir.absolutePath,
        inputJar.absolutePath
    )

    // 任务执行完后，将 classes.dex 移动并重命名为 vFlowCore.dex
    doLast {
        val generatedDex = File(tempDexDir, "classes.dex")

        if (generatedDex.exists()) {
            if (targetDex.exists()) {
                targetDex.delete()
            }
            generatedDex.copyTo(targetDex)
            println("✅ Server Dex 构建成功并已复制到: ${targetDex.absolutePath}")
            println("📊 DEX 大小: ${targetDex.length() / 1024} KB")

            // 清理临时文件
            // generatedDex.delete()
        } else {
            throw GradleException("d8 命令执行失败，未生成 classes.dex")
        }
    }
}