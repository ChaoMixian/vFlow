package com.chaomixian.vflow.core.module

import com.chaomixian.vflow.core.workflow.module.data.CalculationModule
import com.chaomixian.vflow.core.workflow.module.data.InputModule
import com.chaomixian.vflow.core.workflow.module.data.QuickViewModule
import com.chaomixian.vflow.core.workflow.module.data.SetVariableModule
import com.chaomixian.vflow.core.workflow.module.device.*
import com.chaomixian.vflow.core.workflow.module.file.ImportImageModule
import com.chaomixian.vflow.core.workflow.module.file.SaveImageModule
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
            // 新增：对分类进行排序，将触发器放在最前
            .toSortedMap(compareBy {
                when (it) {
                    "触发器" -> 0
                    "数据" -> 1
                    "文件" -> 2 // 新分类的位置
                    "设备" -> 3
                    "逻辑控制" -> 4
                    "其他" -> 5
                    else -> 99
                }
            })
    }

    fun initialize() {
        modules.clear()

        //触发器
        register(ManualTriggerModule())

        // 数据
        register(CalculationModule())
        register(SetVariableModule())
        register(InputModule())
        register(QuickViewModule())

        // 文件 (新增)
        register(ImportImageModule())
        register(SaveImageModule())

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