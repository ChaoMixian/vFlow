// 文件: main/java/com/chaomixian/vflow/ui/repository/RepositoryPagerAdapter.kt
package com.chaomixian.vflow.ui.repository

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

/**
 * 仓库PagerAdapter
 * 管理工作流和模块两个Tab
 */
class RepositoryPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> WorkflowRepoFragment()
            1 -> ModuleRepoFragment()
            else -> throw IllegalArgumentException("Invalid position: $position")
        }
    }
}
