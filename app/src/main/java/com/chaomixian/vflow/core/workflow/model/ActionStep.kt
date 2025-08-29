package com.chaomixian.vflow.core.workflow.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue
import java.util.UUID

@Parcelize
data class ActionStep(
    val moduleId: String,
    val parameters: @RawValue Map<String, Any?>,
    var indentationLevel: Int = 0,
    // ID对于稳定的列表操作至关重要
    val id: String = UUID.randomUUID().toString()
) : Parcelable {

    // --- 核心修复 ---
    // 我们重写 equals 和 hashCode，让它们只依赖于唯一的 `id`。
    // 这可以防止不稳定的 `parameters` map 在集合操作（如`removeAll`）中导致崩溃。
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