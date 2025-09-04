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
import com.chaomixian.vflow.ui.common.BaseActivity // 导入 BaseActivity
import com.google.android.material.appbar.AppBarLayout
import android.widget.Button
import android.widget.TextView

// 继承 BaseActivity 以应用动态主题
class PermissionActivity : BaseActivity() {

    private lateinit var requiredPermissions: ArrayList<Permission>
    private lateinit var headerTextView: TextView
    private lateinit var continueButton: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PermissionAdapter
    private var workflowName: String? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // 回调后不需要做特殊处理，onResume会统一刷新
        }

    private val appSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // 从设置页面返回后，不需要做特殊处理，onResume会统一刷新
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

        setupUI()
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && permission.id == Manifest.permission.POST_NOTIFICATIONS) {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            PermissionType.SPECIAL -> {
                val intent = when(permission.id) {
                    PermissionManager.ACCESSIBILITY.id -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    PermissionManager.OVERLAY.id -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
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