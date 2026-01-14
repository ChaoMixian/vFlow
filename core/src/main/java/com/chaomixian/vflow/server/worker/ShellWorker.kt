// 文件: server/src/main/java/com/chaomixian/vflow/server/worker/ShellWorker.kt
package com.chaomixian.vflow.server.worker

import com.chaomixian.vflow.server.common.Config
import com.chaomixian.vflow.server.common.utils.SystemUtils
import com.chaomixian.vflow.server.wrappers.shell.*
import kotlin.system.exitProcess

class ShellWorker : BaseWorker(Config.PORT_WORKER_SHELL, "Shell") {

    override fun registerWrappers() {
        // 注册所有 Shell 级别的 Wrappers
        wrappers["clipboard"] = IClipboardWrapper()
        wrappers["input"] = IInputManagerWrapper()
        wrappers["wifi"] = IWifiManagerWrapper()
        wrappers["bluetooth_manager"] = IBluetoothManagerWrapper()
        wrappers["power"] = IPowerManagerWrapper()
        wrappers["activity"] = IActivityManagerWrapper()
    }

    /**
     * ShellWorker 的启动入口
     * 处理权限降级逻辑
     */
    fun run() {
        // 如果当前是 Root 身份启动的 ShellWorker，需要主动降权
        if (SystemUtils.isRoot()) {
            if (!SystemUtils.dropPrivilegesToShell()) {
                System.err.println("❌ Critical: Failed to drop privileges for ShellWorker.")
                exitProcess(1)
            }
        }

        // 启动 ServerSocket
        super.start()
    }
}