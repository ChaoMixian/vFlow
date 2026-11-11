package com.chaomixian.vflow.api.mcp

import android.content.Context
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.types.serialization.VObjectGsonAdapter
import com.chaomixian.vflow.core.utils.StorageManager
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.core.workflow.model.WorkflowReentryBehavior
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.io.File
import java.util.UUID

/**
 * MCP 会话中成功执行的一次模块调用。
 *
 * 只记录成功执行的动作；失败、权限不足、状态查询和导出工具本身不会进入历史。
 */
internal data class RecordedMcpOperation(
    val toolName: String,
    val moduleId: String,
    val arguments: JsonObject,
    val summary: String,
    val timestamp: Long
)

internal data class RemovedMcpStep(
    val toolName: String,
    val summary: String,
    val reason: String
)

internal data class McpWorkflowExportResult(
    val workflow: Workflow?,
    val json: String,
    val saved: Boolean,
    val filePath: String?,
    val keptSteps: Int,
    val removedSteps: List<RemovedMcpStep>,
    val error: String? = null,
    val warning: String? = null
)

/**
 * 把本次 MCP 会话中执行过的动作转换成 vFlow 工作流。
 *
 * 清理规则是确定性的，不依赖外部模型：
 * - 移除状态/查找/OCR 等只用于观察的调用；
 * - 移除完全重复的调用；
 * - 只保留成功执行过的动作；
 * - 保留用户显式要求保留的观察调用。
 */
internal class McpWorkflowExporter(private val context: Context) {

    private val gson: Gson = GsonBuilder()
        .registerTypeHierarchyAdapter(VObject::class.java, VObjectGsonAdapter())
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create()

    private val observationTools = setOf(
        "vflow_system_get_phone_status",
        "vflow_core_screen_status",
        "vflow_system_get_screen_state",
        "vflow_interaction_get_current_activity",
        "vflow_interaction_find_element",
        "vflow_device_find_text",
        "vflow_interaction_find_until",
        "vflow_snippet_find_until",
        "vflow_interaction_find_image",
        "vflow_interaction_ocr",
        "vflow_interaction_ui_selector",
        "vflow_system_get_clipboard",
        "vflow_core_get_clipboard",
        "vflow_system_get_battery_status",
        "vflow_system_get_usage_stats",
        "vflow_system_systeminfo",
        "vflow_system_find_installed_app",
        "vflow_network_get_ip",
        "vflow_notification_find",
        "vflow_core_volume_state",
        "vflow_core_wifi_state",
        "vflow_core_bluetooth_state",
        "vflow_core_nfc_state",
        "vflow_core_hotspot_state",
        "vflow_ui_interaction_get_value",
        "vflow_system_location_range_check"
    )

    private val observationModuleIds = setOf(
        "vflow.system.get_phone_status",
        "vflow.core.screen_status",
        "vflow.system.get_screen_state",
        "vflow.interaction.get_current_activity",
        "vflow.interaction.find_element",
        "vflow.device.find_text",
        "vflow.interaction.find_until",
        "vflow.interaction.find_image",
        "vflow.interaction.ocr",
        "vflow.interaction.ui_selector",
        "vflow.system.get_clipboard",
        "vflow.core.get_clipboard",
        "vflow.system.get_battery_status",
        "vflow.system.get_usage_stats",
        "vflow.system.systeminfo",
        "vflow.system.find_installed_app",
        "vflow.network.get_ip",
        "vflow.notification.find",
        "vflow.core.volume_state",
        "vflow.core.wifi_state",
        "vflow.core.bluetooth_state",
        "vflow.core.nfc_state",
        "vflow.core.hotspot_state",
        "vflow.ui.interaction.get_value",
        "vflow.system.location_range_check"
    )

