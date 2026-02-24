// 文件: main/java/com/chaomixian/vflow/core/workflow/module/triggers/handlers/BackTapTriggerHandler.kt
// 描述: 轻敲背面触发器处理器，使用加速度计检测双击/三击

package com.chaomixian.vflow.core.workflow.module.triggers.handlers

import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.core.workflow.module.triggers.BackTapTriggerModule
import com.chaomixian.vflow.services.TriggerService
import com.chaomixian.vflow.ui.settings.ModuleConfigActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class BackTapTriggerHandler : BaseTriggerHandler(), SensorEventListener {

    companion object {
        private const val TAG = "BackTapTriggerHandler"
        const val ACTION_BACKTAP_EVENT = "com.chaomixian.vflow.BACKTAP_EVENT"
        const val EXTRA_TAP_COUNT = "tap_count"

        // 时间参数（毫秒）
        private const val MIN_TAP_INTERVAL_MS = 100L   // 最小敲击间隔
        private const val MAX_DOUBLE_TAP_MS = 500L     // 双击最大间隔
        private const val MAX_TRIPLE_TAP_MS = 750L    // 三击最大间隔
        private const val TAP_TIMEOUT_MS = 1000L      // 敲击超时
        private const val DOUBLE_TAP_DELAY_MS = 200L  // 双击确认延迟（等待可能的第三次敲击）

        // 加速度阈值
        private const val TAP_THRESHOLD = 15f          // 敲击检测阈值
    }

    private var context: Context? = null
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var isListening = false

    private val listeningWorkflows = CopyOnWriteArrayList<Workflow>()
    private val activeTriggers = CopyOnWriteArrayList<ResolvedBackTapTrigger>()

    // 敲击时间戳队列
    private val tapTimestamps = mutableListOf<Long>()
    private var tapDetectionJob: Job? = null

    // 上一次加速度值（用于计算变化）
    private var lastAccel = FloatArray(3)
    private var lastAccelSet = false

    private lateinit var prefs: SharedPreferences

    data class ResolvedBackTapTrigger(
        val workflowId: String,
        val mode: String // "双击" 或 "三击"
    )

    override fun start(context: Context) {
        super.start(context)
        this.context = context.applicationContext
        prefs = this.context!!.getSharedPreferences(ModuleConfigActivity.PREFS_NAME, Context.MODE_PRIVATE)

        sensorManager = this.context!!.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (accelerometer == null) {
            DebugLogger.e(TAG, "No accelerometer available")
            return
        }

        startListening()
        DebugLogger.d(TAG, "BackTapTriggerHandler started")
    }

    override fun stop(context: Context) {
        super.stop(context)
        stopListening()
        tapDetectionJob?.cancel()
        this.context = null
        DebugLogger.d(TAG, "BackTapTriggerHandler stopped")
    }

    private fun startListening() {
        if (isListening) return
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            isListening = true
            DebugLogger.d(TAG, "Started listening to accelerometer")
        }
    }

    private fun stopListening() {
        if (!isListening) return
        sensorManager?.unregisterListener(this)
        isListening = false
        DebugLogger.d(TAG, "Stopped listening to accelerometer")
    }

    override fun addWorkflow(context: Context, workflow: Workflow) {
        listeningWorkflows.removeAll { it.id == workflow.id }
        listeningWorkflows.add(workflow)
        DebugLogger.d(TAG, "Added workflow: ${workflow.name}")
        reloadTriggers()
    }

    override fun removeWorkflow(context: Context, workflowId: String) {
        val removed = listeningWorkflows.removeAll { it.id == workflowId }
        if (removed) {
            DebugLogger.d(TAG, "Removed workflow: $workflowId")
            reloadTriggers()
        }
    }

    private fun reloadTriggers() {
        activeTriggers.clear()
        val triggers = listeningWorkflows.mapNotNull { workflow ->
            val config = workflow.triggerConfig ?: return@mapNotNull null
            val mode = config["mode"] as? String ?: BackTapTriggerModule.MODE_DOUBLE_TAP
            ResolvedBackTapTrigger(workflow.id, mode)
        }
        activeTriggers.addAll(triggers)

        if (activeTriggers.isEmpty()) {
            stopListening()
        } else {
            startListening()
        }

        DebugLogger.d(TAG, "Reloaded triggers: ${activeTriggers.size} active")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        if (!lastAccelSet) {
            lastAccel[0] = x
            lastAccel[1] = y
            lastAccel[2] = z
            lastAccelSet = true
            return
        }

        // 计算加速度变化
        val deltaX = x - lastAccel[0]
        val deltaY = y - lastAccel[1]
        val deltaZ = z - lastAccel[2]

        lastAccel[0] = x
        lastAccel[1] = y
        lastAccel[2] = z

        // 计算向量幅度
        val acceleration = kotlin.math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ)

        // 获取灵敏度设置
        // sensitivity: 0（最灵敏，容易触发）-> 0.75（最难触发）
        // threshold: 越小越灵敏，越大越难触发
        val sensitivity = prefs.getFloat(ModuleConfigActivity.KEY_BACKTAP_SENSITIVITY, 0.05f)
        val threshold = TAP_THRESHOLD * (0.25f + sensitivity)

        if (acceleration > threshold) {
            detectTap()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 不需要处理
    }

    private fun detectTap() {
        val now = System.currentTimeMillis()

        // 清理过旧的敲击记录
        tapTimestamps.removeAll { now - it > TAP_TIMEOUT_MS }

        if (tapTimestamps.isNotEmpty()) {
            val lastTap = tapTimestamps.last()
            val interval = now - lastTap

            // 如果间隔太短，忽略
            if (interval < MIN_TAP_INTERVAL_MS) {
                return
            }
        }

        tapTimestamps.add(now)
        checkTapPattern()
    }

    private fun checkTapPattern() {
        val now = System.currentTimeMillis()
        if (tapTimestamps.isEmpty()) return

        val firstTap = tapTimestamps.first()
        val totalDuration = now - firstTap

        // 检查是否有双击和三击触发器
        val hasDoubleTap = activeTriggers.any { it.mode == BackTapTriggerModule.MODE_DOUBLE_TAP }
        val hasTripleTap = activeTriggers.any { it.mode == BackTapTriggerModule.MODE_TRIPLE_TAP }

        // 三击检测：至少有3次敲击，且总时间在 MAX_TRIPLE_TAP_MS 内
        if (hasTripleTap && tapTimestamps.size >= 3) {
            if (totalDuration <= MAX_TRIPLE_TAP_MS) {
                // 触发三击
                tapDetectionJob?.cancel()  // 取消待处理的双击触发
                DebugLogger.d(TAG, "Triple tap detected")
                executeMatchingWorkflows(3)
                tapTimestamps.clear()
                return
            }
        }

        // 双击检测：只有当没有三击触发器时，才立即触发双击
        // 如果同时有三击触发器，需要延迟触发双击以等待可能的第三次敲击
        if (hasDoubleTap && tapTimestamps.size >= 2) {
            val lastInterval = tapTimestamps.last() - tapTimestamps[tapTimestamps.size - 2]

            if (!hasTripleTap) {
                // 没有三击触发器，立即检查双击
                if (lastInterval <= MAX_DOUBLE_TAP_MS && totalDuration <= MAX_DOUBLE_TAP_MS) {
                    DebugLogger.d(TAG, "Double tap detected (no triple tap)")
                    executeMatchingWorkflows(2)
                    tapTimestamps.clear()
                    return
                }
            } else {
                // 同时有三击触发器，启动延迟检测
                // 如果已经有待处理的检测，忽略
                if (tapDetectionJob?.isActive == true) return

                // 启动延迟检测
                tapDetectionJob = triggerScope.launch {
                    delay(DOUBLE_TAP_DELAY_MS)
                    // 延迟后再次检查
                    if (tapTimestamps.size >= 2) {
                        val currentNow = System.currentTimeMillis()
                        val currentFirst = tapTimestamps.firstOrNull() ?: return@launch
                        val currentTotalDuration = currentNow - currentFirst
                        val currentLastInterval = if (tapTimestamps.size >= 2) {
                            tapTimestamps.last() - tapTimestamps[tapTimestamps.size - 2]
                        } else return@launch

                        // 再次检查三击
                        if (tapTimestamps.size >= 3 && currentTotalDuration <= MAX_TRIPLE_TAP_MS) {
                            DebugLogger.d(TAG, "Triple tap detected (delayed)")
                            executeMatchingWorkflows(3)
                            tapTimestamps.clear()
                        } else if (currentLastInterval <= MAX_DOUBLE_TAP_MS && currentTotalDuration <= MAX_DOUBLE_TAP_MS) {
                            // 没有第三次敲击，触发双击
                            DebugLogger.d(TAG, "Double tap detected (after delay)")
                            executeMatchingWorkflows(2)
                            tapTimestamps.clear()
                        }
                    }
                }
            }
        }

        // 如果超时未达到任何模式，清空
        if (tapTimestamps.isNotEmpty() && totalDuration > MAX_TRIPLE_TAP_MS) {
            tapDetectionJob?.cancel()
            tapTimestamps.clear()
        }
    }

    private fun executeMatchingWorkflows(tapCount: Int) {
        val ctx = context ?: return
        val mode = if (tapCount == 2) BackTapTriggerModule.MODE_DOUBLE_TAP else BackTapTriggerModule.MODE_TRIPLE_TAP

        val matchingWorkflowIds = activeTriggers.filter {
            it.mode == mode
        }.map { it.workflowId }

        if (matchingWorkflowIds.isNotEmpty()) {
            val matchingWorkflows = listeningWorkflows.filter { it.id in matchingWorkflowIds }
            if (matchingWorkflows.isNotEmpty()) {
                // 简化处理：直接执行第一个匹配的 workflow
                triggerScope.launch {
                    com.chaomixian.vflow.core.execution.WorkflowExecutor.execute(matchingWorkflows.first(), ctx)
                }
            }
        }
    }
}
