// 文件: main/java/com/chaomixian/vflow/core/module/ModuleRegistry.kt
package com.chaomixian.vflow.core.module

import android.content.Context
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

    fun register(module: ActionModule, context: Context? = null) {
        if (modules.containsKey(module.id)) {
            DebugLogger.w(TAG,"警告: 模块ID '${module.id}' 被重复注册。")
        }
        modules[module.id] = module

        // 如果是BaseModule且提供了Context，则注入Context
        if (context != null && module is BaseModule) {
            module.initContext(context)
        }
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

    /**
     * 初始化模块注册表
     * @param context Application Context，用于注入到模块中以支持国际化
     */
    fun initialize(context: Context) {
        // 如果核心模块已经注册过，就不再执行 modules.clear()，防止误删用户模块
        if (isCoreInitialized) return

        modules.clear()

        // 触发器
        register(ManualTriggerModule(), context)
        register(ReceiveShareTriggerModule(), context)
        register(AppStartTriggerModule(), context)
        register(KeyEventTriggerModule(), context)
        register(TimeTriggerModule(), context)
        register(BatteryTriggerModule(), context)
        register(WifiTriggerModule(), context)
        register(BluetoothTriggerModule(), context)
        register(SmsTriggerModule(), context)
        register(NotificationTriggerModule(), context)
        register(ElementTriggerModule(), context)
        register(GKDTriggerModule(), context)
        register(LocationTriggerModule(), context)

        // 界面交互
        register(FindTextModule(), context)
        register(FindElementModule(), context)
        register(UiSelectorModule(), context)
        register(ClickModule(), context)
        register(ScreenOperationModule(), context)
        register(SendKeyEventModule(), context)
        register(InputTextModule(), context)
        register(CaptureScreenModule(), context)
        register(OCRModule(), context)
        register(AgentModule(), context)
        register(AutoGLMModule(), context)
        register(FindTextUntilModule(), context)
        register(FindImageModule(), context)
        register(OperitModule(), context)

        // 逻辑控制
        register(IfModule(), context)
        register(ElseModule(), context)
        register(EndIfModule(), context)
        register(LoopModule(), context)
        register(EndLoopModule(), context)
        register(ForEachModule(), context)
        register(EndForEachModule(), context)
        register(JumpModule(), context)
        register(WhileModule(), context)
        register(EndWhileModule(), context)
        register(BreakLoopModule(), context)
        register(ContinueLoopModule(), context)
        register(StopWorkflowModule(), context)
        register(CallWorkflowModule(), context)
        register(StopAndReturnModule(), context)

        // 数据
        register(CreateVariableModule(), context)
        register(RandomVariableModule(), context)
        register(ModifyVariableModule(), context)
        register(GetVariableModule(), context)
        register(CalculationModule(), context)
        register(TextProcessingModule(), context)
        register(TextSplitModule(), context)
        register(TextReplaceModule(), context)
        register(TextExtractModule(), context)
        register(Base64EncodeOrDecodeModule(), context)
        register(CommentModule(), context)
        register(FileOperationModule(), context)

        // 文件
        register(ImportImageModule(), context)
        register(SaveImageModule(), context)
        register(AdjustImageModule(), context)
        register(RotateImageModule(), context)
        register(ApplyMaskModule(), context)

        // 网络
        register(GetIpAddressModule(), context)
        register(HttpRequestModule(), context)
        register(AIModule(), context)

        // 应用与系统
        register(DelayModule(), context)
        register(InputModule(), context)
        register(QuickViewModule(), context)
        register(ToastModule(), context)
        register(LuaModule(), context)
        register(LaunchAppModule(), context)
        register(CloseAppModule(), context)
        register(GetClipboardModule(), context)
        register(SetClipboardModule(), context)
        register(ShareModule(), context)
        register(SendNotificationModule(), context)
        register(WifiModule(), context)
        register(BluetoothModule(), context)
        register(BrightnessModule(), context)
        register(WakeScreenModule(), context)
        register(SleepScreenModule(), context)
        register(ReadSmsModule(), context)
        register(FindNotificationModule(), context)
        register(RemoveNotificationModule(), context)
        register(GetAppUsageStatsModule(), context)
        register(InvokeModule(), context)
        register(SystemInfoModule(), context)
        register(PlayAudioModule(), context)

        // Core (Beta) 模块
        // 网络控制组
        register(CoreBluetoothModule(), context)           // 蓝牙控制（开启/关闭/切换）
        register(CoreBluetoothStateModule(), context)      // 读取蓝牙状态
        register(CoreWifiModule(), context)                // WiFi控制（开启/关闭/切换）
        register(CoreWifiStateModule(), context)           // 读取WiFi状态
        register(CoreNfcModule(), context)                 // NFC控制（开启/关闭/切换）
        register(CoreNfcStateModule(), context)            // 读取NFC状态
        register(CoreSetClipboardModule(), context)        // 设置剪贴板
        register(CoreGetClipboardModule(), context)        // 读取剪贴板
        // 屏幕控制组
        register(CoreWakeScreenModule(), context)          // 唤醒屏幕
        register(CoreSleepScreenModule(), context)         // 关闭屏幕
        register(CoreScreenStatusModule(), context)        // 读取屏幕状态
        register(CoreCaptureScreenModule(), context)       // 截屏（Core）
        // 输入交互组
        register(CoreScreenOperationModule(), context)     // 屏幕操作（点击/滑动）
        register(CoreInputTextModule(), context)           // 输入文本
        register(CorePressKeyModule(), context)            // 按键
        register(CoreTouchReplayModule(), context)         // 触摸回放
        // 应用管理组
        register(CoreForceStopAppModule(), context)        // 强制停止应用
        // 系统控制组
        register(CoreShellCommandModule(), context)        // 执行命令
        register(CoreVolumeModule(), context)              // 音量控制
        register(CoreVolumeStateModule(), context)         // 读取音量

        // Shizuku 模块
        register(ShellCommandModule(), context)
        register(AlipayShortcutsModule(), context)
        register(WeChatShortcutsModule(), context)
        register(ColorOSShortcutsModule(), context)
        register(GeminiAssistantModule(), context)

        // Snippet 模板
        register(FindTextUntilSnippet(), context)

        // UI 组件模块
        // 容器块 (Activity / 悬浮窗 / 对话框)
        register(CreateActivityModule(), context)
        register(ShowActivityModule(), context)
        register(EndActivityModule(), context)
        register(CreateFloatWindowModule(), context)
        register(ShowFloatWindowModule(), context)
        register(EndFloatWindowModule(), context)


        // UI 组件 (文本 / 输入 / 按钮 / 开关)
        register(UiTextModule(), context)
        register(UiInputModule(), context)
        register(UiButtonModule(), context)
        register(UiSwitchModule(), context)

        // 交互逻辑 (事件监听 / 更新 / 关闭 / 获取值)
        register(OnUiEventModule(), context)
        register(EndOnUiEventModule(), context)
        register(UpdateUiComponentModule(), context)
        register(GetComponentValueModule(), context)
        register(ExitActivityModule(), context)

        isCoreInitialized = true
    }
}