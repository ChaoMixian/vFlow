// 文件: main/java/com/chaomixian/vflow/core/module/ModuleRegistry.kt
package com.chaomixian.vflow.core.module

import android.content.ContentValues.TAG
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.workflow.module.data.*
import com.chaomixian.vflow.core.workflow.module.file.*
import com.chaomixian.vflow.core.workflow.module.interaction.*
import com.chaomixian.vflow.core.workflow.module.logic.*
import com.chaomixian.vflow.core.workflow.module.network.*
import com.chaomixian.vflow.core.workflow.module.notification.*
import com.chaomixian.vflow.core.workflow.module.shizuku.*
import com.chaomixian.vflow.core.workflow.module.system.*
import com.chaomixian.vflow.core.workflow.module.triggers.*
import com.chaomixian.vflow.core.workflow.module.snippet.*

object ModuleRegistry {
    private val modules = mutableMapOf<String, ActionModule>()
    private var isCoreInitialized = false // 这里的Core是指内建额度模块

    fun register(module: ActionModule) {
        if (modules.containsKey(module.id)) {
            DebugLogger.w(TAG,"警告: 模块ID '${module.id}' 被重复注册。")
        }
        modules[module.id] = module
    }

    fun getModule(id: String): ActionModule? = modules[id]
    fun getAllModules(): List<ActionModule> = modules.values.toList()

    fun getModulesByCategory(): Map<String, List<ActionModule>> {
        return modules.values
            .filter { it.blockBehavior.type != BlockType.BLOCK_END && it.blockBehavior.type != BlockType.BLOCK_MIDDLE }
            .groupBy { it.metadata.category }
            .toSortedMap(compareBy {
                when (it) {
                    "触发器" -> 0
                    "界面交互" -> 1
                    "逻辑控制" -> 2
                    "数据" -> 3
                    "文件" -> 4
                    "网络" -> 5
                    "应用与系统" -> 6
                    "Shizuku" -> 7
                    "模板" -> 8
                    else -> 99
                }
            })
    }

    /**
     * 强制重置注册表。
     * 用于在删除模块后清空缓存，以便重新加载。
     */
    fun reset() {
        modules.clear()
        isCoreInitialized = false
    }

    fun initialize() {
        // 如果核心模块已经注册过，就不再执行 modules.clear()，防止误删用户模块
        if (isCoreInitialized) return

        modules.clear()

        // 触发器
        register(ManualTriggerModule())
        register(ReceiveShareTriggerModule())
        register(AppStartTriggerModule())
        register(KeyEventTriggerModule())
        register(TimeTriggerModule())
        register(BatteryTriggerModule())
        register(WifiTriggerModule())
        register(BluetoothTriggerModule())
        register(SmsTriggerModule())
        register(NotificationTriggerModule())

        // 界面交互
        register(FindTextModule())
        register(ClickModule())
        register(ScreenOperationModule())
        register(SendKeyEventModule())
        register(InputTextModule())
        register(CaptureScreenModule())
        register(OCRModule())
        register(AgentModule())
        register(AutoGLMModule())
        register(FindTextUntilModule())

        // 逻辑控制
        register(IfModule())
        register(ElseModule())
        register(EndIfModule())
        register(LoopModule())
        register(EndLoopModule())
        register(ForEachModule())
        register(EndForEachModule())
        register(JumpModule())
        register(WhileModule())
        register(EndWhileModule())
        register(BreakLoopModule())
        register(ContinueLoopModule())
        register(StopWorkflowModule())
        register(CallWorkflowModule())
        register(StopAndReturnModule())

        // 数据
        register(CreateVariableModule())
        register(ModifyVariableModule())
        register(GetVariableModule())
        register(CalculationModule())
        register(TextProcessingModule())

        // 文件
        register(ImportImageModule())
        register(SaveImageModule())
        register(AdjustImageModule())
        register(RotateImageModule())
        register(ApplyMaskModule())

        // 网络
        register(GetIpAddressModule())
        register(HttpRequestModule())
        register(AIModule())

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
        register(SendNotificationModule())
        register(WifiModule())
        register(BluetoothModule())
        register(BrightnessModule())
        register(ReadSmsModule())
        register(FindNotificationModule())
        register(RemoveNotificationModule())
        register(GetAppUsageStatsModule())
        register(InvokeModule())

        // Shizuku 模块
        register(ShellCommandModule())
        register(AlipayShortcutsModule())
        register(WeChatShortcutsModule())
        register(ColorOSShortcutsModule())
        register(GeminiAssistantModule())

        // Snippet 模板
        register(FindTextUntilSnippet())

        isCoreInitialized = true
    }
}