package com.chaomixian.vflow.ui.main.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.chaomixian.vflow.R

// 文件：HomeFragment.kt
// 描述：主界面中的“首页”屏幕。

/**
 * “首页” Fragment。
 * 目前简单地加载一个布局文件。
 */
class HomeFragment : Fragment() {

    /** 创建并返回 Fragment 的视图。 */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // 加载 fragment_home 布局
        return inflater.inflate(R.layout.fragment_home, container, false)
    }
}