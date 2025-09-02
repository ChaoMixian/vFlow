// 文件: main/java/com/chaomixian/vflow/core/workflow/model/ActionStep.kt

package com.chaomixian.vflow.core.workflow.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue
import java.util.UUID

@Parcelize
data class ActionStep(
    val moduleId: String,
    // --- 核心修复 ---
    // 为 parameters 添加 @RawValue 注解，以解决 Parcelize 无法处理 Any? 类型的编译错误。
    val parameters: @RawValue Map<String, Any?>,
    var indentationLevel: Int = 0,
    // ID对于稳定的列表操作至关重要
    val id: String = UUID.randomUUID().toString()
) : Parcelable {

    // 我们重写 equals 和 hashCode，让它们只依赖于唯一的 `id`。
    // 这可以防止不稳定的 `parameters` map 在集合操作（如 DiffUtil）中导致问题。
    // 列表现在可以安全且唯一地识别每个步骤，无论其内容如何。

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ActionStep
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}