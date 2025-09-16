// 文件: main/java/com/chaomixian/vflow/permissions/PermissionActivity.kt
// 描述: 权限请求 Activity，用于向用户请求工作流或应用所需的各项权限。

package com.chaomixian.vflow.permissions

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.ui.common.BaseActivity
import com.google.android.material.appbar.AppBarLayout
import android.widget.Button
import android.widget.TextView
import rikka.shizuku.Shizuku

// 继承 BaseActivity 以应用动态主题
class PermissionActivity : BaseActivity() {

    private lateinit var requiredPermissions: ArrayList<Permission>
    private lateinit var headerTextView: TextView
    private lateinit var continueButton: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PermissionAdapter
    private var workflowName: String? = null
    private val SHIZUKU_PERMISSION_REQUEST_CODE = 123

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // 回调后不需要做特殊处理，onResume会统一刷新
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

    override fun onDestroy() {
        super.onDestroy()
        // 移除监听器，防止内存泄漏
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionsStatus()
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

    private fun requestPermission(permission: Permission) {
        when (permission.type) {
            PermissionType.RUNTIME -> {
                // 智能判断需要请求的具体权限
                var permissionToRequest = permission.id
                // 如果是我们定义的“存储权限”，并且系统版本是 Android 13 或更高
                if (permission.id == Manifest.permission.READ_EXTERNAL_STORAGE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // 那么实际要请求的权限是 READ_MEDIA_IMAGES
                    permissionToRequest = Manifest.permission.READ_MEDIA_IMAGES
                }
                // 使用最终确定的权限ID来发起请求
                requestPermissionLauncher.launch(permissionToRequest)
            }
            PermissionType.SPECIAL -> {
                if (permission.id == PermissionManager.SHIZUKU.id) {
                    Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
                    return // 直接返回，等待回调
                }

                val intent = when(permission.id) {
                    PermissionManager.ACCESSIBILITY.id -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    PermissionManager.OVERLAY.id -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    } else null
                    PermissionManager.WRITE_SETTINGS.id -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))
                    } else null
                    // 添加对电池优化权限的处理
                    PermissionManager.IGNORE_BATTERY_OPTIMIZATIONS.id -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
                    } else null
                    // 添加对精确闹钟权限的处理
                    PermissionManager.EXACT_ALARM.id -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    } else null
                    else -> null
                }
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

    companion object {
        const val EXTRA_PERMISSIONS = "permissions_list"
        const val EXTRA_WORKFLOW_NAME = "workflow_name"
    }
}