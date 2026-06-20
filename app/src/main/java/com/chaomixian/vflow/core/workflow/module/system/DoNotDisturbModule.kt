package com.chaomixian.vflow.core.workflow.module.system

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Build
import android.service.notification.Condition
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.ActionMetadata
import com.chaomixian.vflow.core.module.AiModuleRiskLevel
import com.chaomixian.vflow.core.module.BaseModule
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.InputStyle
import com.chaomixian.vflow.core.module.OutputDefinition
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.module.ProgressUpdate
import com.chaomixian.vflow.core.module.directToolMetadata
import com.chaomixian.vflow.core.module.normalizeEnumValue
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.main.MainActivity
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class DoNotDisturbModule : BaseModule() {

    companion object {
        const val ACTION_TOGGLE = "toggle"
        const val ACTION_ON = "on"
        const val ACTION_OFF = "off"

        private val ACTION_LEGACY_MAP = mapOf(
            "切换" to ACTION_TOGGLE,
            "开启" to ACTION_ON,
            "打开" to ACTION_ON,
            "关闭" to ACTION_OFF
        )

        private const val PREFS_NAME = "vflow_do_not_disturb"
        private const val PREF_RULE_ID = "automatic_zen_rule_id"
        private const val CONDITION_SCHEME = "vflow"
        private const val CONDITION_HOST = "do_not_disturb"
    }

    override val id = "vflow.system.do_not_disturb"

    override val metadata = ActionMetadata(
        name = "免打扰模式",
        nameStringRes = R.string.module_vflow_system_do_not_disturb_name,
        description = "通过 vFlow 自动规则开启、关闭或切换免打扰模式。",
        descriptionStringRes = R.string.module_vflow_system_do_not_disturb_desc,
        iconRes = R.drawable.rounded_notifications_unread_24,
        category = "应用与系统",
        categoryId = "device"
    )

    override val aiMetadata = directToolMetadata(
        riskLevel = AiModuleRiskLevel.STANDARD,
        directToolDescription = "Turn vFlow's Android Do Not Disturb automatic rule on, off, or toggle it.",
        workflowStepDescription = "Change vFlow's Android Do Not Disturb automatic rule.",
        inputHints = mapOf(
            "action" to "Use canonical values: toggle, on, or off."
        ),
        requiredInputIds = setOf("action")
    )

    override val requiredPermissions = listOf(PermissionManager.NOTIFICATION_POLICY)

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "action",
            name = "操作",
            nameStringRes = R.string.param_vflow_system_do_not_disturb_action_name,
            staticType = ParameterType.ENUM,
            defaultValue = ACTION_TOGGLE,
            options = listOf(ACTION_TOGGLE, ACTION_ON, ACTION_OFF),
            optionsStringRes = listOf(
                R.string.option_vflow_system_do_not_disturb_toggle,
                R.string.option_vflow_system_do_not_disturb_on,
                R.string.option_vflow_system_do_not_disturb_off
            ),
            legacyValueMap = ACTION_LEGACY_MAP,
            inputStyle = InputStyle.CHIP_GROUP
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "success",
            name = "是否成功",
            typeName = VTypeRegistry.BOOLEAN.id,
            nameStringRes = R.string.output_vflow_system_do_not_disturb_success_name
        ),
        OutputDefinition(
            id = "enabled",
            name = "vFlow 勿扰规则已开启",
            typeName = VTypeRegistry.BOOLEAN.id,
            nameStringRes = R.string.output_vflow_system_do_not_disturb_enabled_name
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val action = getInputs().normalizeEnumValue(
            "action",
            step.parameters["action"] as? String,
            ACTION_TOGGLE
        ) ?: ACTION_TOGGLE
        val displayText = getActionDisplayName(context, action)
        val actionPill = PillUtil.Pill(displayText, "action", isModuleOption = true)
        return PillUtil.buildSpannable(context, "${metadata.getLocalizedName(context)}: ", actionPill)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val action = getInputs().normalizeEnumValue(
            "action",
            context.getVariable("action").asString(),
            ACTION_TOGGLE
        ) ?: ACTION_TOGGLE

        val ruleName = appContext.getString(R.string.module_vflow_system_do_not_disturb_name)

        val notificationManager = context.applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (!notificationManager.isNotificationPolicyAccessGranted) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_system_do_not_disturb_permission_missing),
                appContext.getString(R.string.error_vflow_system_do_not_disturb_permission_missing_desc)
            )
        }

        val actionName = getActionDisplayName(context.applicationContext, action)
        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_system_do_not_disturb_setting, actionName)))

        return try {
            val enabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                val ruleId = ensureRule(context.applicationContext, notificationManager, ruleName)
                    ?: return ExecutionResult.Failure(
                        appContext.getString(R.string.error_vflow_system_do_not_disturb_set_failed),
                        appContext.getString(R.string.error_vflow_system_do_not_disturb_rule_create_failed)
                    )
                // Read the actual conditionId from the existing rule to avoid mismatch
                val rule = notificationManager.getAutomaticZenRule(ruleId)
                val actualConditionId = rule?.conditionId ?: conditionId(context.applicationContext, ruleName)
                val currentState = getRuleState(notificationManager, ruleId)
                val targetState = resolveTargetState(currentState, action)
                    ?: return ExecutionResult.Failure(
                        appContext.getString(R.string.error_vflow_system_do_not_disturb_invalid_action),
                        appContext.getString(R.string.error_vflow_system_do_not_disturb_invalid_action_detail, action)
                    )
                val enabled = targetState == Condition.STATE_TRUE
                notificationManager.setAutomaticZenRuleState(
                    ruleId,
                    Condition(
                        actualConditionId,
                        getConditionSummary(context.applicationContext, enabled),
                        targetState,
                        Condition.SOURCE_USER_ACTION
                    )
                )
                enabled
            } else {
                val targetFilter = resolveLegacyTargetFilter(
                    notificationManager.currentInterruptionFilter,
                    action
                ) ?: return ExecutionResult.Failure(
                    appContext.getString(R.string.error_vflow_system_do_not_disturb_invalid_action),
                    appContext.getString(R.string.error_vflow_system_do_not_disturb_invalid_action_detail, action)
                )
                notificationManager.setInterruptionFilter(targetFilter)
                targetFilter != NotificationManager.INTERRUPTION_FILTER_ALL
            }
            onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_system_do_not_disturb_completed)))
            ExecutionResult.Success(
                mapOf(
                    "success" to VBoolean(true),
                    "enabled" to VBoolean(enabled)
                )
            )
        } catch (e: SecurityException) {
            ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_system_do_not_disturb_set_failed),
                e.localizedMessage ?: appContext.getString(R.string.error_vflow_system_do_not_disturb_unknown)
            )
        } catch (e: Exception) {
            ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_system_do_not_disturb_set_failed),
                e.localizedMessage ?: appContext.getString(R.string.error_vflow_system_do_not_disturb_unknown)
            )
        }
    }

    internal fun resolveTargetState(currentState: Int, action: String): Int? {
        return when (action) {
            ACTION_ON -> Condition.STATE_TRUE
            ACTION_OFF -> Condition.STATE_FALSE
            ACTION_TOGGLE -> if (currentState == Condition.STATE_TRUE) {
                Condition.STATE_FALSE
            } else {
                Condition.STATE_TRUE
            }
            else -> null
        }
    }

    internal fun resolveLegacyTargetFilter(currentFilter: Int, action: String): Int? {
        return when (action) {
            ACTION_ON -> NotificationManager.INTERRUPTION_FILTER_NONE
            ACTION_OFF -> NotificationManager.INTERRUPTION_FILTER_ALL
            ACTION_TOGGLE -> if (currentFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
                NotificationManager.INTERRUPTION_FILTER_NONE
            } else {
                NotificationManager.INTERRUPTION_FILTER_ALL
            }
            else -> null
        }
    }

    private fun ensureRule(context: Context, notificationManager: NotificationManager, ruleName: String): String? {
        val existingRuleEntry = notificationManager.automaticZenRules?.entries?.find { it.value.name == ruleName }
        if (existingRuleEntry != null) {
            return existingRuleEntry.key
        }

        val rule = createAutomaticZenRule(context, ruleName)
        return try {
            notificationManager.addAutomaticZenRule(rule)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    internal fun createAutomaticZenRule(context: Context, ruleName: String): AutomaticZenRule {
        return createAutomaticZenRule(
            ruleName,
            context.packageName
        )
    }

    internal fun createAutomaticZenRule(ruleName: String, packageName: String): AutomaticZenRule {
        return AutomaticZenRule(
            ruleName,
            null,
            createRuleConfigurationActivity(packageName),
            conditionId(packageName, ruleName),
            null,
            NotificationManager.INTERRUPTION_FILTER_NONE,
            true
        )
    }

    internal fun createRuleConfigurationActivity(packageName: String): ComponentName {
        return ComponentName(packageName, ruleConfigurationActivityClassName())
    }

    internal fun ruleConfigurationActivityClassName(): String {
        return MainActivity::class.java.name
    }

    private fun getRuleState(notificationManager: NotificationManager, ruleId: String): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            notificationManager.getAutomaticZenRuleState(ruleId)
        } else if (notificationManager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
            Condition.STATE_FALSE
        } else {
            Condition.STATE_TRUE
        }
    }

    private fun conditionId(context: Context, ruleName: String): Uri {
        return conditionId(context.packageName, ruleName)
    }

    private fun conditionId(packageName: String, ruleName: String): Uri {
        return Uri.Builder()
            .scheme(CONDITION_SCHEME)
            .authority(CONDITION_HOST)
            .appendPath(packageName)
            .appendPath(ruleName)
            .build()
    }

    private fun getConditionSummary(context: Context, enabled: Boolean): String {
        return if (enabled) {
            context.getString(R.string.msg_vflow_system_do_not_disturb_rule_enabled)
        } else {
            context.getString(R.string.msg_vflow_system_do_not_disturb_rule_disabled)
        }
    }

    private fun getActionDisplayName(context: Context, action: String): String {
        return when (action) {
            ACTION_ON -> context.getString(R.string.option_vflow_system_do_not_disturb_on)
            ACTION_OFF -> context.getString(R.string.option_vflow_system_do_not_disturb_off)
            else -> context.getString(R.string.option_vflow_system_do_not_disturb_toggle)
        }
    }
}
