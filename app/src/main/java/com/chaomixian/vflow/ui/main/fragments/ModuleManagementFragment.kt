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
import com.chaomixian.vflow.core.locale.toast
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
            listItems.add(ModuleListItem.Header(getString(R.string.header_user_modules, userModules.size)))
            listItems.addAll(userModules.map { ModuleListItem.Item(it as BaseModule) })
        }

        if (builtInModules.isNotEmpty()) {
            listItems.add(ModuleListItem.Header(getString(R.string.header_builtin_modules, builtInModules.size)))
            listItems.addAll(builtInModules.map { ModuleListItem.Item(it as BaseModule) })
        }

        if (listItems.isEmpty()) {
            listItems.add(ModuleListItem.Header(getString(R.string.text_no_modules)))
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
                            .setTitle(getString(R.string.dialog_module_exists_title))
                            .setMessage(getString(R.string.dialog_module_exists_message, session.manifest.id))
                            .setPositiveButton(getString(R.string.dialog_button_overwrite)) { _, _ ->
                                performInstallCommit(session)
                            }
                            .setNegativeButton(android.R.string.cancel, null)
                            .show()
                    } else {
                        performInstallCommit(session)
                    }
                }.onFailure { e ->
                    showErrorDialog(getString(R.string.dialog_parse_module_failed), e.message)
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
                    showErrorDialog(getString(R.string.dialog_install_failed), e.message)
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
                    msg.append(getString(R.string.dialog_module_dependency_message, dependencyNames.size, ""))
                    dependencyNames.forEach { name -> msg.append("• $name\n") }

                    builder.setTitle(getString(R.string.dialog_module_dependency_title))
                        .setMessage(msg.toString())
                        .setPositiveButton(getString(R.string.dialog_button_force_delete)) { _, _ -> deleteModule(module) }
                        .setNegativeButton(android.R.string.cancel, null)
                        .create().apply {
                            setOnShowListener {
                                getButton(android.content.DialogInterface.BUTTON_POSITIVE).setTextColor(
                                    resources.getColor(android.R.color.holo_red_light, null)
                                )
                            }
                        }.show()
                } else {
                    val localizedName = module.metadata.getLocalizedName(requireContext())
                    builder.setTitle(getString(R.string.dialog_delete_module_title))
                        .setMessage(getString(R.string.dialog_delete_module_message, localizedName))
                        .setPositiveButton(android.R.string.ok) { _, _ -> deleteModule(module) }
                        .setNegativeButton(android.R.string.cancel, null)
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
                ModuleRegistry.initialize(requireContext())
                ModuleManager.loadModules(requireContext(), force = true)
                refreshModuleList()
                requireContext().toast(R.string.toast_module_deleted)
            }
        }
    }

    private fun showErrorDialog(title: String, message: String?) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message ?: getString(R.string.error_unknown_error))
            .setPositiveButton(getString(R.string.common_ok), null)
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

                tvName.text = meta.getLocalizedName(context)
                tvDesc.text = meta.getLocalizedDescription(context)
                ivIcon.setImageResource(if (meta.iconRes != 0) meta.iconRes else R.drawable.rounded_circles_ext_24)

                tvCategory.text = meta.category
                tvCategory.visibility = View.VISIBLE

                if (module is ScriptedModule) {
                    btnDelete.visibility = View.VISIBLE
                    btnDelete.setOnClickListener { onDeleteClick(module) }
                    tvAuthorVer.visibility = View.VISIBLE
                    tvAuthorVer.text = getString(R.string.module_author_version, module.author, module.version)
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
            "ACCESSIBILITY" -> getString(R.string.permission_accessibility)
            "OVERLAY" -> getString(R.string.permission_overlay)
            "STORAGE" -> getString(R.string.permission_storage)
            "USAGE_STATS" -> getString(R.string.permission_usage_stats)
            "NOTIFICATION_LISTENER" -> getString(R.string.permission_notification_listener)
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

        val localizedName = module.metadata.getLocalizedName(requireContext())
        tvTitle.text = "$localizedName - ${getString(R.string.label_module_details)}"
        tvId.text = "${getString(R.string.label_module_id)}: ${module.id}"

        val inputs = module.getInputs()
        if (inputs.isEmpty()) {
            tvInputs.text = getString(R.string.label_no_input_params)
        } else {
            val sb = StringBuilder()
            inputs.forEachIndexed { index, input ->
                sb.append("${index + 1}. ${input.getLocalizedName(requireContext())} (${input.id})\n")
                sb.append("   ${getString(R.string.label_param_type)}: ${input.staticType.name}")
                if (index < inputs.size - 1) sb.append("\n")
            }
            tvInputs.text = sb.toString()
        }

        val outputs = try { module.getOutputs(null) } catch (e: Exception) { emptyList() }
        if (outputs.isEmpty()) {
            tvOutputs.text = getString(R.string.label_no_output_vars)
        } else {
            val sb = StringBuilder()
            outputs.forEachIndexed { index, output ->
                sb.append("${index + 1}. ${output.getLocalizedName(requireContext())} (${output.id})\n")
                sb.append("   ${getString(R.string.label_param_type)}: ${output.typeName}")
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