// 文件: main/java/com/chaomixian/vflow/services/TriggerService.kt
// 描述: 统一的后台服务，实现了持久化处理器和精细化任务分发。

package com.chaomixian.vflow.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.logging.ExecutionLogger
import com.chaomixian.vflow.core.logging.LogManager
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.core.workflow.module.triggers.KeyEventTriggerModule
import com.chaomixian.vflow.core.workflow.module.triggers.handlers.ITriggerHandler
import com.chaomixian.vflow.core.workflow.module.triggers.handlers.KeyEventTriggerHandler
import com.chaomixian.vflow.core.workflow.module.triggers.handlers.TriggerHandlerRegistry
import com.chaomixian.vflow.permissions.PermissionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TriggerService : Service() {

    private lateinit var workflowManager: WorkflowManager
    // 处理器现在是服务的持久成员，在 onCreate 时创建
    private val triggerHandlers = mutableMapOf<String, ITriggerHandler>()
    // 为服务创建一个独立的协程作用域
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())


    companion object {
        private const val TAG = "TriggerServiceManager"
        private const val NOTIFICATION_ID = 2
        private const val CHANNEL_ID = "trigger_service_channel"
        // 新增的精确指令 Actions
        const val ACTION_WORKFLOW_CHANGED = "com.chaomixian.vflow.ACTION_WORKFLOW_CHANGED"
        const val ACTION_WORKFLOW_REMOVED = "com.chaomixian.vflow.ACTION_WORKFLOW_REMOVED"
        const val EXTRA_WORKFLOW = "extra_workflow"
        const val EXTRA_OLD_WORKFLOW = "extra_old_workflow"
    }

    override fun onCreate() {
        super.onCreate()
        workflowManager = WorkflowManager(applicationContext)

        // 让服务变得自给自足，无论应用进程是否存活，都能正确初始化所有依赖项。
        // 这可以修复在后台被杀后触发工作流导致的 UninitializedPropertyAccessException 崩溃。
        DebugLogger.initialize(applicationContext) // 确保服务独立运行时也能初始化
        ModuleRegistry.initialize()
        TriggerHandlerRegistry.initialize() // [新增] 确保服务独立运行时也能初始化注册表
        ExecutionNotificationManager.initialize(this)
        LogManager.initialize(applicationContext)
        ExecutionLogger.initialize(applicationContext, serviceScope) // 使用服务的协程作用域

        // 在服务创建时就注册并启动所有处理器
        registerAndStartHandlers()
        DebugLogger.d(TAG, "TriggerService 已创建并启动了 ${triggerHandlers.size} 个触发器处理器。")

        // 首次启动时，加载所有活动的触发器
        loadAllActiveTriggers()

        // [新增] 在服务创建时（如开机后）检查并应用启动设置
        checkAndApplyStartupSettings()
    }

    /**
     * 检查并应用 Shizuku 相关的启动设置
     */
    private fun checkAndApplyStartupSettings() {
        val prefs = getSharedPreferences("vFlowPrefs", Context.MODE_PRIVATE)
        val autoEnableAccessibility = prefs.getBoolean("autoEnableAccessibility", false)
        val forceKeepAlive = prefs.getBoolean("forceKeepAliveEnabled", false)

        // 只有当任一开关为 true 时才执行检查
        if (autoEnableAccessibility || forceKeepAlive) {
            serviceScope.launch {
                // 延迟几秒，确保 Shizuku 服务在开机后有足够的时间准备好
                delay(10000)
                if (ShizukuManager.isShizukuActive(this@TriggerService)) {
                    DebugLogger.d(TAG, "正在从后台服务应用启动设置...")
                    if (autoEnableAccessibility) {
                        ShizukuManager.enableAccessibilityService(this@TriggerService)
                        DebugLogger.d(TAG, "已在启动时自动启用无障碍服务。")
                    }
                    if (forceKeepAlive) {
                        ShizukuManager.startWatcher(this@TriggerService)
                        DebugLogger.d(TAG, "已在启动时自动启动 Shizuku 守护。")
                    }
                } else {
                    DebugLogger.w(TAG, "无法应用启动设置: Shizuku 未激活。")
                }
            }
        }
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())

        // 处理来自 TriggerServiceProxy 的精确指令
        when (intent?.action) {
            ACTION_WORKFLOW_CHANGED -> {
                val newWorkflow = intent.getParcelableExtra<Workflow>(EXTRA_WORKFLOW)
                val oldWorkflow = intent.getParcelableExtra<Workflow>(EXTRA_OLD_WORKFLOW)
                if (newWorkflow != null) {
                    handleWorkflowChanged(newWorkflow, oldWorkflow)
                }
            }
            ACTION_WORKFLOW_REMOVED -> {
                val removedWorkflow = intent.getParcelableExtra<Workflow>(EXTRA_WORKFLOW)
                if (removedWorkflow != null) {
                    handleWorkflowRemoved(removedWorkflow)
                }
            }
            // 按键事件直接分发
            KeyEventTriggerHandler.ACTION_KEY_EVENT_RECEIVED -> {
                (triggerHandlers[KeyEventTriggerModule().id] as? KeyEventTriggerHandler)?.handleKeyEventIntent(this, intent)
            }
        }

        return START_STICKY
    }

    /**
     * 此方法现在从 TriggerHandlerRegistry 动态加载处理器，而不是硬编码。
     */
    private fun registerAndStartHandlers() {
        triggerHandlers.clear()
        // 从注册表获取所有已注册的处理器工厂
        val factories = TriggerHandlerRegistry.getAllHandlerFactories()
        factories.forEach { (triggerId, factory) ->
            // 通过工厂函数创建处理器实例
            val handler = factory()
            triggerHandlers[triggerId] = handler
        }
        // 启动所有处理器
        triggerHandlers.values.forEach { it.start(this) }
    }


    /**
     * 在服务首次启动时，加载所有已启用的工作流。
     */
    private fun loadAllActiveTriggers() {
        val activeWorkflows = workflowManager.getAllWorkflows().filter { it.isEnabled }
        DebugLogger.d(TAG, "TriggerService 首次启动，加载 ${activeWorkflows.size} 个活动的触发器。")
        activeWorkflows.forEach { workflow ->
            // 复用变更逻辑，确保启动时也进行权限检查
            handleWorkflowChanged(workflow, null)
        }
    }

    /**
     * 处理工作流变更，增加权限守卫。
     * @param newWorkflow 新的工作流状态。
     * @param oldWorkflow 旧的工作流状态，可能为null（表示新增）。
     */
    private fun handleWorkflowChanged(newWorkflow: Workflow, oldWorkflow: Workflow?) {
        val newHandler = getHandlerForWorkflow(newWorkflow)
        val oldHandler = oldWorkflow?.let { getHandlerForWorkflow(it) }

        // 步骤1：如果存在旧版本，无论如何都先从其处理器中移除，确保状态更新的原子性。
        if (oldWorkflow != null && oldHandler != null) {
            DebugLogger.d(TAG, "准备更新，正在从处理器中移除旧版: ${oldWorkflow.name}")
            oldHandler.removeWorkflow(this, oldWorkflow.id)
        }

        // 步骤2：处理新版本的工作流。
        if (newWorkflow.isEnabled) {
            // 用户意图是启用此工作流，现在检查权限。
            val missingPermissions = PermissionManager.getMissingPermissions(this, newWorkflow)

            if (missingPermissions.isEmpty()) {
                // 权限充足，可以安全地添加到处理器。
                if (newHandler != null) {
                    DebugLogger.d(TAG, "权限正常，正在向处理器添加/更新: ${newWorkflow.name}")
                    newHandler.addWorkflow(this, newWorkflow)
                }
                // 如果这个工作流之前因为权限问题被禁用过，现在权限已恢复，
                // 我们需要重置标记位并保存，以确保状态正确。
                if (newWorkflow.wasEnabledBeforePermissionsLost) {
                    val fixedWorkflow = newWorkflow.copy(wasEnabledBeforePermissionsLost = false)
                    workflowManager.saveWorkflow(fixedWorkflow)
                }
            } else {
                // 权限不足！这是关键的保护点。
                DebugLogger.w(TAG, "工作流 '${newWorkflow.name}' 因缺少权限 (${missingPermissions.joinToString { it.name }}) 将被自动禁用。")
                // 创建一个被禁用的副本，并设置标志位。
                val disabledWorkflow = newWorkflow.copy(
                    isEnabled = false,
                    wasEnabledBeforePermissionsLost = true // 记录下用户原本是想启用它的
                )
                // 保存这个被禁用的版本。这会再次触发 handleWorkflowChanged，
                // 但下一次调用时 isEnabled 为 false，流程会正确地将其从处理器中移除。
                workflowManager.saveWorkflow(disabledWorkflow)
            }
        } else {
            // 如果工作流本身就是禁用的，只需确保它已从处理器中移除即可。
            if (newHandler != null) {
                DebugLogger.d(TAG, "工作流 '${newWorkflow.name}' 已被禁用，正在从处理器中移除。")
                newHandler.removeWorkflow(this, newWorkflow.id)
            }
        }
    }


    /**
     * 处理被删除的工作流。
     */
    private fun handleWorkflowRemoved(removedWorkflow: Workflow) {
        DebugLogger.d(TAG, "处理器正在移除已删除的工作流: ${removedWorkflow.name}")
        getHandlerForWorkflow(removedWorkflow)?.removeWorkflow(this, removedWorkflow.id)
    }

    /**
     * 根据工作流的触发器类型查找对应的处理器。
     */
    private fun getHandlerForWorkflow(workflow: Workflow): ITriggerHandler? {
        val triggerType = workflow.triggerConfig?.get("type") as? String
        return triggerHandlers[triggerType]
    }

    override fun onDestroy() {
        super.onDestroy()
        triggerHandlers.values.forEach { it.stop(this) }
        // 服务销毁时，取消协程作用域
        serviceScope.cancel()
        DebugLogger.d(TAG, "TriggerService 已销毁。")
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "后台触发器服务", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("vFlow 后台服务")
            .setContentText("正在监听自动化触发器...")
            .setSmallIcon(R.drawable.ic_workflows)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}