package com.chaomixian.vflow.ui.workflow_editor.inspector

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.ActionModule
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.services.UiInspectorService

class WorkflowInspectorInsertController(
    private val activity: Activity,
    private val actionSteps: MutableList<ActionStep>,
    private val recyclerView: RecyclerView,
    private val onStepsChanged: () -> Unit
) {
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            @Suppress("DEPRECATION")
            val request = intent?.getParcelableExtra<WorkflowInspectorInsertRequest>(
                WorkflowInspectorInsertRequest.EXTRA_INSERT_REQUEST
            ) ?: return
            handleInsertRequest(request)
        }
    }

    fun register() {
        LocalBroadcastManager.getInstance(activity).registerReceiver(
            receiver,
            IntentFilter(WorkflowInspectorInsertRequest.ACTION_INSERT_REQUEST)
        )
    }

    fun unregister() {
        LocalBroadcastManager.getInstance(activity).unregisterReceiver(receiver)
    }

    fun startInspector() {
        val intent = Intent(activity, UiInspectorService::class.java).apply {
            putExtra(WorkflowInspectorInsertRequest.EXTRA_ENABLE_WORKFLOW_INSERT, true)
        }
        activity.startService(intent)
        Toast.makeText(activity, R.string.editor_toast_ui_inspector_started, Toast.LENGTH_SHORT).show()
    }

    private fun handleInsertRequest(request: WorkflowInspectorInsertRequest) {
        val success = when (request.type) {
            WorkflowInspectorInsertRequest.Type.CLICK -> {
                insertModule(
                    moduleId = "vflow.device.click",
                    parameters = linkedMapOf("target" to (request.value ?: ""))
                )
            }
            WorkflowInspectorInsertRequest.Type.UI_SELECTOR -> {
                insertModule(
                    moduleId = "vflow.interaction.ui_selector",
                    parameters = linkedMapOf("selector" to (request.value ?: ""))
                )
            }
            WorkflowInspectorInsertRequest.Type.LAUNCH_APP -> {
                insertModule(
                    moduleId = "vflow.system.launch_app",
                    parameters = linkedMapOf(
                        "packageName" to (request.packageName ?: ""),
                        "activityName" to (request.activityName ?: "LAUNCH")
                    )
                )
            }
            WorkflowInspectorInsertRequest.Type.LAUNCH_ACTIVITY -> {
                insertModule(
                    moduleId = "vflow.system.launch_app",
                    parameters = linkedMapOf(
                        "packageName" to (request.packageName ?: ""),
                        "activityName" to (request.activityName ?: "")
                    )
                )
            }
            WorkflowInspectorInsertRequest.Type.FIND_TEXT -> {
                insertModule(
                    moduleId = "vflow.device.find.text",
                    parameters = linkedMapOf("targetText" to (request.value ?: ""))
                )
            }
        }

        if (!success) {
            Toast.makeText(activity, R.string.editor_toast_module_insert_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun insertModule(
        moduleId: String,
        parameters: LinkedHashMap<String, Any?>
    ): Boolean {
        if (parameters.values.any { value ->
                when (value) {
                    is String -> value.isBlank()
                    null -> true
                    else -> false
                }
            }
        ) {
            return false
        }

        val module = ModuleRegistry.getModule(moduleId) ?: return false
        val stepsToAdd = module.createSteps()
        if (stepsToAdd.isEmpty()) return false

        val firstStep = stepsToAdd.first()
        val updatedParameters = applyParameterUpdates(module, firstStep, parameters)
        actionSteps.add(firstStep.copy(parameters = updatedParameters))
        if (stepsToAdd.size > 1) {
            actionSteps.addAll(stepsToAdd.drop(1))
        }

        onStepsChanged()
        recyclerView.post {
            recyclerView.smoothScrollToPosition((recyclerView.adapter?.itemCount ?: 1) - 1)
        }
        Toast.makeText(
            activity,
            activity.getString(R.string.editor_toast_module_inserted, module.metadata.getLocalizedName(activity)),
            Toast.LENGTH_SHORT
        ).show()
        return true
    }

    private fun applyParameterUpdates(
        module: ActionModule,
        baseStep: ActionStep,
        parameters: LinkedHashMap<String, Any?>
    ): Map<String, Any?> {
        var currentStep = baseStep
        for ((parameterId, value) in parameters) {
            val updated = module.onParameterUpdated(currentStep, parameterId, value)
            currentStep = currentStep.copy(parameters = updated)
        }
        return currentStep.parameters
    }
}
