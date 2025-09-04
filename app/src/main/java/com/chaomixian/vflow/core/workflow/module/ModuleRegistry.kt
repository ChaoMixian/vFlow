package com.chaomixian.vflow.core.module

import com.chaomixian.vflow.core.workflow.module.data.CalculationModule
import com.chaomixian.vflow.core.workflow.module.data.SetVariableModule
import com.chaomixian.vflow.core.workflow.module.device.*
import com.chaomixian.vflow.core.workflow.module.logic.*
import com.chaomixian.vflow.core.workflow.module.triggers.*

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

    fun getModulesByCategory(): Map<String, List<ActionModule>> {
        return modules.values
            .filter { it.blockBehavior.type != BlockType.BLOCK_END && it.blockBehavior.type != BlockType.BLOCK_MIDDLE }
            .groupBy { it.metadata.category }
    }

    fun initialize() {
        modules.clear()

        //触发器
        register(ManualTriggerModule())

        // 数据
        register(CalculationModule())
        register(SetVariableModule())

        // 设备
        register(DelayModule())
        register(FindTextModule())
        register(ClickModule())
        register(ToastModule())

        // 逻辑控制
        register(IfModule())
        register(ElseModule())
        register(EndIfModule())
        register(LoopModule())
        register(EndLoopModule())
    }
}