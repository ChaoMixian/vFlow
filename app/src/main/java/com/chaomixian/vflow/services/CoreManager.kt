// 文件: main/java/com/chaomixian/vflow/services/CoreManager.kt
package com.chaomixian.vflow.services

import android.content.Context
import com.chaomixian.vflow.core.logging.DebugLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * vFlow Core 生命周期管理器。
 *
 * 职责：
 * - 管理 Core 进程的状态（状态机）
 * - 健康检查和自动重启
 * - 提供便捷的 ensureStarted() 方法
 *
 * 不负责：
 * - 具体的启动逻辑（由 CoreLauncher 负责）
 */
object CoreManager {
    private const val TAG = "CoreManager"

    /**
     * Core 状态
     */
    enum class State {
        STOPPED,    // 未启动
        STARTING,   // 启动中
        RUNNING,    // 运行中
        STOPPING,   // 停止中
        ERROR       // 错误状态
    }

    private val _state = MutableStateFlow(State.STOPPED)
    val state: StateFlow<State> = _state.asStateFlow()

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 权限模式（从 VFlowCoreBridge 获取）
    val privilegeMode: VFlowCoreBridge.PrivilegeMode
        get() = VFlowCoreBridge.privilegeMode

    // 健康监控任务
    private var healthMonitorJob: Job? = null

    // 配置
    private var healthCheckInterval = 30_000L // 30秒
    private var maxRestartAttempts = 3
    private var restartDelay = 5_000L // 5秒

    /**
     * 确保 Core 已启动（如果未启动则自动启动）。
     *
     * @param context 上下文
     * @param mode 启动模式
     * @return 是否成功启动
     */
    suspend fun ensureStarted(
        context: Context,
        mode: CoreLauncher.LaunchMode = CoreLauncher.LaunchMode.AUTO
    ): Boolean {
        // 先检查实际状态
        val actuallyRunning = VFlowCoreBridge.ping()

        if (actuallyRunning) {
            // Core实际在运行，同步状态
            if (_state.value != State.RUNNING) {
                _state.value = State.RUNNING
                DebugLogger.i(TAG, "Core 正在运行，更新状态为 RUNNING")
            } else {
                DebugLogger.d(TAG, "Core 已在运行")
            }
            return true
        }

        DebugLogger.i(TAG, "Core 未运行，正在启动...")

        // 更新状态
        _state.value = State.STARTING

        // 调用 CoreLauncher 启动
        val result = CoreLauncher.launch(context, mode)

        if (result.success) {
            _state.value = State.RUNNING
            DebugLogger.i(TAG, "Core 启动成功，权限模式: ${result.privilegeMode}")
            return true
        } else {
            _state.value = State.ERROR
            DebugLogger.e(TAG, "Core 启动失败: ${result.error}")
            return false
        }
    }

    /**
     * 停止 Core。
     *
     * @param context 上下文
     * @return 是否成功停止
     */
    suspend fun stop(context: Context): Boolean {
        // 先检查实际状态，而不是仅依赖 _state
        val actuallyRunning = VFlowCoreBridge.ping()

        if (!actuallyRunning) {
            // Core确实没在运行
            if (_state.value != State.STOPPED) {
                _state.value = State.STOPPED
                DebugLogger.i(TAG, "Core 未运行，更新状态为 STOPPED")
            } else {
                DebugLogger.d(TAG, "Core 已经处于停止状态")
            }
            return true
        }

        // Core实际在运行，需要停止
        DebugLogger.i(TAG, "Core 正在运行，开始停止...")
        _state.value = State.STOPPING

        // 停止健康监控
        stopHealthMonitoring()

        val result = CoreLauncher.stop(context)

        if (result) {
            _state.value = State.STOPPED
            DebugLogger.i(TAG, "Core 已停止")
        } else {
            DebugLogger.w(TAG, "Core 停止失败")
        }

        return result
    }

    /**
     * 重启 Core。
     *
     * @param context 上下文
     * @param mode 启动模式
     * @return 是否成功重启
     */
    suspend fun restart(
        context: Context,
        mode: CoreLauncher.LaunchMode = CoreLauncher.LaunchMode.AUTO
    ): Boolean {
        DebugLogger.i(TAG, "正在重启 Core...")
        val result = CoreLauncher.restart(context, mode)

        if (result.success) {
            _state.value = State.RUNNING
            DebugLogger.i(TAG, "Core 重启成功")
        } else {
            _state.value = State.ERROR
            DebugLogger.e(TAG, "Core 重启失败: ${result.error}")
        }

        return result.success
    }

    /**
     * 健康检查。
     *
     * @return Core 是否健康（运行中且响应 ping）
     */
    suspend fun healthCheck(): Boolean {
        val isHealthy = VFlowCoreBridge.ping()

        if (!isHealthy && _state.value == State.RUNNING) {
            DebugLogger.w(TAG, "健康检查失败，Core 状态异常")
            _state.value = State.ERROR
        }

        return isHealthy
    }

    /**
     * 启动健康监控（自动重启）。
     *
     * @param context 上下文
     * @param interval 检查间隔（毫秒）
     * @param maxAttempts 最大重启尝试次数
     */
    fun startHealthMonitoring(
        context: Context,
        interval: Long = healthCheckInterval,
        maxAttempts: Int = maxRestartAttempts
    ) {
        // 如果已经在监控，先停止
        stopHealthMonitoring()

        healthCheckInterval = interval
        maxRestartAttempts = maxAttempts

        DebugLogger.i(TAG, "启动健康监控 (间隔: ${interval}ms, 最大重启: ${maxAttempts}次)")

        healthMonitorJob = managerScope.launch {
            var restartAttempts = 0

            while (isActive) {
                delay(interval)

                // 执行健康检查
                val isHealthy = healthCheck()

                if (!isHealthy && _state.value != State.STOPPED && _state.value != State.STOPPING) {
                    DebugLogger.w(TAG, "健康检查失败，Core 未响应")

                    // 检查是否需要自动重启
                    if (restartAttempts < maxAttempts) {
                        restartAttempts++
                        DebugLogger.i(TAG, "尝试自动重启 ($restartAttempts/$maxAttempts)...")

                        _state.value = State.STARTING
                        val result = CoreLauncher.launch(
                            context,
                            CoreLauncher.LaunchMode.AUTO
                        )

                        if (result.success) {
                            _state.value = State.RUNNING
                            DebugLogger.i(TAG, "自动重启成功")
                            restartAttempts = 0 // 重置计数器
                        } else {
                            _state.value = State.ERROR
                            DebugLogger.e(TAG, "自动重启失败: ${result.error}")
                        }
                    } else {
                        DebugLogger.e(TAG, "已达到最大重启次数 ($maxAttempts)，停止自动重启")
                        _state.value = State.ERROR
                        break
                    }
                } else if (isHealthy) {
                    // 如果健康，重置重启计数器
                    restartAttempts = 0
                }
            }
        }
    }

    /**
     * 停止健康监控。
     */
    fun stopHealthMonitoring() {
        healthMonitorJob?.cancel()
        healthMonitorJob = null
        DebugLogger.d(TAG, "健康监控已停止")
    }

    /**
     * 检查 Core 是否正在运行。
     * 这个方法会实际检查 Core 进程，而不仅仅是检查状态。
     */
    fun isRunning(): Boolean {
        return VFlowCoreBridge.ping()
    }
}