    fun export(
        operations: List<RecordedMcpOperation>,
        name: String,
        description: String,
        save: Boolean,
        keepObservations: Boolean,
        dryRun: Boolean
    ): McpWorkflowExportResult {
        if (operations.isEmpty()) {
            return McpWorkflowExportResult(
                workflow = null,
                json = "",
                saved = false,
                filePath = null,
                keptSteps = 0,
                removedSteps = emptyList(),
                error = "本次 MCP 会话还没有成功执行过可导出的动作。"
            )
        }

        val removed = mutableListOf<RemovedMcpStep>()
        val kept = mutableListOf<RecordedMcpOperation>()
        val seen = HashSet<String>()

        operations.forEach { operation ->
            val argsKey = gson.toJson(operation.arguments)
            val operationKey = "${operation.toolName}|$argsKey"
            val isObservation = operation.toolName in observationTools ||
                operation.moduleId in observationModuleIds

            when {
                !keepObservations && isObservation -> removed += RemovedMcpStep(
                    operation.toolName,
                    operation.summary,
                    "观察类调用，导出时不需要"
                )

                !seen.add(operationKey) -> removed += RemovedMcpStep(
                    operation.toolName,
                    operation.summary,
                    "完全重复的调用"
                )

                else -> kept += operation
            }
        }

        if (kept.isEmpty()) {
            return McpWorkflowExportResult(
                workflow = null,
                json = "",
                saved = false,
                filePath = null,
                keptSteps = 0,
                removedSteps = removed,
                error = "清理后没有剩余的实际动作；请先执行至少一个真正的操作。"
            )
        }

        val workflowName = name.ifBlank {
            "MCP 导出工作流 ${java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}"
        }
        val workflowDescription = description.ifBlank {
            "由 vFlow MCP 自动导出；已移除观察类和重复调用，只保留实际执行的动作。"
        }
        val workflowId = "mcp_export_${UUID.randomUUID()}"
        val steps = kept.map { operation ->
            ActionStep(
                moduleId = operation.moduleId,
                parameters = buildParameters(operation)
            )
        }
        val manualTrigger = ActionStep(
            moduleId = "vflow.trigger.manual",
            parameters = emptyMap()
        )
        val workflow = Workflow(
            id = workflowId,
            name = workflowName,
            triggers = listOf(manualTrigger),
            steps = steps,
            isEnabled = true,
            description = workflowDescription,
            author = "vFlow MCP",
            tags = listOf("mcp", "auto-export"),
            maxExecutionTime = 300,
            reentryBehavior = WorkflowReentryBehavior.STOP_CURRENT_AND_RUN_NEW
        )

        var saved = false
        var filePath: String? = null
        var warning: String? = null
        var finalWorkflow = workflow
        var json = gson.toJson(workflow)

        if (save && !dryRun) {
            try {
                val manager = WorkflowManager(context)
                manager.saveWorkflow(workflow)
                saved = true
                finalWorkflow = manager.getWorkflow(workflow.id) ?: workflow
                json = gson.toJson(finalWorkflow)
            } catch (e: Exception) {
                warning = "工作流已生成，但保存到 vFlow 失败：${e.message ?: e.javaClass.simpleName}"
            }
        }

        if (!dryRun) {
            try {
                val exportsDir = File(StorageManager.rootDir, "exports").apply { mkdirs() }
                val safeName = workflowName
                    .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    .take(80)
                    .ifBlank { "workflow" }
                val file = File(exportsDir, "${safeName}_${workflowId.takeLast(8)}.json")
                file.writeText(json, Charsets.UTF_8)
                filePath = file.absolutePath
            } catch (e: Exception) {
                warning = listOfNotNull(warning, "JSON 文件写入失败：${e.message ?: e.javaClass.simpleName}").joinToString("；")
            }
        }

        return McpWorkflowExportResult(
            workflow = finalWorkflow,
            json = json,
            saved = saved,
            filePath = filePath,
            keptSteps = steps.size,
            removedSteps = removed,
            warning = warning
        )
    }

    /**
     * MCP 工具参数只包含用户显式传入的字段；合并模块默认参数后，导出的步骤在
     * vFlow 中单独执行时才有完整的默认值。
     */
    private fun buildParameters(operation: RecordedMcpOperation): Map<String, Any?> {
        val defaults = runCatching {
            ModuleRegistry.getModule(operation.moduleId)
                ?.createSteps()
                ?.firstOrNull()
                ?.parameters
                .orEmpty()
        }.getOrDefault(emptyMap())

        val parameters = LinkedHashMap<String, Any?>(defaults)
        operation.arguments.entrySet().forEach { (key, value) ->
            parameters[key] = jsonToKotlin(value)
        }
        return parameters
    }

    private fun jsonToKotlin(element: JsonElement): Any? {
        if (element.isJsonNull) return null
        if (element.isJsonPrimitive) {
            val primitive = element.asJsonPrimitive
            return when {
                primitive.isBoolean -> primitive.asBoolean
                primitive.isString -> primitive.asString
                primitive.isNumber -> {
                    val raw = primitive.asString
                    raw.toIntOrNull()
                        ?: raw.toLongOrNull()
                        ?: raw.toDoubleOrNull()
                        ?: raw
                }
                else -> primitive.asString
            }
        }
        if (element.isJsonArray) {
            val array = element.asJsonArray
            return array.map { jsonToKotlin(it) }
        }
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            return obj.entrySet().associate { it.key to jsonToKotlin(it.value) }
        }
        return null
    }
}
