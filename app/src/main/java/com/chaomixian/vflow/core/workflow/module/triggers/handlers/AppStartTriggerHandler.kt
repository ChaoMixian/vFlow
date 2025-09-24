// 文件: main/java/com/chaomixian/vflow/core/workflow/module/triggers/handlers/AppStartTriggerHandler.kt
// 描述: 采用基于状态验证的稳定关闭检测逻辑
package com.chaomixian.vflow.core.workflow.module.triggers.handlers

import android.content.Context
import android.util.Log
import com.chaomixian.vflow.core.execution.WorkflowExecutor
import com.chaomixian.vflow.services.ServiceStateBus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class AppStartTriggerHandler : ListeningTriggerHandler() {

    private var collectorJob: Job? = null
    private var lastForegroundPackage: String? = null

    // 记录应用的打开状态和关闭检测任务
    private val appOpenStates = ConcurrentHashMap<String, Boolean>()
    private val pendingCloseChecks = ConcurrentHashMap<String, Job>()

    // 关闭检测的延迟时间和验证次数
    private val closeCheckDelay = 800L  // 增加延迟时间提高稳定性
    private val verificationDelay = 200L // 状态验证间隔

    companion object {
        private const val TAG = "AppStartTriggerHandler"
    }

    override fun getTriggerModuleId(): String = "vflow.trigger.app_start"

    // 常见的桌面应用包名列表
    private val launcherPackages = setOf(
        "com.android.launcher",      // 原生桌面
        "com.android.launcher3",     // Pixel Launcher
        "com.oppo.launcher",         // ColorOS 桌面
        "com.coloros.launcher3",     // ColorOS 新版桌面
        "com.oneplus.launcher",      // OnePlus 桌面
        "com.miui.home",             // MIUI 桌面
        "com.huawei.android.launcher", // 华为桌面
        "com.samsung.android.app.launcher", // 三星桌面
        "com.vivo.launcher",         // Vivo 桌面
        "com.realme.launcher",       // Realme 桌面
        "com.nothing.launcher"       // Nothing 桌面
    )

    // 需要忽略的系统包名
    private val ignoredPackages = setOf(
        "com.android.systemui",      // 系统UI（包括侧滑返回提示）
        "android",                   // 系统核心
        "com.android.settings",      // 设置应用的快速弹窗
        "com.android.documentsui",   // 文件选择器弹窗
        "com.android.permissioncontroller", // 权限弹窗
        "com.google.android.packageinstaller", // 安装包弹窗
        "com.miui.securitycenter",   // MIUI安全中心弹窗
        "com.coloros.safecenter",    // ColorOS安全中心弹窗
        "com.oppo.safe",             // OPPO安全应用弹窗
        "com.android.incallui",      // 通话界面
        "com.android.dialer"         // 拨号界面短暂出现
    )

    private var lastCheckTime = 0L
    private val minCheckInterval = 100L // 减少到100ms，提高响应性

    // 防误触机制：记录最近的窗口变化历史
    private val recentWindowHistory = mutableListOf<Pair<String, Long>>()
    private val historyKeepTime = 2000L // 保留2秒内的历史

    override fun startListening(context: Context) {
        if (collectorJob != null) return
        Log.d(TAG, "启动应用事件监听...")

        // 检测当前系统的桌面包名
        detectLauncherPackage(context)

        triggerScope.launch {
            // 获取初始前台应用包名
            lastForegroundPackage = getCurrentForegroundApp()
            lastForegroundPackage?.let { pkg ->
                if (!isLauncherPackage(pkg)) {
                    appOpenStates[pkg] = true
                    Log.d(TAG, "初始前台应用: $pkg")
                }
            }
        }

        collectorJob = ServiceStateBus.windowChangeEventFlow
            .filter { it.first.isNotBlank() && !ignoredPackages.contains(it.first) }
            .onEach { (packageName, className) ->
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastCheckTime >= minCheckInterval) {
                    // 更新窗口历史
                    updateWindowHistory(packageName, currentTime)

                    // 检查是否为有效的窗口变化
                    if (isValidWindowChange(packageName, currentTime)) {
                        handleAppChangeEvent(context, packageName, className)
                        lastCheckTime = currentTime
                        lastSuccessfulEventTime = currentTime // 记录成功处理事件的时间

                        // 在处理事件后，安排一个按需检查作为备份
                        scheduleOnDemandCheck(context, 8000L)
                    }
                }
            }
            .launchIn(triggerScope)

        // 初始化时间戳
        lastSuccessfulEventTime = System.currentTimeMillis()

        // 启动补充检查任务（仅在必要时使用，避免影响主要检测逻辑）
        // startPeriodicLauncherCheck(context)  // 暂时禁用，优先使用事件驱动方式

        // 启动一次性的初始状态检查
        scheduleOnDemandCheck(context, 10000L)
    }

    private var periodicCheckJob: Job? = null
    private var launcherReturnDebounceJob: Job? = null

    override fun stopListening(context: Context) {
        collectorJob?.cancel()
        periodicCheckJob?.cancel()
        launcherReturnDebounceJob?.cancel()
        pendingCloseChecks.values.forEach { it.cancel() }
        collectorJob = null
        periodicCheckJob = null
        launcherReturnDebounceJob = null
        lastForegroundPackage = null
        appOpenStates.clear()
        pendingCloseChecks.clear()
        recentWindowHistory.clear()
        Log.d(TAG, "应用事件监听已停止。")
    }

    /**
     * 更新窗口变化历史
     */
    private fun updateWindowHistory(packageName: String, timestamp: Long) {
        recentWindowHistory.add(packageName to timestamp)
        // 清理过期的历史记录
        recentWindowHistory.removeAll { (_, time) ->
            timestamp - time > historyKeepTime
        }
    }

    /**
     * 判断是否为有效的窗口变化（过滤误触和短暂弹窗）
     */
    private fun isValidWindowChange(packageName: String, currentTime: Long): Boolean {
        // 如果是桌面应用，需要额外验证
        if (isLauncherPackage(packageName)) {
            return isValidLauncherSwitch(packageName, currentTime)
        }

        // 对于普通应用，检查是否为短暂出现的弹窗
        val samePackageCount = recentWindowHistory.count { (pkg, time) ->
            pkg == packageName && currentTime - time <= 1000 // 1秒内
        }

        // 如果同一个包名在1秒内出现超过3次，可能是弹窗或异常，忽略
        if (samePackageCount > 3) {
            Log.d(TAG, "忽略可能的弹窗或异常窗口变化: $packageName (1秒内出现${samePackageCount}次)")
            return false
        }

        return true
    }

    /**
     * 验证桌面切换是否有效（防止误触）
     */
    private fun isValidLauncherSwitch(launcherPackage: String, currentTime: Long): Boolean {
        // 检查最近500ms内是否有频繁的桌面切换
        val recentLauncherSwitches = recentWindowHistory.count { (pkg, time) ->
            isLauncherPackage(pkg) && currentTime - time <= 500
        }

        if (recentLauncherSwitches > 1) {
            Log.d(TAG, "忽略可能的误触桌面切换: $launcherPackage")
            return false
        }

        return true
    }

    private fun handleAppChangeEvent(context: Context, newPackage: String, newClassName: String) {
        val previousPackage = lastForegroundPackage
        val isNewPackageLauncher = isLauncherPackage(newPackage)
        val wasPreviousPackageLauncher = previousPackage?.let { isLauncherPackage(it) } ?: false

        Log.d(TAG, "有效窗口变化: '$previousPackage' -> '$newPackage' (是否桌面: $isNewPackageLauncher)")

        // 如果切换到桌面，使用防抖机制
        if (isNewPackageLauncher) {
            handleSwitchToLauncherWithDebounce(context, previousPackage, newPackage)
            return
        }

        // 如果从桌面切换到应用，取消桌面防抖任务
        if (wasPreviousPackageLauncher) {
            launcherReturnDebounceJob?.cancel()
            handleAppOpenFromLauncher(context, newPackage, newClassName)
            lastForegroundPackage = newPackage
            return
        }

        // 如果新包名与旧包名相同，忽略
        if (newPackage == previousPackage) {
            return
        }

        // 普通的应用间切换
        lastForegroundPackage = newPackage
        handleAppOpenEvent(context, newPackage, newClassName)

        if (previousPackage != null && !wasPreviousPackageLauncher) {
            handlePotentialAppCloseEvent(context, previousPackage)
        }
    }

    /**
     * 带防抖的桌面切换处理
     */
    private fun handleSwitchToLauncherWithDebounce(context: Context, previousPackage: String?, newPackage: String) {
        // 取消之前的防抖任务
        launcherReturnDebounceJob?.cancel()

        launcherReturnDebounceJob = triggerScope.launch {
            delay(800L) // 800ms防抖时间

            // 验证当前确实还在桌面
            val currentApp = getCurrentForegroundApp()
            val isStillOnLauncher = currentApp?.let { isLauncherPackage(it) } ?: false

            if (isStillOnLauncher && previousPackage != null && !isLauncherPackage(previousPackage)) {
                Log.d(TAG, "确认用户返回桌面，应用 $previousPackage 已关闭")
                lastForegroundPackage = newPackage
                handleImmediateAppCloseEvent(context, previousPackage)
            } else {
                Log.d(TAG, "桌面切换被取消，用户可能只是短暂经过桌面")
            }

            launcherReturnDebounceJob = null
        }
    }

    private fun handleAppOpenFromLauncher(context: Context, packageName: String, className: String) {
        Log.d(TAG, "从桌面打开应用: $packageName")
        appOpenStates[packageName] = true
        checkForAppTrigger(context, packageName, className, "打开时")
    }

    private fun handleImmediateAppCloseEvent(context: Context, packageName: String) {
        // 取消之前的关闭检测任务
        pendingCloseChecks[packageName]?.cancel()
        pendingCloseChecks.remove(packageName)

        // 如果应用之前是打开状态，直接触发关闭事件
        if (appOpenStates[packageName] == true) {
            appOpenStates[packageName] = false
            Log.d(TAG, "确认应用关闭（返回桌面）: $packageName")
            checkForAppTrigger(context, packageName, "", "关闭时")
        }
    }

    private fun handleAppOpenEvent(context: Context, packageName: String, className: String) {
        val wasOpen = appOpenStates[packageName] == true

        if (!wasOpen) {
            // 应用从关闭状态变为打开状态
            appOpenStates[packageName] = true
            Log.d(TAG, "应用打开: $packageName")

            // 取消该应用的关闭检测任务（如果存在）
            pendingCloseChecks[packageName]?.cancel()
            pendingCloseChecks.remove(packageName)

            checkForAppTrigger(context, packageName, className, "打开时")
        } else {
            Log.d(TAG, "应用 $packageName 已处于打开状态，忽略重复打开事件")
        }
    }

    private fun handlePotentialAppCloseEvent(context: Context, packageName: String) {
        // 取消之前的关闭检测任务
        pendingCloseChecks[packageName]?.cancel()

        // 启动新的关闭检测任务
        val closeCheckJob = triggerScope.launch {
            delay(closeCheckDelay)

            // 执行多次验证以确保应用确实关闭了
            val isReallyClosedList = mutableListOf<Boolean>()
            repeat(3) { // 进行3次验证
                val currentForeground = getCurrentForegroundApp()
                val isReallyClosed = currentForeground != packageName
                isReallyClosedList.add(isReallyClosed)

                Log.d(TAG, "关闭验证 ${it + 1}/3 for $packageName: 当前前台=$currentForeground, 已关闭=$isReallyClosed")

                if (it < 2) delay(verificationDelay) // 不是最后一次验证时等待
            }

            // 如果大部分验证都确认应用已关闭，则认为应用真的关闭了
            val confirmedClosed = isReallyClosedList.count { it } >= 2

            if (confirmedClosed && appOpenStates[packageName] == true) {
                appOpenStates[packageName] = false
                Log.d(TAG, "确认应用关闭: $packageName")
                checkForAppTrigger(context, packageName, "", "关闭时")
            } else {
                Log.d(TAG, "应用 $packageName 未真正关闭，取消关闭事件")
            }

            pendingCloseChecks.remove(packageName)
        }

        pendingCloseChecks[packageName] = closeCheckJob
    }

    /**
     * 检测当前系统的桌面包名
     */
    private fun detectLauncherPackage(context: Context) {
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
            intent.addCategory(android.content.Intent.CATEGORY_HOME)
            val resolveInfo = context.packageManager.resolveActivity(intent, 0)
            val launcherPackage = resolveInfo?.activityInfo?.packageName

            if (launcherPackage != null && !launcherPackages.contains(launcherPackage)) {
                Log.d(TAG, "检测到系统桌面包名: $launcherPackage")
                // 动态添加到桌面包名列表
                (launcherPackages as MutableSet).add(launcherPackage)
            }
        } catch (e: Exception) {
            Log.w(TAG, "检测桌面包名失败: ${e.message}")
        }
    }

    /**
     * 判断是否为桌面应用
     */
    private fun isLauncherPackage(packageName: String): Boolean {
        return launcherPackages.contains(packageName)
    }

    // 记录上次成功处理事件的时间，用于检测是否有遗漏
    private var lastSuccessfulEventTime = 0L

    /**
     * 轻量级的状态检查，仅在特定条件下触发
     * 避免持续轮询，减少对系统的影响
     */
    private fun scheduleOnDemandCheck(context: Context, delayMs: Long = 5000L) {
        // 取消之前的检查任务
        periodicCheckJob?.cancel()

        periodicCheckJob = triggerScope.launch {
            delay(delayMs)

            try {
                val currentApp = getCurrentForegroundApp()
                val currentTime = System.currentTimeMillis()

                // 只有在长时间没有事件更新且当前应用与记录不符时才进行检查
                if (currentTime - lastSuccessfulEventTime > 10000L && // 10秒没有事件
                    currentApp != null &&
                    currentApp != lastForegroundPackage) {

                    Log.d(TAG, "执行按需检查: 记录=${lastForegroundPackage}, 实际=$currentApp")

                    val isCurrentLauncher = isLauncherPackage(currentApp)
                    val wasLastLauncher = lastForegroundPackage?.let { isLauncherPackage(it) } ?: false

                    if (isCurrentLauncher && !wasLastLauncher && lastForegroundPackage != null) {
                        Log.d(TAG, "按需检查发现状态不同步，修正状态")
                        handleImmediateAppCloseEvent(context, lastForegroundPackage!!)
                        lastForegroundPackage = currentApp
                        lastSuccessfulEventTime = currentTime
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "按需检查失败: ${e.message}")
            }
        }
    }

    /**
     * 获取当前前台应用包名
     */
    private fun getCurrentForegroundApp(): String? {
        return try {
            ServiceStateBus.getAccessibilityService()?.rootInActiveWindow?.packageName?.toString()
        } catch (e: Exception) {
            Log.w(TAG, "获取当前前台应用失败: ${e.message}")
            null
        }
    }

    private fun checkForAppTrigger(context: Context, packageName: String, className: String, eventType: String) {
        listeningWorkflows.forEach { workflow ->
            val config = workflow.triggerConfig ?: return@forEach
            val configEvent = config["event"] as? String
            val targetPackage = config["packageName"] as? String
            val targetActivity = config["activityName"] as? String

            if (configEvent == eventType && targetPackage == packageName) {
                val activityMatches = if (eventType == "打开时") {
                    targetActivity == "LAUNCH" || targetActivity == className
                } else {
                    true // 关闭事件不需要匹配具体Activity
                }

                if (activityMatches) {
                    triggerScope.launch {
                        Log.i(TAG, "触发工作流 '${workflow.name}', 事件: $packageName $eventType")
                        WorkflowExecutor.execute(workflow, context)
                    }
                }
            }
        }
    }
}