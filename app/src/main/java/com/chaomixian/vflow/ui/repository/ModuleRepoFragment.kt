// 文件: main/java/com/chaomixian/vflow/ui/repository/ModuleRepoFragment.kt
package com.chaomixian.vflow.ui.repository

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

/**
 * 模块仓库Fragment
 * 暂未实现，占位符
 */
class ModuleRepoFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // TODO: 实现模块仓库功能
        return inflater.inflate(android.R.layout.simple_list_item_1, container, false)
    }

    companion object {
        fun newInstance() = ModuleRepoFragment()
    }
}
