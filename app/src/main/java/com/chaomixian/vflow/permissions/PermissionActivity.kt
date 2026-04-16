// 文件: main/java/com/chaomixian/vflow/permissions/PermissionActivity.kt
// 描述: 权限请求 Activity，用于向用户请求工作流或应用所需的各项权限。

package com.chaomixian.vflow.permissions

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.workflow.WorkflowPermissionRecovery
import com.chaomixian.vflow.services.ShellManager
import com.chaomixian.vflow.ui.common.BaseActivity
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.button.MaterialButton
import android.widget.Button
import android.widget.TextView
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import com.chaomixian.vflow.core.locale.toast
import com.chaomixian.vflow.core.logging.DebugLogger
import androidx.core.net.toUri

// 继承 BaseActivity 以应用动态主题
class PermissionActivity : BaseActivity() {

    companion object {
        const val EXTRA_PERMISSIONS = "permissions_list"
        const val EXTRA_WORKFLOW_NAME = "workflow_name"
        private const val TAG = "PermissionActivity"
    }

    private lateinit var requiredPermissions: ArrayList<Permission>
    private lateinit var headerTextView: TextView
    private lateinit var continueButton: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PermissionAdapter
    private var workflowName: String? = null
    private val SHIZUKU_PERMISSION_REQUEST_CODE = 123

    // 统一使用一个多权限请求启动器
    private val requestMultiplePermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            // 收到结果后，onResume 会刷新状态，这里无需额外操作
        }

    private val appSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // 从设置页面返回后，不需要做特殊处理，onResume会统一刷新
        }

    // 定义 Shizuku 权限请求结果监听器
    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
                // 收到结果后，刷新权限状态
                refreshPermissionsStatus()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission)
        applyWindowInsets()

        requiredPermissions = intent.getParcelableArrayListExtra(EXTRA_PERMISSIONS) ?: arrayListOf()
        workflowName = intent.getStringExtra(EXTRA_WORKFLOW_NAME)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar_permission)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        headerTextView = findViewById(R.id.text_permission_header)
        continueButton = findViewById(R.id.button_permission_continue)
        recyclerView = findViewById(R.id.recycler_view_permissions)

        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)

        setupUI()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_permission, menu)

        // 设置一键授权按钮的点击监听器
        val grantAllItem = menu.findItem(R.id.menu_grant_all)
        val grantAllButton = grantAllItem?.actionView as? MaterialButton
        grantAllButton?.setOnClickListener {
            grantAllPermissions()
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_grant_all -> {
                grantAllPermissions()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 移除监听器，防止内存泄漏
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionsStatus()
        WorkflowPermissionRecovery.recoverEligibleWorkflows(this)
    }

    private fun setupUI() {
        if (workflowName != null) {
            headerTextView.text = getString(R.string.permission_header_for_workflow, workflowName)
            continueButton.isVisible = true
            continueButton.setOnClickListener {
                setResult(RESULT_OK)
                finish()
            }
        } else {
            headerTextView.text = getString(R.string.permission_header_global)
            continueButton.isVisible = false
        }

        adapter = PermissionAdapter(requiredPermissions) { permission ->
            requestPermission(permission)
        }
        recyclerView.adapter = adapter
    }

    private fun refreshPermissionsStatus() {
        adapter.notifyDataSetChanged()
        val allGranted = requiredPermissions.all { PermissionManager.isGranted(this, it) }
        continueButton.isEnabled = allGranted
    }

    /**
     * 重构权限请求逻辑，以正确处理权限组。
     */
    private fun requestPermission(permission: Permission) {
        when (permission.type) {
            PermissionType.RUNTIME -> {
                // 如果 runtimePermissions 列表不为空，说明是权限组，请求列表中的所有权限
                val permissionsToRequest = if (permission.runtimePermissions.isNotEmpty()) {
                    permission.runtimePermissions.toTypedArray()
                } else {
                    // 否则，是单个权限，只请求其 id
                    arrayOf(permission.id)
                }
                requestMultiplePermissionsLauncher.launch(permissionsToRequest)
            }
            PermissionType.SPECIAL -> {
                if (permission.id == PermissionManager.SHIZUKU.id) {
                    try {
                        Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
                    } catch (e: Exception) {
                        // Shizuku 未启动或异常
                        toast(R.string.permission_toast_shizuku_not_started)
                    }
                    return
                }
                // 使用 PermissionManager 提供的统一接口获取 Intent
                val intent = PermissionManager.getSpecialPermissionIntent(this, permission)
                intent?.let { appSettingsLauncher.launch(it) }
            }
        }
    }


    private fun applyWindowInsets() {
        val appBar = findViewById<AppBarLayout>(R.id.app_bar_layout_permission)
        ViewCompat.setOnApplyWindowInsetsListener(appBar) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top)
            insets
        }
    }

    /**
     * 一键授予所有权限
     * 使用 adb 命令通过 Shizuku 或 Root 授予所有可自动授予的权限
     */
    private fun grantAllPermissions() {
        lifecycleScope.launch {
            try {
                // 检查是否有可用的 Shell 方式
                val canUseShell = ShellManager.isShizukuActive(this@PermissionActivity) ||
                                 ShellManager.isRootAvailable()
                if (!canUseShell) {
                    toast(R.string.permission_grant_all_no_shell)
                    return@launch
                }

                toast(R.string.permission_grant_all_started)

                var grantedCount = 0
                var failedCount = 0
                val totalCount = requiredPermissions.size

                // 遍历所有权限并尝试授予
                for (permission in requiredPermissions) {
                    // 只尝试授予尚未授予的权限
                    if (PermissionManager.isGranted(this@PermissionActivity, permission)) {
                        continue
                    }

                    val success = PermissionManager.autoGrantPermission(this@PermissionActivity, permission)
                    if (success) {
                        grantedCount++
                    } else {
                        failedCount++
                    }
                }

                // 延迟一下再刷新状态
                kotlinx.coroutines.delay(500)
                refreshPermissionsStatus()

                // 显示结果
                when {
                    grantedCount > 0 && failedCount == 0 -> {
                        toast(getString(R.string.permission_grant_all_success, grantedCount))
                    }
                    grantedCount > 0 && failedCount > 0 -> {
                        toast(getString(R.string.permission_grant_all_partial, grantedCount, failedCount))
                    }
                    failedCount > 0 && grantedCount == 0 -> {
                        toast(getString(R.string.permission_grant_all_failed, failedCount))
                    }
                    else -> {
                        toast(R.string.permission_grant_all_already_granted)
                    }
                }
            } catch (e: Exception) {
                DebugLogger.e(TAG, "一键授权出错", e)
                toast(getString(R.string.permission_grant_all_error, e.message ?: "未知错误"))
            }
        }
    }
}
