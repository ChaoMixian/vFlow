// 文件: main/java/com/chaomixian/vflow/ui/workflow_editor/WorkflowAiGenerator.kt
package com.chaomixian.vflow.ui.workflow_editor

import android.content.ContentValues.TAG
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object WorkflowAiGenerator {

    private val gson = Gson()

    data class AiConfig(
        val provider: String,
        val baseUrl: String,
        val apiKey: String,
        val model: String,
        val temperature: Double = 0.2
    )

    private fun generateSystemPrompt(): String {
        val sb = StringBuilder()
        sb.append("""
            You are an expert configuration generator for the Android automation app "vFlow".
            Your task is to convert the user's natural language requirement into a valid JSON workflow configuration.
            
            ### JSON Structure Rules
            1. The root object represents a `Workflow`.
            2. `id`: Must be a unique UUID string.
            3. `name`: A descriptive name for the workflow (in Chinese).
            4. `isEnabled`: Boolean, usually true.
            5. `steps`: An array of `ActionStep` objects.
            
            ### CRITICAL RULE: TRIGGER FIRST
            1. **The first step (index 0) in the `steps` array MUST be a module from the '触发器' (Trigger) category.**
            2. If the user's requirement specifies a trigger (e.g., "when receiving SMS", "at 8:00 AM"), use the corresponding trigger module.
            3. If the user's requirement DOES NOT specify a trigger (e.g., "click this button", "extract text"), **YOU MUST USE 'vflow.trigger.manual' (手动触发) as the first step.**
            
            ### ActionStep Structure
            Each step in `steps` array has:
            - `id`: A unique UUID string (Critical: used for variable referencing).
            - `moduleId`: The exact ID of the module (see Module Definitions below).
            - `parameters`: A dictionary of input parameters.
            
            ### IMPORTANT LOGIC & VARIABLE RULES
            1. **Loop Indices Start at 1**: When using `Loop` ("vflow.logic.loop.start") or `ForEach` ("vflow.logic.foreach.start"), the output variables `loop_index` and `index` **start counting from 1** (1-based), NOT 0. Do not add +1 manually if the user asks for the "first" item.
            2. **Block Structure**: Every "Start" module (e.g., `If`, `Loop`, `ForEach`, `While`) **MUST** be closed by its corresponding "End" module (e.g., `EndIf`, `EndLoop`, `EndForEach`, `EndWhile`) later in the steps array.
            3. **Input Text**: The `Input Text` module types into the *currently focused* field. Therefore, it is almost always preceded by a `Click` action on the target input field to ensure focus.
            4. **Magic Variables & Property Access**:
               - Basic syntax: `{{STEP_ID.OUTPUT_ID}}` - Use the output of a previous step.
               - Property access: `{{STEP_ID.OUTPUT_ID.PROPERTY}}` - Access a property of a variable.
               - **Available Properties by Type**:
                 * **Image (图片)**: `.width` (宽度), `.height` (高度), `.path` (文件路径), `.size` (文件大小), `.name` (文件名)
                 * **List (列表)**: `.count` (数量), `.first` (第一项), `.last` (最后一项), `.random` (随机一项)
                 * **Dictionary (字典)**: `.count` (数量), `.keys` (所有键), `.values` (所有值)
                 * **Number (数字)**: `.int` (整数部分), `.round` (四舍五入), `.abs` (绝对值)
                 * **String (文本)**: `.length` (长度), `.uppercase` (大写), `.lowercase` (小写), `.trim` (去除首尾空格), `.removeSpaces` (去除所有空格)
                 * **Screen Element (界面元素)**: `.text` (文本内容), `.center_x` (中心X), `.center_y` (中心Y), `.width` (宽度), `.height` (高度)
                 * **Coordinate (坐标)**: `.x` (X坐标), `.y` (Y坐标)
               - **Example**:
                 * Step A (ID: "step_find_img") finds an image, outputs "element".
                 * Step B clicks the image: use `"target": "{{step_find_img.element.center_x}}", "{{step_find_img.element.center_y}}"`
                 * Step C gets image width: use `"value": "{{step_find_img.element.width}}"`

            Example:
            Step 1 (ID: "step_A"): Finds text, outputs "first_result".
            Step 2: Clicks the element found in Step 1.
            Parameter "target" in Step 2 should be: `{{step_A.first_result}}`

            ### Available Modules (Module Definitions)
            You must ONLY use the modules listed below. Pay attention to `moduleId`, `Inputs` (key names and types), and `Outputs` (for referencing).
            
        """.trimIndent())

        // 动态遍历注册表
        val allModules = ModuleRegistry.getAllModules()
            .sortedBy { it.metadata.category }

        var currentCategory = ""

        for (module in allModules) {
            // if (module.id.startsWith("vflow.ai")) continue

            // 阻止 AI 使用模板/Snippet，因为它们不是原子操作
            if (module.metadata.category == "模板" || module.id.contains("snippet")) continue

            if (module.metadata.category != currentCategory) {
                currentCategory = module.metadata.category
                sb.append("\n--- Category: $currentCategory ---\n")
            }

            sb.append("\n[Module: ${module.metadata.name}]\n")
            sb.append("  - Module ID: \"${module.id}\"\n")
            sb.append("  - Description: ${module.metadata.description}\n")

            // Inputs
            val inputs = module.getInputs().filter { !it.isHidden }
            if (inputs.isNotEmpty()) {
                sb.append("  - Inputs:\n")
                inputs.forEach { input ->
                    val typeStr = when(input.staticType) {
                        ParameterType.ENUM -> "Enum ${input.options}"
                        else -> input.staticType.name
                    }
                    val magicStr = if (input.acceptsMagicVariable) " [Accepts Variable]" else ""
                    sb.append("    * \"${input.id}\" ($typeStr)$magicStr: ${input.name}\n")
                }
            }

            // Outputs
            try {
                val outputs = module.getOutputs(null)
                if (outputs.isNotEmpty()) {
                    sb.append("  - Outputs:\n")
                    outputs.forEach { output ->
                        sb.append("    * \"${output.id}\": ${output.name}\n")
                    }
                }
            } catch (e: Exception) { }
        }

        sb.append("""
            
            ### Output Format
            Return ONLY valid JSON. No Markdown code blocks.
        """.trimIndent())

        return sb.toString()
    }

    suspend fun generateWorkflow(requirement: String, config: AiConfig): Result<Workflow> {
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(120, TimeUnit.SECONDS)
                    .build()

                val systemPrompt = generateSystemPrompt()

                val messages = listOf(
                    mapOf("role" to "system", "content" to systemPrompt),
                    mapOf("role" to "user", "content" to "Requirement: $requirement")
                )

                val payload = mapOf(
                    "model" to config.model,
                    "messages" to messages,
                    "temperature" to config.temperature,
                    "response_format" to mapOf("type" to "json_object")
                )

                val jsonBody = gson.toJson(payload)
                val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())

                val endpoint = if (config.baseUrl.endsWith("/")) "${config.baseUrl}chat/completions" else "${config.baseUrl}/chat/completions"

                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("Authorization", "Bearer ${config.apiKey}")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                val responseStr = response.body?.string()

                if (!response.isSuccessful || responseStr.isNullOrBlank()) {
                    DebugLogger.e(TAG, "API Request Failed: ${response.code} $responseStr")
                    return@withContext Result.failure(Exception("API Request Failed: ${response.code} $responseStr"))
                }

                val jsonObject = gson.fromJson(responseStr, JsonObject::class.java)
                val choices = jsonObject.getAsJsonArray("choices")
                if (choices == null || choices.size() == 0) {
                    DebugLogger.e(TAG, "Empty choices from API")
                    return@withContext Result.failure(Exception("Empty choices from API"))
                }

                val content = choices[0].asJsonObject.getAsJsonObject("message").get("content").asString
                val workflow = gson.fromJson(content, Workflow::class.java)

                Result.success(workflow)

            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}