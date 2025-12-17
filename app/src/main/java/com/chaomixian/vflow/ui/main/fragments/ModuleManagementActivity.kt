package com.chaomixian.vflow.ui.main.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.workflow.module.scripted.ModuleManager
import com.chaomixian.vflow.core.workflow.module.scripted.ModuleManifest
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.chip.Chip
import com.chaomixian.vflow.permissions.PermissionManager

/**
 * 模块管理 Fragment。
 * 显示已安装的模块列表，并提供安装/删除功能。
 */
class ModuleManagementFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ModuleListAdapter

    // 文件选择器 Launcher
    private val installModuleLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val result = ModuleManager.installModule(requireContext(), it)
            if (result.isSuccess) {
                Toast.makeText(requireContext(), result.getOrNull(), Toast.LENGTH_SHORT).show()
                refreshList()
            } else {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("安装失败")
                    .setMessage(result.exceptionOrNull()?.message)
                    .setPositiveButton("确定", null)
                    .show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_module_management, container, false)

        recyclerView = view.findViewById(R.id.recycler_view_modules)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        view.findViewById<ExtendedFloatingActionButton>(R.id.fab_install_module).setOnClickListener {
            // 启动文件选择器，过滤 ZIP 文件
            installModuleLauncher.launch(arrayOf("application/zip"))
        }

        refreshList()
        return view
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    /** 刷新模块列表 */
    private fun refreshList() {
        val modules = ModuleManager.getInstalledModules(requireContext())
        adapter = ModuleListAdapter(modules) { manifest ->
            showDeleteDialog(manifest)
        }
        recyclerView.adapter = adapter
    }

    /** 显示删除确认对话框 */
    private fun showDeleteDialog(manifest: ModuleManifest) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除模块")
            .setMessage("确定要删除 '${manifest.name}' 吗？\n删除后将在下次应用启动时生效。")
            .setPositiveButton("删除") { _, _ ->
                ModuleManager.deleteModule(requireContext(), manifest.id)
                refreshList()
                Toast.makeText(requireContext(), "模块已删除", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 内部适配器类 */
    class ModuleListAdapter(
        private val modules: List<ModuleManifest>,
        private val onDeleteClick: (ModuleManifest) -> Unit
    ) : RecyclerView.Adapter<ModuleListAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.text_module_name)
            val description: TextView = view.findViewById(R.id.text_module_description)
            val author: TextView = view.findViewById(R.id.text_module_author)
            val version: TextView = view.findViewById(R.id.text_module_version)
            val deleteButton: View = view.findViewById(R.id.button_delete_module)
            val permissionsGroup: ViewGroup = view.findViewById(R.id.chip_group_permissions)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_module, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val module = modules[position]
            val context = holder.itemView.context

            // 基础信息
            holder.name.text = module.name
            holder.description.text = module.description

            // 作者与版本 (没有则隐藏)
            if (!module.author.isNullOrBlank() && module.author != "Unknown") {
                holder.author.visibility = View.VISIBLE
                holder.author.text = "作者: ${module.author}"
            } else {
                holder.author.visibility = View.GONE
            }

            if (!module.version.isNullOrBlank() && module.version != "Unknown") {
                holder.version.visibility = View.VISIBLE
                holder.version.text = "版本：${module.version}"
            } else {
                holder.version.visibility = View.GONE
            }

            // 删除按钮
            holder.deleteButton.setOnClickListener { onDeleteClick(module) }

            // 权限展示 (参考工作流卡片逻辑)
            holder.permissionsGroup.removeAllViews()
            val inflater = LayoutInflater.from(context)

            module.permissions?.forEach { permId ->
                // 尝试从权限管理器匹配友好名称
                val permission = PermissionManager.allKnownPermissions.find { it.id == permId }
                val chipText = permission?.name ?: permId.substringAfterLast('.')

                val chip = inflater.inflate(R.layout.chip_permission, holder.permissionsGroup, false) as Chip
                chip.text = chipText
                chip.setChipIconResource(R.drawable.ic_shield)
                holder.permissionsGroup.addView(chip)
            }
            holder.permissionsGroup.visibility = if (holder.permissionsGroup.childCount > 0) View.VISIBLE else View.GONE
        }

        override fun getItemCount() = modules.size
    }
}