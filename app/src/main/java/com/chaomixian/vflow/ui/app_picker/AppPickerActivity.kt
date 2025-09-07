// 文件: main/java/com/chaomixian/vflow/ui/app_picker/AppPickerActivity.kt
// 描述: 应用选择界面的 Activity。
//      它会显示一个本机安装的非系统应用列表。
//      用户选择一个应用后，会跳转到 ActivityPickerActivity 来选择该应用的具体 Activity。
package com.chaomixian.vflow.ui.app_picker

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.ui.common.BaseActivity
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AppPickerActivity 类
 * 继承自 BaseActivity 以应用统一的主题和样式。
 * 职责是展示一个应用列表，让用户选择。
 */
class AppPickerActivity : BaseActivity() {

    private lateinit var recyclerView: RecyclerView // 用于显示应用列表的视图
    private lateinit var adapter: AppListAdapter // 列表的适配器

    /**
     * 伴生对象，用于存放常量。
     */
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

        // 初始化 RecyclerView
        recyclerView = findViewById(R.id.recycler_view_apps)

        // 开始异步加载应用列表
        loadApps()
    }

    /**
     * 异步加载设备上的应用列表。
     * 使用协程在IO线程执行耗时的应用查询操作，避免阻塞主线程。
     */
    private fun loadApps() {
        // 创建一个在IO线程池中运行的协程
        CoroutineScope(Dispatchers.IO).launch {
            val pm = packageManager // 获取 PackageManager 实例
            // 获取所有已安装应用的列表
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)

            // 过滤、映射和排序应用列表
            val appList = packages
                .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 && pm.getLaunchIntentForPackage(it.packageName) != null } // 只保留非系统且可启动的应用
                .map { appInfo ->
                    // 将 ApplicationInfo 转换为我们自定义的 AppInfo 数据类
                    AppInfo(
                        appName = appInfo.loadLabel(pm).toString(), // 应用名称
                        packageName = appInfo.packageName,         // 应用包名
                        icon = appInfo.loadIcon(pm)                  // 应用图标
                    )
                }
                .sortedBy { it.appName } // 按应用名称排序

            // 切换回主线程来更新UI
            withContext(Dispatchers.Main) {
                // 创建 RecyclerView 的适配器
                adapter = AppListAdapter(appList) { appInfo ->
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
}