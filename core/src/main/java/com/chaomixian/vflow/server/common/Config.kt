// 文件: server/src/main/java/com/chaomixian/vflow/server/common/Config.kt
package com.chaomixian.vflow.server.common

object Config {
    // Master 监听端口 (对外)
    const val PORT_MASTER = 19999

    // Worker 监听端口 (对内 - Loopback)
    const val PORT_WORKER_SHELL = 20001
    const val PORT_WORKER_ROOT = 20002

    // Socket 连接与读取超时 (毫秒)
    const val SOCKET_TIMEOUT = 10000

    // 监听地址配置
    const val LOCALHOST = "127.0.0.1"  // 本地回环
    const val BIND_ADDRESS = "0.0.0.0"  // 绑定所有网卡，允许远程连接

    // 路由表配置：定义哪些 Target 由哪个 Worker 处理
    val ROUTING_TABLE = mapOf(
        // Shell 权限可处理
        "clipboard" to PORT_WORKER_SHELL,
        "input" to PORT_WORKER_SHELL,
        "wifi" to PORT_WORKER_SHELL,
        "bluetooth_manager" to PORT_WORKER_SHELL,
        "power" to PORT_WORKER_SHELL,
        "activity" to PORT_WORKER_SHELL,

        // 必须 Root 权限
        "system_root" to PORT_WORKER_ROOT
    )
}