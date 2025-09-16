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
import com.chaomixian.vflow.core.logging.ExecutionLogger
import com.chaomixian.vflow.core.logging.LogManager
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.core.workflow.module.triggers.AppStartTriggerModule
import com.chaomixian.vflow.core.workflow.module.triggers.KeyEventTriggerModule
import com.chaomixian.vflow.core.workflow.module.triggers.handlers.AppStartTriggerHandler
import com.chaomixian.vflow.core.workflow.module.triggers.handlers.ITriggerHandler
import com.chaomixian.vflow.core.workflow.module.triggers.handlers.KeyEventTriggerHandler
// [新增] 导入 TriggerHandlerRegistry
import com.chaomixian.vflow.core.workflow.module.triggers.handlers.TriggerHandlerRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

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
        ModuleRegistry.initialize()
        TriggerHandlerRegistry.initialize() // [新增] 确保服务独立运行时也能初始化注册表
        ExecutionNotificationManager.initialize(this)
        LogManager.initialize(applicationContext)
        ExecutionLogger.initialize(applicationContext, serviceScope) // 使用服务的协程作用域

        // 在服务创建时就注册并启动所有处理器
        registerAndStartHandlers()
        Log.d(TAG, "TriggerService 已创建并启动了 ${triggerHandlers.size} 个触发器处理器。")

        // 首次启动时，加载所有活动的触发器
        loadAllActiveTriggers()
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
        Log.d(TAG, "TriggerService 首次启动，加载 ${activeWorkflows.size} 个活动的触发器。")
        activeWorkflows.forEach { workflow ->
            getHandlerForWorkflow(workflow)?.addWorkflow(this, workflow)
        }
    }

    /**
     * 处理单个工作流的变更。
     * 无论何种变更（修改、禁用、启用），都先尝试移除旧的工作流实例，
     * 然后再根据新实例的状态决定是否要添加。
     */
    private fun handleWorkflowChanged(newWorkflow: Workflow, oldWorkflow: Workflow?) {
        val newHandler = getHandlerForWorkflow(newWorkflow)
        val oldHandler = oldWorkflow?.let { getHandlerForWorkflow(it) }

        // 场景1: 如果存在旧的工作流版本，总是先从对应的处理器中移除它。
        // 这确保了对现有工作流的修改能被正确处理。
        if (oldWorkflow != null && oldHandler != null) {
            Log.d(TAG, "正在为更新准备，从处理器移除旧版本: ${oldWorkflow.name}")
            oldHandler.removeWorkflow(this, oldWorkflow.id)
        }

        // 场景2: 如果新的工作流是启用状态，则将其添加到对应的处理器中。
        // 这会处理“新增”、“启用”以及“修改后依然启用”的情况。
        if (newWorkflow.isEnabled && newHandler != null) {
            Log.d(TAG, "向处理器添加/更新: ${newWorkflow.name}")
            newHandler.addWorkflow(this, newWorkflow)
        }
    }


    /**
     * 处理被删除的工作流。
     */
    private fun handleWorkflowRemoved(removedWorkflow: Workflow) {
        Log.d(TAG, "处理器正在移除已删除的工作流: ${removedWorkflow.name}")
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
        Log.d(TAG, "TriggerService 已销毁。")
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