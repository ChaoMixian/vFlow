// 文件: server/src/main/java/com/chaomixian/vflow/server/worker/RootWorker.kt
package com.chaomixian.vflow.server.worker

import com.chaomixian.vflow.server.common.Config
//import com.chaomixian.vflow.server.wrappers.shell.*

class RootWorker : BaseWorker(Config.PORT_WORKER_ROOT, "Root") {

    override fun registerWrappers() {
        // 注册需要 Root 权限的 Wrappers
        // wrappers["activity"] = IActivityManagerWrapper()
    }

    fun run() {
        // RootWorker 不需要特殊的降权逻辑，直接启动
        super.start()
    }
}