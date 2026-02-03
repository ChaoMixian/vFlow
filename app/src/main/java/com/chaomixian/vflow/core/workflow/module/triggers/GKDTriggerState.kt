package com.chaomixian.vflow.core.workflow.module.triggers

import com.chaomixian.vflow.core.workflow.model.Workflow
import li.songe.selector.Selector
import kotlinx.coroutines.Job

/**
 * GKD订阅触发器状态数据类
 */
data class GKDTriggerState(
    val workflow: Workflow,
    val rules: List<ResolvedGKDRule>,
    // 每个规则的执行状态
    val ruleStates: MutableMap<ResolvedGKDRule, RuleExecutionState> = mutableMapOf()
) {
    // 已匹配的规则 key 集合（用于 preKeys 检查）
    val matchedKeys: MutableSet<Int> = mutableSetOf()

    // 当前应用包名和 Activity（用于 resetMatch 检查）
    var currentPackage: String? = null
    var currentActivity: String? = null

    // 最后触发的规则（用于 preRules 检查）
    var lastTriggerRule: ResolvedGKDRule? = null

    /**
     * 获取或创建规则的执行状态
     */
    fun getRuleState(rule: ResolvedGKDRule): RuleExecutionState {
        return ruleStates.getOrPut(rule) { RuleExecutionState(rule) }
    }

    /**
     * 重置所有规则的状态
     */
    fun reset() {
        ruleStates.values.forEach { it.reset() }
    }

    /**
     * 重置所有规则的状态和已匹配的 key
     */
    fun resetAll() {
        ruleStates.values.forEach { it.reset() }
        matchedKeys.clear()
        lastTriggerRule = null
    }

    /**
     * 重置指定规则的状态
     */
    fun resetRule(rule: ResolvedGKDRule) {
        ruleStates[rule]?.reset()
    }

    /**
     * 取消所有规则的任务
     */
    fun cancelAllJobs() {
        ruleStates.values.forEach { it.cancelJobs() }
    }
}

/**
 * 单个规则的执行状态
 * 使用规则自己的参数配置
 */
