// 文件: server/src/main/java/com/chaomixian/vflow/server/common/Config.kt
package com.chaomixian.vflow.server.common

object Config {
    // ============================================
    // 日志配置
    // ============================================

    /**
     * DEBUG 模式开关
     * - true: 显示所有日志（包括 DEBUG 级别）
     * - false: 只显示 INFO、WARN、ERROR 级别
     *
     * 生产环境建议设置为 false 以减少日志输出
     */
    const val DEBUG = true

    // ============================================
    // 端口配置
    // ============================================

    // Master 监听端口 (对外)
    const val PORT_MASTER = 19999

    // Worker 监听端口 (对内 - Loopback)
    const val PORT_WORKER_SHELL = 20001
    const val PORT_WORKER_ROOT = 20002

    // ============================================
    // 网络配置
    // ============================================

    // Socket 连接与读取超时 (毫秒)
    // 0 = 无限超时，适用于长连接场景
    const val SOCKET_TIMEOUT = 0

    // 监听地址配置
    const val LOCALHOST = "127.0.0.1"  // 本地回环
    const val BIND_ADDRESS = "0.0.0.0"  // 绑定所有网卡，允许远程连接

    // ============================================
    // 路由表配置
    // ============================================

    // 路由表配置：定义哪些 Target 由哪个 Worker 处理
    val ROUTING_TABLE = mapOf(
        // Shell 权限可处理
        "clipboard" to PORT_WORKER_SHELL,
        "input" to PORT_WORKER_SHELL,
        "wifi" to PORT_WORKER_SHELL,
        "bluetooth_manager" to PORT_WORKER_SHELL,
        "nfc" to PORT_WORKER_SHELL,
        "power" to PORT_WORKER_SHELL,
        "activity" to PORT_WORKER_SHELL,
        "connectivity" to PORT_WORKER_SHELL,
        "location" to PORT_WORKER_SHELL,
        "alarm" to PORT_WORKER_SHELL,
        "activity_task" to PORT_WORKER_SHELL,
        "screenshot" to PORT_WORKER_SHELL,

        // 必须 Root 权限
        "system_root" to PORT_WORKER_ROOT
    )

    // 注意：system target 由 Master 动态路由，不在静态路由表中

    // ============================================
    // 初始化配置
    // ============================================

    init {
        // 根据 DEBUG 配置设置日志级别
        Logger.setLevel(if (DEBUG) Logger.Level.DEBUG else Logger.Level.INFO)
    }
}
