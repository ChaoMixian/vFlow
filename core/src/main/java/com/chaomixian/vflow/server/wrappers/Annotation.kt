// 文件: server/src/main/java/com/chaomixian/vflow/server/wrappers/Annotation.kt
package com.chaomixian.vflow.server.wrappers

enum class ProcessType {
    SHELL,
    ROOT
}

/**
 * 标记 Wrapper 运行所需的最小权限进程
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RunOn(val value: ProcessType)