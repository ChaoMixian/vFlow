package com.chaomixian.vflow.core.logging

import com.chaomixian.vflow.R

internal object LogMessageFormatter {
    private val legacyFailurePattern = Regex("""^在步骤\s*#(\d+)\s*\((.+)\)\s*执行失败$""")
    private val legacyFailurePatternCompact = Regex("""^在步骤#(\d+)\((.+)\)执行失败$""")

    fun resolve(
        entry: LogEntry,
        stringResolver: (Int, Array<out Any>) -> String
    ): String? {
        val resolved = entry.messageKey?.let { ResolvedMessage(it, entry.messageArgs) }
            ?: inferLegacyMessage(entry.message)
            ?: return entry.message

        return when (resolved.key) {
            LogMessageKey.EXECUTION_COMPLETED -> {
                stringResolver(R.string.log_message_execution_completed, emptyArray())
            }
            LogMessageKey.EXECUTION_CANCELLED -> {
                stringResolver(R.string.log_message_execution_cancelled, emptyArray())
            }
            LogMessageKey.EXECUTION_FAILED_AT_STEP -> {
                val stepNumber = resolved.args.getOrNull(0) ?: "?"
                val moduleName = resolved.args.getOrNull(1)
                    ?.takeIf { it.isNotBlank() }
                    ?: stringResolver(R.string.ui_inspector_unknown, emptyArray())
                stringResolver(
                    R.string.log_message_execution_failed_at_step,
                    arrayOf(stepNumber, moduleName)
                )
            }
            LogMessageKey.TRIGGER_SKIPPED_MISSING_PERMISSIONS -> {
                val permissionNames = resolved.args.getOrNull(0)
                    ?.takeIf { it.isNotBlank() }
                    ?: stringResolver(R.string.ui_inspector_unknown, emptyArray())
                stringResolver(
                    R.string.log_message_trigger_skipped_missing_permissions,
                    arrayOf(permissionNames)
                )
            }
        }
    }

    private fun inferLegacyMessage(message: String?): ResolvedMessage? {
        val normalizedMessage = message?.trim().orEmpty()
        if (normalizedMessage.isEmpty()) {
            return null
        }

        return when (normalizedMessage) {
            "执行完毕",
            "执行完成" -> ResolvedMessage(LogMessageKey.EXECUTION_COMPLETED)
            "执行已停止" -> ResolvedMessage(LogMessageKey.EXECUTION_CANCELLED)
            else -> {
                val match = legacyFailurePattern.matchEntire(normalizedMessage)
                    ?: legacyFailurePatternCompact.matchEntire(normalizedMessage)
                match?.destructured?.let { (stepNumber, moduleName) ->
                    ResolvedMessage(
                        key = LogMessageKey.EXECUTION_FAILED_AT_STEP,
                        args = listOf(stepNumber, moduleName)
                    )
                }
            }
        }
    }

    private data class ResolvedMessage(
        val key: LogMessageKey,
        val args: List<String> = emptyList()
    )
}
