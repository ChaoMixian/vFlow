// 文件: main/java/com/chaomixian/vflow/ui/main/fragments/ModuleManagementFragment.kt
package com.chaomixian.vflow.ui.main.fragments

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.AttrRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.BaseModule
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.core.utils.StorageManager
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.module.scripted.ModuleManager
import com.chaomixian.vflow.core.workflow.module.scripted.ScriptedModule
import com.chaomixian.vflow.permissions.Permission
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ModuleManagementFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ModuleListAdapter
    private lateinit var fab: ExtendedFloatingActionButton

    private val installModuleLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            installModule(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_module_management, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recycler_view_modules)
        fab = view.findViewById(R.id.fab_install_module)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = ModuleListAdapter(emptyList(), ::showModuleDetails, ::confirmDeleteModule)
        recyclerView.adapter = adapter

        if (!StorageManager.modulesDir.exists()) {
            StorageManager.modulesDir.mkdirs()
        }

        refreshModuleList()

        fab.setOnClickListener {
            installModuleLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed"))
        }
    }

    private fun refreshModuleList() {
        val allModules = ModuleRegistry.getAllModules()
        val userModules = allModules.filter { it is ScriptedModule }
        val builtInModules = allModules.filter { it !is ScriptedModule }

        val listItems = mutableListOf<ModuleListItem>()

        if (userModules.isNotEmpty()) {
            listItems.add(ModuleListItem.Header("用户模块 (${userModules.size})"))
            listItems.addAll(userModules.map { ModuleListItem.Item(it as BaseModule) })
        }

        if (builtInModules.isNotEmpty()) {
            listItems.add(ModuleListItem.Header("内置模块 (${builtInModules.size})"))
            listItems.addAll(builtInModules.map { ModuleListItem.Item(it as BaseModule) })
        }

        if (listItems.isEmpty()) {
            listItems.add(ModuleListItem.Header("暂无模块"))
        }

        adapter.updateData(listItems)
    }

    private fun installModule(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val prepareResult = ModuleManager.prepareInstall(requireContext(), uri)

            withContext(Dispatchers.Main) {
                prepareResult.onSuccess { session ->
                    if (ModuleManager.isModuleInstalled(session.manifest.id)) {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("模块已存在")
                            .setMessage("检测到 ID 为 \"${session.manifest.id}\" 的模块已存在。\n\n是否覆盖安装？")
                            .setPositiveButton("覆盖") { _, _ ->
                                performInstallCommit(session)
                            }
                            .setNegativeButton("取消", null)
                            .show()
                    } else {
                        performInstallCommit(session)
                    }
                }.onFailure { e ->
                    showErrorDialog("解析模块失败", e.message)
                }
            }
        }
    }

    private fun performInstallCommit(session: ModuleManager.InstallSession) {
        lifecycleScope.launch(Dispatchers.IO) {
            val commitResult = ModuleManager.commitInstall(session)
            withContext(Dispatchers.Main) {
                commitResult.onSuccess { msg ->
                    refreshModuleList()
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                }.onFailure { e ->
                    showErrorDialog("安装失败", e.message)
                }
            }
        }
    }

    private fun confirmDeleteModule(module: BaseModule) {
        lifecycleScope.launch(Dispatchers.IO) {
            val dependencyNames = checkDependencies(module.id)

            withContext(Dispatchers.Main) {
                val builder = MaterialAlertDialogBuilder(requireContext())

                if (dependencyNames.isNotEmpty()) {
                    val msg = StringBuilder()
                    msg.append("警告：以下 ${dependencyNames.size} 个工作流正在使用此模块：\n\n")
                    dependencyNames.forEach { name -> msg.append("• $name\n") }
                    msg.append("\n强制删除会导致这些工作流失效。")

                    builder.setTitle("存在依赖关系")
                        .setMessage(msg.toString())
                        .setPositiveButton("强制删除") { _, _ -> deleteModule(module) }
                        .setNegativeButton("取消", null)
                        .create().apply {
                            setOnShowListener {
                                getButton(android.content.DialogInterface.BUTTON_POSITIVE).setTextColor(
                                    resources.getColor(android.R.color.holo_red_light, null)
                                )
                            }
                        }.show()
                } else {
                    builder.setTitle("删除模块")
                        .setMessage("确定要删除模块 \"${module.metadata.name}\" 吗？")
                        .setPositiveButton("删除") { _, _ -> deleteModule(module) }
                        .setNegativeButton("取消", null)
                        .show()
                }
            }
        }
    }

    private fun checkDependencies(moduleId: String): List<String> {
        val workflowManager = WorkflowManager(requireContext())
        return workflowManager.getAllWorkflows()
            .filter { wf -> wf.steps.any { it.moduleId == moduleId } }
            .map { it.name }
    }

    private fun deleteModule(module: BaseModule) {
        lifecycleScope.launch(Dispatchers.IO) {
            val modulesDir = StorageManager.modulesDir
            val targetDir = File(modulesDir, module.id)
            if (targetDir.exists()) {
                targetDir.deleteRecursively()
            }

            withContext(Dispatchers.Main) {
                ModuleRegistry.reset()
                ModuleRegistry.initialize()
                ModuleManager.loadModules(requireContext(), force = true)
                refreshModuleList()
                Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showErrorDialog(title: String, message: String?) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message ?: "未知错误")
            .setPositiveButton("确定", null)
            .show()
    }

    // --- 适配器部分 ---

    sealed class ModuleListItem {
        data class Header(val title: String) : ModuleListItem()
        data class Item(val module: BaseModule) : ModuleListItem()
    }

    inner class ModuleListAdapter(
        private var items: List<ModuleListItem>,
        private val onInfoClick: (BaseModule) -> Unit,
        private val onDeleteClick: (BaseModule) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val TYPE_HEADER = 0
        private val TYPE_ITEM = 1

        fun updateData(newItems: List<ModuleListItem>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int): Int {
            return when (items[position]) {
                is ModuleListItem.Header -> TYPE_HEADER
                is ModuleListItem.Item -> TYPE_ITEM
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == TYPE_HEADER) {
                val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
                view.setPadding(32, 32, 32, 16)
                val tv = view.findViewById<TextView>(android.R.id.text1)
                tv.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
                tv.setTextColor(parent.context.resolveThemeColor(com.google.android.material.R.attr.colorPrimary))
                object : RecyclerView.ViewHolder(view) {}
            } else {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_module, parent, false)
                ModuleViewHolder(view)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is ModuleListItem.Header -> {
                    val tv = holder.itemView.findViewById<TextView>(android.R.id.text1)
                    tv.text = item.title
                }
                is ModuleListItem.Item -> (holder as ModuleViewHolder).bind(item.module)
            }
        }

        override fun getItemCount(): Int = items.size

        inner class ModuleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val ivIcon: ImageView = itemView.findViewById(R.id.iv_module_icon)
            val tvName: TextView = itemView.findViewById(R.id.tv_module_name)
            val tvCategory: TextView = itemView.findViewById(R.id.tv_module_category)
            val tvDesc: TextView = itemView.findViewById(R.id.tv_module_description)
            val tvAuthorVer: TextView = itemView.findViewById(R.id.tv_module_author_version)
            val btnInfo: ImageButton = itemView.findViewById(R.id.btn_module_info)
            val btnDelete: ImageButton = itemView.findViewById(R.id.btn_delete_module)
            val cgPermissions: ChipGroup = itemView.findViewById(R.id.cg_permissions)

            fun bind(module: BaseModule) {
                val meta = module.metadata
                val context = itemView.context

                tvName.text = meta.name
                tvDesc.text = meta.description
                ivIcon.setImageResource(if (meta.iconRes != 0) meta.iconRes else R.drawable.rounded_circles_ext_24)

                tvCategory.text = meta.category
                tvCategory.visibility = View.VISIBLE

                if (module is ScriptedModule) {
                    btnDelete.visibility = View.VISIBLE
                    btnDelete.setOnClickListener { onDeleteClick(module) }
                    tvAuthorVer.visibility = View.VISIBLE
                    tvAuthorVer.text = "作者: ${module.author}   版本: v${module.version}"
                } else {
                    btnDelete.visibility = View.GONE
                    tvAuthorVer.visibility = View.GONE
                }

                // 权限展示
                cgPermissions.removeAllViews()
                val permissions = try { module.getRequiredPermissions(null) } catch (e: Exception) { emptyList() }

                if (permissions.isNotEmpty()) {
                    cgPermissions.visibility = View.VISIBLE

                    val inflater = LayoutInflater.from(context)

                    permissions.forEach { perm ->
                        // 使用 chip_permission 布局
                        val chip = inflater.inflate(R.layout.chip_permission, cgPermissions, false) as Chip
                        chip.text = getPermissionLabel(perm)
                        chip.setChipIconResource(R.drawable.ic_shield)

                        cgPermissions.addView(chip)
                    }
                } else {
                    cgPermissions.visibility = View.GONE
                }

                btnInfo.setOnClickListener { onInfoClick(module) }
            }
        }
    }

    private fun Context.resolveThemeColor(@AttrRes attrRes: Int): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(attrRes, typedValue, true)
        return typedValue.data
    }

    private fun getPermissionLabel(permission: Permission): String {
        return when (permission.name) {
            "ACCESSIBILITY" -> "无障碍"
            "OVERLAY" -> "悬浮窗"
            "STORAGE" -> "读写存储"
            "USAGE_STATS" -> "应用使用情况"
            "NOTIFICATION_LISTENER" -> "通知读取"
            else -> permission.name
        }
    }

    private fun showModuleDetails(module: BaseModule) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_module_detail, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tv_detail_title)
        val tvId = dialogView.findViewById<TextView>(R.id.tv_detail_id)
        val tvInputs = dialogView.findViewById<TextView>(R.id.tv_detail_inputs)
        val tvOutputs = dialogView.findViewById<TextView>(R.id.tv_detail_outputs)
        val btnClose = dialogView.findViewById<Button>(R.id.btn_close_dialog)

        tvTitle.text = "${module.metadata.name} - 详情"
        tvId.text = "模块ID: ${module.id}"

        val inputs = module.getInputs()
        if (inputs.isEmpty()) {
            tvInputs.text = "无输入参数"
        } else {
            val sb = StringBuilder()
            inputs.forEachIndexed { index, input ->
                sb.append("${index + 1}. ${input.name} (${input.id})\n")
                sb.append("   类型: ${input.staticType.name}")
                if (index < inputs.size - 1) sb.append("\n")
            }
            tvInputs.text = sb.toString()
        }

        val outputs = try { module.getOutputs(null) } catch (e: Exception) { emptyList() }
        if (outputs.isEmpty()) {
            tvOutputs.text = "无输出变量"
        } else {
            val sb = StringBuilder()
            outputs.forEachIndexed { index, output ->
                sb.append("${index + 1}. ${output.name} (${output.id})\n")
                sb.append("   类型: ${output.typeName}")
                if (index < outputs.size - 1) sb.append("\n")
            }
            tvOutputs.text = sb.toString()
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()
        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}