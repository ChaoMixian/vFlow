package com.chaomixian.vflow.ui.shortcut

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.ui.common.BaseActivity
import com.chaomixian.vflow.ui.common.ShortcutHelper
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.io.File
import java.io.FileOutputStream

/**
 * 快捷方式配置页面。允许用户设置快捷方式的名称和图标。
 */
class ShortcutConfigActivity : BaseActivity() {

    private lateinit var workflowManager: WorkflowManager
    private lateinit var workflow: Workflow
    private var selectedIconRes: String? = null
    private var customImagePath: String? = null

    private lateinit var nameEditText: TextInputEditText
    private lateinit var iconAdapter: IconSelectorAdapter
    private var originalShortcutName: String? = null
    private var originalShortcutIconRes: String? = null

    companion object {
        const val EXTRA_WORKFLOW_ID = "workflow_id"
        private const val PICK_IMAGE_REQUEST = 1001
    }

    // 相册选择结果
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            handleImageSelection(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shortcut_config)

        workflowManager = WorkflowManager(this)

        // 获取工作流 ID
        val workflowId = intent.getStringExtra(EXTRA_WORKFLOW_ID)
        if (workflowId == null) {
            finish()
            return
        }

        workflow = workflowManager.getWorkflow(workflowId) ?: run {
            finish()
            return
        }

        // 保存原始配置
        originalShortcutName = workflow.shortcutName
        originalShortcutIconRes = workflow.shortcutIconRes

        setupUI()
        loadCurrentConfig()
    }

    private fun setupUI() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.title = getString(R.string.title_shortcut_config)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        nameEditText = findViewById(R.id.edit_text_shortcut_name)

        // 设置图标选择器
        val iconRecyclerView = findViewById<RecyclerView>(R.id.recycler_view_icons)
        iconAdapter = IconSelectorAdapter(
            onIconSelected = { iconRes ->
                selectedIconRes = iconRes
                customImagePath = null
                // 不需要再次调用 setSelectedIcon，适配器内部已经处理了选中状态
            },
            onCustomImageSelected = {
                openImagePicker()
            }
        )
        iconRecyclerView.layoutManager = GridLayoutManager(this, 5)
        iconRecyclerView.adapter = iconAdapter

        // 保存并添加快捷方式按钮
        findViewById<MaterialButton>(R.id.btn_save).setOnClickListener {
            saveShortcutConfigAndCreate()
        }

        // 重置按钮
        findViewById<MaterialButton>(R.id.btn_reset).setOnClickListener {
            resetToDefault()
        }
    }

    private fun loadCurrentConfig() {
        // 加载当前名称
        nameEditText.setText(workflow.shortcutName)

        // 加载当前图标
        selectedIconRes = workflow.shortcutIconRes

        // 检查是否是自定义图片
        if (workflow.shortcutIconRes?.startsWith("file://") == true ||
            workflow.shortcutIconRes?.startsWith("/") == true) {
            customImagePath = workflow.shortcutIconRes
            iconAdapter.setCustomImage(workflow.shortcutIconRes!!)
        } else {
            iconAdapter.setSelectedIcon(workflow.shortcutIconRes)
        }
    }

    private fun openImagePicker() {
        pickImage.launch("image/*")
    }

    private fun handleImageSelection(uri: Uri) {
        try {
            // 获取文件名
            val fileName = getFileName(uri) ?: "custom_icon_${System.currentTimeMillis()}.png"

            // 创建快捷方式图标目录
            val iconsDir = File(filesDir, "shortcut_icons")
            if (!iconsDir.exists()) {
                iconsDir.mkdirs()
            }

            // 保存图片到应用目录
            val iconFile = File(iconsDir, fileName)
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(iconFile).use { output ->
                    input.copyTo(output)
                }
            }

            customImagePath = iconFile.absolutePath
            selectedIconRes = customImagePath
            iconAdapter.setCustomImage(customImagePath!!)

            Toast.makeText(this, "图片已选择", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "图片选择失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (columnIndex >= 0) {
                        result = cursor.getString(columnIndex)
                    }
                }
            }
        }
        return result
    }

    private fun saveShortcutConfigAndCreate() {
        val nameText = nameEditText.text?.toString()?.trim()

        android.util.Log.d("ShortcutConfigActivity", "Saving shortcut config for workflow: ${workflow.name}")
        android.util.Log.d("ShortcutConfigActivity", "Custom name: $nameText, selected icon: $selectedIconRes")

        // 确定最终的图标路径
        val finalIconPath = customImagePath ?: selectedIconRes

        // 更新工作流配置
        val updatedWorkflow = workflow.copy(
            shortcutName = if (nameText.isNullOrEmpty()) null else nameText,
            shortcutIconRes = finalIconPath
        )

        android.util.Log.d("ShortcutConfigActivity", "Updated workflow - shortcutName: ${updatedWorkflow.shortcutName}, shortcutIconRes: ${updatedWorkflow.shortcutIconRes}")

        workflowManager.saveWorkflow(updatedWorkflow)

        // 验证保存是否成功
        val savedWorkflow = workflowManager.getWorkflow(workflow.id)
        android.util.Log.d("ShortcutConfigActivity", "Saved workflow - shortcutName: ${savedWorkflow?.shortcutName}, shortcutIconRes: ${savedWorkflow?.shortcutIconRes}")

        Toast.makeText(this, R.string.button_save_shortcut, Toast.LENGTH_SHORT).show()

        // 创建快捷方式
        ShortcutHelper.requestPinnedShortcut(this, updatedWorkflow)

        // 延迟关闭 Activity，以便用户看到 Toast
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            finish()
        }, 500)
    }

    private fun resetToDefault() {
        nameEditText.text = null
        selectedIconRes = null
        customImagePath = null
        iconAdapter.setSelectedIcon(null)
        Toast.makeText(this, R.string.button_reset_default, Toast.LENGTH_SHORT).show()
    }
}
