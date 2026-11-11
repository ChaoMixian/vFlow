// 文件: main/java/com/chaomixian/vflow/core/workflow/module/system/GetPhoneStatusModule.kt
package com.chaomixian.vflow.core.workflow.module.system

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.Rect
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.ActionMetadata
import com.chaomixian.vflow.core.module.AiModuleRiskLevel
import com.chaomixian.vflow.core.module.BaseModule
import com.chaomixian.vflow.core.module.BlockType
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.core.module.OutputDefinition
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.module.ProgressUpdate
import com.chaomixian.vflow.core.module.ValidationResult
import com.chaomixian.vflow.core.module.directToolMetadata
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.types.complex.VImage
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.AccessibilityService as VFlowAccessibilityService
import com.chaomixian.vflow.services.ServiceStateBus
import com.chaomixian.vflow.services.ShellManager
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.util.ArrayDeque
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.resume

/**
 * 获取手机状态（权限感知）
 *
 * 这是 MCP 的“上下文入口”工具：先读取 vFlow 当前已授予的权限、每个 MCP 工具
 * 在当前权限下的可用状态，再采集设备状态、屏幕状态和屏幕截图。
 *
 * 为了在缺少权限时仍然可用，本模块本身不声明必需权限；它会把缺失能力写进 JSON，
 * 由调用方（AI）决定下一步是否请求权限或改用其他工具。
 */
class GetPhoneStatusModule : BaseModule() {

    override val id = "vflow.system.get_phone_status"

    override val metadata = ActionMetadata(
        name = "获取手机状态",
        nameStringRes = R.string.module_vflow_system_get_phone_status_name,
        description = "读取当前权限、工具可用状态、设备状态、屏幕状态和精简UI摘要，截图可选",
        descriptionStringRes = R.string.module_vflow_system_get_phone_status_desc,
        iconRes = R.drawable.rounded_fullscreen_portrait_24,
        category = "应用与系统",
        categoryId = "device"
    )

