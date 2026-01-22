// 文件: server/src/main/java/com/chaomixian/vflow/server/worker/ShellWorker.kt
package com.chaomixian.vflow.server.worker

import com.chaomixian.vflow.server.common.Config
import com.chaomixian.vflow.server.common.Workarounds
import com.chaomixian.vflow.server.common.utils.SystemUtils
import com.chaomixian.vflow.server.wrappers.shell.*
import kotlin.system.exitProcess

class ShellWorker : BaseWorker(Config.PORT_WORKER_SHELL, "Shell") {

    override fun registerWrappers() {
        // 注册所有 Shell 级别的 ServiceWrappers
        serviceWrappers["clipboard"] = IClipboardWrapper()
        serviceWrappers["input"] = IInputManagerWrapper()
        serviceWrappers["wifi"] = IWifiManagerWrapper()
        serviceWrappers["bluetooth_manager"] = IBluetoothManagerWrapper()
        serviceWrappers["power"] = IPowerManagerWrapper()
        serviceWrappers["activity"] = IActivityManagerWrapper()
        serviceWrappers["connectivity"] = IConnectivityManagerWrapper()
        serviceWrappers["location"] = ILocationManagerWrapper()
        serviceWrappers["alarm"] = IAlarmManagerWrapper()
        serviceWrappers["activity_task"] = IActivityTaskManagerWrapper()

        // 注册所有 Shell 级别的 SimpleWrappers (不需要连接系统服务的)
        simpleWrappers["screenshot"] = IScreenshotWrapper()

        // 注意：system target 由 Master 动态路由，不在 wrappers 中注册
    }

    /**
     * ShellWorker 的启动入口
     * 处理权限降级逻辑
     */
    fun run() {
        // 如果通过 vflow_shell_exec 启动，此时应该已经是 Shell 权限
        // 如果通过 app_process 直接启动（回退模式），则需要降权
        if (SystemUtils.isRoot()) {
            System.err.println("⚠️ ShellWorker started as Root, dropping privileges...")
            if (!SystemUtils.dropPrivilegesToShell()) {
                System.err.println("❌ Critical: Failed to drop privileges for ShellWorker.")
                exitProcess(1)
            }
        } else {
            println("✅ ShellWorker running as Shell (UID: ${SystemUtils.getMyUid()})")
        }

        // 应用 FakeContext 工作区，伪装成 com.android.shell
        // 必须在任何服务连接之前调用
        Workarounds.apply()
        println("✅ FakeContext applied as com.android.shell")

        // 启动 ServerSocket
        super.start()
    }
}