data class RuleExecutionState(
    val rule: ResolvedGKDRule,
    var matchChangedTime: Long = 0L,
    var actionTriggerTime: Long = 0L,
    var actionCount: Int = 0,
    var actionDelayTriggerTime: Long = 0L,
    // 任务引用（用于取消）
    var actionDelayJob: Job? = null,
    var matchDelayJob: Job? = null
) {
    // 获取规则的参数，使用默认值（遵循 GKD 的默认值）
    internal val actionCd: Long get() = rule.actionCd ?: 1000L
    internal val matchTime: Long? get() = rule.matchTime
    internal val matchDelay: Long get() = rule.matchDelay ?: 0L
    internal val actionMaximum: Int? get() = rule.actionMaximum
    internal val actionDelay: Long get() = rule.actionDelay ?: 0L
    internal val forcedTime: Long get() = rule.forcedTime ?: 0L

    /**
     * 取消所有任务
     */
    fun cancelJobs() {
        actionDelayJob?.cancel()
        matchDelayJob?.cancel()
        actionDelayJob = null
        matchDelayJob = null
    }

    /**
     * 获取当前触发状态
     */
    fun getStatus(): TriggerStatus {
        val t = System.currentTimeMillis()

        // 获取参数值到局部变量，避免 smart cast 问题
        val actionCdVal = actionCd
        val actionDelayVal = actionDelay
        val matchTimeVal = matchTime
        val matchDelayVal = matchDelay
        val actionMaximumVal = actionMaximum
        val forcedTimeVal = forcedTime

        // 0. 检查强制等待时间
        if (forcedTimeVal > 0) {
            // 如果在强制等待期间，不允许任何操作
            if (t < matchChangedTime + matchDelayVal + forcedTimeVal) {
                return TriggerStatus.ForcedWaiting
            }
        }

        // 1. 检查最大触发次数
        if (actionMaximumVal != null && actionCount >= actionMaximumVal) {
            return TriggerStatus.MaxReached
        }

        // 2. 检查冷却时间 (actionCd)
        if (t - actionTriggerTime < actionCdVal) {
            return TriggerStatus.Cooling
        }

        // 3. 检查触发延迟 (actionDelay)
        if (actionDelayVal > 0) {
            if (actionDelayTriggerTime == 0L) {
                return TriggerStatus.WaitingDelay
            }
            if (t - actionDelayTriggerTime < actionDelayVal) {
                return TriggerStatus.InDelay
            }
        }

        // 4. 检查匹配延迟 (matchDelay)
        if (matchDelayVal > 0 && t - matchChangedTime < matchDelayVal) {
            return TriggerStatus.MatchDelay
        }

        // 5. 检查匹配时间窗口 (matchTime)
        if (matchTimeVal != null && matchTimeVal > 0) {
            if (t - matchChangedTime > matchTimeVal + matchDelayVal) {
                return TriggerStatus.Expired
            }
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
     * 检查是否在强制等待期间
     */
    fun isInForcedTime(): Boolean {
        if (forcedTime <= 0) return false
        val t = System.currentTimeMillis()
        return t < matchChangedTime + matchDelay + forcedTime
    }

    /**
     * 记录匹配成功
     */
    fun recordMatch(t: Long) {
        matchChangedTime = t
    }

    /**
     * 记录触发
     */
    fun recordTrigger(t: Long) {
        actionTriggerTime = t
        actionDelayTriggerTime = 0L
        actionCount++
    }

    /**
     * 开始触发延迟
     */
    fun startActionDelay(t: Long) {
        actionDelayTriggerTime = t
    }

    /**
     * 重置状态
     */
    fun reset() {
        matchChangedTime = 0L
        actionTriggerTime = 0L
        actionCount = 0
        actionDelayTriggerTime = 0L
        cancelJobs()
    }
}

/**
 * 已解析的GKD规则
 */
data class ResolvedGKDRule(
    val name: String,
    val groupName: String,
    val appId: String?,
    val activityIds: List<String>?,
    val excludeActivityIds: List<String>?,
    val matches: List<Selector>,
    val anyMatches: List<Selector>,
    val excludeMatches: List<Selector>,
    val excludeAllMatches: List<Selector>,
    // 规则自己的参数配置（可为空，使用默认值）
    val key: Int?,
    val preKeys: List<Int>,
    val resetMatch: String?,
    val actionCd: Long?,
    val actionDelay: Long?,
    val matchDelay: Long?,
    val matchTime: Long?,
    val matchRoot: Boolean?,
    val fastQuery: Boolean?,
    val actionMaximum: Int?,
    val actionCdKey: Int?,
    val actionMaximumKey: Int?,
    val forcedTime: Long?,
    val priorityTime: Long?,
    val priorityActionMaximum: Int?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ResolvedGKDRule) return false
        return name == other.name && groupName == other.groupName
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + groupName.hashCode()
        return result
    }

    /**
     * 检查前置条件是否满足
     */
    fun checkPreKeys(matchedKeys: Set<Int>): Boolean {
        if (preKeys.isEmpty()) return true
        return preKeys.all { it in matchedKeys }
    }

    /**
     * 判断是否需要重置
     * @param oldPackage 旧的应用包名
     * @param oldActivity 旧的 Activity
     * @param newPackage 新的应用包名
     * @param newActivity 新的 Activity
     */
    fun shouldReset(
        oldPackage: String?,
        oldActivity: String?,
        newPackage: String?,
        newActivity: String?
    ): Boolean {
        val reset = resetMatch ?: return false
        return when (reset) {
            "app" -> oldPackage != newPackage
            "activity" -> oldActivity != newActivity || oldPackage != newPackage
            "match" -> false // match 重置是手动触发的，不在这里处理
            else -> false
        }
    }

    /**
     * 检查是否是优先级规则
     */
    fun isPriorityRule(actionCount: Int): Boolean {
        if (priorityTime == null || priorityTime <= 0) return false
        if (priorityActionMaximum != null && actionCount >= priorityActionMaximum) return false
        return true
    }
}