    override val aiMetadata = directToolMetadata(
        riskLevel = AiModuleRiskLevel.READ_ONLY,
        directToolDescription = "Permission-aware phone status snapshot. MUST be called once before any other vFlow MCP tool. By default returns a broad summary: permission counts and missing permissions, partial/unavailable tools, device/screen state, and all effective controls from the active window (no artificial upper limit by default), including unlabeled clickable containers, scrollable lists, input fields and labeled controls. Pass max_ui_nodes > 0 only when you want to cap the UI list. Screenshots and full permission/tool lists are opt-in.",
        workflowStepDescription = "Read permissions, tool availability, device state, screen state and a broad UI summary.",
    )

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "include_ui",
            name = "包含UI摘要",
            staticType = ParameterType.BOOLEAN,
            defaultValue = true,
            acceptsMagicVariable = false,
            hint = "返回当前活动窗口中所有可能用到的控件，包括有文字/描述、可点击、可滚动、可输入和可勾选的节点，默认开启"
        ),
        InputDefinition(
            id = "max_ui_nodes",
            name = "UI节点上限（0=无上限）",
            staticType = ParameterType.NUMBER,
            defaultValue = 0,
            acceptsMagicVariable = false,
            hint = "默认 0 表示不设上限，返回当前窗口全部有效控件；填大于 0 的值时最低按 20 处理"
        ),
        InputDefinition(
            id = "include_screenshot",
            name = "包含屏幕截图",
            staticType = ParameterType.BOOLEAN,
            defaultValue = false,
            acceptsMagicVariable = false,
            hint = "默认不截图；需要视觉信息时再显式开启，避免每次传输大图片"
        ),
        InputDefinition(
            id = "max_screenshot_width",
            name = "截图最大宽度",
            staticType = ParameterType.NUMBER,
            defaultValue = 1080,
            acceptsMagicVariable = true,
            hint = "默认 1080，范围 320-2160，用于控制返回图片大小"
        ),
        InputDefinition(
            id = "include_permission_details",
            name = "包含权限明细",
            staticType = ParameterType.BOOLEAN,
            defaultValue = false,
            acceptsMagicVariable = false,
            hint = "默认只返回权限统计和缺失权限；需要完整权限描述时开启"
        ),
        InputDefinition(
            id = "include_tool_details",
            name = "包含工具明细",
            staticType = ParameterType.BOOLEAN,
            defaultValue = false,
            acceptsMagicVariable = false,
            hint = "默认只返回工具统计及不可用/部分可用工具；需要全部工具清单时开启"
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id),
        OutputDefinition("permissions_granted", "已授予权限数", VTypeRegistry.NUMBER.id),
        OutputDefinition("permissions_total", "权限总数", VTypeRegistry.NUMBER.id),
        OutputDefinition("tools_available", "完全可用工具数", VTypeRegistry.NUMBER.id),
        OutputDefinition("tools_partial", "部分可用工具数", VTypeRegistry.NUMBER.id),
        OutputDefinition("tools_total", "工具总数", VTypeRegistry.NUMBER.id),
        OutputDefinition("screenshot", "屏幕截图", VTypeRegistry.IMAGE.id),
        OutputDefinition("status_json", "状态数据(JSON)", VTypeRegistry.STRING.id)
    )

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val appContext = context.applicationContext
        val includeScreenshot = context.getVariableAsBoolean("include_screenshot") ?: false
        val maxScreenshotWidth = (context.getVariableAsInt("max_screenshot_width") ?: 1080)
            .coerceIn(320, 2160)
        val includeUi = context.getVariableAsBoolean("include_ui") ?: true
        val rawMaxUiNodes = context.getVariableAsInt("max_ui_nodes") ?: 0
        val maxUiNodes = if (rawMaxUiNodes <= 0) {
            Int.MAX_VALUE
        } else {
            rawMaxUiNodes.coerceAtLeast(20)
        }
        val includePermissionDetails =
            context.getVariableAsBoolean("include_permission_details") ?: false
        val includeToolDetails =
            context.getVariableAsBoolean("include_tool_details") ?: false

        onProgress(ProgressUpdate("正在读取权限与工具可用状态..."))
        val permissionData = withContext(Dispatchers.IO) {
            buildPermissionData(appContext)
        }

        onProgress(ProgressUpdate("正在读取设备与屏幕状态..."))
        val deviceJson = withContext(Dispatchers.IO) {
            buildDeviceStatus(appContext)
        }
        val accessibilityService = context.services.get(VFlowAccessibilityService::class)
            ?: ServiceStateBus.getAccessibilityService()
        val screenJson = buildScreenStatus(appContext, accessibilityService)
        val uiJson = if (includeUi) {
            onProgress(ProgressUpdate("正在读取精简UI..."))
            withContext(Dispatchers.Default) {
                buildUiSummary(accessibilityService, maxUiNodes)
            }
        } else {
            JsonObject().apply {
                addProperty("requested", false)
                addProperty("available", false)
            }
        }

        var screenshotImage: VImage? = null
        val screenshotJson = JsonObject().apply {
            addProperty("requested", includeScreenshot)
            addProperty("captured", false)
            addProperty("skipped", !includeScreenshot)
        }

        if (includeScreenshot) {
            onProgress(ProgressUpdate("正在截取屏幕..."))
            // 截图是附加信息，不是状态工具的必需输出。无障碍和 Shell 回退都失败时，
            // 直接标记 skipped 并继续返回权限/设备/屏幕状态，避免整个工具失败。
            val attempt = runCatching {
                captureScreenshot(
                    appContext,
                    accessibilityService,
                    context.workDir,
                    maxScreenshotWidth
                )
            }.getOrElse {
                ScreenshotAttempt(
                    capture = null,
                    source = "none",
                    error = "截图异常，已跳过: ${it.message.orEmpty()}"
                )
            }
            val capture = attempt.capture
            if (capture != null) {
                screenshotImage = VImage(capture.file.absolutePath)
                screenshotJson.apply {
                    addProperty("captured", true)
                    addProperty("skipped", false)
                    addProperty("source", attempt.source)
                    addProperty("width", capture.width)
                    addProperty("height", capture.height)
                    addProperty("format", "jpeg")
                    addProperty("file", capture.file.absolutePath)
                    addProperty("timestamp", capture.timestamp)
                    attempt.errorCode?.let { addProperty("accessibility_error_code", it) }
                }
            } else {
                screenshotJson.addProperty("skipped", true)
                screenshotJson.addProperty("error", attempt.error ?: screenshotUnavailableReason(appContext))
                attempt.errorCode?.let { screenshotJson.addProperty("accessibility_error_code", it) }
            }
        }

        val permissionItems = permissionData.permissions
        val tools = permissionData.tools
        val availableCount = tools.count { it.status == "available" }
        val partialCount = tools.count { it.status == "partial" }
        val grantedCount = permissionItems.count { it.granted }

        val root = JsonObject().apply {
            addProperty("success", true)
            addProperty("timestamp", System.currentTimeMillis())
            addProperty("server", "vFlow MCP")
            addProperty("call_requirement", "Call this tool once before using any other vFlow MCP tool.")
            add("permissions", JsonObject().apply {
                addProperty("granted", grantedCount)
                addProperty("total", permissionItems.size)
                if (includePermissionDetails) {
                    add("items", JsonArray().apply {
                        permissionItems.forEach { permission ->
                            add(JsonObject().apply {
                                addProperty("id", permission.id)
                                addProperty("name", permission.name)
                                addProperty("description", permission.description)
                                addProperty("type", permission.type)
                                addProperty("granted", permission.granted)
                                if (permission.runtimePermissions.isNotEmpty()) {
                                    add("runtime_permissions", JsonArray().apply {
                                        permission.runtimePermissions.forEach(::add)
                                    })
                                }
                            })
                        }
                    })
                } else {
                    // 默认只传缺失权限，避免把 22 项权限描述全部塞进上下文。
                    add("missing", JsonArray().apply {
                        permissionItems.filterNot { it.granted }.forEach { permission ->
                            add(JsonObject().apply {
                                addProperty("id", permission.id)
                                addProperty("name", permission.name)
                            })
                        }
                    })
                }
            })
            add("tool_summary", JsonObject().apply {
                addProperty("total", tools.size)
                addProperty("available", availableCount)
                addProperty("partial", partialCount)
                addProperty("unavailable", tools.size - availableCount - partialCount)
            })
            if (includeToolDetails) {
                add("tools", JsonArray().apply {
                    tools.forEach { tool ->
                        add(JsonObject().apply {
                            addProperty("name", tool.name)
                            addProperty("module_id", tool.moduleId)
                            addProperty("title", tool.title)
                            addProperty("status", tool.status)
                            addProperty("available", tool.status == "available")
                            addProperty("partially_available", tool.status == "partial")
                            add("required_permissions", JsonArray().apply {
                                tool.requiredPermissions.forEach { add(it) }
                            })
                            add("granted_permissions", JsonArray().apply {
                                tool.grantedPermissions.forEach { add(it) }
                            })
                            add("missing_permissions", JsonArray().apply {
                                tool.missingPermissions.forEach { add(it) }
                            })
                        })
                    }
                })
            } else {
                // 默认只列出不能完全使用的工具；完整 142 条清单需要显式开启。
                add("tools", JsonObject().apply {
                    add("partial", buildCompactToolArray(tools.filter { it.status == "partial" }))
                    add("unavailable", buildCompactToolArray(tools.filter { it.status == "unavailable" }))
                })
            }
            add("ui", uiJson)
            add("device", deviceJson)
            add("screen", screenJson)
            add("screenshot", screenshotJson)
        }

        onProgress(
            ProgressUpdate(
                "已读取 ${permissionItems.size} 项权限、${tools.size} 个工具，" +
                    "截图${if (screenshotImage != null) "成功" else "不可用"}"
            )
        )

        val outputs = linkedMapOf<String, com.chaomixian.vflow.core.types.VObject>(
            "success" to VBoolean(true),
            "permissions_granted" to VNumber(grantedCount.toDouble()),
            "permissions_total" to VNumber(permissionItems.size.toDouble()),
            "tools_available" to VNumber(availableCount.toDouble()),
            "tools_partial" to VNumber(partialCount.toDouble()),
            "tools_total" to VNumber(tools.size.toDouble()),
            "status_json" to VString(root.toString())
        )
        screenshotImage?.let { outputs["screenshot"] = it }

        return ExecutionResult.Success(outputs)
    }

    override fun validate(step: ActionStep, allSteps: List<ActionStep>): ValidationResult {
        return ValidationResult(true)
    }

    override fun createSteps(): List<ActionStep> = listOf(
        ActionStep(moduleId = id, parameters = emptyMap())
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        return context.getString(R.string.summary_vflow_system_get_phone_status)
    }

    private data class PermissionEntry(
        val id: String,
        val name: String,
        val description: String,
        val type: String,
        val granted: Boolean,
        val runtimePermissions: List<String>,
    )

    private data class ToolEntry(
        val name: String,
        val moduleId: String,
        val title: String,
        val status: String,
        val requiredPermissions: List<String>,
        val grantedPermissions: List<String>,
        val missingPermissions: List<String>,
    )

    private data class PermissionData(
        val permissions: List<PermissionEntry>,
        val tools: List<ToolEntry>,
    )

    private data class ScreenshotCapture(
        val file: File,
        val width: Int,
        val height: Int,
        val timestamp: Long,
    )

    private data class ScreenshotAttempt(
        val capture: ScreenshotCapture?,
        val source: String,
        val errorCode: Int? = null,
        val error: String? = null,
    )

    private fun buildPermissionData(context: Context): PermissionData {
        // 显式补充常用权限，确保即使没有模块声明也能在状态中看到。
        val allPermissions = (
            PermissionManager.getAllRegisteredPermissions() + listOf(
                PermissionManager.ACCESSIBILITY,
                PermissionManager.NOTIFICATIONS,
                PermissionManager.MICROPHONE,
                PermissionManager.OVERLAY,
                PermissionManager.NOTIFICATION_LISTENER_SERVICE,
                PermissionManager.NOTIFICATION_POLICY,
                PermissionManager.STORAGE,
                PermissionManager.SMS,
                PermissionManager.BLUETOOTH,
                PermissionManager.WRITE_SETTINGS,
                PermissionManager.WRITE_SECURE_SETTINGS,
                PermissionManager.LOCATION,
                PermissionManager.SHIZUKU,
                PermissionManager.ROOT,
                PermissionManager.IGNORE_BATTERY_OPTIMIZATIONS,
                PermissionManager.EXACT_ALARM,
                PermissionManager.USAGE_STATS,
                PermissionManager.READ_PHONE_STATE
            )
            ).distinctBy { it.id }.sortedBy { it.id }

        val grantedMap = allPermissions.associate { permission ->
            permission.id to runCatching {
                PermissionManager.isGranted(context, permission)
            }.getOrDefault(false)
        }

        val permissionEntries = allPermissions.map { permission ->
            PermissionEntry(
                id = permission.id,
                name = permission.getLocalizedName(context),
                description = permission.getLocalizedDescription(context),
                type = permission.type.name.lowercase(Locale.ROOT),
                granted = grantedMap[permission.id] == true,
                runtimePermissions = permission.runtimePermissions
            )
        }

        val tools = ModuleRegistry.getAllModules()
            .asSequence()
            .filter { it.blockBehavior.type == BlockType.NONE }
            .filterNot { it.id.startsWith(TRIGGER_MODULE_PREFIX) }
            .mapNotNull { module ->
                val baseStep = runCatching {
                    module.createSteps().firstOrNull()
                }.getOrNull() ?: ActionStep(module.id, emptyMap())
                val required = runCatching {
                    module.getRequiredPermissions(baseStep)
                }.getOrDefault(emptyList()).distinctBy { it.id }
                val granted = required.filter { grantedMap[it.id] == true }
                val missing = required.filterNot { grantedMap[it.id] == true }
                val status = when {
                    missing.isEmpty() -> "available"
                    granted.isNotEmpty() -> "partial"
                    else -> "unavailable"
                }
                ToolEntry(
                    name = mcpToolName(module.id),
                    moduleId = module.id,
                    title = module.metadata.getLocalizedName(context),
                    status = status,
                    requiredPermissions = required.map { it.getLocalizedName(context) },
                    grantedPermissions = granted.map { it.getLocalizedName(context) },
                    missingPermissions = missing.map { it.getLocalizedName(context) }
                )
            }
            .sortedBy { it.name }
            .toList()

        return PermissionData(permissionEntries, tools)
    }

    private fun buildDeviceStatus(context: Context): JsonObject {
        val battery = readBattery(context)
        val memory = readMemory(context)
        val storage = readStorage(context)
        val network = readNetwork(context)

        return JsonObject().apply {
            add("build", JsonObject().apply {
                addProperty("manufacturer", Build.MANUFACTURER)
                addProperty("brand", Build.BRAND)
                addProperty("model", Build.MODEL)
                addProperty("device", Build.DEVICE)
                addProperty("product", Build.PRODUCT)
                addProperty("hardware", Build.HARDWARE)
                addProperty("board", Build.BOARD)
                addProperty("build_id", Build.ID)
                addProperty("fingerprint", Build.FINGERPRINT)
                addProperty("android_release", Build.VERSION.RELEASE)
                addProperty("sdk_int", Build.VERSION.SDK_INT)
                addProperty("security_patch", Build.VERSION.SECURITY_PATCH)
                add("supported_abis", JsonArray().apply {
                    Build.SUPPORTED_ABIS.forEach(::add)
                })
            })
            addProperty("uptime_ms", SystemClock.elapsedRealtime())
            addProperty("boot_time_ms", System.currentTimeMillis() - SystemClock.elapsedRealtime())
            addProperty("locale", Locale.getDefault().toLanguageTag())
            addProperty("timezone", TimeZone.getDefault().id)
            add("battery", battery)
            add("memory", memory)
            add("storage", storage)
            add("network", network)
        }
    }

    private fun buildScreenStatus(
        context: Context,
        service: AccessibilityService?,
    ): JsonObject {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val metrics = context.resources.displayMetrics
        val configuration = context.resources.configuration
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = windowManager.defaultDisplay

        val brightness = runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        }.getOrDefault(-1)

        val rootPackage = runCatching {
            val root = service?.rootInActiveWindow ?: return@runCatching ""
            val packageName = root.packageName?.toString().orEmpty()
            @Suppress("DEPRECATION")
            root.recycle()
            packageName
        }.getOrDefault("")
        val currentPackage = ServiceStateBus.lastActivityPackageName
            ?.takeIf { it.isNotBlank() }
            ?: rootPackage
        val windowPackage = ServiceStateBus.lastWindowPackageName
            ?.takeIf { it.isNotBlank() }
            ?: rootPackage

        return JsonObject().apply {
            addProperty("screen_on", powerManager.isInteractive)
            addProperty("interactive", powerManager.isInteractive)
            addProperty("locked", keyguardManager.isDeviceLocked)
            addProperty("unlocked", !keyguardManager.isDeviceLocked)
            addProperty("width_px", metrics.widthPixels)
            addProperty("height_px", metrics.heightPixels)
            addProperty("density_dpi", metrics.densityDpi)
            addProperty("density", metrics.density.toDouble())
            addProperty("orientation", orientationName(configuration.orientation))
            addProperty("rotation", rotationName(display.rotation))
            addProperty("rotation_degrees", rotationDegrees(display.rotation))
            addProperty("refresh_rate_hz", display.refreshRate.toDouble())
            addProperty("brightness", brightness)
            addProperty("accessibility_service_enabled",
                PermissionManager.isAccessibilityServiceEnabledInSettings(context))
            addProperty("accessibility_service_connected",
                ServiceStateBus.isAccessibilityServiceRunning())
            addProperty("current_package", currentPackage)
            addProperty("current_activity", ServiceStateBus.lastActivityClassName.orEmpty())
            addProperty("window_package", windowPackage)
            addProperty("window_class", ServiceStateBus.lastWindowClassName.orEmpty())
        }
    }

    /**
     * 默认只返回不可用/部分可用工具的精简条目，避免把 142 个工具的完整权限数组
     * 全部塞进 status_json。
     */
    private fun buildCompactToolArray(entries: List<ToolEntry>): JsonArray {
        return JsonArray().apply {
            entries.forEach { entry ->
                add(JsonObject().apply {
                    addProperty("name", entry.name)
                    addProperty("title", entry.title)
                    add("missing_permissions", JsonArray().apply {
                        entry.missingPermissions.forEach(::add)
                    })
                })
            }
        }
    }

    private data class UiCandidate(
        val text: String,
        val description: String,
        val hint: String,
        val viewId: String,
        val className: String,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val clickable: Boolean,
        val longClickable: Boolean,
        val editable: Boolean,
        val scrollable: Boolean,
        val focusable: Boolean,
        val focused: Boolean,
        val checkable: Boolean,
        val checked: Boolean,
        val selected: Boolean,
        val password: Boolean,
        val enabled: Boolean,
    ) {
        fun score(): Int = (text.length + description.length) * 10 +
            (if (clickable) 30 else 0) +
            (if (longClickable) 15 else 0) +
            (if (editable) 25 else 0) +
            (if (scrollable) 10 else 0) +
            (if (viewId.isNotEmpty()) 2 else 0)

        fun boundsKey(): String = "$left,$top,$right,$bottom"

        fun fullLabel(): String = "$text $description $hint".trim()
    }

    /**
     * 读取当前活动窗口的 UI 摘要。
     *
     * 目标是把 AI 可能用到的信息尽量都带上，同时仍然限制总传输量：
     * 有文字/描述/hint、可点击、可长按、可滚动、可输入、可勾选或带有效
     * viewId 的节点都会保留；只有完全不可见、屏幕外、宽高为 0 的节点，
     * 以及既无标签又无任何操作能力的纯布局容器会被过滤。
     * 同一 bounds 的无障碍节点只做属性合并，不再按父子包含关系删除节点，
     * 避免把卡片内真正可点击的子按钮误删。
     */
    private fun buildUiSummary(
        service: VFlowAccessibilityService?,
        maxNodes: Int,
    ): JsonObject {
        val result = JsonObject().apply {
            addProperty("requested", true)
            addProperty("available", false)
        }

        if (service == null) {
            result.addProperty("reason", "无障碍服务未连接")
            return result
        }

        // rootInActiveWindow 在部分 MIUI/桌面切换场景会短暂返回 null，
        // 此时从无障碍窗口列表里挑 active/focused 窗口的根节点作为兜底。
        val windows = runCatching { service.windows }.getOrDefault(emptyList())
        val root = runCatching { service.rootInActiveWindow }.getOrNull()
            ?: windows.firstOrNull { it.isActive }?.root
            ?: windows.firstOrNull { it.isFocused }?.root
            ?: windows.firstOrNull()?.root
        windows.forEach { runCatching { it.recycle() } }
        if (root == null) {
            result.addProperty("reason", "无法获取当前活动窗口")
            return result
        }

        val limit = maxNodes.coerceAtLeast(20)
        val maxVisited = (limit.toLong() * 40L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        // 先收集比最终上限更多的候选，再按 bounds 去重和择优，避免 BFS 前几个
        // 空壳容器把后面的真实文本节点挤掉。
        val maxCandidates = maxOf(
            (limit.toLong() * 8L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            300
        )
        val metrics = service.resources.displayMetrics
        val screenRect = Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
        // 这些纯布局容器即使带 viewId，也不会单独成为操作入口；有文字、
        // 可点击/可滚动等属性时仍会保留，不会因为类名被无条件丢弃。
        val layoutOnlyClasses = setOf(
            "View", "ViewGroup", "FrameLayout", "LinearLayout", "RelativeLayout",
            "ConstraintLayout", "CoordinatorLayout", "ScrollView",
            "NestedScrollView", "RecyclerView", "ComposeView", "AndroidComposeView",
        )
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        val candidates = LinkedHashMap<String, UiCandidate>()
        var visited = 0
        var truncated = false

        val rootPackage = root.packageName?.toString().orEmpty()
        val rootClass = root.className?.toString().orEmpty()
        queue.add(root to 0)

        try {
            while (queue.isNotEmpty()) {
                val (node, depth) = queue.removeFirst()
                visited++

                if (visited > maxVisited) {
                    truncated = true
                    node.recycle()
                    break
                }

                val visible = runCatching { node.isVisibleToUser }.getOrDefault(true)
                val bounds = Rect()
                runCatching { node.getBoundsInScreen(bounds) }
                val onScreen = bounds.width() > 0 && bounds.height() > 0 &&
                    Rect.intersects(bounds, screenRect)

                if (!visible || !onScreen) {
                    node.recycle()
                    continue
                }

                val text = runCatching { node.text?.toString()?.trim().orEmpty() }.getOrDefault("")
                val description = runCatching {
                    node.contentDescription?.toString()?.trim().orEmpty()
                }.getOrDefault("")
                val hint = runCatching { node.hintText?.toString()?.trim().orEmpty() }
                    .getOrDefault("")
                val rawViewId = runCatching { node.viewIdResourceName?.toString().orEmpty() }
                    .getOrDefault("")
                val shortViewId = rawViewId.substringAfterLast(":id/")
                val className = runCatching { node.className?.toString().orEmpty() }.getOrDefault("")
                val clickable = runCatching { node.isClickable }.getOrDefault(false)
                val longClickable = runCatching { node.isLongClickable }.getOrDefault(false)
                val editable = runCatching { node.isEditable }.getOrDefault(false)
                val checkable = runCatching { node.isCheckable }.getOrDefault(false)
                val scrollable = runCatching { node.isScrollable }.getOrDefault(false)
                val focusable = runCatching { node.isFocusable }.getOrDefault(false)
                val focused = runCatching { node.isFocused }.getOrDefault(false)
                val checked = runCatching { node.isChecked }.getOrDefault(false)
                val selected = runCatching { node.isSelected }.getOrDefault(false)
                val password = runCatching { node.isPassword }.getOrDefault(false)
                val enabled = runCatching { node.isEnabled }.getOrDefault(true)
                val hasLabel = text.isNotEmpty() || description.isNotEmpty() || hint.isNotEmpty()
                val actionable = clickable || longClickable || editable || checkable || scrollable
                val simpleClass = className.substringAfterLast('.')
                val hasUsefulId = shortViewId.isNotEmpty() && simpleClass !in layoutOnlyClasses

                // 不再只保留有文字的节点：无文字但可点击、可长按、可滚动、
                // 可输入、可勾选或带有效 viewId 的节点都可能是操作入口。
                // 只过滤完全没有语义、也没有任何操作能力的布局容器。
                if (hasLabel || actionable || hasUsefulId || focused) {
                    val candidate = UiCandidate(
                        text = text,
                        description = description,
                        hint = hint,
                        viewId = shortViewId,
                        className = simpleClass,
                        left = bounds.left,
                        top = bounds.top,
                        right = bounds.right,
                        bottom = bounds.bottom,
                        clickable = clickable,
                        longClickable = longClickable,
                        editable = editable,
                        scrollable = scrollable,
                        focusable = focusable,
                        focused = focused,
                        checkable = checkable,
                        checked = checked,
                        selected = selected,
                        password = password,
                        enabled = enabled,
                    )
                    val key = candidate.boundsKey()
                    val existing = candidates[key]
                    if (existing == null) {
                        candidates[key] = candidate
                        if (candidates.size >= maxCandidates) {
                            truncated = true
                            node.recycle()
                            break
                        }
                    } else {
                        // 同一区域有多个无障碍节点时，保留文本更丰富的那个，
                        // 同时合并可点击/可输入等操作属性。
                        val winner = if (candidate.score() > existing.score()) candidate else existing
                        candidates[key] = winner.copy(
                            clickable = existing.clickable || candidate.clickable,
                            longClickable = existing.longClickable || candidate.longClickable,
                            editable = existing.editable || candidate.editable,
                            scrollable = existing.scrollable || candidate.scrollable,
                            focusable = existing.focusable || candidate.focusable,
                            focused = existing.focused || candidate.focused,
                            checkable = existing.checkable || candidate.checkable,
                            checked = existing.checked || candidate.checked,
                            selected = existing.selected || candidate.selected,
                            password = existing.password || candidate.password,
                            enabled = existing.enabled && candidate.enabled,
                        )
                    }
                }

                if (depth < 100 && node.childCount > 0) {
                    for (index in 0 until node.childCount) {
                        val child = runCatching { node.getChild(index) }.getOrNull()
                        if (child != null) {
                            queue.add(child to depth + 1)
                        }
                    }
                }

                node.recycle()
            }

            // 仅按屏幕位置排序，不再按父子包含关系删除节点；同一 bounds
            // 的重复节点已经在收集阶段合并。这样卡片容器和其内部按钮、
            // 列表容器和列表项都会同时保留，AI 可以自行选择点击目标。
            val ordered = candidates.values.sortedWith(
                compareBy<UiCandidate>({ it.top }, { it.left })
            )
            val selected = ordered.take(limit)
            if (ordered.size > limit) truncated = true
            val nodes = JsonArray().apply {
                selected.forEach { candidate ->
                    add(JsonObject().apply {
                        if (candidate.text.isNotEmpty()) addProperty("text", candidate.text.take(120))
                        if (candidate.description.isNotEmpty()) {
                            addProperty("desc", candidate.description.take(80))
                        }
                        if (candidate.hint.isNotEmpty()) addProperty("hint", candidate.hint.take(60))
                        if (candidate.viewId.isNotEmpty()) addProperty("id", candidate.viewId)
                        if (candidate.className.isNotEmpty()) addProperty("class", candidate.className)
                        addProperty("bounds", candidate.boundsKey())
                        addProperty(
                            "center",
                            "${(candidate.left + candidate.right) / 2},${(candidate.top + candidate.bottom) / 2}"
                        )
                        if (candidate.clickable) addProperty("clickable", true)
                        if (candidate.longClickable) addProperty("long_clickable", true)
                        if (candidate.editable) addProperty("editable", true)
                        if (candidate.scrollable) addProperty("scrollable", true)
                        if (candidate.focusable) addProperty("focusable", true)
                        if (candidate.focused) addProperty("focused", true)
                        if (candidate.checkable) addProperty("checkable", true)
                        if (candidate.checked) addProperty("checked", true)
                        if (candidate.selected) addProperty("selected", true)
                        if (candidate.password) addProperty("password", true)
                        if (!candidate.enabled) addProperty("enabled", false)
                    })
                }
            }

            result.addProperty("available", true)
            result.addProperty("package", rootPackage)
            result.addProperty("window_class", rootClass)
            result.addProperty("count", nodes.size())
            result.addProperty("visited", visited)
            result.addProperty("truncated", truncated)
            result.add("nodes", nodes)
        } catch (e: Exception) {
            result.addProperty("reason", e.message ?: "读取 UI 失败")
        } finally {
            // 处理过程中被截断/异常退出时，队列中的节点也必须释放。
            while (queue.isNotEmpty()) {
                runCatching { queue.removeFirst().first.recycle() }
            }
        }

        return result
    }

    private fun readBattery(context: Context): JsonObject {
        val intent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) level * 100.0 / scale else -1.0
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val health = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val temperature = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        val technology = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY).orEmpty()

        return JsonObject().apply {
            addProperty("level", level)
            addProperty("scale", scale)
            addProperty("percent", if (percent >= 0) percent else -1.0)
            addProperty("status", batteryStatusName(status))
            addProperty("plugged", pluggedName(plugged))
            addProperty("charging", plugged != 0 || status == BatteryManager.BATTERY_STATUS_CHARGING)
            addProperty("health", batteryHealthName(health))
            addProperty("temperature_c", if (temperature >= 0) temperature / 10.0 else -1.0)
            addProperty("voltage_mv", voltage)
            addProperty("technology", technology)
        }
    }

    private fun readMemory(context: Context): JsonObject {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        return JsonObject().apply {
            addProperty("total_mb", info.totalMem / 1024.0 / 1024.0)
            addProperty("available_mb", info.availMem / 1024.0 / 1024.0)
            addProperty("threshold_mb", info.threshold / 1024.0 / 1024.0)
            addProperty("low_memory", info.lowMemory)
        }
    }

    private fun readStorage(context: Context): JsonObject {
        return runCatching {
            val stat = StatFs(context.filesDir.absolutePath)
            JsonObject().apply {
                addProperty("total_bytes", stat.blockCountLong * stat.blockSizeLong)
                addProperty("available_bytes", stat.availableBlocksLong * stat.blockSizeLong)
            }
        }.getOrElse {
            JsonObject().apply {
                addProperty("error", it.message.orEmpty())
            }
        }
    }

    private fun readNetwork(context: Context): JsonObject {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork
        val capabilities = network?.let { manager.getNetworkCapabilities(it) }
        val transports = JsonArray()
        if (capabilities != null) {
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) transports.add("wifi")
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) transports.add("cellular")
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) transports.add("ethernet")
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) transports.add("vpn")
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) transports.add("bluetooth")
        }
        return JsonObject().apply {
            addProperty("connected", capabilities != null)
            add("transports", transports)
            addProperty("validated", capabilities?.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true)
            addProperty("metered", capabilities?.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false)
        }
    }

    private suspend fun captureScreenshot(
        context: Context,
        service: AccessibilityService?,
        workDir: File,
        maxWidth: Int,
    ): ScreenshotAttempt {
        var accessibilityError: String? = null
        var accessibilityErrorCode: Int? = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && service != null) {
            val accessibilityAttempt = withTimeoutOrNull(ACCESSIBILITY_SCREENSHOT_TIMEOUT_MS) {
                captureWithAccessibility(context, service, workDir, maxWidth)
            } ?: ScreenshotAttempt(
                capture = null,
                source = "accessibility",
                error = "无障碍截图超时，已跳过"
            )
            if (accessibilityAttempt.capture != null) {
                return accessibilityAttempt
            }
            accessibilityError = accessibilityAttempt.error
            accessibilityErrorCode = accessibilityAttempt.errorCode
        } else {
            accessibilityError = when {
                service == null -> "无障碍服务未连接"
                else -> "Android 11 以下不支持无障碍截图"
            }
        }

        val shellAttempt = withTimeoutOrNull(SHELL_SCREENSHOT_TIMEOUT_MS) {
            captureWithShell(context, workDir, maxWidth)
        } ?: ScreenshotAttempt(
            capture = null,
            source = "shell",
            error = "Shell 截图超时，已跳过"
        )
        if (shellAttempt.capture != null) {
            return shellAttempt
        }

        return ScreenshotAttempt(
            capture = null,
            source = "none",
            errorCode = accessibilityErrorCode,
            error = listOfNotNull(accessibilityError, shellAttempt.error).joinToString("；")
        )
    }

    private suspend fun captureWithAccessibility(
        context: Context,
        service: AccessibilityService,
        workDir: File,
        maxWidth: Int,
    ): ScreenshotAttempt {
        var failureCode: Int? = null
        val result = try {
            suspendCancellableCoroutine<AccessibilityService.ScreenshotResult?> { continuation ->
                service.takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    context.mainExecutor,
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                            if (continuation.isActive) {
                                continuation.resume(screenshot)
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            failureCode = errorCode
                            if (continuation.isActive) {
                                continuation.resume(null)
                            }
                        }
                    }
                )
            }
        } catch (e: Exception) {
            return ScreenshotAttempt(
                capture = null,
                source = "accessibility",
                errorCode = failureCode,
                error = "无障碍截图异常: ${e.message.orEmpty()}"
            )
        } ?: return ScreenshotAttempt(
            capture = null,
            source = "accessibility",
            errorCode = failureCode,
            error = "无障碍截图回调失败（错误码 ${failureCode ?: -1}）"
        )

        val capture = runCatching {
            val hardwareBuffer = result.hardwareBuffer
            val wrapped = Bitmap.wrapHardwareBuffer(hardwareBuffer, result.colorSpace)
                ?: run {
                    hardwareBuffer.close()
                    return@runCatching null
                }
            val software = wrapped.copy(Bitmap.Config.ARGB_8888, false)
            hardwareBuffer.close()
            if (software == null) {
                return@runCatching null
            }
            saveBitmap(context, workDir, software, maxWidth)?.copy(timestamp = result.timestamp)
        }.getOrNull()

        return ScreenshotAttempt(
            capture = capture,
            source = "accessibility",
            errorCode = failureCode,
            error = if (capture == null) "无障碍截图解码失败" else null
        )
    }

    private suspend fun captureWithShell(
        context: Context,
        workDir: File,
        maxWidth: Int,
    ): ScreenshotAttempt {
        val hasShizuku = runCatching { ShellManager.isShizukuActive(context) }.getOrDefault(false)
        val hasRoot = if (hasShizuku) {
            false
        } else {
            runCatching { ShellManager.isRootAvailable() }.getOrDefault(false)
        }
        if (!hasShizuku && !hasRoot) {
            return ScreenshotAttempt(
                capture = null,
                source = "shell",
                error = "没有可用的 Shizuku/Root 权限"
            )
        }

        val tempDir = File("/sdcard/vFlow/temp").apply { mkdirs() }
        if (!tempDir.exists()) {
            return ScreenshotAttempt(
                capture = null,
                source = "shell",
                error = "无法创建 /sdcard/vFlow/temp"
            )
        }

        val remote = File(tempDir, "phone_status_${System.currentTimeMillis()}.png")
        val output = withContext(Dispatchers.IO) {
            runCatching {
                ShellManager.execShellCommand(
                    context,
                    "screencap -p \"${remote.absolutePath}\""
                )
            }.getOrElse { "Error: ${it.message.orEmpty()}" }
        }

        if (!remote.exists() || remote.length() <= 0L) {
            return ScreenshotAttempt(
                capture = null,
                source = "shell",
                error = "Shell 截图失败: ${output.take(160)}"
            )
        }

        val bitmap = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(remote.absolutePath)
        }
        if (bitmap == null) {
            remote.delete()
            return ScreenshotAttempt(
                capture = null,
                source = "shell",
                error = "Shell 截图文件无法解码"
            )
        }

        val capture = saveBitmap(context, workDir, bitmap, maxWidth)
        remote.delete()
        return ScreenshotAttempt(
            capture = capture,
            source = "shell",
            error = if (capture == null) "Shell 截图保存失败" else null
        )
    }

    private fun saveBitmap(
        context: Context,
        workDir: File,
        bitmap: Bitmap,
        maxWidth: Int,
    ): ScreenshotCapture? {
        return runCatching {
            val scaled = if (bitmap.width > maxWidth) {
                val targetHeight = (bitmap.height * (maxWidth.toFloat() / bitmap.width))
                    .toInt()
                    .coerceAtLeast(1)
                Bitmap.createScaledBitmap(bitmap, maxWidth, targetHeight, true)
            } else {
                bitmap
            }

            val targetDir = if (workDir.exists() || workDir.mkdirs()) {
                workDir
            } else {
                File(context.cacheDir, "phone-status").apply { mkdirs() }
            }
            val file = File(targetDir, "phone_status_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { output ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, output)
            }
            val capture = ScreenshotCapture(
                file = file,
                width = scaled.width,
                height = scaled.height,
                timestamp = System.currentTimeMillis()
            )
            if (scaled !== bitmap) {
                scaled.recycle()
            }
            bitmap.recycle()
            capture
        }.getOrNull()
    }

    private fun screenshotUnavailableReason(context: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return "Android 11 以下不支持无障碍截图"
        }
        if (!PermissionManager.isAccessibilityServiceEnabledInSettings(context)) {
            return "无障碍服务未启用"
        }
        if (!ServiceStateBus.isAccessibilityServiceRunning()) {
            return "无障碍服务未连接"
        }
        return "无障碍截图失败，可能被安全窗口拦截或服务未授予截图能力"
    }

    private fun mcpToolName(moduleId: String): String {
        val normalized = moduleId
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9_]+"), "_")
            .trim('_')
        return if (normalized.startsWith("vflow_")) normalized else "vflow_$normalized"
    }

    private fun orientationName(orientation: Int): String = when (orientation) {
        android.content.res.Configuration.ORIENTATION_LANDSCAPE -> "landscape"
        android.content.res.Configuration.ORIENTATION_PORTRAIT -> "portrait"
        else -> "undefined"
    }

    private fun rotationName(rotation: Int): String = when (rotation) {
        Surface.ROTATION_0 -> "0"
        Surface.ROTATION_90 -> "90"
        Surface.ROTATION_180 -> "180"
        Surface.ROTATION_270 -> "270"
        else -> "unknown"
    }

    private fun rotationDegrees(rotation: Int): Int = when (rotation) {
        Surface.ROTATION_0 -> 0
        Surface.ROTATION_90 -> 90
        Surface.ROTATION_180 -> 180
        Surface.ROTATION_270 -> 270
        else -> -1
    }

    private fun batteryStatusName(status: Int): String = when (status) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
        BatteryManager.BATTERY_STATUS_FULL -> "full"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not_charging"
        else -> "unknown"
    }

    private fun pluggedName(plugged: Int): String = when (plugged) {
        BatteryManager.BATTERY_PLUGGED_AC -> "ac"
        BatteryManager.BATTERY_PLUGGED_USB -> "usb"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
        else -> "none"
    }

    private fun batteryHealthName(health: Int): String = when (health) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "good"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheat"
        BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over_voltage"
        BatteryManager.BATTERY_HEALTH_COLD -> "cold"
        else -> "unknown"
    }

    companion object {
        private const val TRIGGER_MODULE_PREFIX = "vflow.trigger."
        private const val ACCESSIBILITY_SCREENSHOT_TIMEOUT_MS = 2_000L
        private const val SHELL_SCREENSHOT_TIMEOUT_MS = 4_000L
    }
}
