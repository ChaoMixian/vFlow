// 文件: main/java/com/chaomixian/vflow/ui/app_picker/UnifiedAppPickerSheet.kt
// 描述: 统一的应用/Activity选择器，支持两种模式：选择应用、选择Activity
package com.chaomixian.vflow.ui.app_picker

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.databinding.SheetUnifiedAppPickerBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 选择模式
 */
enum class AppPickerMode {
    SELECT_APP,       // 只选择应用包名
    SELECT_ACTIVITY   // 选择应用和Activity
}

/**
 * 统一应用选择器
 * 以BottomSheet形式展示，支持展开Activity列表
 */
class UnifiedAppPickerSheet : BottomSheetDialogFragment() {

    private var _binding: SheetUnifiedAppPickerBinding? = null
    private val binding get() = _binding!!

    private lateinit var appAdapter: ExpandableAppListAdapter
    private var allApps: List<AppInfo> = emptyList()

    // 选择模式
    private var mode: AppPickerMode = AppPickerMode.SELECT_ACTIVITY

    // 是否显示系统应用
    private var showSystemApps = false

    // 回调
    private var onResultCallback: ((Intent) -> Unit)? = null

    companion object {
        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_SELECTED_PACKAGE_NAME = "selected_package_name"
        const val EXTRA_SELECTED_ACTIVITY_NAME = "selected_activity_name"

        fun newInstance(mode: AppPickerMode = AppPickerMode.SELECT_ACTIVITY): UnifiedAppPickerSheet {
            return UnifiedAppPickerSheet().apply {
                arguments = Bundle().apply {
                    putString(EXTRA_MODE, mode.name)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.getString(EXTRA_MODE)?.let {
            mode = try { AppPickerMode.valueOf(it) } catch (e: Exception) { AppPickerMode.SELECT_ACTIVITY }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetUnifiedAppPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        setupRecyclerView()
        setupSearch()
        loadApps()
    }

    private fun setupUI() {
        // 根据模式设置标题
        binding.titleText.text = when (mode) {
            AppPickerMode.SELECT_APP -> getString(R.string.text_select_app)
            AppPickerMode.SELECT_ACTIVITY -> getString(R.string.text_select_activity)
        }

        // 系统应用开关
        binding.systemAppChip.apply {
            isChecked = showSystemApps
            setOnCheckedChangeListener { _, isChecked ->
                if (showSystemApps != isChecked) {
                    showSystemApps = isChecked
                    loadApps()
                }
            }
        }

        // 返回按钮
        binding.backButton.setOnClickListener {
            dismiss()
        }
    }

    private fun setupRecyclerView() {
        appAdapter = ExpandableAppListAdapter(
            mode = mode,
            onAppClick = { appInfo ->
                when (mode) {
                    AppPickerMode.SELECT_APP -> {
                        // 直接返回包名
                        val resultIntent = Intent().apply {
                            putExtra(EXTRA_SELECTED_PACKAGE_NAME, appInfo.packageName)
                        }
                        onResultCallback?.invoke(resultIntent)
                        dismiss()
                    }
                    AppPickerMode.SELECT_ACTIVITY -> {
                        // 展开Activity列表或显示Activity选择Sheet
                        if (appAdapter.isExpanded(appInfo)) {
                            appAdapter.collapse(appInfo)
                        } else {
                            // 加载并显示Activity
                            loadActivitiesForApp(appInfo) { activities ->
                                appAdapter.expand(appInfo, activities)
                            }
                        }
                    }
                }
            },
            onActivityClick = { appInfo, activityName ->
                val resultIntent = Intent().apply {
                    putExtra(EXTRA_SELECTED_PACKAGE_NAME, appInfo.packageName)
                    putExtra(EXTRA_SELECTED_ACTIVITY_NAME, activityName)
                }
                onResultCallback?.invoke(resultIntent)
                dismiss()
            }
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = appAdapter
        }
    }

    private fun setupSearch() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                filterApps(newText)
                return true
            }
        })
    }

