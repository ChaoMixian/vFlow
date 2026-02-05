// 文件: main/java/com/chaomixian/vflow/ui/app_picker/SimpleAppPickerActivity.kt
// 描述: 简化版应用选择器，只选择应用包名，不选择具体 Activity。
//      用于 AppStartTrigger 等需要多选应用的模块。
package com.chaomixian.vflow.ui.app_picker

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Bundle
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
 * SimpleAppPickerActivity - 简化版应用选择器
 * 与 AppPickerActivity 不同的是，这个选择器直接返回包名，不需要选择具体的 Activity。
 */
class SimpleAppPickerActivity : BaseActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AppListAdapter
    private lateinit var searchView: SearchView

    private var allApps: List<AppInfo> = emptyList()
    private var showSystemApps = false

    companion object {
        const val EXTRA_SELECTED_PACKAGE_NAME = "selected_package_name"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_picker)

        val appBarLayout = findViewById<AppBarLayout>(R.id.app_bar_layout)
        ViewCompat.setOnApplyWindowInsetsListener(appBarLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top)
            insets
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.inflateMenu(R.menu.app_picker_menu)

        val systemAppChip = toolbar.findViewById<Chip>(R.id.system_app_chip)
        systemAppChip?.apply {
            isChecked = showSystemApps
            setOnCheckedChangeListener { _, isChecked ->
                if (showSystemApps != isChecked) {
                    showSystemApps = isChecked
                    loadApps()
                }
            }
        }

        recyclerView = findViewById(R.id.recycler_view_apps)
        searchView = findViewById(R.id.search_view)

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                filterApps(newText)
                return true
            }
        })

        loadApps()
    }

    private fun loadApps() {
        CoroutineScope(Dispatchers.IO).launch {
            val pm = packageManager

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
                adapter = AppListAdapter(allApps) { appInfo ->
                    // 直接返回包名，不跳转到 ActivityPicker
                    val resultIntent = Intent().apply {
                        putExtra(EXTRA_SELECTED_PACKAGE_NAME, appInfo.packageName)
                    }
                    setResult(Activity.RESULT_OK, resultIntent)
                    finish()
                }
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
