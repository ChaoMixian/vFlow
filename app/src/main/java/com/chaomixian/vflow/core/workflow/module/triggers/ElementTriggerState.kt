package com.chaomixian.vflow.core.workflow.module.triggers

import android.view.accessibility.AccessibilityNodeInfo
import com.chaomixian.vflow.core.workflow.model.Workflow
import li.songe.selector.Selector

/**
 * 元素触发器状态数据类
 * 参考 gkd ResolvedRule.kt 实现
 */
data class ElementTriggerState(
    val workflow: Workflow,
    val selector: Selector,
    // 触发配置参数
    val matchDelay: Long = 0L,
    val actionDelay: Long = 0L,
    val matchTime: Long = 0L,
    val actionCd: Long = 1000L,
    val actionMaximum: Int? = null,
    // 动态状态
    var matchChangedTime: Long = 0L,
    var actionTriggerTime: Long = 0L,
    var actionCount: Int = 0,
    var actionDelayTriggerTime: Long = 0L,
    var lastMatchedNodeId: Int? = null  // 用于去重：记录上次匹配的节点 ID
) {
    /**
     * 获取当前触发状态
     */
    fun getStatus(): TriggerStatus {
        val t = System.currentTimeMillis()

        // 1. 检查冷却时间 (actionCd)
        if (t - actionTriggerTime < actionCd) {
            return TriggerStatus.Cooling
        }

        // 2. 检查触发延迟 (actionDelay)
        if (actionDelay > 0) {
            if (actionDelayTriggerTime == 0L) {
                return TriggerStatus.WaitingDelay
            }
            if (t - actionDelayTriggerTime < actionDelay) {
                return TriggerStatus.InDelay
            }
        }

        // 3. 检查匹配时间窗口 (matchTime)
        if (matchTime > 0) {
            if (t - matchChangedTime > matchTime + matchDelay) {
                return TriggerStatus.Expired
            }
        }

        // 4. 检查最大触发次数
        if (actionMaximum != null && actionCount >= actionMaximum) {
            return TriggerStatus.MaxReached
        }

        return TriggerStatus.Ready
    }

    /**
     * 检查是否应该触发
     */
    fun shouldTrigger(): Boolean {
        return getStatus() == TriggerStatus.Ready
    }

    /**
     * 重置状态
     */
    fun reset() {
        matchChangedTime = 0L
        actionTriggerTime = 0L
        actionCount = 0
        actionDelayTriggerTime = 0L
        lastMatchedNodeId = null
    }
}

/**
 * 触发器状态枚举
 */
sealed class TriggerStatus {
    /** 可触发 */
    data object Ready : TriggerStatus()

    /** 冷却中 */
    data object Cooling : TriggerStatus()

    /** 等待延迟开始 */
    data object WaitingDelay : TriggerStatus()

    /** 延迟中 */
    data object InDelay : TriggerStatus()

    /** 已过期 */
    data object Expired : TriggerStatus()

    /** 达到最大触发次数 */
    data object MaxReached : TriggerStatus()
}
