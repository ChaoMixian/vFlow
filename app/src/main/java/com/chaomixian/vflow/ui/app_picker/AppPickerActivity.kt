// 文件: main/java/com/chaomixian/vflow/ui/app_picker/AppPickerActivity.kt
// 描述: 应用选择界面的 Activity。
//      默认显示在启动器中显示的应用（非系统应用）。
//      可通过右上角菜单开关来显示所有应用（包括系统应用）。
//      用户选择一个应用后，会跳转到 ActivityPickerActivity 来选择该应用的具体 Activity。
package com.chaomixian.vflow.ui.app_picker

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SearchView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.ui.common.BaseActivity
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

/**
 * AppPickerActivity 类
 * 继承自 BaseActivity 以应用统一的主题和样式。
 * 职责是展示一个应用列表，让用户选择。
 */
class AppPickerActivity : BaseActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AppListAdapter
    private lateinit var searchView: SearchView

    // 保存完整列表用于本地过滤
    private var allApps: List<AppInfo> = emptyList()

    // 是否显示系统应用的开关状态
    private var showSystemApps = false

    companion object {
        // Intent extra 的键名，用于在 Activity 之间传递所选应用的包名
        const val EXTRA_SELECTED_PACKAGE_NAME = "selected_package_name"
        // Intent extra 的键名，用于在 Activity 之间传递所选 Activity 的名称
        const val EXTRA_SELECTED_ACTIVITY_NAME = "selected_activity_name"
    }

    /**
     * ActivityResultLauncher 用于接收 ActivityPickerActivity 的返回结果。
     * 当用户在下一个界面（ActivityPickerActivity）完成选择后，结果会在这里被处理。
     */
    private val activityPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // 检查返回结果是否是成功的 (RESULT_OK)
        if (result.resultCode == Activity.RESULT_OK) {
            // 如果成功，将结果（包含包名和Activity名）继续向上传递给调用此 Activity 的地方（即 WorkflowEditorActivity）
            setResult(Activity.RESULT_OK, result.data)
            // 完成当前 Activity，返回到工作流编辑器
            finish()
        }
    }

    /**
     * Activity 的创建入口。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_picker) // 加载布局文件

        // 适配全面屏，为顶部栏添加内边距
        val appBarLayout = findViewById<AppBarLayout>(R.id.app_bar_layout)
        ViewCompat.setOnApplyWindowInsetsListener(appBarLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top)
            insets
        }


        // 初始化顶部工具栏
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        // 为工具栏的导航按钮（返回按钮）设置点击监听
        toolbar.setNavigationOnClickListener { finish() }
        // 启用工具栏的选项菜单
        toolbar.inflateMenu(R.menu.app_picker_menu)
        // 获取菜单中的 Chip 控件
        val systemAppChip = toolbar.findViewById<Chip>(R.id.system_app_chip)
        systemAppChip?.apply {
            // 设置当前状态
            isChecked = showSystemApps
            // 设置监听器
            setOnCheckedChangeListener { _, isChecked ->
                if (showSystemApps != isChecked) {
                    showSystemApps = isChecked
                    // 重新加载应用列表
                    loadApps()
                }
            }
        }

        // 初始化 RecyclerView
        recyclerView = findViewById(R.id.recycler_view_apps)

        // 获取搜索框视图
        searchView = findViewById(R.id.search_view)

        // 设置搜索监听
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                filterApps(newText)
                return true
            }
        })

        // 开始异步加载应用列表
        loadApps()
    }

    /**
     * 异步加载设备上的应用列表。
     * 使用协程在IO线程执行耗时的应用查询操作，避免阻塞主线程。
     * 根据 showSystemApps 标志决定是否显示系统应用。
     */
    private fun loadApps() {
        // 创建一个在IO线程池中运行的协程
        CoroutineScope(Dispatchers.IO).launch {
            val pm = packageManager

            val appList = if (showSystemApps) {
                // 显示所有应用（包括系统应用）
                val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

                installedApps.mapNotNull { appInfo ->
                    // 过滤掉没有合适标签或图标的应用
                    val label = appInfo.loadLabel(pm).toString()
                    if (label.isNotEmpty()) {
                        AppInfo(
                            appName = label,
                            packageName = appInfo.packageName,
                            icon = appInfo.loadIcon(pm)
                        )
                    } else {
                        null
                    }
                }
            } else {
                // 只显示在启动器中显示的应用（非系统应用）
                val mainIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }

                val resolveInfos = pm.queryIntentActivities(
                    mainIntent,
                    PackageManager.MATCH_ALL or PackageManager.GET_RESOLVED_FILTER
                )

                // 使用 Set 去重（同一个应用可能有多个 LAUNCHER Activity）
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

            // 切换回主线程来更新UI
            withContext(Dispatchers.Main) {
                allApps = appList
                adapter = AppListAdapter(allApps) { appInfo ->
                    // 定义列表项的点击事件
                    // 当用户点击一个应用时，启动 ActivityPickerActivity
                    val intent = Intent(this@AppPickerActivity, ActivityPickerActivity::class.java).apply {
                        // 将选中的应用包名传递给下一个 Activity
                        putExtra(EXTRA_SELECTED_PACKAGE_NAME, appInfo.packageName)
                    }
                    // 使用我们之前定义的 launcher 来启动 Activity
                    activityPickerLauncher.launch(intent)
                }
                // 将适配器设置给 RecyclerView
                recyclerView.adapter = adapter
            }
        }
    }

    private fun filterApps(query: String?) {
        if (!::adapter.isInitialized) return

        val filteredList = if (query.isNullOrBlank()) {
            allApps
        } else {
            val lowercaseQuery = query.lowercase(Locale.getDefault())
            allApps.filter {
                it.appName.lowercase(Locale.getDefault()).contains(lowercaseQuery) ||
                        it.packageName.lowercase(Locale.getDefault()).contains(lowercaseQuery)
            }
        }
        adapter.updateData(filteredList)
    }
}