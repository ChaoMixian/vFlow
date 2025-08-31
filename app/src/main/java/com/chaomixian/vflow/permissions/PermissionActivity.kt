package com.chaomixian.vflow.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.color.MaterialColors

class PermissionActivity : AppCompatActivity() {

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
        // 每次返回页面时都刷新权限状态
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
                // 可以扩展其他运行时权限
            }
            PermissionType.SPECIAL -> {
                if (permission.id == PermissionManager.ACCESSIBILITY.id) {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    startActivity(intent)
                }
                // 可以扩展其他特殊权限，如悬浮窗
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

class PermissionAdapter(
    private val permissions: List<Permission>,
    private val onGrantClick: (Permission) -> Unit
) : RecyclerView.Adapter<PermissionAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.text_permission_name)
        val status: TextView = view.findViewById(R.id.text_permission_status)
        val description: TextView = view.findViewById(R.id.text_permission_description)
        val grantButton: Button = view.findViewById(R.id.button_grant_permission)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_permission, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val permission = permissions[position]
        val context = holder.itemView.context

        holder.name.text = permission.name
        holder.description.text = permission.description

        val isGranted = PermissionManager.isGranted(context, permission)

        if (isGranted) {
            holder.status.text = context.getString(R.string.permission_status_granted)
            holder.status.setTextColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimary, 0))
            holder.grantButton.text = context.getString(R.string.permission_button_granted)
            holder.grantButton.isEnabled = false
        } else {
            holder.status.text = context.getString(R.string.permission_status_denied)
            holder.status.setTextColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorError, 0))
            holder.grantButton.text = context.getString(R.string.permission_button_grant)
            holder.grantButton.isEnabled = true
            holder.grantButton.setOnClickListener { onGrantClick(permission) }
        }
    }

    override fun getItemCount() = permissions.size
}