package com.chaomixian.vflow.core.utils

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.DoNotInline
import androidx.annotation.RequiresApi

fun AccessibilityNodeInfo.getCompatUniqueId(): String? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Api33Impl.getUniqueId(this)
    } else {
        null
    }
}

fun AccessibilityNodeInfo.getCompatStableId(): Int {
    return getCompatUniqueId()?.hashCode() ?: hashCode()
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object Api33Impl {
    @DoNotInline
    fun getUniqueId(node: AccessibilityNodeInfo): String? = node.uniqueId
}
