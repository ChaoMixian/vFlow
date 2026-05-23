package com.chaomixian.vflow.services

import android.Manifest
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.logging.ExecutionLogger
import com.chaomixian.vflow.core.logging.LogManager
import com.chaomixian.vflow.core.locale.LocaleManager
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.core.workflow.module.scripted.ModuleManager
import com.chaomixian.vflow.core.workflow.module.triggers.handlers.VoiceTriggerHandler
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.speech.voice.VoiceTriggerConfig
import com.chaomixian.vflow.speech.voice.VoiceTriggerModelManager
import com.chaomixian.vflow.ui.common.AppearanceManager
import com.chaomixian.vflow.ui.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class VoiceTriggerService : Service() {
    companion object {
        private const val TAG = "VoiceTriggerService"
        private const val CHANNEL_ID = "voice_trigger_service_channel"
        private const val NOTIFICATION_ID = 2002
        private const val VOICE_TRIGGER_MODULE_ID = "vflow.trigger.voice_template"

        @Volatile
        private var running = false

        fun startIfEligible(context: Context) {
            dispatchIfAllowed(
                context,
                Intent(context, VoiceTriggerService::class.java).apply {
                    action = TriggerService.ACTION_RELOAD_TRIGGERS
                }
            )
        }

        fun notifyWorkflowChanged(context: Context, newWorkflow: Workflow, oldWorkflow: Workflow?) {
            val delta = WorkflowTriggerDelta(
                workflowId = newWorkflow.id,
                oldTriggerRefs = oldWorkflow
                    ?.toAutoTriggerSpecs()
                    ?.filter { it.type == VOICE_TRIGGER_MODULE_ID }
                    ?.map { WorkflowTriggerRef(triggerId = it.triggerId, type = it.type) }
                    .orEmpty()
            )
            dispatchIfAllowed(
                context,
                Intent(context, VoiceTriggerService::class.java).apply {
                    action = TriggerService.ACTION_WORKFLOW_CHANGED
                    putExtra(TriggerService.EXTRA_TRIGGER_DELTA, delta)
                }
            )
        }

        fun notifyWorkflowRemoved(context: Context, removedWorkflow: Workflow) {
            val delta = WorkflowTriggerDelta(
                workflowId = removedWorkflow.id,
                oldTriggerRefs = removedWorkflow
                    .toAutoTriggerSpecs()
                    .filter { it.type == VOICE_TRIGGER_MODULE_ID }
                    .map { WorkflowTriggerRef(triggerId = it.triggerId, type = it.type) }
            )
            dispatchIfAllowed(
                context,
                Intent(context, VoiceTriggerService::class.java).apply {
                    action = TriggerService.ACTION_WORKFLOW_REMOVED
                    putExtra(TriggerService.EXTRA_TRIGGER_DELTA, delta)
                }
            )
        }

        fun reloadTriggers(context: Context) {
            dispatchIfAllowed(
                context,
                Intent(context, VoiceTriggerService::class.java).apply {
                    action = TriggerService.ACTION_RELOAD_TRIGGERS
                }
            )
        }

        private fun dispatchIfAllowed(context: Context, intent: Intent) {
            if (!running && !canStartNewInstance(context)) {
                DebugLogger.d(TAG, "Skip starting voice trigger service for action=${intent.action}")
                return
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (t: Throwable) {
                DebugLogger.w(TAG, "Failed to dispatch voice trigger service action=${intent.action}", t)
            }
        }

        private fun canStartNewInstance(context: Context): Boolean {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
            val state = ActivityManager.RunningAppProcessInfo()
            ActivityManager.getMyMemoryState(state)
            return state.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
        }
    }

    private lateinit var workflowManager: WorkflowManager
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var voiceTriggerHandler: VoiceTriggerHandler? = null

    override fun attachBaseContext(newBase: Context) {
        val languageCode = LocaleManager.getLanguage(newBase)
        val localizedContext = LocaleManager.applyLanguage(newBase, languageCode)
        val context = AppearanceManager.applyDisplayScale(localizedContext)
        super.attachBaseContext(context)
    }

    override fun onCreate() {
        super.onCreate()
        running = true
        workflowManager = WorkflowManager(applicationContext)

        DebugLogger.initialize(applicationContext)
        ModuleRegistry.initialize(applicationContext)
        ModuleManager.loadModules(applicationContext)
        ExecutionNotificationManager.initialize(this)
        LogManager.initialize(applicationContext)
        ExecutionLogger.initialize(this, serviceScope)

        if (!startForegroundSafely()) {
            return
        }
        ensureFreshHandler()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            TriggerService.ACTION_WORKFLOW_CHANGED -> {
                val delta = intent.getParcelableExtra<WorkflowTriggerDelta>(TriggerService.EXTRA_TRIGGER_DELTA)
                if (delta != null) {
                    handleWorkflowChanged(delta)
                } else {
                    reloadVoiceTriggers()
                }
            }
            TriggerService.ACTION_WORKFLOW_REMOVED -> {
                val delta = intent.getParcelableExtra<WorkflowTriggerDelta>(TriggerService.EXTRA_TRIGGER_DELTA)
                if (delta != null) {
                    handleWorkflowRemoved(delta)
                } else {
                    reloadVoiceTriggers()
                }
            }
            TriggerService.ACTION_RELOAD_TRIGGERS,
            null -> reloadVoiceTriggers()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        voiceTriggerHandler?.stop(this)
        voiceTriggerHandler = null
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun reloadVoiceTriggers() {
        if (!hasPrerequisites()) {
            DebugLogger.d(TAG, "Voice trigger prerequisites are incomplete, stopping service.")
            stopSelf()
            return
        }

        val activeWorkflows = workflowManager.getAllWorkflows().filter { workflow ->
            workflow.isEnabled && workflow.hasTriggerType(VOICE_TRIGGER_MODULE_ID)
        }
        if (activeWorkflows.isEmpty()) {
            DebugLogger.d(TAG, "No active voice-trigger workflows, stopping service.")
            stopSelf()
            return
        }

        ensureFreshHandler()

        var addedTriggerCount = 0
        activeWorkflows.forEach { workflow ->
            val missingPermissions = PermissionManager.getMissingPermissions(this, workflow)
            if (missingPermissions.isNotEmpty()) {
                return@forEach
            }

            workflow.toAutoTriggerSpecs()
                .filter { it.type == VOICE_TRIGGER_MODULE_ID }
                .forEach { trigger ->
                    voiceTriggerHandler?.addTrigger(this, trigger)
                    addedTriggerCount++
                }
        }

        if (addedTriggerCount == 0) {
            DebugLogger.d(TAG, "No eligible voice-trigger specs were added, stopping service.")
            stopSelf()
        }
    }

    private fun ensureFreshHandler() {
        voiceTriggerHandler?.stop(this)
        voiceTriggerHandler = VoiceTriggerHandler().also { it.start(this) }
    }

    private fun handleWorkflowChanged(delta: WorkflowTriggerDelta) {
        val latestWorkflow = workflowManager.getWorkflow(delta.workflowId)
        delta.oldTriggerRefs.forEach { triggerRef ->
            voiceTriggerHandler?.removeTrigger(this, triggerRef.triggerId)
        }
        if (latestWorkflow == null || !latestWorkflow.isEnabled) {
            reloadVoiceTriggers()
            return
        }

        if (!latestWorkflow.hasTriggerType(VOICE_TRIGGER_MODULE_ID)) {
            reloadVoiceTriggers()
            return
        }

        val missingPermissions = PermissionManager.getMissingPermissions(this, latestWorkflow)
        if (missingPermissions.isNotEmpty()) {
            reloadVoiceTriggers()
            return
        }

        ensureFreshHandlerIfNeeded()
        latestWorkflow.toAutoTriggerSpecs()
            .filter { it.type == VOICE_TRIGGER_MODULE_ID }
            .forEach { trigger ->
                voiceTriggerHandler?.addTrigger(this, trigger)
            }
    }

    private fun handleWorkflowRemoved(delta: WorkflowTriggerDelta) {
        delta.oldTriggerRefs.forEach { triggerRef ->
            voiceTriggerHandler?.removeTrigger(this, triggerRef.triggerId)
        }
        reloadVoiceTriggers()
    }

    private fun ensureFreshHandlerIfNeeded() {
        if (voiceTriggerHandler == null) {
            ensureFreshHandler()
        }
    }

    private fun hasPrerequisites(): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        if (!VoiceTriggerModelManager(this).isModelInstalled()) {
            return false
        }
        return VoiceTriggerConfig.hasTemplates(this)
    }

    private fun startForegroundSafely(): Boolean {
        return try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
            true
        } catch (t: Throwable) {
            DebugLogger.w(TAG, "Failed to enter microphone foreground-service mode", t)
            stopSelf()
            false
        }
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.voice_trigger_service_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            MainActivity.createAppLaunchIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.voice_trigger_service_notification_title))
            .setContentText(getString(R.string.voice_trigger_service_notification_text))
            .setSmallIcon(R.drawable.ic_workflows)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
    }
}
