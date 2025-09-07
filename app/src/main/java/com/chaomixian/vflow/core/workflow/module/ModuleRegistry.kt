package com.chaomixian.vflow.core.module

import com.chaomixian.vflow.core.workflow.module.data.*
import com.chaomixian.vflow.core.workflow.module.file.*
import com.chaomixian.vflow.core.workflow.module.interaction.*
import com.chaomixian.vflow.core.workflow.module.logic.*
import com.chaomixian.vflow.core.workflow.module.system.*
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
            // 更新为新的分类和排序
            .toSortedMap(compareBy {
                when (it) {
                    "触发器" -> 0
                    "界面交互" -> 1
                    "逻辑控制" -> 2
                    "数据" -> 3
                    "文件" -> 4
                    "应用与系统" -> 5
                    else -> 99
                }
            })
    }

    fun initialize() {
        modules.clear()

        // 触发器
        register(ManualTriggerModule())
        register(ReceiveShareTriggerModule())
        register(AppStartTriggerModule())

        // 界面交互
        register(FindTextModule())
        register(ClickModule())
        register(SendKeyEventModule())

        // 逻辑控制
        register(IfModule())
        register(ElseModule())
        register(EndIfModule())
        register(LoopModule())
        register(EndLoopModule())

        // 数据
        register(CalculationModule())
        register(SetVariableModule())
        register(TextProcessingModule())

        // 文件
        register(ImportImageModule())
        register(SaveImageModule())
        register(AdjustImageModule())
        register(ApplyMaskModule())

        // 应用与系统
        register(DelayModule())
        register(InputModule())
        register(QuickViewModule())
        register(ToastModule())
        register(LuaModule())
        register(LaunchAppModule())
        register(GetClipboardModule())
        register(SetClipboardModule())
        register(ShareModule())
    }
}