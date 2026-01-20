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
import com.chaomixian.vflow.core.workflow.module.ui.blocks.*
import com.chaomixian.vflow.core.workflow.module.ui.components.*
import com.chaomixian.vflow.core.workflow.module.core.*

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
                    "Core (Beta)" -> 7
                    "Shizuku" -> 8
                    "模板" -> 9
                    "UI 组件" -> 10
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
        register(FindImageModule())

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
        register(RandomVariableModule())
        register(ModifyVariableModule())
        register(GetVariableModule())
        register(CalculationModule())
        register(TextProcessingModule())
        register(Base64EncodeOrDecodeModule())

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
        register(CloseAppModule())
        register(GetClipboardModule())
        register(SetClipboardModule())
        register(ShareModule())
        register(SendNotificationModule())
        register(WifiModule())
        register(BluetoothModule())
        register(BrightnessModule())
        register(WakeScreenModule())
        register(SleepScreenModule())
        register(ReadSmsModule())
        register(FindNotificationModule())
        register(RemoveNotificationModule())
        register(GetAppUsageStatsModule())
        register(InvokeModule())
        register(SystemInfoModule())

        // Core (Beta) 模块
        // 网络控制组
        register(CoreBluetoothModule())           // 蓝牙控制（开启/关闭/切换）
        register(CoreBluetoothStateModule())      // 读取蓝牙状态
        register(CoreWifiModule())                // WiFi控制（开启/关闭/切换）
        register(CoreWifiStateModule())           // 读取WiFi状态
        register(CoreSetClipboardModule())        // 设置剪贴板
        register(CoreGetClipboardModule())        // 读取剪贴板
        // 屏幕控制组
        register(CoreWakeScreenModule())          // 唤醒屏幕
        register(CoreSleepScreenModule())         // 关闭屏幕
        // 输入交互组
        register(CoreScreenOperationModule())     // 屏幕操作（点击/滑动）
        register(CoreInputTextModule())           // 输入文本
        register(CorePressKeyModule())            // 按键
        // 应用管理组
        register(CoreForceStopAppModule())        // 强制停止应用
        // 系统控制组
        register(CoreShellCommandModule())        // 执行命令

        // Shizuku 模块
        register(ShellCommandModule())
        register(AlipayShortcutsModule())
        register(WeChatShortcutsModule())
        register(ColorOSShortcutsModule())
        register(GeminiAssistantModule())

        // Snippet 模板
        register(FindTextUntilSnippet())

        // UI 组件模块
        // 容器块 (Activity / 悬浮窗 / 对话框)
        register(CreateActivityModule())
        register(ShowActivityModule())
        register(EndActivityModule())
        register(CreateFloatWindowModule())
        register(ShowFloatWindowModule())
        register(EndFloatWindowModule())


        // UI 组件 (文本 / 输入 / 按钮 / 开关)
        register(UiTextModule())
        register(UiInputModule())
        register(UiButtonModule())
        register(UiSwitchModule())

        // 交互逻辑 (事件监听 / 更新 / 关闭 / 获取值)
        register(OnUiEventModule())
        register(EndOnUiEventModule())
        register(UpdateUiComponentModule())
        register(GetComponentValueModule())
        register(ExitActivityModule())

        isCoreInitialized = true
    }
}