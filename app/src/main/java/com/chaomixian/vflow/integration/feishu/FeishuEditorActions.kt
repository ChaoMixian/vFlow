package com.chaomixian.vflow.integration.feishu

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.EditorAction
import com.chaomixian.vflow.ui.settings.ModuleConfigActivity

object FeishuEditorActions {
    fun openModuleConfigAction(): EditorAction {
        return EditorAction(
            labelStringRes = R.string.module_editor_action_open_feishu_config
        ) { context: Context ->
            context.startActivity(
                ModuleConfigActivity.createIntent(
                    context,
                    ModuleConfigActivity.SECTION_FEISHU
                )
            )
        }
    }
}
