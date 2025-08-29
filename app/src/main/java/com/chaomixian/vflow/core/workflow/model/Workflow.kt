package com.chaomixian.vflow.core.workflow.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Workflow(
    val id: String,
    var name: String,
    var steps: List<ActionStep>,
    var script: String = "" // <-- 新增：用于存储生成的Lua脚本
) : Parcelable