    private fun loadApps() {
        CoroutineScope(Dispatchers.IO).launch {
            val pm = requireContext().packageManager

            val appList = if (showSystemApps) {
                val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                installedApps.mapNotNull { appInfo ->
                    val label = appInfo.loadLabel(pm).toString()
                    if (label.isNotEmpty()) {
                        AppInfo(
                            appName = label,
                            packageName = appInfo.packageName,
                            icon = appInfo.loadIcon(pm)
                        )
                    } else null
                }
            } else {
                val mainIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfos = pm.queryIntentActivities(
                    mainIntent,
                    PackageManager.MATCH_ALL or PackageManager.GET_RESOLVED_FILTER
                )
                val uniquePackages = mutableMapOf<String, ResolveInfo>()
                for (resolveInfo in resolveInfos) {
                    val packageName = resolveInfo.activityInfo.packageName
                    if (!uniquePackages.containsKey(packageName)) {
                        uniquePackages[packageName] = resolveInfo
                    }
                }
                uniquePackages.values.map { resolveInfo ->
                    val appInfo = resolveInfo.activityInfo.applicationInfo
                    AppInfo(
                        appName = appInfo.loadLabel(pm).toString(),
                        packageName = appInfo.packageName,
                        icon = appInfo.loadIcon(pm)
                    )
                }
            }.sortedBy { it.appName.lowercase(Locale.getDefault()) }

            withContext(Dispatchers.Main) {
                allApps = appList
                appAdapter.updateData(allApps)
            }
        }
    }

    private fun loadActivitiesForApp(appInfo: AppInfo, onLoaded: (List<ActivityItem>) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val pm = requireContext().packageManager
            val activities = mutableListOf<ActivityItem>()

            try {
                val packageInfo = pm.getPackageInfo(
                    appInfo.packageName,
                    PackageManager.GET_ACTIVITIES
                )

                // 添加"启动应用"选项
                activities.add(ActivityItem(name = "LAUNCH", label = getString(R.string.text_launch_app), isExported = true))

                packageInfo.activities?.forEach { activityInfo ->
                    activities.add(
                        ActivityItem(
                            name = activityInfo.name,
                            label = activityInfo.loadLabel(pm).toString(),
                            isExported = activityInfo.exported
                        )
                    )
                }
            } catch (e: Exception) {
                // 忽略错误
            }

            withContext(Dispatchers.Main) {
                onLoaded(activities)
            }
        }
    }

    private fun filterApps(query: String?) {
        if (query.isNullOrBlank()) {
            // 无搜索词时，恢复正常显示
            appAdapter.setSearchQuery("")
            appAdapter.collapseAll()
            appAdapter.updateData(allApps)
        } else {
            val lowercaseQuery = query.lowercase(Locale.getDefault())
            // 先过滤应用
            val filteredApps = allApps.filter {
                it.appName.lowercase(Locale.getDefault()).contains(lowercaseQuery) ||
                        it.packageName.lowercase(Locale.getDefault()).contains(lowercaseQuery)
            }
            appAdapter.updateData(filteredApps)
            appAdapter.setSearchQuery(query)

            // 如果是 SELECT_ACTIVITY 模式，异步加载 Activity 并展开
            if (mode == AppPickerMode.SELECT_ACTIVITY) {
                loadActivitiesForAllApps(filteredApps) { activitiesMap ->
                    appAdapter.expandAll(filteredApps, activitiesMap)
                }
            }
        }
    }

    /**
     * 批量加载多个应用的 Activity
     */
    private fun loadActivitiesForAllApps(
        apps: List<AppInfo>,
        onLoaded: (Map<String, List<ActivityItem>>) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val activitiesMap = mutableMapOf<String, List<ActivityItem>>()
            val pm = requireContext().packageManager

            for (app in apps) {
                try {
                    val packageInfo = pm.getPackageInfo(
                        app.packageName,
                        PackageManager.GET_ACTIVITIES
                    )

                    val activities = mutableListOf<ActivityItem>()
                    // 添加"启动应用"选项
                    activities.add(ActivityItem(name = "LAUNCH", label = getString(R.string.text_launch_app), isExported = true))

                    packageInfo.activities?.forEach { activityInfo ->
                        activities.add(
                            ActivityItem(
                                name = activityInfo.name,
                                label = activityInfo.loadLabel(pm).toString(),
                                isExported = activityInfo.exported
                            )
                        )
                    }
                    activitiesMap[app.packageName] = activities
                } catch (e: Exception) {
                    // 忽略错误
                }
            }

            withContext(Dispatchers.Main) {
                onLoaded(activitiesMap)
            }
        }
    }

    fun setOnResultCallback(callback: (Intent) -> Unit) {
        onResultCallback = callback
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
