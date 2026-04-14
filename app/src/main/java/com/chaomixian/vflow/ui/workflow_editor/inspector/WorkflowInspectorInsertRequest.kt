package com.chaomixian.vflow.ui.workflow_editor.inspector

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class WorkflowInspectorInsertRequest(
    val type: Type,
    val value: String? = null,
    val packageName: String? = null,
    val activityName: String? = null
) : Parcelable {

    enum class Type {
        CLICK,
        UI_SELECTOR,
        LAUNCH_APP,
        LAUNCH_ACTIVITY,
        FIND_TEXT
    }

    companion object {
        const val ACTION_INSERT_REQUEST = "com.chaomixian.vflow.action.WORKFLOW_INSPECTOR_INSERT"
        const val EXTRA_ENABLE_WORKFLOW_INSERT = "enable_workflow_insert"
        const val EXTRA_INSERT_REQUEST = "insert_request"

        fun click(target: String) = WorkflowInspectorInsertRequest(
            type = Type.CLICK,
            value = target
        )

        fun uiSelector(selector: String) = WorkflowInspectorInsertRequest(
            type = Type.UI_SELECTOR,
            value = selector
        )

        fun launchApp(packageName: String) = WorkflowInspectorInsertRequest(
            type = Type.LAUNCH_APP,
            packageName = packageName,
            activityName = "LAUNCH"
        )

        fun launchActivity(packageName: String, activityName: String) = WorkflowInspectorInsertRequest(
            type = Type.LAUNCH_ACTIVITY,
            packageName = packageName,
            activityName = activityName
        )

        fun findText(text: String) = WorkflowInspectorInsertRequest(
            type = Type.FIND_TEXT,
            value = text
        )
    }
}
