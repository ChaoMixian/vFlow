// 文件: java/com/chaomixian/vflow/ui/common/DynamicUiActivity.kt
package com.chaomixian.vflow.ui.common

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import com.chaomixian.vflow.core.workflow.module.ui.UiCommand
import com.chaomixian.vflow.core.workflow.module.ui.UiEvent
import com.chaomixian.vflow.core.workflow.module.ui.UiSessionBus
import com.chaomixian.vflow.core.workflow.module.ui.model.UiElement
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import android.view.View
import android.widget.TextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Serializable

class DynamicUiActivity : BaseActivity() {

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ELEMENTS = "extra_elements"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_IS_INTERACTIVE = "extra_is_interactive"
        const val EXTRA_SESSION_ID = "session_id"
    }

    private var isInteractive = false
    private var sessionId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "用户界面"
        isInteractive = intent.getBooleanExtra(EXTRA_IS_INTERACTIVE, false)
        sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: ""

        @Suppress("UNCHECKED_CAST")
        val elements = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(EXTRA_ELEMENTS, UiElement::class.java)
        } else {
            intent.getParcelableArrayListExtra(EXTRA_ELEMENTS)
        } ?: arrayListOf()

        // 动态构建布局
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            fitsSystemWindows = true
        }

        val toolbar = MaterialToolbar(this).apply {
            this.title = title
            // 使用应用主题色
            setBackgroundColor(getColor(com.google.android.material.R.color.material_dynamic_primary95))
            setNavigationIcon(com.google.android.material.R.drawable.material_ic_keyboard_arrow_left_black_24dp)
            setNavigationOnClickListener {
                handleBackPress()
            }
        }
        rootLayout.addView(toolbar)

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        val contentContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
        }
        scrollView.addView(contentContainer)
        rootLayout.addView(scrollView)
        setContentView(rootLayout)

        // 渲染组件
        DynamicUiRenderer.render(this, elements, contentContainer, lifecycleScope, sessionId) { data ->
            // 提交回调 (仅用于非交互模式的提交按钮，或交互模式下的最终提交)
            finishWithResult(data)
        }

        // 如果是交互模式，启动指令监听
        if (isInteractive && sessionId.isNotEmpty()) {
            startCommandListener()
        }

        // 处理物理返回键
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPress()
            }
        })
    }

    private fun handleBackPress() {
        if (isInteractive) {
            // 交互模式下，返回键视为一个事件，而不是直接关闭 Activity
            // 这样工作流可以决定是关闭还是提示
            lifecycleScope.launch(Dispatchers.IO) {
                val allValues = DynamicUiRenderer.collectAllValues()
                UiSessionBus.emitEvent(UiEvent(sessionId, "system", "back_pressed", null, allValues))
            }
            // 同时为了保险，默认行为是关闭。如果需要拦截，可以在这里不做 finish，等待 workflow 指令。
            // 这里我们选择直接关闭，并通知总线
            finish()
        } else {
            finish()
        }
    }

    private fun startCommandListener() {
        lifecycleScope.launch {
            UiSessionBus.getCommandFlow(sessionId)?.collectLatest { cmd ->
                handleCommand(cmd)
            }
        }
    }

    private suspend fun handleCommand(cmd: UiCommand) = withContext(Dispatchers.Main) {
        when (cmd.type) {
            "close" -> {
                finishWithResult(DynamicUiRenderer.collectData())
            }
            "toast" -> {
                val msg = cmd.payload["message"] as? String
                if (!msg.isNullOrBlank()) {
                    Toast.makeText(this@DynamicUiActivity, msg, Toast.LENGTH_SHORT).show()
                }
            }
            "update" -> {
                val targetId = cmd.targetId
                if (targetId != null) {
                    val view = DynamicUiRenderer.getView(targetId)
                    if (view != null) {
                        updateViewProperties(view, cmd.payload)
                    }
                }
            }
        }
    }

    private fun updateViewProperties(view: View, payload: Map<String, Any?>) {
        val newText = payload["text"] as? String
        val newEnabled = payload["enabled"] as? Boolean
        val newVisible = payload["visible"] as? Boolean
        val newChecked = payload["checked"] as? Boolean

        // 布局属性
        val padding = payload["padding"] as? Number
        val margin = payload["margin"] as? Number
        val textSize = payload["textSize"] as? Number
        val background = payload["background"] as? String

        // 应用可见性
        if (newVisible != null) view.visibility = if (newVisible) View.VISIBLE else View.GONE

        // 应用启用状态
        if (newEnabled != null) view.isEnabled = newEnabled

        // 应用内边距
        if (padding != null) {
            val p = padding.toInt()
            view.setPadding(p, p, p, p)
        }

        // 应用外边距
        if (margin != null) {
            val m = margin.toInt()
            (view.layoutParams as? LinearLayout.LayoutParams)?.let {
                it.setMargins(m, m, m, m)
                view.layoutParams = it
            }
        }

        // 应用背景色（支持颜色名称或十六进制）
        if (background != null) {
            try {
                val color = when {
                    background.startsWith("#") -> android.graphics.Color.parseColor(background)
                    else -> android.graphics.Color.parseColor("#$background")
                }
                view.setBackgroundColor(color)
            } catch (e: Exception) {
                // 忽略无效的颜色值
            }
        }

        // 应用组件特定的属性
        when (view) {
            is TextView -> { // 包含 Button 和 MaterialSwitch 的文本
                if (newText != null) view.text = newText
                if (textSize != null) view.textSize = textSize.toFloat()
            }
            is TextInputEditText -> {
                if (newText != null && newText != view.text.toString()) {
                    view.setText(newText)
                    // 移动光标到末尾
                    view.setSelection(newText.length)
                }
                if (textSize != null) view.textSize = textSize.toFloat()
            }
            is MaterialSwitch -> {
                if (newText != null) view.text = newText
                if (newChecked != null && newChecked != view.isChecked) {
                    view.isChecked = newChecked
                }
            }
        }
    }

    private fun finishWithResult(data: Map<String, Any>) {
        val resultIntent = Intent()
        resultIntent.putExtra(EXTRA_RESULT_DATA, data as Serializable)
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isInteractive && sessionId.isNotEmpty()) {
            // 通知总线窗口已关闭，防止工作流无限等待
            lifecycleScope.launch(Dispatchers.IO) {
                val allValues = DynamicUiRenderer.collectAllValues()
                UiSessionBus.emitEvent(UiEvent(sessionId, "system", "closed", null, allValues))
                UiSessionBus.notifyClosed(sessionId)
            }
        }
    }
}