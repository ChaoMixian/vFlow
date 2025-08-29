package com.chaomixian.vflow.core.module

import com.chaomixian.vflow.modules.device.DelayModule
import com.chaomixian.vflow.modules.logic.*
import com.chaomixian.vflow.modules.triggers.ManualTriggerModule

object ModuleRegistry {
    private val modules = mutableMapOf<String, ActionModule>()

    fun register(module: ActionModule) {
        if (modules.containsKey(module.id)) {
            println("警告: 模块ID '${module.id}' 被重复注册。")
        }
        modules[module.id] = module
    }

    fun getModule(id: String): ActionModule? = modules[id]
    fun getAllModules(): List<ActionModule> = modules.values.toList()

    // 修复：过滤掉不应由用户直接添加的模块（如 end, middle）
    fun getModulesByCategory(): Map<String, List<ActionModule>> {
        return modules.values
            .filter { it.blockBehavior.type != BlockType.BLOCK_END && it.blockBehavior.type != BlockType.BLOCK_MIDDLE }
            .groupBy { it.metadata.category }
    }

    fun initialize() {
        modules.clear()
        register(ManualTriggerModule())
        register(DelayModule())
        register(IfModule())
        register(ElseModule())
        register(EndIfModule())
        register(LoopModule())
        register(EndLoopModule())
    }
}