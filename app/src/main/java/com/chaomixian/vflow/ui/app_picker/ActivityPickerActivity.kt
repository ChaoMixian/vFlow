// 文件: main/java/com/chaomixian/vflow/ui/app_picker/ActivityPickerActivity.kt
// 描述: 用于选择指定应用内 Activity 的界面。
package com.chaomixian.vflow.ui.app_picker

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.ui.common.BaseActivity
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar

class ActivityPickerActivity : BaseActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ActivityListAdapter
    private var packageName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_activity_picker)

        // [新增] 适配全面屏，为顶部栏添加内边距
        val appBarLayout = findViewById<AppBarLayout>(R.id.app_bar_layout)
        ViewCompat.setOnApplyWindowInsetsListener(appBarLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top)
            insets
        }

        packageName = intent.getStringExtra(AppPickerActivity.EXTRA_SELECTED_PACKAGE_NAME)
        if (packageName == null) {
            finish()
            return
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        recyclerView = findViewById(R.id.recycler_view_activities)

        loadActivities()
    }

    private fun loadActivities() {
        try {
            val packageInfo = packageManager.getPackageInfo(
                packageName!!,
                PackageManager.GET_ACTIVITIES
            )
            val activities = packageInfo.activities?.toList() ?: emptyList()

            adapter = ActivityListAdapter(activities) { activityName ->
                val resultIntent = Intent().apply {
                    putExtra(AppPickerActivity.EXTRA_SELECTED_PACKAGE_NAME, packageName)
                    putExtra(AppPickerActivity.EXTRA_SELECTED_ACTIVITY_NAME, activityName)
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }
            recyclerView.adapter = adapter

        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            finish()
        }
    }
}