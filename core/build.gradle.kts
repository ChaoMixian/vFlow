import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties
import javax.inject.Inject

abstract class GenerateCoreBuildInfoTask : DefaultTask() {
    @get:Input
    abstract val versionCode: Property<Int>

    @get:Input
    abstract val versionName: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val targetFile = outputFile.get().asFile
        targetFile.parentFile.mkdirs()
        targetFile.writeText(
            """
            package com.chaomixian.vflow.server.common

            object CoreBuildInfo {
                const val VERSION_CODE = ${versionCode.get()}
                const val VERSION_NAME = "${versionName.get()}"
            }
            """.trimIndent()
        )
    }
}

abstract class BuildDexTask : DefaultTask() {
    @get:InputFile
    abstract val inputJar: RegularFileProperty

    @get:InputFile
    abstract val androidJar: RegularFileProperty

    @get:Input
    abstract val d8Path: Property<String>

    @get:Input
    abstract val coreVersion: Property<Int>

    @get:OutputDirectory
    abstract val tempDexDir: DirectoryProperty

    @get:OutputFile
    abstract val targetDex: RegularFileProperty

    @get:OutputFile
    abstract val targetVersionFile: RegularFileProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun buildDex() {
        val tempDex = tempDexDir.get().asFile
        val targetDexFile = targetDex.get().asFile
        val targetVersion = targetVersionFile.get().asFile
        val androidJarFile = androidJar.get().asFile

        tempDex.mkdirs()
        targetDexFile.parentFile.mkdirs()

        execOperations.exec {
            commandLine(
                d8Path.get(),
                "--lib", androidJarFile.absolutePath,
                "--output", tempDex.absolutePath,
                inputJar.get().asFile.absolutePath
            )
        }

        val generatedDex = tempDex.resolve("classes.dex")
        if (!generatedDex.exists()) {
            throw GradleException("d8 命令执行失败，未生成 classes.dex")
        }

        if (targetDexFile.exists()) {
            targetDexFile.delete()
        }
        generatedDex.copyTo(targetDexFile)
        targetVersion.writeText(coreVersion.get().toString())

        println("✅ Server Dex 构建成功并已复制到: ${targetDexFile.absolutePath}")
        println("📊 DEX 大小: ${targetDexFile.length() / 1024} KB")
        println("🧩 Core 版本: ${targetVersion.readText().trim()}")
    }
}

plugins {
    id("java-library")
    kotlin("jvm") // 使用标准 Kotlin JVM 插件
}

val vflowCoreVersion = 17

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
val d8ExecutablePath = "$sdkDir/build-tools/$buildToolsVersion/d8" +
        if (System.getProperty("os.name").lowercase().contains("windows")) ".bat" else ""

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

val generatedCoreBuildInfoDir = layout.buildDirectory.dir("generated/sources/coreBuildInfo/kotlin")
val generatedCoreBuildInfoFile = layout.buildDirectory.file(
    "generated/sources/coreBuildInfo/kotlin/com/chaomixian/vflow/server/common/CoreBuildInfo.kt"
)
val androidJarFile = file(androidJar)

sourceSets {
    main {
        java.srcDir(generatedCoreBuildInfoDir)
    }
}

val generateCoreBuildInfo by tasks.registering(GenerateCoreBuildInfoTask::class) {
    versionCode.set(vflowCoreVersion)
    versionName.set(vflowCoreVersion.toString())
    outputFile.set(generatedCoreBuildInfoFile)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    dependsOn(generateCoreBuildInfo)
    compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
}

dependencies {
    // 仅编译时依赖 android.jar (运行时由系统提供)
    compileOnly(files(androidJar))

    // JSON 解析库 (运行时需要，会被打入 dex)
    implementation("org.json:json:20251224")
}

tasks.jar {
    dependsOn(generateCoreBuildInfo)
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
tasks.register<BuildDexTask>("buildDex") {
    group = "build"
    description = "将 Jar 编译为 Dex 并复制到 App assets"

    dependsOn(tasks.jar)

    inputJar.set(tasks.jar.flatMap { it.archiveFile })
    androidJar.set(androidJarFile)
    d8Path.set(d8ExecutablePath)
    coreVersion.set(vflowCoreVersion)
    tempDexDir.set(layout.buildDirectory.dir("dex"))
    targetDex.set(rootProject.file("app/src/main/assets/vFlowCore.dex"))
    targetVersionFile.set(rootProject.file("app/src/main/assets/vFlowCore.version"))
}
